package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import oracle.jdbc.datasource.OracleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import javax.sql.DataSource;
import java.sql.SQLException;

public class OracleEmbeddingStoreWithFilteringIT extends EmbeddingStoreWithFilteringIT {

    private static final OracleContainer ORACLE_CONTAINER =
            new OracleContainer("gvenzl/oracle-free:slim-faststart")
                    .withDatabaseName("test")
                    .withUsername("test")
                    .withPassword("test")
                    .withStartupTimeoutSeconds(600);

    private static final OracleEmbeddingStore EMBEDDING_STORE =
            OracleEmbeddingStore.builder()
                .tableName("oracle_embedding_store_with_filtering_it")
                .dataSource(CommonTestOperations.getDataSource(ORACLE_CONTAINER))
                .build();

    @AfterAll
    public static void shutDownContainer() {
        ORACLE_CONTAINER.stop();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return EMBEDDING_STORE;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return CommonTestOperations.getEmbeddingModel();
    }

    @Override
    protected void clearStore() {
        embeddingStore().removeAll();
    }
}
