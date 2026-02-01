package ru.itmo.analyzer.deserializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import ru.itmo.analyzer.model.TraceEvent;

public final class TraceObjectMapper {

    private TraceObjectMapper() {}

    public static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();

        SimpleModule module = new SimpleModule();
        module.addDeserializer(TraceEvent.class, new TraceEventDeserializer());

        mapper.registerModule(module);
        return mapper;
    }
}