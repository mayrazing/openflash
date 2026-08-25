package openflash_ai_runtime.common;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Captures expected Codex warnings without forwarding stack traces to the console. */
public final class CodexLogCapture implements AutoCloseable {

    private final Logger logger;
    private final boolean originalAdditive;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private CodexLogCapture(Class<?> source) {
        logger = (Logger) LoggerFactory.getLogger(source);
        originalAdditive = logger.isAdditive();
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);
    }

    public static CodexLogCapture capture(Class<?> source) {
        return new CodexLogCapture(source);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setAdditive(originalAdditive);
        appender.stop();
    }
}
