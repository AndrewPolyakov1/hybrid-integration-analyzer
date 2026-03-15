package com.company.contest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting main");
        FileUtils fileUtils = new FileUtils();
        fileUtils.openFile("test.txt");
        fileUtils.closeFile();
        log.info("Finishing main");
    }
}