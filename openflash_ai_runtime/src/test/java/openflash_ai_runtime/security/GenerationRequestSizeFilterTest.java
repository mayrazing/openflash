package openflash_ai_runtime.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import openflash_ai_runtime.common.SafeErrorResponseWriter;
import openflash_ai_runtime.validation.GenerationRequestValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class GenerationRequestSizeFilterTest {

    @Test
    void rejectsDeclaredOversizeBodyBeforeReadingOrCallingChain() throws Exception {
        AtomicBoolean bodyRead = new AtomicBoolean();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/internal/core/platform-ai/generations") {
            @Override
            public long getContentLengthLong() {
                return GenerationRequestValidator.MAX_JSON_BODY_BYTES + 1L;
            }

            @Override
            public jakarta.servlet.ServletInputStream getInputStream() {
                bodyRead.set(true);
                throw new AssertionError("declared oversize body must not be read");
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter().doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                chainCalled.set(true));

        assertThat(bodyRead).isFalse();
        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"code\":40001");
    }

    @Test
    void rejectsChunkedOrUnknownLengthOversizeBodyAfterOnlyBoundedRead() throws Exception {
        byte[] content = "x".repeat(GenerationRequestValidator.MAX_JSON_BODY_BYTES + 1)
                .getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/internal/core/platform-ai/generations") {
            @Override
            public long getContentLengthLong() {
                return -1L;
            }
        };
        request.setContent(content);
        request.addHeader("Transfer-Encoding", "chunked");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter().doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"code\":40001");
    }

    @Test
    void exactBoundaryIsReplayedAndOtherPathsAreNotRead() throws Exception {
        byte[] boundary = "x".repeat(GenerationRequestValidator.MAX_JSON_BODY_BYTES)
                .getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest exact = new MockHttpServletRequest(
                "POST", "/api/internal/core/platform-ai/generations");
        exact.setContent(boundary);
        AtomicBoolean exactCalled = new AtomicBoolean();
        filter().doFilter(exact, new MockHttpServletResponse(), (wrapped, ignored) -> {
            exactCalled.set(true);
            assertThat(wrapped.getInputStream().readAllBytes()).isEqualTo(boundary);
        });
        assertThat(exactCalled).isTrue();

        MockHttpServletRequest other = new MockHttpServletRequest(
                "POST", "/api/internal/core/platform-ai/generations/other") {
            @Override
            public jakarta.servlet.ServletInputStream getInputStream() {
                throw new AssertionError("other paths must not be pre-read");
            }
        };
        AtomicBoolean otherCalled = new AtomicBoolean();
        filter().doFilter(other, new MockHttpServletResponse(), (same, ignored) -> {
            otherCalled.set(true);
            assertThat(same).isSameAs(other);
        });
        assertThat(otherCalled).isTrue();
    }

    @Test
    void removesContextPathBeforeClassifyingMatrixGenerationPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/runtime/api/internal/core/platform-ai;matrix=value/generations") {
            @Override
            public jakarta.servlet.ServletInputStream getInputStream() {
                throw new AssertionError("matrix request body must not be read");
            }
        };
        request.setContextPath("/runtime");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter().doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"code\":40001");
    }

    @Test
    void cachedStreamSupportsAllReadFormsAndAccurateReadyFinishedState() throws Exception {
        AtomicReference<jakarta.servlet.http.HttpServletRequest> wrapped =
                new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/internal/core/platform-ai/generations");
        request.setContent("a\nbc".getBytes(StandardCharsets.UTF_8));

        filter().doFilter(request, new MockHttpServletResponse(),
                (candidate, ignored) -> wrapped.set(
                        (jakarta.servlet.http.HttpServletRequest) candidate));

        ServletInputStream single = wrapped.get().getInputStream();
        assertThat(single.isReady()).isTrue();
        assertThat(single.isFinished()).isFalse();
        assertThat(single.read()).isEqualTo('a');
        assertThat(single.isFinished()).isFalse();
        byte[] tail = new byte[3];
        assertThat(single.read(tail, 0, tail.length)).isEqualTo(3);
        assertThat(tail).containsExactly('\n', 'b', 'c');
        assertThat(single.isReady()).isTrue();
        assertThat(single.isFinished()).isTrue();
        assertThat(single.read()).isEqualTo(-1);

        ServletInputStream lines = wrapped.get().getInputStream();
        byte[] line = new byte[8];
        assertThat(lines.readLine(line, 0, line.length)).isEqualTo(2);
        assertThat(new String(line, 0, 2, StandardCharsets.UTF_8)).isEqualTo("a\n");
        assertThat(lines.readAllBytes()).isEqualTo("bc".getBytes(StandardCharsets.UTF_8));
        assertThat(lines.isFinished()).isTrue();
    }

    @Test
    void cachedStreamNotifiesReadListenerExactlyOnceWithoutHoldingItsStateLock()
            throws Exception {
        ServletInputStream stream = cachedStream("abc");
        List<String> events = new ArrayList<>();

        assertThatCode(() -> stream.setReadListener(new ReadListener() {
            @Override
            public void onDataAvailable() throws IOException {
                events.add("data");
                FutureTask<Integer> readElsewhere = new FutureTask<>(stream::read);
                Thread reader = new Thread(readElsewhere, "cached-body-listener-test");
                reader.start();
                try {
                    assertThat(readElsewhere.get(1, TimeUnit.SECONDS)).isEqualTo((int) 'a');
                } catch (Exception failure) {
                    throw new IOException(failure);
                }
                assertThat(stream.readAllBytes()).isEqualTo("bc".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public void onAllDataRead() {
                events.add("all");
            }

            @Override
            public void onError(Throwable throwable) {
                events.add("error");
            }
        })).doesNotThrowAnyException();

        assertThat(events).containsExactly("data", "all");
        assertThat(stream.isFinished()).isTrue();
        assertThat(stream.read()).isEqualTo(-1);
        assertThat(events).containsExactly("data", "all");
        assertThatThrownBy(() -> stream.setReadListener(new NoOpReadListener()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cachedStreamRoutesListenerFailuresToOnErrorWithoutCompletionCallback()
            throws Exception {
        ServletInputStream stream = cachedStream("abc");
        List<String> events = new ArrayList<>();

        assertThatCode(() -> stream.setReadListener(new ReadListener() {
            @Override
            public void onDataAvailable() throws IOException {
                events.add("data");
                throw new IOException("listener-failure");
            }

            @Override
            public void onAllDataRead() {
                events.add("all");
            }

            @Override
            public void onError(Throwable throwable) {
                events.add("error:" + throwable.getClass().getSimpleName());
            }
        })).doesNotThrowAnyException();

        assertThat(events).containsExactly("data", "error:IOException");
    }

    private static ServletInputStream cachedStream(String content) throws Exception {
        AtomicReference<ServletInputStream> stream = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/internal/core/platform-ai/generations");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        filter().doFilter(request, new MockHttpServletResponse(),
                (wrapped, ignored) -> stream.set(wrapped.getInputStream()));
        return stream.get();
    }

    private static final class NoOpReadListener implements ReadListener {
        @Override public void onDataAvailable() { }
        @Override public void onAllDataRead() { }
        @Override public void onError(Throwable throwable) { }
    }

    private static GenerationRequestSizeFilter filter() {
        return new GenerationRequestSizeFilter(
                new SafeErrorResponseWriter(new ObjectMapper()));
    }
}
