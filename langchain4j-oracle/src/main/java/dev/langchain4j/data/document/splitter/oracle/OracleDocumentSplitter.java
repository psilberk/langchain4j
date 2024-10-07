package dev.langchain4j.data.document.splitter.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OracleDocumentSplitter {

    private static final Logger log = LoggerFactory.getLogger(OracleDocumentSplitter.class);

    private final Connection conn;
    private final String pref;
    
    public OracleDocumentSplitter(Connection conn, String pref) {
        this.conn = conn;
        this.pref = pref;
    }

    public String[] split(String content) {
        
        List<String> strArr = new ArrayList<>();
        
        try {
            String query = "select t.column_value as data from dbms_vector_chain.utl_to_chunks(?, json(?)) t";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setObject(1, content);
            stmt.setObject(2, pref);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String text = rs.getString("data");

                    ObjectMapper mapper = new ObjectMapper();
                    Chunk chunk = mapper.readValue(text, Chunk.class);
                    strArr.add(chunk.chunk_data);
                }
            }
        } catch (IOException | SQLException ex) {
            String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            log.warn("Failed to split '{}': {}", pref, message);
        }
        
        return strArr.toArray(new String[strArr.size()]);
    }
}
