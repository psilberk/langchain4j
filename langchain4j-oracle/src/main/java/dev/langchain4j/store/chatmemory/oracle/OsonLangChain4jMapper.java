package dev.langchain4j.store.chatmemory.oracle;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.JacksonChatMessageJsonCodec;
import oracle.jdbc.provider.oson.OsonFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Direct streaming mapper for LangChain4J {@link ChatMessage} lists to/from Oracle OSON bytes.
 * <p>
 * Leverages <strong>Jackson streaming API compatibility</strong> in Oracle's {@link OsonFactory}:
 * <ul>
 *   <li>{@code LC4J_MAPPER.writeValue(OSON JsonGenerator, messages)} → OSON bytes</li>
 *   <li>{@code LC4J_MAPPER.readValue(OSON JsonParser, TypeReference)} → messages</li>
 * </ul>
 * <p>
 * <strong>Zero intermediates</strong>: No {@link com.fasterxml.jackson.databind.JsonNode}, no strings, no manual dispatch.
 * LangChain4J's pre-configured {@link ObjectMapper} (with mixins for polymorphism) serializes directly to OSON stream.
 * <p>
 * Requires Oracle JDBC 23ai+ for full OSON streaming support.
 */
final class OsonLangChain4jMapper {

    private static final ObjectMapper LC4J_MAPPER = createMapper();

    private static final TypeReference<List<ChatMessage>> CHAT_MESSAGE_LIST_TYPE =
            new TypeReference<>() {};

    private static final OsonFactory OSON_FACTORY = new OsonFactory();

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = JacksonChatMessageJsonCodec.chatMessageJsonMapperBuilder().build();
        mapper.configOverride(List.class)
                .setInclude(JsonInclude.Value.construct(
                        JsonInclude.Include.NON_EMPTY,
                        JsonInclude.Include.USE_DEFAULTS));
        return mapper;
    }

    /**
     * Serializes {@link List&lt;ChatMessage&gt;} directly to OSON bytes via streaming.
     */

    static byte[] toOsonBytes(List<ChatMessage> messages) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator gen = OSON_FACTORY.createGenerator(out)) {
            LC4J_MAPPER.writeValue(gen, messages);
            gen.flush();
        }
        return out.toByteArray();
    }

    /**
     * Deserializes OSON bytes directly to {@link List&lt;ChatMessage&gt;} via streaming.
     */
    static List<ChatMessage> fromOsonBytes(byte[] osonBytes) throws IOException {
        if (osonBytes == null || osonBytes.length == 0) {
            return List.of();
        }
        try (JsonParser parser = OSON_FACTORY.createParser(osonBytes)) {
            return LC4J_MAPPER.readValue(parser, CHAT_MESSAGE_LIST_TYPE);
        }
    }

    private OsonLangChain4jMapper() {}
}
