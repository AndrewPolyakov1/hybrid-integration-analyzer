package ru.itmo.analyzer.trace;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.itmo.analyzer.trace.model.Argument;
import ru.itmo.analyzer.trace.model.ClassInfo;
import ru.itmo.analyzer.trace.model.MethodInfo;
import ru.itmo.analyzer.trace.model.MethodSignature;
import ru.itmo.analyzer.trace.model.Phase;
import ru.itmo.analyzer.trace.model.ThreadInfo;
import ru.itmo.analyzer.trace.model.Timestamp;
import ru.itmo.analyzer.trace.model.TraceEvent;
import ru.itmo.analyzer.trace.model.TypeInfo;
import ru.itmo.analyzer.trace.model.ValueInfo;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class TraceEventDeserializer extends JsonDeserializer<TraceEvent> {

    @Override
    public TraceEvent deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {

        ObjectNode root = p.getCodec().readTree(p);

        LocalDateTime ldt = LocalDateTime.ofInstant(
                Instant.parse(root.get("timestamp").asText()),
                ZoneOffset.UTC
        );

        Timestamp timestamp = new Timestamp(ldt);
        Phase phase = Phase.valueOf(root.get("phase").asText());
        ThreadInfo thread = new ThreadInfo(root.get("thread").asText());

        ClassInfo classInfo = new ClassInfo(root.get("class").asText());
        MethodInfo method = new MethodInfo(root.get("method").asText());
        MethodSignature signature = new MethodSignature(root.get("signature").asText());
        ClassInfo instanceClass = new ClassInfo(root.get("instanceClass").asText());
        Integer lineNumber = root.get("line").asInt();
        List<Argument> arguments = new ArrayList<>();
        for (JsonNode argNode : root.withArray("arguments")) {
            TypeInfo type = new TypeInfo(argNode.get("type").asText());
            ValueInfo value = new ValueInfo(argNode.get("value").asText());
            arguments.add(new Argument(type, value));
        }

        return new TraceEvent(
                timestamp,
                phase,
                thread,
                classInfo,
                method,
                signature,
                instanceClass,
                arguments,
                lineNumber
        );
    }
}
