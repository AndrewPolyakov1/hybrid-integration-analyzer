package ru.itmo.analyzer.trace.parser;

import java.io.File;
import java.io.IOException;

public interface Parser {
    Object parse(File file) throws IOException;
}
