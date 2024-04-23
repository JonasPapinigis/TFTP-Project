package org.example;

import java.util.logging.*;
public class LogSetup {
    public static void setupLogger() {
        Logger logger = Logger.getLogger("");
        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.ALL);
        logger.addHandler(consoleHandler);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
    }
}