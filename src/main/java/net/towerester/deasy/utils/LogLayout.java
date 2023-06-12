package net.towerester.deasy.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.LayoutBase;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * Implementation of layout base class for logback logger**/
public class LogLayout extends LayoutBase<ILoggingEvent> {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLACK = "\u001B[30m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";

    @Override
    public String doLayout(ILoggingEvent event) {
        StringBuilder str = new StringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        str.append(ANSI_BLACK);
        str.append(dateFormat.format(new Date()));
        str.append(ANSI_RESET);
        str.append(" ");
        str.append(event.getLevel() == Level.DEBUG ? ANSI_GREEN : (event.getLevel() == Level.ERROR ? ANSI_RED : (event.getLevel() == Level.INFO ? ANSI_WHITE : ANSI_YELLOW)));
        str.append(event.getLevel());
        str.append(ANSI_RESET);
        str.append(" ");
        str.append(ANSI_CYAN);
        str.append("[");

        try {
            str.append(Class.forName(event.getLoggerName()).getSimpleName());
        } catch(ClassNotFoundException e) {
            str.append(event.getLoggerName());
        }

        str.append("]");
        str.append(": ");
        str.append(ANSI_RESET);
        str.append(event.getFormattedMessage());

        if(event.getThrowableProxy() != null) {
            str.append("\n");
            str.append(ANSI_RED);
            str.append(event.getThrowableProxy().getClassName());
            str.append(": ");
            str.append(event.getThrowableProxy().getMessage());

            Arrays.stream(event.getThrowableProxy().getStackTraceElementProxyArray()).forEach(elementProxy -> {
                StackTraceElement element = elementProxy.getStackTraceElement();

                str.append("\n");
                str.append("\t");
                str.append(ANSI_RED);
                str.append("at ");
                str.append(element.getClassName());
                str.append(":");
                str.append(element.getMethodName());
                str.append("():");
                str.append(element.getLineNumber());
            });

            str.append(ANSI_RESET);
        }

        str.append(CoreConstants.LINE_SEPARATOR);

        return str.toString();
    }
}
