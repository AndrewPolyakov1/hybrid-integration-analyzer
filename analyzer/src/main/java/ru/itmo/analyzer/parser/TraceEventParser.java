package ru.itmo.analyzer.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.itmo.analyzer.deserializer.TraceObjectMapper;
import ru.itmo.analyzer.model.TraceEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TraceEventParser implements Parser {

    private static final ObjectMapper MAPPER = TraceObjectMapper.create();

    @Override
    public List<TraceEvent> parse(File file) throws IOException {
        List<TraceEvent> traceEvents = new ArrayList<>();
        this.parse(file, traceEvents::add);
        return traceEvents;
    }

    public void parse(
            File file,
            Consumer<TraceEvent> consumer
    ) throws IOException {
        JsonFactory factory = MAPPER.getFactory();

        try (JsonParser parser = factory.createParser(file)) {
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                consumer.accept(
                        MAPPER.readValue(parser, TraceEvent.class)
                );
            }
        }
    }
}
