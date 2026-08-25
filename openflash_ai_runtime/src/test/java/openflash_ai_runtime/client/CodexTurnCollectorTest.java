package openflash_ai_runtime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import openflash_ai_runtime.common.AiErrorCode;
import openflash_ai_runtime.common.CodexAppException;
import org.junit.jupiter.api.Test;

class CodexTurnCollectorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void interleavedThreadsAndTurnsNeverMixText() throws Exception {
        CodexTurnCollector first = collector(7, "thread-1", new ArrayList<>());
        CodexTurnCollector second = collector(7, "thread-2", new ArrayList<>());
        first.bindTurn("thread-1", "turn-1");
        second.bindTurn("thread-2", "turn-2");

        notifyBoth(first, second, "item/agentMessage/delta",
                "{\"threadId\":\"thread-2\",\"turnId\":\"turn-2\",\"itemId\":\"a2\",\"delta\":\"wrong fallback\"}");
        notifyBoth(first, second, "item/completed",
                item("thread-1", "turn-1", "a1", "first"));
        notifyBoth(first, second, "item/completed",
                item("thread-2", "turn-2", "a2", "second"));
        notifyBoth(first, second, "turn/completed", completed("thread-2", "turn-2", "completed", "[]"));
        notifyBoth(first, second, "turn/completed", completed("thread-1", "turn-1", "completed", "[]"));

        assertEquals("first", first.result().get());
        assertEquals("second", second.result().get());
    }

    @Test
    void buffersEventsBeforeTurnResponseThenValidatesTurnIdentity() throws Exception {
        CodexTurnCollector collector = collector(3, "thread-1", new ArrayList<>());
        collector.accept("item/completed", json(item("thread-1", "turn-1", "a1", "early")));
        collector.accept("turn/completed", json(completed("thread-1", "turn-1", "completed", "[]")));

        assertFalse(collector.result().isDone());
        collector.bindTurn("thread-1", "turn-1");
        assertEquals("early", collector.result().get());

        CodexTurnCollector mismatch = collector(3, "thread-2", new ArrayList<>());
        mismatch.accept("item/completed", json(item("thread-2", "turn-not-response", "a2", "x")));
        mismatch.bindTurn("thread-2", "turn-response");
        assertCode(mismatch, AiErrorCode.AI_CODEX_PROTOCOL_INCOMPATIBLE);
    }

    @Test
    void terminalBeforeMismatchedTurnResponseStillFailsProtocol() {
        CodexTurnCollector collector = collector(3, "thread-1", new ArrayList<>());
        collector.accept("item/completed", json(item("thread-1", "turn-event", "a1", "early")));
        collector.accept("turn/completed", json(completed(
                "thread-1", "turn-event", "completed", "[]")));

        collector.bindTurn("thread-1", "turn-response");

        assertCode(collector, AiErrorCode.AI_CODEX_PROTOCOL_INCOMPATIBLE);
    }

    @Test
    void completedAgentMessagesAreCanonicalOrderedAndDoNotDuplicateDeltas() throws Exception {
        CodexTurnCollector collector = collector(1, "thread-1", new ArrayList<>());
        collector.bindTurn("thread-1", "turn-1");
        collector.accept("item/agentMessage/delta", json(delta("thread-1", "turn-1", "a1", "par")));
        collector.accept("item/agentMessage/delta", json(delta("thread-1", "turn-1", "a1", "tial")));
        collector.accept("item/completed", json(item("thread-1", "turn-1", "a1", "complete one")));
        collector.accept("item/completed", json(item("thread-1", "turn-1", "a2", "complete two")));
        collector.accept("turn/completed", json(completed(
                "thread-1", "turn-1", "completed",
                "[{\"id\":\"a1\",\"type\":\"agentMessage\",\"text\":\"complete one\"},"
                        + "{\"id\":\"a2\",\"type\":\"agentMessage\",\"text\":\"complete two\"}]")));

        assertEquals("complete one\ncomplete two", collector.result().get());
    }

    @Test
    void terminalItemsFillOnlyMissingItemNotifications() throws Exception {
        CodexTurnCollector collector = collector(1, "thread-1", new ArrayList<>());
        collector.bindTurn("thread-1", "turn-1");
        collector.accept("item/completed", json(item("thread-1", "turn-1", "a2", "notified two")));
        collector.accept("turn/completed", json(completed(
                "thread-1", "turn-1", "completed",
                "[{\"id\":\"a1\",\"type\":\"agentMessage\",\"text\":\"fallback one\"},"
                        + "{\"id\":\"a2\",\"type\":\"agentMessage\",\"text\":\"stale two\"}]")));

        assertEquals("fallback one\nnotified two", collector.result().get());
    }

    @Test
    void deltasAreFallbackWhenNoCompleteAgentMessageExists() throws Exception {
        CodexTurnCollector collector = collector(1, "thread-1", new ArrayList<>());
        collector.bindTurn("thread-1", "turn-1");
        collector.accept("item/agentMessage/delta", json(delta("thread-1", "turn-1", "a1", "fall")));
        collector.accept("item/agentMessage/delta", json(delta("thread-1", "turn-1", "a1", "back")));
        collector.accept("turn/completed", json(completed("thread-1", "turn-1", "completed", "[]")));

        assertEquals("fallback", collector.result().get());
    }

    @Test
    void canonicalBlankCompletedMessageRejectsNonBlankDeltaFallback() {
        CodexTurnCollector collector = collector(1, "thread-1", new ArrayList<>());
        collector.bindTurn("thread-1", "turn-1");
        collector.accept("item/agentMessage/delta", json(delta(
                "thread-1", "turn-1", "a1", "must not leak through")));
        collector.accept("item/completed", json(item("thread-1", "turn-1", "a1", "  ")));
        collector.accept("turn/completed", json(completed(
                "thread-1", "turn-1", "completed", "[]")));

        assertCode(collector, AiErrorCode.AI_EMPTY_RESPONSE);
    }

    @Test
    void canonicalBlankTerminalMessageRejectsNonBlankDeltaFallback() {
        CodexTurnCollector collector = collector(1, "thread-1", new ArrayList<>());
        collector.bindTurn("thread-1", "turn-1");
        collector.accept("item/agentMessage/delta", json(delta(
                "thread-1", "turn-1", "a1", "must not leak through")));
        collector.accept("turn/completed", json(completed(
                "thread-1", "turn-1", "completed",
                "[{\"id\":\"a1\",\"type\":\"agentMessage\",\"text\":\"  \"}]")));

        assertCode(collector, AiErrorCode.AI_EMPTY_RESPONSE);
    }

    @Test
    void completedWithoutNonBlankTextUsesExistingEmptyResponseCode() {
        CodexTurnCollector collector = collector(1, "thread-1", new ArrayList<>());
        collector.bindTurn("thread-1", "turn-1");
        collector.accept("item/completed", json(item("thread-1", "turn-1", "a1", "  ")));
        collector.accept("turn/completed", json(completed("thread-1", "turn-1", "completed", "[]")));

        assertCode(collector, AiErrorCode.AI_EMPTY_RESPONSE);
    }

    @Test
    void failedAndUnexpectedInterruptedHaveStableCodes() {
        CodexTurnCollector failed = collector(1, "thread-1", new ArrayList<>());
        failed.bindTurn("thread-1", "turn-1");
        failed.accept("turn/completed", json(completed("thread-1", "turn-1", "failed", "[]")));
        assertCode(failed, AiErrorCode.AI_CODEX_RUNTIME_FAILED);

        CodexTurnCollector interrupted = collector(1, "thread-2", new ArrayList<>());
        interrupted.bindTurn("thread-2", "turn-2");
        interrupted.accept("turn/completed", json(completed("thread-2", "turn-2", "interrupted", "[]")));
        assertCode(interrupted, AiErrorCode.AI_INTERRUPTED);
    }

    @Test
    void duplicateLateTerminalAndUnknownEventsCannotChangeFirstOutcome() throws Exception {
        CodexTurnCollector collector = collector(1, "thread-1", new ArrayList<>());
        collector.bindTurn("thread-1", "turn-1");
        collector.accept("future/unknown", json("{\"threadId\":\"thread-1\",\"turnId\":\"turn-1\"}"));
        collector.accept("item/completed", json(item("thread-1", "turn-1", "a1", "first")));
        collector.accept("turn/completed", json(completed("thread-1", "turn-1", "completed", "[]")));
        collector.accept("turn/completed", json(completed("thread-1", "turn-1", "failed", "[]")));
        collector.accept("item/completed", json(item("thread-1", "turn-1", "a2", "late")));

        assertEquals("first", collector.result().get());
    }

    @Test
    void toolItemsFailClosedAndRequestOneTargetedInterrupt() {
        List<String> interrupts = new ArrayList<>();
        for (String type : List.of(
                "commandExecution", "fileChange", "mcpToolCall", "webSearch",
                "appToolCall", "dynamicToolCall", "collabAgentToolCall", "subAgentActivity",
                "imageView", "imageGeneration", "futureToolKind")) {
            CodexTurnCollector collector = collector(9, "thread-" + type, interrupts);
            collector.bindTurn("thread-" + type, "turn-" + type);
            collector.accept("item/completed", json(toolItem(
                    "thread-" + type, "turn-" + type, type)));
            collector.accept("item/completed", json(toolItem(
                    "thread-" + type, "turn-" + type, type)));
            assertCode(collector, AiErrorCode.AI_CODEX_TOOL_BLOCKED);
        }

        assertEquals(11, interrupts.size());
        assertTrue(interrupts.stream().allMatch(value -> value.startsWith("9:thread-") && value.contains(":turn-")));
    }

    @Test
    void toolItemStartedFailsClosedBeforeCompletionAndRequestsInterrupt() {
        List<String> interrupts = new ArrayList<>();
        CodexTurnCollector collector = collector(9, "thread-1", interrupts);
        collector.bindTurn("thread-1", "turn-1");

        collector.accept("item/started", json(startedItem(
                "thread-1", "turn-1", "tool-1", "commandExecution")));

        assertCode(collector, AiErrorCode.AI_CODEX_TOOL_BLOCKED);
        assertEquals(List.of("9:thread-1:turn-1"), interrupts);
    }

    @Test
    void safeItemStartedDoesNotFailCollector() throws Exception {
        CodexTurnCollector collector = collector(9, "thread-1", new ArrayList<>());
        collector.bindTurn("thread-1", "turn-1");
        collector.accept("item/started", json(startedItem(
                "thread-1", "turn-1", "message-1", "agentMessage")));
        collector.accept("item/completed", json(item(
                "thread-1", "turn-1", "message-1", "answer")));
        collector.accept("turn/completed", json(completed(
                "thread-1", "turn-1", "completed", "[]")));

        assertEquals("answer", collector.result().get());
    }

    @Test
    void malformedKnownEventFailsProtocolAndRequestsInterrupt() {
        List<String> interrupts = new ArrayList<>();
        CodexTurnCollector collector = collector(2, "thread-1", interrupts);
        collector.bindTurn("thread-1", "turn-1");
        collector.accept("item/completed", json(
                "{\"threadId\":\"thread-1\",\"turnId\":\"turn-1\",\"item\":{\"id\":\"a1\"}}"));

        assertCode(collector, AiErrorCode.AI_CODEX_PROTOCOL_INCOMPATIBLE);
        assertEquals(List.of("2:thread-1:turn-1"), interrupts);
    }

    private static CodexTurnCollector collector(
            long generation, String threadId, List<String> interrupts) {
        return new CodexTurnCollector(
                generation,
                threadId,
                (requestedGeneration, requestedThread, requestedTurn) -> interrupts.add(
                        requestedGeneration + ":" + requestedThread + ":" + requestedTurn));
    }

    private static void notifyBoth(
            CodexTurnCollector first, CodexTurnCollector second, String method, String params) {
        JsonNode json = json(params);
        first.accept(method, json);
        second.accept(method, json);
    }

    private static void assertCode(CodexTurnCollector collector, AiErrorCode expected) {
        ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> collector.result().get(500, TimeUnit.MILLISECONDS));
        CodexAppException exception = (CodexAppException) failure.getCause();
        assertEquals(expected, exception.getErrorCode());
    }

    private static String delta(String threadId, String turnId, String itemId, String delta) {
        return "{\"threadId\":\"" + threadId + "\",\"turnId\":\"" + turnId
                + "\",\"itemId\":\"" + itemId + "\",\"delta\":\"" + delta + "\"}";
    }

    private static String item(
            String threadId, String turnId, String itemId, String text) {
        return "{\"threadId\":\"" + threadId + "\",\"turnId\":\"" + turnId
                + "\",\"item\":{\"id\":\"" + itemId
                + "\",\"type\":\"agentMessage\",\"text\":\"" + text + "\"}}";
    }

    private static String toolItem(String threadId, String turnId, String type) {
        return "{\"threadId\":\"" + threadId + "\",\"turnId\":\"" + turnId
                + "\",\"item\":{\"id\":\"tool\",\"type\":\"" + type + "\"}}";
    }

    private static String startedItem(
            String threadId, String turnId, String itemId, String type) {
        return "{\"threadId\":\"" + threadId + "\",\"turnId\":\"" + turnId
                + "\",\"item\":{\"id\":\"" + itemId + "\",\"type\":\"" + type + "\"}}";
    }

    private static String completed(
            String threadId, String turnId, String status, String items) {
        return "{\"threadId\":\"" + threadId + "\",\"turn\":{\"id\":\"" + turnId
                + "\",\"status\":\"" + status + "\",\"items\":" + items + "}}";
    }

    private static JsonNode json(String value) {
        try {
            return JSON.readTree(value);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
