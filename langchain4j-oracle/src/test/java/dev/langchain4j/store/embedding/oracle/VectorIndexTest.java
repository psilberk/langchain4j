package dev.langchain4j.store.embedding.oracle;

import oracle.jdbc.OracleType;
import oracle.sql.json.OracleJsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.*;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests which verify all possible configurations of {@link OracleEmbeddingStore.Builder#vectorIndex(CreateOption)}
 */
public class VectorIndexTest {

    static final String TABLE_NAME = "LANGCHAIN4J_EMBEDDING_STORE";
    static final String INDEX_NAME = TABLE_NAME + "_VECTOR_INDEX";

    @ParameterizedTest
    @EnumSource(CreateOption.class)
    public void testCreateOption(CreateOption createOption) throws SQLException {
        OracleEmbeddingStore oracleEmbeddingStore =
                newEmbeddingStoreBuilder()
                        .vectorIndex(createOption)
                        .build();

        verifyIndexExists(createOption);

        try {
            verifySearch(oracleEmbeddingStore);
        }
        finally {
            dropTable();
        }
    }

    @ParameterizedTest
    @MethodSource("createIndexArguments")
    public void testCreateIndexOnStoreCreation(
        String indexType,
        int targetAccuracy,
        int degreeOfParallelism,
        /// IVF Parameters
        int neighborPartitions,
        int samplePerPartition,
        int minVectorsPerPartition,
        /// HNSW Parameters
        int efConstruction,
        int neighbors) throws Exception {

        try {
            OracleEmbeddingStore store = OracleEmbeddingStore.builder()
                .dataSource(CommonTestOperations.getDataSource())
                .embeddingTable(EmbeddingTable
                    .builder()
                    .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                    .name(TABLE_NAME)
                    .build())
                .build();

            float[] vector0 = CommonTestOperations.randomFloats(512);
            float[] vector1 = vector0.clone();
            List<String> ids = insertData(store, vector0, vector1);

            if (indexType == "IVF_VECTOR_INDEX") {
                store.createIndex(createIVFIndex(store, targetAccuracy, degreeOfParallelism, neighborPartitions, samplePerPartition, minVectorsPerPartition).build());
            } else {
                store.createIndex(createHNSWIndex(store, targetAccuracy, degreeOfParallelism, efConstruction, neighbors).build());
            }

            try (Connection connection = CommonTestOperations.getVectorIndexDataSource().getConnection();
                 PreparedStatement stmt = connection.prepareStatement("select IDX_PARAMS from vecsys.vector$index where IDX_NAME = ?")
            ) {
                stmt.setString(1, INDEX_NAME);
                ResultSet rs = stmt.executeQuery();
                Assertions.assertTrue(rs.next(), "A index should be returned");
                OracleJsonObject params = rs.getObject("IDX_PARAMS", OracleJsonObject.class);
                assertIndexType(indexType, params);
                assertTargetAccuracy(targetAccuracy, params);
                assertDegreeOfParallelism(degreeOfParallelism, params);
                assertNeighborPartitions(neighborPartitions, params);
                assertSamplePerPartition(samplePerPartition, params);
                assertMinVectorsPerPartition(minVectorsPerPartition, params);
                assertEfConstruction(efConstruction, params);
                assertNeighbors(neighbors, params);
                Assertions.assertFalse(rs.next(), "Only one index should be returned");
            }
            searchData(store, ids, vector1);
        } finally {
            dropTable(TABLE_NAME);
        }
    }

    @Test
    public void testMetadataKeyAndVectorIndex() throws SQLException {
        try {
            OracleEmbeddingStore oracleEmbeddingStore = OracleEmbeddingStore
                .builder()
                .dataSource(CommonTestOperations.getDataSource())
                .embeddingTable(EmbeddingTable
                    .builder()
                    .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                    .name(TABLE_NAME)
                    .build())
                .vectorIndex(CreateOption.CREATE_NONE)
                .build();

            JsonIndexBuilder jsonIndexBuilder = (JsonIndexBuilder) oracleEmbeddingStore.getIndexBuilder(IndexType.FUNCTION_JSON_INDEX);
            oracleEmbeddingStore.createIndex(
                jsonIndexBuilder
                    .key("key", OracleType.NUMBER, JsonIndexBuilder.Order.ASC)
                    .createOption(CreateOption.CREATE_OR_REPLACE)
                    .build());

            IVFIndexBuilder ivfIndexBuilder = (IVFIndexBuilder) oracleEmbeddingStore.getIndexBuilder(IndexType.IVF_VECTOR_INDEX);
            String indexName = oracleEmbeddingStore.createIndex(
                ivfIndexBuilder
                    .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                    .minVectorsPerPartition(10)
                    .neighborPartitions(3)
                    .samplePerPartition(15)
                    .targetAccuracy(90)
                    .build());

            verifyIndexExists(CreateOption.CREATE_OR_REPLACE,
                TABLE_NAME,
                indexName,
                "VECTOR");

            verifySearch(oracleEmbeddingStore);
        } finally {
            dropTable(TABLE_NAME);
        }
    }

    @Test
    public void testMetadataKeysIndex() throws SQLException {
        try {
            OracleEmbeddingStore oracleEmbeddingStore = OracleEmbeddingStore
                .builder()
                .dataSource(CommonTestOperations.getDataSource())
                .embeddingTable(EmbeddingTable
                    .builder()
                    .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                    .name(TABLE_NAME)
                    .build())
                .vectorIndex(CreateOption.CREATE_NONE)
                .build();

            JsonIndexBuilder jsonIndexBuilder = (JsonIndexBuilder) oracleEmbeddingStore.getIndexBuilder(IndexType.FUNCTION_JSON_INDEX);
            String indexName = oracleEmbeddingStore.createIndex(
                jsonIndexBuilder
                    .key("key", OracleType.NUMBER, JsonIndexBuilder.Order.ASC)
                    .key("name", OracleType.VARCHAR2, JsonIndexBuilder.Order.DESC)
                    .createOption(CreateOption.CREATE_OR_REPLACE)
                    .build());

            verifyIndexExists(CreateOption.CREATE_OR_REPLACE,
                TABLE_NAME,
                indexName,
                "FUNCTION-BASED NORMAL");

            verifySearch(oracleEmbeddingStore);
        } finally {
            dropTable(TABLE_NAME);
        }
    }

    @Test
    public void createAndDropIndexTest() {
        try {
            try {
                OracleEmbeddingStore oracleEmbeddingStore = OracleEmbeddingStore
                    .builder()
                    .dataSource(CommonTestOperations.getDataSource())
                    .embeddingTable(EmbeddingTable
                        .builder()
                        .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                        .name(TABLE_NAME)
                        .build())
                    .vectorIndex(CreateOption.CREATE_NONE)
                    .build();

                // JSON index
                JsonIndexBuilder jsonIndexBuilder = (JsonIndexBuilder) oracleEmbeddingStore.getIndexBuilder(IndexType.FUNCTION_JSON_INDEX);
                String indexName = oracleEmbeddingStore.createIndex(
                    jsonIndexBuilder
                        .indexName("TABLE_INDEX_NAME")
                        .key("key", OracleType.NUMBER, JsonIndexBuilder.Order.ASC)
                        .key("name", OracleType.VARCHAR2, JsonIndexBuilder.Order.DESC)
                        .createOption(CreateOption.CREATE_OR_REPLACE)
                        .build());

                assertEquals("TABLE_INDEX_NAME", indexName, "Incorrect index name");
                verifyIndexExists(CreateOption.CREATE_OR_REPLACE,
                    TABLE_NAME,
                    indexName,
                    "FUNCTION-BASED NORMAL");
                oracleEmbeddingStore.dropIndex(indexName);

                // IVF index
                IVFIndexBuilder ivfIndexBuilder = (IVFIndexBuilder) oracleEmbeddingStore.getIndexBuilder(IndexType.IVF_VECTOR_INDEX);
                indexName = oracleEmbeddingStore.createIndex(
                    ivfIndexBuilder
                        .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                        .minVectorsPerPartition(10)
                        .neighborPartitions(3)
                        .samplePerPartition(15)
                        .targetAccuracy(90)
                        .build());

                assertEquals(INDEX_NAME, indexName, "Incorrect index name");
                verifyIndexExists(CreateOption.CREATE_OR_REPLACE,
                    TABLE_NAME,
                    indexName,
                    "VECTOR");
                oracleEmbeddingStore.dropIndex(indexName);

                // HNSW index
                HNSWIndexBuilder hnswIndexBuilder = (HNSWIndexBuilder) oracleEmbeddingStore.getIndexBuilder(IndexType.HNSW_VECTOR_INDEX);
                indexName = oracleEmbeddingStore.createIndex(
                    hnswIndexBuilder
                        .createOption(CreateOption.CREATE_OR_REPLACE)
                        .efConstruction(2)
                        .neighbors(3)
                        .targetAccuracy(90)
                        .build());

                assertEquals(INDEX_NAME, indexName, "Incorrect index name");
                verifyIndexExists(CreateOption.CREATE_OR_REPLACE,
                    TABLE_NAME,
                    indexName,
                    "VECTOR");
                oracleEmbeddingStore.dropIndex(indexName);
                verifySearch(oracleEmbeddingStore);
            } finally {
                dropTable(TABLE_NAME);
            }
        } catch (SQLException sqlException) {
            fail(sqlException);
        }
    }

    private VectorIndexBuilder<IVFIndexBuilder> createIVFIndex(OracleEmbeddingStore oracleEmbeddingStore,
                                                               int targetAccuracy,
                                                               int degreeOfParallelism,
                                                               int neighborPartitions,
                                                               int samplePerPartition,
                                                               int minVectorsPerPartition) {
        IVFIndexBuilder vectorIndexBuilder = (IVFIndexBuilder)oracleEmbeddingStore.getIndexBuilder(IndexType.IVF_VECTOR_INDEX);
        vectorIndexBuilder.createOption(CreateOption.CREATE_OR_REPLACE);
        if (targetAccuracy >= 0) vectorIndexBuilder = vectorIndexBuilder.targetAccuracy(targetAccuracy);
        if (neighborPartitions >= 0) vectorIndexBuilder = vectorIndexBuilder.neighborPartitions(neighborPartitions);
        if (samplePerPartition >= 0) vectorIndexBuilder = vectorIndexBuilder.samplePerPartition(samplePerPartition);
        if (minVectorsPerPartition >= 0) vectorIndexBuilder = vectorIndexBuilder.minVectorsPerPartition(minVectorsPerPartition);
        if (degreeOfParallelism >= 0) vectorIndexBuilder = vectorIndexBuilder.degreeOfParallelism(degreeOfParallelism);

        return vectorIndexBuilder;
    }

    private VectorIndexBuilder<HNSWIndexBuilder> createHNSWIndex(OracleEmbeddingStore oracleEmbeddingStore,
                                                                 int targetAccuracy,
                                                                 int degreeOfParallelism,
                                                                 int efConstruction,
                                                                 int neighbors) {
        HNSWIndexBuilder vectorIndexBuilder = (HNSWIndexBuilder)oracleEmbeddingStore.getIndexBuilder(IndexType.HNSW_VECTOR_INDEX);
        vectorIndexBuilder.createOption(CreateOption.CREATE_OR_REPLACE);
        if (targetAccuracy >= 0) vectorIndexBuilder = vectorIndexBuilder.targetAccuracy(targetAccuracy);
        if (efConstruction >= 0) vectorIndexBuilder = vectorIndexBuilder.efConstruction(efConstruction);
        if (neighbors >= 0) vectorIndexBuilder = vectorIndexBuilder.neighbors(neighbors);
        if (degreeOfParallelism >= 0) vectorIndexBuilder = vectorIndexBuilder.degreeOfParallelism(degreeOfParallelism);
        return vectorIndexBuilder;
    }

    /**
     * Queries the USER_INDEXES view to verify that an index has been created or not. This method verifies that the
     * index is of the VECTOR type, and that it has the name specified in the JavaDoc of {@link OracleEmbeddingStore}:
     * {tableName}_EMBEDDING_INDEX.
     * @param createOption Option configured with {@link OracleEmbeddingStore.Builder#vectorIndex(CreateOption)}
     */
    private void verifyIndexExists(CreateOption createOption) throws SQLException {

        verifyIndexExists(createOption, TABLE_NAME, TABLE_NAME + "_VECTOR_INDEX", "VECTOR");
    }

    /**
     * Queries the USER_INDEXES view to verify that an index has been created or not. This method verifies that the
     * index is of the VECTOR type, and that it has the name specified in the JavaDoc of {@link OracleEmbeddingStore}:
     * {tableName}_EMBEDDING_INDEX.
     * @param createOption Option configured with {@link OracleEmbeddingStore.Builder#vectorIndex(CreateOption)}
     * @param tableName The name of the table.
     * @param indexName The name if the index.
     * @param indexType The type of index.
     */
    private void verifyIndexExists(CreateOption createOption, String tableName, String indexName, String indexType) throws SQLException {
        try (Connection connection = CommonTestOperations.getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                 "SELECT 'OK', index_name, index_type" +
                     " FROM user_indexes" +
                     " WHERE table_name='" + tableName + "'" +
                     " AND index_name='" + indexName + "'" +
                     " AND index_type='" + indexType + "'"
             )) {

            if (createOption == CreateOption.CREATE_NONE)
                assertFalse(resultSet.next());
            else
                assertTrue(resultSet.next());
        }

    }

    private void assertIndexType(String expectedIndexType, OracleJsonObject params) {
        if (expectedIndexType == null) { expectedIndexType = IndexType.IVF_VECTOR_INDEX.toString(); } // set to default value if not set
        Assertions.assertEquals(
            expectedIndexType == IndexType.IVF_VECTOR_INDEX.toString() ? "IVF_FLAT" : "HNSW",
            params.getString("type"),
            "Unexpected index type");
    }

    private void assertTargetAccuracy(int expectedTargetAccuracy, OracleJsonObject params) {
        if (expectedTargetAccuracy < 0) { return; }
        Assertions.assertEquals(expectedTargetAccuracy, params.getInt("accuracy"), "Unexpected accuracy");
    }

    private void assertDegreeOfParallelism(int expectedDegreeOfParallelism, OracleJsonObject params) {
        if (expectedDegreeOfParallelism < 0) { return; }
        Assertions.assertEquals(expectedDegreeOfParallelism, params.getInt("degree_of_parallelism"), "Unexpected degree of parallelism");
    }

    private void assertNeighborPartitions(int expectedNeighborPartitions, OracleJsonObject params) {
        if (expectedNeighborPartitions < 0) { return; }
        Assertions.assertEquals(expectedNeighborPartitions, params.getInt("target_centroids"), "Unexpected neighbor partitions");
    }

    private void assertSamplePerPartition(int expectedSamplePerPartition, OracleJsonObject params) {
        if (expectedSamplePerPartition < 0) { return; }
        Assertions.assertEquals(expectedSamplePerPartition, params.getInt("samples_per_partition"), "Unexpected samples per partition");
    }

    private void assertMinVectorsPerPartition(int expectedMinVectorsPerPartition, OracleJsonObject params) {
        if (expectedMinVectorsPerPartition < 0) { return; }
        Assertions.assertEquals(expectedMinVectorsPerPartition, params.getInt("min_vectors_per_partition"), "Unexpected vectors per partition");
    }

    private void assertEfConstruction(int expectedEfConstruction, OracleJsonObject params) {
        if (expectedEfConstruction < 0) { return; }
        Assertions.assertEquals(expectedEfConstruction, params.getInt("efConstruction"), "Unexpected ef construction");
    }

    private void assertNeighbors(int expectedNeighbors, OracleJsonObject params) {
        if (expectedNeighbors < 0) { return; }
        Assertions.assertEquals(expectedNeighbors, params.getInt("num_neighbors"), "Unexpected neighbors");
    }

    static Stream<Arguments> createIndexArguments() {
        return Stream.of(
            Arguments.arguments(IndexType.IVF_VECTOR_INDEX.toString(), -1, -1, -1, -1, -1, -1, -1),
            Arguments.arguments(IndexType.IVF_VECTOR_INDEX.toString(), 80, 1, 5, 2, 3, -1, -1),
            Arguments.arguments(IndexType.IVF_VECTOR_INDEX.toString(), 50, 2, 4, 7, 1, -1, -1),
            Arguments.arguments(IndexType.IVF_VECTOR_INDEX.toString(), 70, 3, -1, 2, 3, -1, -1),
            Arguments.arguments(IndexType.IVF_VECTOR_INDEX.toString(), 90, 4, 5, -1, 3, -1, -1),
            Arguments.arguments(IndexType.IVF_VECTOR_INDEX.toString(), 95, 5, 5, 2, -1, -1, -1),

            Arguments.arguments(IndexType.HNSW_VECTOR_INDEX.toString(), 80, 1, -1, -1, -1, -1, -1),
            Arguments.arguments(IndexType.HNSW_VECTOR_INDEX.toString(), 80, 2, -1, -1, -1, 2, 3),
            //Arguments.arguments(IndexType.HNSW.toString(), 80, 4, -1, -1, -1, 5, 10),
            // Check why degree of parallelism 4 is not working
            Arguments.arguments(IndexType.HNSW_VECTOR_INDEX.toString(), 80, 2, -1, -1, -1, 5, 10),
            Arguments.arguments(IndexType.HNSW_VECTOR_INDEX.toString(), 80, 2, -1, -1, -1, 4, 3),
            Arguments.arguments(IndexType.HNSW_VECTOR_INDEX.toString(), 80, 1, -1, -1, -1, 3, 4)
        );
    }

}

