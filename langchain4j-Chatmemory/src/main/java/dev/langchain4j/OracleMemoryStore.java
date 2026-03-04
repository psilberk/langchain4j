package dev.langchain4j;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
/*
 * This class stores, retrieves, updates, and deletes user and system messages
 * it implements ChatMemoryStore, which provides methods such as getMessages(),
 * updateMessages(), and deleteMessages().
 */
public class OracleMemoryStore implements ChatMemoryStore {
    private final DataSource oracleDataSource;
    private final Duration ttl;
    public OracleMemoryStore(Duration ttl) throws SQLException {
        this.ttl = ttl;
        this.oracleDataSource=OracleWalletDataSourceFactory.createconnection();

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
 */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
    String id=memoryidtostring(memoryId);
    String sql = """
            Select messages_json from chat_memory \s
            where memory_id=? and (expires_at is NULL or expires_at>SYSTIMESTAMP)\s
           \s""";
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
        throw new OracleChatMemoryStoreException("Failed to load Memoryid"+memoryId);
    }

    }
    /*
     * This method updates the stored content list for a memory.
     *
     * it uses Oracle DB MERGE syntax (an “upsert” operation): if the memory record
     * already exists it updates the json content; otherwise, it creates a new record
     * and inserts the content.
     */

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String id= memoryidtostring(memoryId);
        String json = ChatMessageSerializer.messagesToJson(messages);
        String sql= """
                Merge into chat_memory t
                Using (select ? as memory_id , ? as messages_json, ? as expires_at from dual) s
                on (t.memory_id=s.memory_id)
                When matched then update set
                t.messages_json=s.messages_json,
                t.expires_at=s.expires_at,
                t.updated_at=SYSTIMESTAMP
                When Not matched then insert (memory_id, messages_json,updated_at,expires_at) values
                (s.memory_id,s.messages_json,SYSTIMESTAMP,s.expires_at)
                """;
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
            throw new OracleChatMemoryStoreException("Failed to update messages for memoryId=" + id, e);
        }

    }
    /*
     * Deletes stored chat memory when the conversation has expired.
     */
    @Override
    public void deleteMessages(Object memoryId) {
        String id=memoryidtostring(memoryId);
        String sql="Delete from chat_memory where memory_id=? and (expires_at is not null and expires_at<SYSTIMESTAMP) ";
        try(Connection con=oracleDataSource.getConnection()){
            PreparedStatement pr= con.prepareStatement(sql);
            pr.setString(1,id);
            pr.executeUpdate();
        } catch (SQLException e) {
            throw new OracleChatMemoryStoreException("Failed to delete messages for memoryId=" + id, e);
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
     */
    public String memoryidtostring(Object memoryId){
        if(memoryId==null || memoryId.toString().trim().isEmpty()){
            throw new OracleChatMemoryStoreException("No memoryId found");
        }
        return memoryId.toString();
    }
}
