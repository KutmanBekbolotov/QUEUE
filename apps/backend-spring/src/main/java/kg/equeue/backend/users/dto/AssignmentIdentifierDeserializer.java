package kg.equeue.backend.users.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;

public class AssignmentIdentifierDeserializer extends JsonDeserializer<String> {

    private static final List<String> DIRECT_FIELDS = List.of(
            "serviceId",
            "service_id",
            "id",
            "uuid",
            "code",
            "value",
            "key",
            "rowKey"
    );

    private static final List<String> NESTED_FIELDS = List.of(
            "service",
            "item",
            "record",
            "option",
            "entity"
    );

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.readValueAsTree();
        return extract(node);
    }

    private String extract(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            return normalize(node.asText());
        }
        if (!node.isObject()) {
            return null;
        }
        for (String field : DIRECT_FIELDS) {
            String value = extract(node.get(field));
            if (value != null) {
                return value;
            }
        }
        for (String field : NESTED_FIELDS) {
            String value = extract(node.get(field));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
