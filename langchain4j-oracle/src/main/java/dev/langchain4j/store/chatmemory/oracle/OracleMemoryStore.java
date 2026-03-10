package dev.langchain4j.store.chatmemory.oracle;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import oracle.jdbc.pool.OracleDataSource;

/*
 * This class stores, retrieves, updates, and deletes user and system messages
 * it implements ChatMemoryStore, which provides methods such as getMessages(),
 * updateMessages(), and deleteMessages().
 */
public class OracleMemoryStore implements ChatMemoryStore {
    private final OracleDataSource oracleDataSource;
    private final Duration ttl;
    private final String tableName;
    /**
     * Constructs a new Oracle chat memory store with TTL .
     *
     * @param oracleDataSource      used to obtain database connections
     * @param tableName             name of the database table used to persist chat memory.
     * @param ttl                   time-to-live in seconds
     *
     */
    public OracleMemoryStore(OracleDataSource oracleDataSource,String tableName,Duration ttl) throws SQLException {

        this.oracleDataSource= Objects.requireNonNull(oracleDataSource , "oracleDataSource");
        this.tableName=Objects.requireNonNull(tableName,"tablename");
        this.ttl = Objects.requireNonNull(ttl,"ttl");

    }

/* get messages from database , each memoryId has multiple text the store
in json format (eg : memoryId : 1 ,memoryJson:[
{
"text":"Hey user how can i help you",
"type":"SYSTEM"
},
{
"contents":[
{
"text":"hey System","type":"TEXT"}
],
"type":"USER"}]
)
using expires_at to remove all data that could be not use again

@param memoryId The identifier for the memory to retrieve

@return List of chat messages or an empty list if no messages found
 */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
    String id=memoryidtostring(memoryId);
    String sql = """
            Select messages_json from %s \s
            where memory_id=? and (expires_at is NULL or expires_at>SYSTIMESTAMP)\s
           \s""".formatted(tableName);
    try(Connection con=oracleDataSource.getConnection()) {
        PreparedStatement str=con.prepareStatement(sql);
        str.setString(1,id);
        try(ResultSet res=str.executeQuery()){
            if(!res.next())return Collections.emptyList();
            else {
                String json=res.getString(1);
                return ChatMessageDeserializer.messagesFromJson(json);
            }
        }

    } catch (SQLException e) {
        throw new RuntimeException("Failed to load Memoryid"+memoryId,e);
    }

    }
    /*
     * This method updates the stored content list for a memory.
     *
     * it uses Oracle DB MERGE syntax (an “upsert” operation): if the memory record
     * already exists it updates the json content; otherwise, it creates a new record
     * and inserts the content.
     * @param memoryId The identifier for the memory to update
     * @param messages The list of messages to store
     *
     * @throws OracleChatMemoryStoreException If the Oracle operation fails
     */

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if(messages==null || messages.isEmpty()){
            throw new IllegalArgumentException("messages cannot be null or empty");
        }

        String id= memoryidtostring(memoryId);
        String json = ChatMessageSerializer.messagesToJson(messages);
        String sql= """
                Merge into %s t
                Using (select ? as memory_id , ? as messages_json, ? as expires_at from dual) s
                on (t.memory_id=s.memory_id)
                When matched then update set
                t.messages_json=s.messages_json,
                t.expires_at=s.expires_at,
                t.updated_at=SYSTIMESTAMP
                When Not matched then insert (memory_id, messages_json,updated_at,expires_at) values
                (s.memory_id,s.messages_json,SYSTIMESTAMP,s.expires_at)
                """.formatted(tableName);
        Timestamp expiresAt=computeExperationat();
        try(Connection con=oracleDataSource.getConnection()){
            PreparedStatement pr= con.prepareStatement(sql);
            pr.setString(1,id);
            pr.setString(2,json);
            if(expiresAt==null) pr.setNull(3, Types.TIMESTAMP);
            else {
                pr.setTimestamp(3,expiresAt);
            }
            pr.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update messages for memoryId=" + id, e);
        }

    }
    /*
     * Deletes stored chat memory when the conversation has expired.
     *
     * @param memoryId The identifier for the memory to delete
     */
    @Override
    public void deleteMessages(Object memoryId) {
        String id=memoryidtostring(memoryId);
        String sql="Delete from %s where memory_id=?".formatted(tableName);
        try(Connection con=oracleDataSource.getConnection()){
            PreparedStatement pr= con.prepareStatement(sql);
            pr.setString(1,id);
            pr.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete messages for memoryId=" + id, e);
        }


    }
    /*
     * Sets the expiration date.
     * If no expiration is required set ttl to null
     * or to a value less than or equal to 0.
     */
    public Timestamp computeExperationat(){
        if(ttl==null || ttl.isZero() || ttl.isNegative())return null;
        return Timestamp.from(java.time.Instant.now().plus(ttl));
    }
    /*
     * Converts the memoryId object to a String so it can be stored in the Oracle Database.
     *
     * @param memoryId The memory ID to convert
     *
     * @return String representation of the memory ID
     *
     * @throws IllegalArgumentException If memoryId is null or empty
     */
    public String memoryidtostring(Object memoryId){
        if(memoryId==null || memoryId.toString().trim().isEmpty()){
            throw new IllegalArgumentException("memoryId cannot be null or empty");
        }
        return memoryId.toString();
    }
}
