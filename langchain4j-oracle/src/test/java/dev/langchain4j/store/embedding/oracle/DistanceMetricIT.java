package dev.langchain4j.store.embedding.oracle;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.SQLException;

import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.dropTable;
import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.newEmbeddingStoreBuilder;

/**
 * Test cases which configure {@link OracleEmbeddingStore} with all possible {@link DistanceMetric} options and
 * exact/approximate search options.
 */
public class DistanceMetricIT {

    /** Verifies all distance metrics with approximate search */
    @ParameterizedTest
    @EnumSource(DistanceMetric.class)
    public void testDistanceMetricApproximate(DistanceMetric distanceMetric) throws SQLException {
        verifyDistanceMetric(distanceMetric, false);
    }

    /** Verifies all distance metrics with approximate search */
    @ParameterizedTest
    @EnumSource(DistanceMetric.class)
    public void testDistanceMetricExact(DistanceMetric distanceMetric) throws SQLException {
        verifyDistanceMetric(distanceMetric, true);
    }

    private void verifyDistanceMetric(DistanceMetric distanceMetric, boolean isExactSearch) throws SQLException  {

        OracleEmbeddingStore oracleEmbeddingStore =
                newEmbeddingStoreBuilder()
                   .distanceMetric(distanceMetric)
                   .exactSearch(isExactSearch)
                   .build();

        try {
            CommonTestOperations.verifySearch(oracleEmbeddingStore);
        }
        finally {
            dropTable();
        }
    }

}
