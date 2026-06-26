package dev.langchain4j.store.memory.chat.oracle;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.JacksonChatMessageJsonCodec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import oracle.jdbc.provider.oson.OsonFactory;

/**
 * Maps LangChain4j chat message lists to and from Oracle JSON/OSON bytes.
 */
final class OracleJsonChatMessageListMapper {

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

    static byte[] toOsonBytes(List<ChatMessage> messages) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator generator = OSON_FACTORY.createGenerator(out)) {
            LC4J_MAPPER.writeValue(generator, messages);
            generator.flush();
        }
        return out.toByteArray();
    }

    static List<ChatMessage> fromOsonBytes(byte[] osonBytes) throws IOException {
        if (osonBytes == null || osonBytes.length == 0) {
            return List.of();
        }
        try (JsonParser parser = OSON_FACTORY.createParser(osonBytes)) {
            return LC4J_MAPPER.readValue(parser, CHAT_MESSAGE_LIST_TYPE);
        }
    }

    private OracleJsonChatMessageListMapper() {}
}
