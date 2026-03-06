package dev.langchain4j.store.chatmemory.oracle;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
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

public class OracleChatMemoryIT {
    private static String jdbcUrl;
    private static String userName;
    private static String password;

    private final String userId = "user-123";
    private OracleMemoryStore oracleMemoryStore;

@BeforeAll
static void beforeAll() {
    jdbcUrl  = System.getenv("ORACLE_JDBC_URL");
    userName = System.getenv("ORACLE_USERNAME");
    password = System.getenv("ORACLE_PASSWORD");

    // Skip integration tests if not configured
    assumeTrue(jdbcUrl != null && userName != null && password != null,
            "Local Oracle DB env vars not set; skipping integration tests");
}

@BeforeEach
void setUp() throws SQLException {
    OracleDataSource oracleDataSource=new OracleDataSource();
    oracleDataSource.setURL(jdbcUrl);
    oracleDataSource.setUser(userName);
    oracleDataSource.setPassword(password);

        //sqlcode!=-955 check if name of table already exist
try(Connection con=oracleDataSource.getConnection(); Statement st= con.createStatement()){

    st.executeUpdate("""
BEGIN
    EXECUTE IMMEDIATE 'Create table chat_memory(
    memory_id varchar(200) not null,
    messages_json Clob not null,
    update_at timestamp DEFAULT SYSTIMESTAMP not null,
    expires_at timestamp null,
    Constraint PK_chat_memory primary key(memory_id))';
Exception
    When others then
        IF SQLCODE!=-955 then Raise; END IF;
END;

""");
    this.oracleMemoryStore=new OracleMemoryStore(oracleDataSource,"chat_memory", Duration.ZERO);
    oracleMemoryStore.deleteMessages(userId);
    List<ChatMessage> messages = oracleMemoryStore.getMessages(userId);
    assertThat(messages).isEmpty();


}

    }
@AfterEach
void deleteTable() throws SQLException {


    oracleMemoryStore.deleteMessages(userId);

    }
// Verifies OracleMemoryStore can persist and retrieve a list of chat messages (System + User with ImageContent) in Oracle DB
@Test
void set_messages_into_oracle() {
    // getmessages and check if they are empty
    List<ChatMessage> messages = oracleMemoryStore.getMessages(userId);
    assertThat(messages).isEmpty();

    // setting up instruction for system
    List<ChatMessage> chatMessages = new ArrayList<>();
    chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
    //add content to messtages(URL,ask the question)
    List<Content> userMsgContents = new ArrayList<>();
    userMsgContents.add(new ImageContent("someCatImageUrl"));
    chatMessages.add(new UserMessage("What do you see in this image?", userMsgContents));
    oracleMemoryStore.updateMessages(userId, chatMessages);

    // check size of messages
    messages = oracleMemoryStore.getMessages(userId);
    assertThat(messages).hasSize(2);
}

// Verifies OracleMemoryStore can delete stored messages for a given memory/user id from Oracle DB
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
// Verifies OracleMemoryStore TTL behavior, messages expire and are no longer retrievable from Oracle DB after the configured TTL
@Test
void set_messages_with_ttl_into_oracle() throws SQLException {
    OracleDataSource oracleDataSource=new OracleDataSource();
    oracleDataSource.setURL(jdbcUrl);
    oracleDataSource.setUser(userName);
    oracleDataSource.setPassword(password);
    // create new OracleMemoryStore that contains ttl > 0
    OracleMemoryStore ttlMemoryStore = new OracleMemoryStore(oracleDataSource,"chat_memory",Duration.ofSeconds(2));

    // get the messages and check if isEmpty
    List<ChatMessage> messages = ttlMemoryStore.getMessages(userId);
    assertThat(messages).isEmpty();

  //add content to messtages(URL,ask the question)
    List<ChatMessage> chatMessages = new ArrayList<>();
    chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
    List<Content> userMsgContents = new ArrayList<>();
    userMsgContents.add(new ImageContent("someCatImageUrl"));
    chatMessages.add(new UserMessage("What do you see in this image?", userMsgContents));

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
// Verifies input validation: getMessages rejects null memoryId and throws OracleChatMemoryStoreException
@Test
void getMessages_should_throw_exception_when_memoryId_null() {
    assertThatThrownBy(() -> oracleMemoryStore.getMessages(null))
            .isInstanceOf(OracleChatMemoryStoreException.class)
            .hasMessage("memoryId cannot be null or empty");
}

// Verifies input validation: getMessages rejects empty memoryId and throws OracleChatMemoryStoreException

@Test
void getMessages_should_throw_exception_when_memoryId_empty() {
    assertThatThrownBy(() -> oracleMemoryStore.getMessages("   "))
            .isInstanceOf(OracleChatMemoryStoreException.class)
            .hasMessage("memoryId cannot be null or empty");
}
// Verifies input validation: updateMessages rejects null messages and throws OracleChatMemoryStoreException

    @Test
    void updateMessages_should_throw_exception_when_messages_null() {
        assertThatThrownBy(() -> oracleMemoryStore.updateMessages(userId, null))
                .isExactlyInstanceOf(OracleChatMemoryStoreException.class)
                .hasMessage("messages cannot be null or empty");
    }

// Verifies input validation: updateMessages rejects empty messages and throws OracleChatMemoryStoreException

    @Test
    void updateMessages_should_throw_exception_when_messages_empty() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        assertThatThrownBy(() -> oracleMemoryStore.updateMessages(userId, chatMessages))
                .isExactlyInstanceOf(OracleChatMemoryStoreException.class)
                .hasMessage("messages cannot be null or empty");
    }

// Verifies input validation: updateMessages rejects null memoryId and throws OracleChatMemoryStoreException

    @Test
    void updateMessages_should_throw_exception_when_memoryId_null() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        assertThatThrownBy(() -> oracleMemoryStore.updateMessages(null, chatMessages))
                .isExactlyInstanceOf(OracleChatMemoryStoreException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

// Verifies input validation: updateMessages rejects empty memoryId and throws OracleChatMemoryStoreException

    @Test
    void updateMessages_memoryId_empty() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        assertThatThrownBy(() -> oracleMemoryStore.updateMessages("   ", chatMessages))
                .isExactlyInstanceOf(OracleChatMemoryStoreException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

// Verifies input validation: deleteMessages rejects null memoryId and throws OracleChatMemoryStoreException

    @Test
    void deleteMessages_should_throw_exception_when_memoryId_null() {
        assertThatThrownBy(() -> oracleMemoryStore.deleteMessages(null))
                .isExactlyInstanceOf(OracleChatMemoryStoreException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

// Verifies input validation: deleteMessages rejects empty memoryId and throws OracleChatMemoryStoreException

    @Test
    void deleteMessages_should_throw_exception_when_memoryId_empty() {
        assertThatThrownBy(() -> oracleMemoryStore.deleteMessages("   "))
                .isExactlyInstanceOf(OracleChatMemoryStoreException.class)
                .hasMessage("memoryId cannot be null or empty");
    }




}
