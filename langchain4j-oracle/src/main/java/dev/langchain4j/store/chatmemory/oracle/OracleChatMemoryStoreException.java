package dev.langchain4j.store.chatmemory.oracle;

import java.sql.SQLException;
/*
 * OracleChatMemoryStore Exceptions
 *
 */
public class OracleChatMemoryStoreException extends RuntimeException{
    public OracleChatMemoryStoreException(String exception){
        super(exception);
    }
    public OracleChatMemoryStoreException (String exception, SQLException e){
        super(exception,e);
    }
}
