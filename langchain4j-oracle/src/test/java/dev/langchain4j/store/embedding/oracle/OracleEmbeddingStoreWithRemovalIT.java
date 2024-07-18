package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@Testcontainers
public class OracleEmbeddingStoreWithRemovalIT extends EmbeddingStoreWithRemovalIT {

    @Container
    private static final OracleContainer ORACLE_CONTAINER =
            new OracleContainer("gvenzl/oracle-free:23.4-slim-faststart");

    private static final OracleEmbeddingStore EMBEDDING_STORE =
            OracleEmbeddingStore.builder()
                    .tableName("oracle_embedding_store_with_removal_it")
                    .dataSource(CommonTestOperations.getDataSource(ORACLE_CONTAINER))
                    .build();

    @BeforeEach
    public void clearTable() {
        //  A removeAll call happens before each test because EmbeddingStoreWithRemovalIT is designed for each test to
        //  begin with an empty store.
        EMBEDDING_STORE.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return EMBEDDING_STORE;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return CommonTestOperations.getEmbeddingModel();
    }
}
