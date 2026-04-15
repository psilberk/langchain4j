package dev.langchain4j.store.chatmemory.oracle;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.oracle.OracleContainer;

/**
 * Integration tests for {@link OracleMemoryStore} chat-memory persistence against Oracle Database.
 */
public class OracleChatMemoryIT {

    private static final String TABLE_NAME = "chat_memorystore";
    private static final String ORACLE_IMAGE_NAME = "gvenzl/oracle-free:23.6-faststart";

    private static String jdbcUrl;
    private static String userName;
    private static String password;
    private static OracleContainer oracleContainer;

    private final String userId = "user123-sessionB123";
    private static OracleMemoryStore oracleMemoryStore;

    @BeforeAll
    static void beforeAll() {
        String urlFromEnv = System.getenv("ORACLE_JDBC_URL");
        if (urlFromEnv==null) {
            try {
                oracleContainer = new OracleContainer(ORACLE_IMAGE_NAME)
                        .withStartupTimeout(Duration.ofMinutes(20))
                        .withConnectTimeoutSeconds(60 * 20)
                        .withDatabaseName("pdb1")
                        .withUsername("testuser")
                        .withPassword("testpwd");
                oracleContainer.start();

                jdbcUrl = oracleContainer.getJdbcUrl();
                userName = oracleContainer.getUsername();
                password = oracleContainer.getPassword();
                return;
            } catch (RuntimeException e) {
                if (oracleContainer != null) {
                    try {
                        oracleContainer.stop();
                    } catch (RuntimeException ignored) {
                        // Ignore and fallback to environment-based DB config.
                    }
                }
                oracleContainer = null;
            }
        }
        else{
            jdbcUrl = System.getenv("ORACLE_JDBC_URL");
            userName = System.getenv("ORACLE_JDBC_USER");
            password = System.getenv("ORACLE_JDBC_PASSWORD");
        }


        assumeTrue(
                jdbcUrl != null && userName != null && password != null,
                "Container unavailable and ORACLE_JDBC_* env vars not set; skipping OracleChatMemoryIT");
    }
    @AfterAll
    static void afterAll() {
        if (oracleContainer != null) {
            oracleContainer.stop();
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        createTable(TABLE_NAME);
        oracleMemoryStore.deleteMessages(userId);
        assertThat(oracleMemoryStore.getMessages(userId)).isEmpty();
    }

    @AfterEach
    void tearDown() {
        if (oracleMemoryStore != null) {
            oracleMemoryStore.deleteMessages(userId);
        }
    }

    /**
     * Verifies that a mixed sequence of SYSTEM, USER, and AI messages is persisted and loaded in order.
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

    /**
     * Verifies that {@link ToolExecutionResultMessage} is persisted with all relevant fields.
     */
    @Test
    void set_tool_execution_result_message_into_oracle() {
        assertThat(oracleMemoryStore.getMessages(userId)).isEmpty();

        ToolExecutionResultMessage toolResult = ToolExecutionResultMessage.builder()
                .id("call_1")
                .toolName("my_tool")
                .text("{\"status\":\"ok\"}")
                .isError(false)
                .build();

        oracleMemoryStore.updateMessages(userId, List.of(new SystemMessage("System prompt"), toolResult));

        List<ChatMessage> loaded = oracleMemoryStore.getMessages(userId);
        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(1)).isInstanceOf(ToolExecutionResultMessage.class);

        ToolExecutionResultMessage loadedMsg = (ToolExecutionResultMessage) loaded.get(1);
        assertThat(loadedMsg.id()).isEqualTo("call_1");
        assertThat(loadedMsg.toolName()).isEqualTo("my_tool");
        assertThat(loadedMsg.text()).contains("ok");
        assertThat(loadedMsg.isError()).isFalse();
    }

    /**
     * Verifies that AI tool execution requests are preserved across persistence.
     */
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

    /**
     * Verifies that {@link CustomMessage} attributes are persisted and restored.
     */
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

    /**
     * Verifies that standard SYSTEM + USER text-content messages are persisted.
     */
    @Test
    void set_messages_into_oracle() {
        assertThat(oracleMemoryStore.getMessages(userId)).isEmpty();

        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));

        List<Content> userMsgContents = new ArrayList<>();
        userMsgContents.add(new TextContent("sometextcontent"));
        chatMessages.add(new UserMessage("What do you see in this text?", userMsgContents));

        oracleMemoryStore.updateMessages(userId, chatMessages);

        List<ChatMessage> loaded = oracleMemoryStore.getMessages(userId);
        assertThat(loaded).hasSize(2);
    }

    /**
     * Verifies that USER messages with {@link ImageContent} are persisted.
     */
    @Test
    void set_image_into_oracle() {
        assertThat(oracleMemoryStore.getMessages(userId)).isEmpty();

        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));

        List<Content> userMsgContents = new ArrayList<>();
        userMsgContents.add(new ImageContent("https://commons.wikimedia.org/wiki/File:Logo_oracle.jpg"));
        chatMessages.add(new UserMessage("What do you see in this image?", userMsgContents));

        oracleMemoryStore.updateMessages(userId, chatMessages);

        List<ChatMessage> loaded = oracleMemoryStore.getMessages(userId);
        assertThat(loaded).hasSize(2);
    }

    /**
     * Verifies that USER messages with {@link PdfFileContent} are persisted.
     */
    @Test
    void set_pdf_into_oracle() {
        assertThat(oracleMemoryStore.getMessages(userId)).isEmpty();

        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));

        List<Content> userMsgContents = new ArrayList<>();
        userMsgContents.add(new PdfFileContent("somepdfcontent"));
        chatMessages.add(new UserMessage("What do you see in this pdf?", userMsgContents));

        oracleMemoryStore.updateMessages(userId, chatMessages);

        List<ChatMessage> loaded = oracleMemoryStore.getMessages(userId);
        assertThat(loaded).hasSize(2);
    }

    /**
     * Verifies that USER messages with {@link AudioContent} are persisted.
     */
    @Test
    void set_audio_into_oracle() {
        assertThat(oracleMemoryStore.getMessages(userId)).isEmpty();

        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));

        List<Content> userMsgContents = new ArrayList<>();
        userMsgContents.add(new AudioContent("someaudiocontent"));
        chatMessages.add(new UserMessage("What do you see in this audio?", userMsgContents));

        oracleMemoryStore.updateMessages(userId, chatMessages);

        List<ChatMessage> loaded = oracleMemoryStore.getMessages(userId);
        assertThat(loaded).hasSize(2);
    }

    /**
     * Verifies that USER messages with {@link VideoContent} are persisted.
     */
    @Test
    void set_video_into_oracle() {
        assertThat(oracleMemoryStore.getMessages(userId)).isEmpty();

        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));

        List<Content> userMsgContents = new ArrayList<>();
        userMsgContents.add(new VideoContent("somevideocontent"));
        chatMessages.add(new UserMessage("What do you see in this video?", userMsgContents));

        oracleMemoryStore.updateMessages(userId, chatMessages);

        List<ChatMessage> loaded = oracleMemoryStore.getMessages(userId);
        assertThat(loaded).hasSize(2);
    }

    /**
     * Verifies that deleting a memory id removes all its messages.
     */
    @Test
    void delete_messages_from_oracle() {
        List<ChatMessage> chatMessages = List.of(new SystemMessage("You are a large language model working with Langchain4j"));
        oracleMemoryStore.updateMessages(userId, chatMessages);

        assertThat(oracleMemoryStore.getMessages(userId)).hasSize(1);

        oracleMemoryStore.deleteMessages(userId);

        assertThat(oracleMemoryStore.getMessages(userId)).isEmpty();
    }

    /**
     * Verifies TTL behavior: messages become unavailable after expiration.
     */
    @Test
    void set_messages_with_ttl_into_oracle() throws SQLException {
        DataSource dataSource = createDataSource();

        OracleMemoryStore ttlMemoryStore = OracleMemoryStore.builder()
                .dataSource(dataSource)
                .tableName(TABLE_NAME)
                .ttl(Duration.ofSeconds(2))
                .build();

        assertThat(ttlMemoryStore.getMessages(userId)).isEmpty();

        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        chatMessages.add(new UserMessage("What do you see in this text?", List.of(new TextContent("sometextcontent"))));

        ttlMemoryStore.updateMessages(userId, chatMessages);
        assertThat(ttlMemoryStore.getMessages(userId)).hasSize(2);

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(ttlMemoryStore.getMessages(userId)).isEmpty());
    }

    /**
     * Verifies that a memory id with no stored record returns an empty list.
     */
    @Test
    void get_messages_for_unknown_memory_id_should_return_empty() {
        assertThat(oracleMemoryStore.getMessages("unknown-memory-id")).isEmpty();
    }

    /**
     * Verifies update contract semantics: updating an existing memory id replaces stored messages.
     */
    @Test
    void update_messages_should_replace_existing_messages_for_same_memory_id() {
        oracleMemoryStore.updateMessages(userId, List.of(new SystemMessage("first")));
        assertThat(oracleMemoryStore.getMessages(userId)).hasSize(1);

        oracleMemoryStore.updateMessages(userId, List.of(
                new SystemMessage("second"),
                aiMessage("replaced history")
        ));

        List<ChatMessage> loaded = oracleMemoryStore.getMessages(userId);
        assertThat(loaded).hasSize(2);
        assertThat(((SystemMessage) loaded.get(0)).text()).isEqualTo("second");
        assertThat(((AiMessage) loaded.get(1)).text()).isEqualTo("replaced history");
    }

    /**
     * Verifies that deleting a non-existing memory id is a no-op.
     */
    @Test
    void delete_messages_for_unknown_memory_id_should_not_fail() {
        assertThatCode(() -> oracleMemoryStore.deleteMessages("unknown-memory-id")).doesNotThrowAnyException();
    }

    /**
     * Verifies input validation: {@code getMessages} rejects a null memory id.
     */
    @Test
    void getMessages_should_throw_exception_when_memoryId_null() {
        assertThatThrownBy(() -> oracleMemoryStore.getMessages(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies input validation: {@code getMessages} rejects a blank memory id.
     */
    @Test
    void getMessages_should_throw_exception_when_memoryId_empty() {
        assertThatThrownBy(() -> oracleMemoryStore.getMessages("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies input validation: {@code updateMessages} rejects {@code null} messages.
     */
    @Test
    void updateMessages_should_throw_exception_when_messages_null() {
        assertThatThrownBy(() -> oracleMemoryStore.updateMessages(userId, null))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies input validation: {@code updateMessages} rejects empty messages.
     */
    @Test
    void updateMessages_should_throw_exception_when_messages_empty() {
        assertThatThrownBy(() -> oracleMemoryStore.updateMessages(userId, new ArrayList<>()))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies input validation: {@code updateMessages} rejects a null memory id.
     */
    @Test
    void updateMessages_should_throw_exception_when_memoryId_null() {
        List<ChatMessage> chatMessages = List.of(new SystemMessage("You are a large language model working with Langchain4j"));
        assertThatThrownBy(() -> oracleMemoryStore.updateMessages(null, chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies input validation: {@code updateMessages} rejects a blank memory id.
     */
    @Test
    void updateMessages_memoryId_empty() {
        List<ChatMessage> chatMessages = List.of(new SystemMessage("You are a large language model working with Langchain4j"));
        assertThatThrownBy(() -> oracleMemoryStore.updateMessages("   ", chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies input validation: {@code deleteMessages} rejects a null memory id.
     */
    @Test
    void deleteMessages_should_throw_exception_when_memoryId_null() {
        assertThatThrownBy(() -> oracleMemoryStore.deleteMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies input validation: {@code deleteMessages} rejects a blank memory id.
     */
    @Test
    void deleteMessages_should_throw_exception_when_memoryId_empty() {
        assertThatThrownBy(() -> oracleMemoryStore.deleteMessages("   "))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    private static void createTable(String tableName) throws SQLException {
        DataSource dataSource = createDataSource();

        oracleMemoryStore = OracleMemoryStore.builder()
                .dataSource(dataSource)
                .tableName(tableName)
                .ttl(Duration.ZERO)
                .build();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + tableName + " ("
                    + "  memory_id     VARCHAR2(200) NOT NULL, "
                    + "  messages_json JSON NOT NULL, "
                    + "  expires_at    TIMESTAMP NULL, "
                    + "  PRIMARY KEY (memory_id)"
                    + ")");
        }
    }

    private static DataSource createDataSource() throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(jdbcUrl);
        dataSource.setUser(userName);
        dataSource.setPassword(password);
        return dataSource;
    }

    private static boolean isContainerRuntimeAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
