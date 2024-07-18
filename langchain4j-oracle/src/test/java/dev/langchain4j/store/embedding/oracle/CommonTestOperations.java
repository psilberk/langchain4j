package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import oracle.jdbc.datasource.OracleDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.oracle.OracleContainer;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * A collection of operations which are shared by tests in this package.
 */
final class CommonTestOperations {

    /**
     * Model used to generate embeddings for this test. The all-MiniLM-L6-v2 model is chosen for consistency with other
     * implementations of EmbeddingStoreIT.
     */
    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private CommonTestOperations() {}

    private static DataSource dataSource;

    static EmbeddingModel getEmbeddingModel() {
        return EMBEDDING_MODEL;
    }

    static DataSource getDataSource(OracleContainer oracleContainer) {
        if (dataSource == null) {
            try {
                oracleContainer.start();
                OracleDataSource oracleDataSource = new oracle.jdbc.datasource.impl.OracleDataSource();
                oracleDataSource.setUser(oracleContainer.getJdbcUrl());
                oracleDataSource.setUser(oracleContainer.getUsername());
                oracleDataSource.setPassword(oracleContainer.getPassword());
                dataSource = new TestDataSource(oracleDataSource);
            }
            catch (SQLException sqlException) {
                throw new IllegalStateException(sqlException);
            }
        }

        return dataSource;
    }

}
