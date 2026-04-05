package com.company.contest;

import com.test.OtherLib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting main");
        OtherLib otherLib = new OtherLib();
        otherLib.doNothing();
        FileUtils fileUtils = new FileUtils();
        fileUtils.openFile("test.txt");
        fileUtils.closeFile();
        otherLib.doNothing();
        log.info("Finishing main");
    }
}