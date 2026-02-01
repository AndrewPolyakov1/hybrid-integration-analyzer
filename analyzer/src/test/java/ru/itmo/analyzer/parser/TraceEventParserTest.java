package ru.itmo.analyzer.parser;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.fail;

class TraceEventParserTest {
    Logger logger = Logger.getLogger(TraceEventParserTest.class.getName());

    @Test
    void shouldParseSimple() {
        File source = new File("src/test/resources/agent-method-calls.jsonl");
        TraceEventParser traceEventParser = new TraceEventParser();
        try {
            var result = traceEventParser.parse(source);
            logger.info(String.valueOf(result));
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }
}