package dev.langchain4j.store.embedding.oracle;

import oracle.sql.json.OracleJsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.stream.Stream;

public class BuilderParametersIT {

  static final String tableName = "EMBEDDING_STORE";
  static final String indexName = tableName + "_VECTOR_INDEX";

  @AfterEach
  public void afterAll() throws Exception{
    try (Connection connection = CommonTestOperations.getDataSource().getConnection();
         Statement stmt = connection.createStatement()) {
      stmt.execute("DROP INDEX IF EXISTS " + indexName);
      stmt.execute("DROP TABLE IF EXISTS " + tableName);
    }
  }

  @ParameterizedTest
  @MethodSource("createIndexArguments")
  public void testCreateIndexOnStoreCreation(
      String distanceMetric,
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
    OracleEmbeddingStore.Builder vectorIndexBuilder = OracleEmbeddingStore.builder()
        .dataSource(CommonTestOperations.getDataSource())
        .tableName(tableName)
        .exactSearch(false)
        .createTable(true).createIndex(true);
    if (distanceMetric != null) vectorIndexBuilder = vectorIndexBuilder.distanceMetric(DistanceMetric.valueOf(distanceMetric));
    if (indexType != null) vectorIndexBuilder = vectorIndexBuilder.type(IndexType.valueOf(indexType));
    if (targetAccuracy >= 0) vectorIndexBuilder = vectorIndexBuilder.targetAccuracy(targetAccuracy);
    if (degreeOfParallelism >= 0) vectorIndexBuilder = vectorIndexBuilder.degreeOfParallelism(degreeOfParallelism);
    if (neighborPartitions >= 0) vectorIndexBuilder = vectorIndexBuilder.neighborPartitions(neighborPartitions);
    if (samplePerPartition >= 0) vectorIndexBuilder = vectorIndexBuilder.samplePerPartition(samplePerPartition);
    if (minVectorsPerPartition >= 0) vectorIndexBuilder = vectorIndexBuilder.minVectorsPerPartition(minVectorsPerPartition);
    if (efConstruction >= 0) vectorIndexBuilder = vectorIndexBuilder.efConstruction(efConstruction);
    if (neighbors >= 0) vectorIndexBuilder = vectorIndexBuilder.neighbors(neighbors);
    vectorIndexBuilder.build();
    try (Connection connection = CommonTestOperations.getSysDataSource().getConnection();
         PreparedStatement stmt = connection.prepareStatement("select IDX_PARAMS from vecsys.vector$index where IDX_NAME = ?")
    ) {
      stmt.setString(1, indexName);
      ResultSet rs = stmt.executeQuery();
      Assertions.assertTrue(rs.next(), "A index should be returned");
      OracleJsonObject params = rs.getObject("IDX_PARAMS", OracleJsonObject.class);
      assertDistanceMetric(distanceMetric, params);
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

  }

  @Test
  public void testExceptions() {
    OracleEmbeddingStore.Builder vectorIndexBuilder = OracleEmbeddingStore.builder()
        .dataSource(CommonTestOperations.getDataSource())
        .tableName(tableName)
        .exactSearch(false)
        .createTable(true).createIndex(true);
    IllegalArgumentException exception;
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.targetAccuracy(0);});
    Assertions.assertEquals("The target accuracy must be a value between 1 and 100.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.targetAccuracy(101);});
    Assertions.assertEquals("The target accuracy must be a value between 1 and 100.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.neighbors(-1);});
    Assertions.assertEquals("The maximum number of neighbors a vector can have on any layer on a HNSW index must be between 1 and 2048.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.neighbors(2049);});
    Assertions.assertEquals("The maximum number of neighbors a vector can have on any layer on a HNSW index must be between 1 and 2048.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.neighbors(2040);});
    Assertions.assertEquals("This parameter can only be set on an index of type HNSW.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.efConstruction(0);});
    Assertions.assertEquals("EFCONSTRUCTION should be value between 1 and 65535.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.efConstruction(65536);});
    Assertions.assertEquals("EFCONSTRUCTION should be value between 1 and 65535.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.efConstruction(65500);});
    Assertions.assertEquals("This parameter can only be set on an index of type HNSW.", exception.getMessage());
    vectorIndexBuilder.type(IndexType.HNSW);
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.neighborPartitions(0);});
    Assertions.assertEquals("The maximum number of centroid partitions that are created by the IVF index cannot be lower than 1 or higher than 10000000.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.neighborPartitions(10000001);});
    Assertions.assertEquals("The maximum number of centroid partitions that are created by the IVF index cannot be lower than 1 or higher than 10000000.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.neighborPartitions(100);});
    Assertions.assertEquals("This parameter can only be set on an index of type IVF.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.samplePerPartition(0);});
    Assertions.assertEquals("The maximum number of samples per partition must be 1 or higher.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.samplePerPartition(1);});
    Assertions.assertEquals("This parameter can only be set on an index of type IVF.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.minVectorsPerPartition(-1);});
    Assertions.assertEquals("The minimum number of vectors per partition must be positive.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {vectorIndexBuilder.minVectorsPerPartition(0);});
    Assertions.assertEquals("This parameter can only be set on an index of type IVF.", exception.getMessage());
  }

  private void assertDistanceMetric(String expectedDistanceMetric, OracleJsonObject params) {
    if (expectedDistanceMetric == null) { return; }
    Assertions.assertEquals(expectedDistanceMetric.toString(), params.getString("distance"), "Unexpected distance metric");
  }

  private void assertIndexType(String expectedIndexType, OracleJsonObject params) {
    if (expectedIndexType == null) { expectedIndexType = "IVF"; } // set to default value if not set
    Assertions.assertEquals(expectedIndexType == "IVF" ? "IVF_FLAT" : "HNSW", params.getString("type"), "Unexpected index type");
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
        Arguments.arguments(null, null, -1, -1, -1, -1, -1, -1, -1),
        Arguments.arguments(DistanceMetric.COSINE.toString(), IndexType.IVF.toString(), 80, 1, 5, 2, 3, -1, -1),
        Arguments.arguments(DistanceMetric.DOT.toString(), IndexType.IVF.toString(), 50, 2, 4, 7, 1, -1, -1),
        Arguments.arguments(DistanceMetric.EUCLIDEAN.toString(), IndexType.IVF.toString(), 70, 3, -1, 2, 3, -1, -1),
        Arguments.arguments(DistanceMetric.EUCLIDEAN_SQUARED.toString(), IndexType.IVF.toString(), 90, 4, 5, -1, 3, -1, -1),
        Arguments.arguments(DistanceMetric.MANHATTAN.toString(), IndexType.IVF.toString(), 95, 5, 5, 2, -1, -1, -1),
        Arguments.arguments(DistanceMetric.COSINE.toString(), IndexType.HNSW.toString(), 80, 1, -1, -1, -1, -1, -1),
        Arguments.arguments(DistanceMetric.DOT.toString(), IndexType.HNSW.toString(), 80, 2, -1, -1, -1, 2, 3),
        Arguments.arguments(DistanceMetric.EUCLIDEAN.toString(), IndexType.HNSW.toString(), 80, 4, -1, -1, -1, 5, 10),
        Arguments.arguments(DistanceMetric.EUCLIDEAN_SQUARED.toString(), IndexType.HNSW.toString(), 80, 2, -1, -1, -1, 4, 3),
        Arguments.arguments(DistanceMetric.MANHATTAN.toString(), IndexType.HNSW.toString(), 80, 1, -1, -1, -1, 3, 4)
    );
  }

}
