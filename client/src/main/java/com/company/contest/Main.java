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
        File fileUtils = new File();
        fileUtils.open("test.txt");
        fileUtils.read();
        fileUtils.close();
        otherLib.doNothing();

        TestIntegrationLib testIntegrationLib = new TestIntegrationLib();
        testIntegrationLib.changePrivate(true);
        testIntegrationLib.changePrivate(false);

        File file = new File();
        file.open();

        file.read();
        file.read();

        file.close();

        log.info("Finishing main");
    }
}