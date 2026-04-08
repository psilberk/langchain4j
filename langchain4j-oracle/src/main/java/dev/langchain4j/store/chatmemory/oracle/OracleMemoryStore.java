package dev.langchain4j.store.chatmemory.oracle;

import javax.sql.DataSource;
import java.io.IOException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;

import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.JacksonChatMessageJsonCodec;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import oracle.jdbc.OracleStatement;

import oracle.jdbc.OracleType;
import oracle.jdbc.OracleTypes;
import oracle.jdbc.pool.OracleDataSource;

import oracle.jdbc.provider.oson.OsonFactory;
import oracle.sql.json.OracleJsonArray;
import oracle.sql.json.OracleJsonDatum;
import oracle.sql.json.OracleJsonDecimal;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonNumber;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonString;
import oracle.sql.json.OracleJsonValue;



import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;
import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Stores, retrieves, updates, and deletes chat messages (both user and system) for a given memory ID.
 * <p>
 * This class implements {@code ChatMemoryStore} and provides the core persistence operations used by
 * chat memory including:
 * <ul>
 *   <li>{@link #getMessages(Object)} : loads messages for a memory ID (if not expired)</li>
 *   <li>{@code updateMessages(...)} : inserts or replaces the stored messages for a memory ID</li>
 *   <li>{@code deleteMessages(...)} : removes messages for a memory ID</li>
 * </ul>
 * <p>
 * <strong>Database requirement:</strong> This implementation requires Oracle Database
 * <strong>21ai or newer</strong> (or any Oracle Database version that supports the native {@code JSON}
 * data type used by the {@code messages_json JSON} column).
 * <p>
 * Messages are persisted in JSON form (stored in a {@code JSON} column) and deserialized when read.
 * <p>
 * Instances are created using the provided builder to simplify configuration.
 */
public class OracleMemoryStore implements ChatMemoryStore {
    private final OracleDataSource oracleDataSource;
    private final Duration ttl;
    private final String tableName;

    private static final TypeReference<List<ChatMessage>> CHAT_MESSAGE_LIST = new TypeReference<>() {};

    /**
     * Constructs Oracle Memory Store configured by a builder.
     *
     * @param builder Builder that configures the Oracle Memory Store. Not null.
     * @throws IllegalArgumentException If the configuration is not valid.
     * @implNote This constructor does not perform null checks. Validation should occur in {@link Builder#build()},
     * before calling this constructor.
     */
    private OracleMemoryStore(Builder builder) throws SQLException {

        this.oracleDataSource= builder.oracleDataSource;
        this.tableName=builder.tableName;
        this.ttl = builder.ttl;
        try {
            createTable();
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }
    /**
     * Creates the chat memory table if it does not already exist.
     * <p>
     * The table name is provided by the caller (for example via {@code tableName}).
     * <p>
     * The table stores one row per {@code memory_id} (chat session). The full chat history is
     * persisted in {@code messages_json}.
     * <p>
     * Columns:
     * <ul>
     *   <li>{@code memory_id} - Chat session identifier. Required (NOT NULL). Primary key.</li>
     *   <li>{@code messages_json} - Messages payload serialized as JSON. Required (NOT NULL).</li>
     *   <li>{@code expires_at} - Optional expiration timestamp. NULL means no expiration.</li>
     * </ul>
     * <p>

     */
    private void createTable() throws SQLException {
        try(Connection con=oracleDataSource.getConnection(); Statement create= con.createStatement()){


            create.executeUpdate(  "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                    + "  memory_id     VARCHAR2(200) NOT NULL, "
                    + "  messages_json JSON NOT NULL, "
                    + "  expires_at    TIMESTAMP NULL, "
                    + "  PRIMARY KEY (memory_id)"
                    + ")");

        }

        catch (SQLException e) {
            throw new RuntimeException("Failed to create table " + tableName, e);
        }}
    /**
     * Retrieves chat messages for the given {@code memoryId} from the database.
     * <p>
     * Each {@code memoryId} can have multiple messages, stored as a single JSON document. For example:
     * <pre>
     * memoryId: 1
     * messagesJson: [
     *   { "text": "Hey user how can I help you", "type": "SYSTEM" },
     *   {
     *     "contents": [ { "text": "hey System", "type": "TEXT" } ],
     *     "type": "USER"
     *   }
     * ]
     * </pre>
     * <p>
     * Records are considered valid only if {@code expires_at} is {@code NULL} or greater than the current timestamp;
     * expired records are ignored .
     *
     * @param memoryId the identifier for the memory to retrieve; must not be {@code null} or blank
     * @return the list of chat messages, or an empty list if none are found .
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {



        ensureNotNull(memoryId, "memoryId");
        String id = memoryIdToString(memoryId);
        ensureNotBlank(id, "memoryId");

        try(Connection con=oracleDataSource.getConnection() ; PreparedStatement query=con.prepareStatement(
                "SELECT messages_json " +
                        "FROM " + tableName + " " +
                        "WHERE memory_id = ? " +
                        "AND (expires_at IS NULL OR expires_at > SYSTIMESTAMP)")) {

            OracleStatement os = query.unwrap(OracleStatement.class);

            os.defineColumnType(1, OracleTypes.JSON);
            os.setLobPrefetchSize(Integer.MAX_VALUE);
            query.setString(1,id);
            try(ResultSet res=query.executeQuery()){
                if(!res.next())return Collections.emptyList();
                else {


                    OracleJsonDatum datum = res.getObject(1, OracleJsonDatum.class);
                    byte[] osonBytes = datum.getBytes(); // these are OSON bytes
                    return OsonLangChain4jMapper.fromOsonBytes(osonBytes);

                }
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Failed to load Memoryid "+memoryId,e);
        }

    }
    /**
     * Updates the stored message list for the specified {@code memoryId}.
     * <p>
     * This method uses Oracle Database {@code MERGE} syntax (an upsert): if a record for the given memory ID
     * already exists, it updates the stored JSON message content; otherwise it inserts a new row containing
     * the memory ID and the JSON content.
     *
     * @param memoryId the identifier for the memory to update; must not be {@code null} or blank
     * @param messages the list of messages to store; must not be {@code null}
     */

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {

        ensureNotNull(memoryId, "memoryId");
        String id = memoryIdToString(memoryId);
        ensureNotBlank(id, "memoryId");
        ensureNotNull(messages, "messages");
        ensureNotEmpty(messages, "messages");




        Timestamp expiresAt=computeExperationAt();
        final byte[] osonBytes;
        try {
            osonBytes = OsonLangChain4jMapper.toOsonBytes(messages);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize messages to OSON", e);
        }

        try(Connection con=oracleDataSource.getConnection();  PreparedStatement update= con.prepareStatement(
                "MERGE INTO " + tableName + " t "
                        + "USING (SELECT ? AS memory_id, ? AS messages_json, ? AS expires_at FROM dual) s "
                        + "ON (t.memory_id = s.memory_id) "
                        + "WHEN MATCHED THEN UPDATE SET "
                        + "  t.messages_json = s.messages_json, "
                        + "  t.expires_at    = s.expires_at "
                        + "WHEN NOT MATCHED THEN INSERT (memory_id, messages_json,expires_at) VALUES "
                        + "  (s.memory_id, s.messages_json, s.expires_at)");){

            update.setString(1,id);
            update.setObject(2,osonBytes, OracleType.JSON);
            if(expiresAt==null) update.setNull(3, Types.TIMESTAMP);
            else {
                update.setTimestamp(3,expiresAt);
            }
            update.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update messages for memoryId=" + id, e);
        }

    }
    /**
     * Deletes the stored chat memory for the specified {@code memoryId}.
     * <p>
     * This is typically used to remove persisted conversation state when it has expired or is no longer needed.
     *
     * @param memoryId the identifier for the memory to delete; must not be {@code null} or blank
     */
    @Override
    public void deleteMessages(Object memoryId) {
        ensureNotNull(memoryId, "memoryId");
        String id = memoryIdToString(memoryId);
        ensureNotBlank(id, "memoryId");

        try(Connection con=oracleDataSource.getConnection(); PreparedStatement delete= con.prepareStatement("DELETE FROM " + tableName + " WHERE memory_id = ?")){

            delete.setString(1,id);
            delete.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete messages for memoryId=" + id, e);
        }


    }

    /**
     * Sets the time-to-live (TTL)
     * <p>
     * If no expiration is required, set {@code ttl} to {@code null} or to a value less than or equal to {@code 0}.
     * In that case, records will be stored without an expiration timestamp.
     */
    private Timestamp computeExperationAt(){
        if(ttl==null || ttl.isZero() || ttl.isNegative())return null;
        return Timestamp.from(Instant.now().plus(ttl));
    }
    /**
     * Converts the supplied {@code memoryId} to a {@link String} suitable for persistence in Oracle Database.
     * @return the string representation of the memory ID
     */
    private String memoryIdToString(Object memoryId){
        return memoryId.toString();
    }



    /**
     * Creates a new builder instance for constructing a OracleMemoryStore
     *
     * @return A new Builder instance
     */

    public static Builder builder() {
        return new Builder();
    }
    /**
     * Builder for creating OracleMemoryStore instances with fluent API.
     */
    public static class Builder {
        private OracleDataSource oracleDataSource;
        private String tableName = "chat_memory";
        private Duration ttl = Duration.ZERO;


        /**
         * Sets the {@link OracleDataSource} used to obtain JDBC connections for all persistence operations.
         *
         * @param oracleDataSource the Oracle data source; must not be {@code null}
         * @return this builder instance
         */
        public Builder oracleDataSource(OracleDataSource oracleDataSource) {

            this.oracleDataSource = ensureNotNull(oracleDataSource,"oracleDataSource");
            return this;
        }

        /**
         * Sets the database table name used to store and retrieve chat memory.
         *
         * @param tableName the table name; must not be {@code null} or blank
         * @return this builder instance
         */
        public Builder tableName(String tableName) {
            this.tableName = ensureNotNull(tableName,"tableName");
            return this;
        }

        /**
         * Sets the time-to-live (TTL) for stored chat memory.
         * <p>
         * The TTL represents how long a conversation between the user and the AI is retained.
         * For example, if the TTL is 7 days and the user does not interact with the chatbot
         * within that period, the stored conversation expires and the chatbot will “forget”
         * the previous context.
         * <p>
         * This helps keep context relevant (users returning after a long gap typically start a new topic)
         * and prevents unbounded growth of stored conversation history.
         * <p>
         * Recommended TTL values are typically expressed in days, weeks, or months (depending on the
         * product’s usage patterns and retention needs).
         * <p>
         * If {@code ttl} is {@code null} or less than or equal to zero, expiration is disabled and records
         * are stored without an {@code expires_at} timestamp.
         *
         * @param ttl the TTL duration; {@code null} or {@code <= 0} disables expiration
         * @return this builder instance
         */
        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        /**
         * Builds a configured {@link OracleMemoryStore} instance.
         *
         * @return a new {@link OracleMemoryStore}
         * @throws SQLException if initialization fails .
         */
        public OracleMemoryStore build() throws SQLException {
            ensureNotNull(tableName,"tableName");
            ensureNotNull(oracleDataSource,"oracleDataSource");
            return new OracleMemoryStore(this);
        }
    }
}
