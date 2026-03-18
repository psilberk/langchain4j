package dev.langchain4j;

import oracle.jdbc.pool.OracleDataSource;



import javax.sql.DataSource;
import java.sql.SQLException;
/*
Connect to Oracle DB
 */
public class OracleWalletDataSourceFactory {
    public static OracleDataSource createconnection() throws SQLException {
      OracleDataSource oracleDataSource=new OracleDataSource();

        oracleDataSource.setURL(System.getenv("url"));
        oracleDataSource.setUser(System.getenv("user"));
        oracleDataSource.setPassword(System.getenv("password"));

        return oracleDataSource;
    }
}
