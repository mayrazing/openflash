package openflash_plugin.ai_card.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** 捕获预期日志，同时阻止异常堆栈传播到测试控制台。 */
final class ExpectedLogCapture implements AutoCloseable {

    private final Logger logger;
    private final boolean originalAdditive;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private ExpectedLogCapture(Class<?> source) {
        logger = (Logger) LoggerFactory.getLogger(source);
        originalAdditive = logger.isAdditive();
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);
    }

    static ExpectedLogCapture capture(Class<?> source) {
        return new ExpectedLogCapture(source);
    }

    List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setAdditive(originalAdditive);
        appender.stop();
    }
}
