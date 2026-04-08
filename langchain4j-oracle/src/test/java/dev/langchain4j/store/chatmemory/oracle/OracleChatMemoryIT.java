package dev.langchain4j.store.chatmemory.oracle;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import oracle.jdbc.pool.OracleDataSource;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.oracle.OracleContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OracleChatMemoryIT {
    private static String jdbcUrl;
    private static String userName;
    private static String password;

    private final String userId = "user123-sessionB123";
    private static OracleMemoryStore oracleMemoryStore;

@BeforeAll
static void beforeAll() {
    jdbcUrl  = System.getenv("ORACLE_JDBC_URL");
    userName = System.getenv("ORACLE_USERNAME");
    password = System.getenv("ORACLE_PASSWORD");

    // Skip integration tests if not configured
    assumeTrue(jdbcUrl != null ,
            "Local Oracle DB env vars not set; skipping integration tests");
}
// Create the table if it doesn't exist and ensure it's empty
@BeforeEach
void setUp() throws SQLException {

    createTable("chat_memorystore");
    oracleMemoryStore.deleteMessages(userId);
    assertThat(oracleMemoryStore.getMessages(userId)).isEmpty();


}


@AfterEach
void deleteTable() throws SQLException {


    oracleMemoryStore.deleteMessages(userId);

    }

/**
* Verifies OracleMemoryStore can persist and retrieve a list of chat messages
* (System + User with TextContent) in Oracle DB.
*/

@Test
void set_ai_message_into_oracle() {
    assertThat(oracleMemoryStore.getMessages(userId)).isEmpty();

    List<ChatMessage> chatMessages = new ArrayList<>();
    chatMessages.add(new SystemMessage("System prompt"));
    chatMessages.add(new UserMessage("Hello", List.of(new TextContent("Hi there"))));
    chatMessages.add(aiMessage("Hello! How can I help?"));

    oracleMemoryStore.updateMessages(userId, chatMessages);

    List<ChatMessage> loaded = oracleMemoryStore.getMessages(userId);
    assertThat(loaded).hasSize(3);
    assertThat(loaded.get(0)).isInstanceOf(SystemMessage.class);
    assertThat(loaded.get(1)).isInstanceOf(UserMessage.class);
    assertThat(loaded.get(2)).isInstanceOf(AiMessage.class);

    assertThat(((SystemMessage) loaded.get(0)).text()).isEqualTo("System prompt");
    assertThat(((AiMessage) loaded.get(2)).text()).isEqualTo("Hello! How can I help?");
}
    @Test
    void set_tool_execution_result_message_into_oracle() {
        assertThat(oracleMemoryStore.getMessages(userId)).isEmpty();

        ToolExecutionResultMessage toolResult = ToolExecutionResultMessage.builder()
                .id("call_1")
                .toolName("my_tool")
                .text("{\"status\":\"ok\"}")
                .isError(false)
                .build();

        List<ChatMessage> chatMessages = List.of(
                new SystemMessage("System prompt"),
                toolResult
        );

        oracleMemoryStore.updateMessages(userId, chatMessages);

        List<ChatMessage> loaded = oracleMemoryStore.getMessages(userId);
        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(1)).isInstanceOf(ToolExecutionResultMessage.class);

        ToolExecutionResultMessage loadedMsg = (ToolExecutionResultMessage) loaded.get(1);
        assertThat(loadedMsg.id()).isEqualTo("call_1");
        assertThat(loadedMsg.toolName()).isEqualTo("my_tool");
        assertThat(loadedMsg.text()).contains("ok");
        assertThat(loadedMsg.isError()).isFalse();
    }
    @Test
    void set_ai_message_with_tool_execution_request_into_oracle() {
        assertThat(oracleMemoryStore.getMessages(userId)).isEmpty();

        ToolExecutionRequest req = ToolExecutionRequest.builder()
                .id("call_99")
                .name("lookupCustomer")
                .arguments("{\"customerId\":123}")
                .build();

        AiMessage ai = AiMessage.builder()
                .text("Calling a tool...")
                .toolExecutionRequests(List.of(req))
                .build();

        oracleMemoryStore.updateMessages(userId, List.of(new SystemMessage("System prompt"), ai));

        List<ChatMessage> loaded = oracleMemoryStore.getMessages(userId);
        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(1)).isInstanceOf(AiMessage.class);

        AiMessage loadedAi = (AiMessage) loaded.get(1);
        assertThat(loadedAi.toolExecutionRequests()).hasSize(1);
        assertThat(loadedAi.toolExecutionRequests().get(0).id()).isEqualTo("call_99");
        assertThat(loadedAi.toolExecutionRequests().get(0).name()).isEqualTo("lookupCustomer");
    }
    @Test
    void set_custom_message_into_oracle() {
        assertThat(oracleMemoryStore.getMessages(userId)).isEmpty();

        CustomMessage custom = new CustomMessage(Map.of(
                "event", "handoff",
                "queue", "support-l2",
                "priority", 5
        ));

        oracleMemoryStore.updateMessages(userId, List.of(new SystemMessage("System prompt"), custom));

        List<ChatMessage> loaded = oracleMemoryStore.getMessages(userId);
        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(1)).isInstanceOf(CustomMessage.class);

        CustomMessage loadedCustom = (CustomMessage) loaded.get(1);
        assertThat(loadedCustom.attributes()).containsEntry("event", "handoff");
        assertThat(loadedCustom.attributes()).containsEntry("queue", "support-l2");
    }
@Test
void set_messages_into_oracle() {
    // getmessages and check if they are empty
    List<ChatMessage> messages = oracleMemoryStore.getMessages(userId);
    assertThat(messages).isEmpty();

    // setting up instruction for system
    List<ChatMessage> chatMessages = new ArrayList<>();
    chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
    //add content to messtages(ask the question)
    List<Content> userMsgContents = new ArrayList<>();
    userMsgContents.add(new TextContent("sometextcontent"));
    chatMessages.add(new UserMessage("What do you see in this text?", userMsgContents));
    oracleMemoryStore.updateMessages(userId, chatMessages);

    // check size of messages
    List<ChatMessage> newMessages = oracleMemoryStore.getMessages(userId);
    assertThat(newMessages).hasSize(2);
}
    /**
     * Verifies OracleMemoryStore can persist and retrieve a list of chat messages
     * (System + User with ImageContent) in Oracle DB.
     */

    @Test
    void set_image_into_oracle() {
        // getmessages and check if they are empty
        List<ChatMessage> messages = oracleMemoryStore.getMessages(userId);
        assertThat(messages).isEmpty();

        // setting up instruction for system
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        //add content to messtages(ask the question)
        List<Content> userMsgContents = new ArrayList<>();
        userMsgContents.add(new ImageContent("https://commons.wikimedia.org/wiki/File:Logo_oracle.jpg"));
        chatMessages.add(new UserMessage("What do you see in this image?", userMsgContents));
        oracleMemoryStore.updateMessages(userId, chatMessages);
        // check size of messages
        List<ChatMessage> newMessages = oracleMemoryStore.getMessages(userId);
        assertThat(newMessages).hasSize(2);
    }
    /**
     * Verifies OracleMemoryStore can persist and retrieve a list of chat messages
     * (System + User with PdfFileContent) in Oracle DB.
     */
    @Test
    void set_pdf_into_oracle() {
        // getmessages and check if they are empty
        List<ChatMessage> messages = oracleMemoryStore.getMessages(userId);
        assertThat(messages).isEmpty();

        // setting up instruction for system
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        //add content to messtages(ask the question)
        List<Content> userMsgContents = new ArrayList<>();
        userMsgContents.add(new PdfFileContent("somepdfcontent"));
        chatMessages.add(new UserMessage("What do you see in this pdf?", userMsgContents));
        oracleMemoryStore.updateMessages(userId, chatMessages);
        // check size of messages
        List<ChatMessage> newMessages = oracleMemoryStore.getMessages(userId);
        assertThat(newMessages).hasSize(2);
    }

    /**
     * Verifies OracleMemoryStore can persist and retrieve a list of chat messages
     * (System + User with AudioContent) in Oracle DB.
     */
    @Test
    void set_audio_into_oracle() {
        // getmessages and check if they are empty
        List<ChatMessage> messages = oracleMemoryStore.getMessages(userId);
        assertThat(messages).isEmpty();

        // setting up instruction for system
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        //add content to messtages(ask the question)
        List<Content> userMsgContents = new ArrayList<>();
        userMsgContents.add(new AudioContent("someaudiocontent"));
        chatMessages.add(new UserMessage("What do you see in this audio?", userMsgContents));
        oracleMemoryStore.updateMessages(userId, chatMessages);
        // check size of messages
        List<ChatMessage> newMessages = oracleMemoryStore.getMessages(userId);
        assertThat(newMessages).hasSize(2);
    }
    /**
     * Verifies OracleMemoryStore can persist and retrieve a list of chat messages
     * (System + User with VideoContent) in Oracle DB.
     */
    @Test
    void set_video_into_oracle() {
        // getmessages and check if they are empty
        List<ChatMessage> messages = oracleMemoryStore.getMessages(userId);
        assertThat(messages).isEmpty();

        // setting up instruction for system
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        //add content to messtages(ask the question)
        List<Content> userMsgContents = new ArrayList<>();
        userMsgContents.add(new VideoContent("somevideocontent"));
        chatMessages.add(new UserMessage("What do you see in this video?", userMsgContents));
        oracleMemoryStore.updateMessages(userId, chatMessages);
        // check size of messages
        List<ChatMessage> newMessages = oracleMemoryStore.getMessages(userId);
        assertThat(newMessages).hasSize(2);
    }


    /**
*Verifies OracleMemoryStore can delete stored messages
* for a given memory/user id from Oracle DB
*/

@Test
void delete_messages_from_oracle() {
    // get messages of the memoryId
    List<ChatMessage> chatMessages = new ArrayList<>();
    chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
    oracleMemoryStore.updateMessages(userId, chatMessages);
    List<ChatMessage> messages = oracleMemoryStore.getMessages(userId);

    //check if there is only the system message
    assertThat(messages).hasSize(1);

    // delete all messages
    oracleMemoryStore.deleteMessages(userId);

    // check if the messages isEmpty
    messages = oracleMemoryStore.getMessages(userId);

    assertThat(messages).isEmpty();
}

/**
* Verifies OracleMemoryStore TTL behavior, messages expire and are no
* longer retrievable from Oracle DB after the configured TTL
*/
@Test
void set_messages_with_ttl_into_oracle() throws SQLException {
    OracleDataSource oracleDataSource=new OracleDataSource();
    oracleDataSource.setURL(jdbcUrl);
    oracleDataSource.setUser(userName);
    oracleDataSource.setPassword(password);
    // create new OracleMemoryStore that contains ttl > 0
    OracleMemoryStore ttlMemoryStore=OracleMemoryStore
            .builder()
            .oracleDataSource(oracleDataSource)
            .tableName("chat_memorystore")
            .ttl(Duration.ofSeconds(2))
            .build();
    // get the messages and check if isEmpty
    List<ChatMessage> messages = ttlMemoryStore.getMessages(userId);
    assertThat(messages).isEmpty();

  //add content to messtages(URL,ask the question)
    List<ChatMessage> chatMessages = new ArrayList<>();
    chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
    List<Content> userMsgContents = new ArrayList<>();
    userMsgContents.add(new TextContent("sometextcontent"));
    chatMessages.add(new UserMessage("What do you see in this text?", userMsgContents));

    ttlMemoryStore.updateMessages(userId, chatMessages);

    // check size of new messages
    messages = ttlMemoryStore.getMessages(userId);

    assertThat(messages).hasSize(2);

    // wait for 4 seconds to check if the messages are deleted
    try {
        Thread.sleep(3000);
    } catch (InterruptedException e) {
        throw new RuntimeException(e);
    }

    // verify that messages
    messages = ttlMemoryStore.getMessages(userId);
    assertThat(messages).isEmpty();
}
/**
*Verifies input validation: getMessages rejects null
*memoryId and throws OracleChatMemoryStoreException
*/
@Test
void getMessages_should_throw_exception_when_memoryId_null() {
    assertThatThrownBy(() -> oracleMemoryStore.getMessages(null))
            .isInstanceOf(IllegalArgumentException.class)
            ;
}

/**
* Verifies input validation: getMessages rejects empty
* memoryId and throws OracleChatMemoryStoreException
*/

@Test
void getMessages_should_throw_exception_when_memoryId_empty() {
    assertThatThrownBy(() -> oracleMemoryStore.getMessages("   "))
            .isInstanceOf(IllegalArgumentException.class)
            ;
}
/**
*Verifies input validation: updateMessages rejects null
*messages and throws OracleChatMemoryStoreException
*/

    @Test
    void updateMessages_should_throw_exception_when_messages_null() {
        assertThatThrownBy(() -> oracleMemoryStore.updateMessages(userId, null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                ;
    }

/**
* Verifies input validation: updateMessages rejects empty
* messages and throws OracleChatMemoryStoreException
*/

    @Test
    void updateMessages_should_throw_exception_when_messages_empty() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        assertThatThrownBy(() -> oracleMemoryStore.updateMessages(userId, chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
               ;
    }

/**
* Verifies input validation: updateMessages rejects null
* memoryId and throws OracleChatMemoryStoreException
*/

    @Test
    void updateMessages_should_throw_exception_when_memoryId_null() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        assertThatThrownBy(() -> oracleMemoryStore.updateMessages(null, chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                ;
    }

/**
* Verifies input validation: updateMessages rejects empty
* memoryId and throws OracleChatMemoryStoreException
*/

    @Test
    void updateMessages_memoryId_empty() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        assertThatThrownBy(() -> oracleMemoryStore.updateMessages("   ", chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                ;
    }

/**
* Verifies input validation: deleteMessages rejects null
* memoryId and throws OracleChatMemoryStoreException
*/

    @Test
    void deleteMessages_should_throw_exception_when_memoryId_null() {
        assertThatThrownBy(() -> oracleMemoryStore.deleteMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                ;
    }

/**
* Verifies input validation: deleteMessages rejects empty
* memoryId and throws OracleChatMemoryStoreException
*/

    @Test
    void deleteMessages_should_throw_exception_when_memoryId_empty() {
        assertThatThrownBy(() -> oracleMemoryStore.deleteMessages("   "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
               ;
    }
    private static void createTable(String tableName) throws SQLException {
        OracleDataSource oracleDataSource=new OracleDataSource();
        oracleDataSource.setURL(jdbcUrl);
        oracleDataSource.setUser(userName);
        oracleDataSource.setPassword(password);
        oracleMemoryStore=OracleMemoryStore
                .builder()
                .oracleDataSource(oracleDataSource)
                .tableName(tableName)
                .ttl(Duration.ZERO)
                .build();
        try (Connection connection = oracleDataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("CREATE TABLE IF NOT EXISTS " + tableName + " ("
                    + "  memory_id     VARCHAR2(200) NOT NULL, "
                    + "  messages_json JSON NOT NULL, "
                    + "  expires_at    TIMESTAMP NULL, "
                    + "  PRIMARY KEY (memory_id)"
                    + ")");
        }
    }



}
