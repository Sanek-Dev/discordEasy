package net.towerester.deasy.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogLayoutTest {
    private static final Logger logger = LoggerFactory.getLogger(LogLayoutTest.class);

    @Test
    public void printException() {
        logger.error("Exception!", new RuntimeException("Test"));
    }

    @Test
    public void printInfo() {
        logger.info("Test");

        String s = "hello@hellomail.com";
        boolean res = s.matches(".*@.*\\.[a-w]+");

        Assertions.assertTrue(res);
    }
}
