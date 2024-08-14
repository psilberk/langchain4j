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

public class VectorIndexBuilderIT {

  static final String TABLE_NAME = "EMBEDDING_STORE";
  static final String INDEX_NAME = TABLE_NAME + "_VECTOR_INDEX";

  @AfterEach
  public void afterAll() throws Exception{
    try (Connection connection = CommonTestOperations.getDataSource().getConnection();
         Statement stmt = connection.createStatement()) {
      stmt.execute("DROP INDEX IF EXISTS " + INDEX_NAME);
      stmt.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
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
    OracleEmbeddingStore.Builder builder = OracleEmbeddingStore.builder()
        .dataSource(CommonTestOperations.getDataSource());
    VectorIndexBuilder vectorIndexBuilder;
    if (indexType == "IVF") {
      vectorIndexBuilder = new IVFIndexBuilder();
      IVFIndexBuilder ivfIndexBuilder = (IVFIndexBuilder) vectorIndexBuilder;
      if (neighborPartitions >= 0) ivfIndexBuilder = ivfIndexBuilder.neighborPartitions(neighborPartitions);
      if (samplePerPartition >= 0) ivfIndexBuilder = ivfIndexBuilder.samplePerPartition(samplePerPartition);
      if (minVectorsPerPartition >= 0) ivfIndexBuilder = ivfIndexBuilder.minVectorsPerPartition(minVectorsPerPartition);
    } else {
      vectorIndexBuilder = new HNSWIndexBuilder();
      HNSWIndexBuilder hnswIndexBuilder = (HNSWIndexBuilder) vectorIndexBuilder;
      if (efConstruction >= 0) hnswIndexBuilder = hnswIndexBuilder.efConstruction(efConstruction);
      if (neighbors >= 0) hnswIndexBuilder = hnswIndexBuilder.neighbors(neighbors);
    }
    vectorIndexBuilder.createOption(CreateOption.CREATE_OR_REPLACE);
    if (targetAccuracy >= 0) vectorIndexBuilder = vectorIndexBuilder.targetAccuracy(targetAccuracy);
    if (degreeOfParallelism >= 0) vectorIndexBuilder = vectorIndexBuilder.degreeOfParallelism(degreeOfParallelism);
    if (distanceMetric != null) vectorIndexBuilder = vectorIndexBuilder.distanceMetric(DistanceMetric.valueOf(distanceMetric));
    builder.embeddingTable(
        EmbeddingTable
            .builder()
            .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
            .name(TABLE_NAME)
            .vectorIndexBuilder(vectorIndexBuilder)
            .build()
    ).build();
    try (Connection connection = CommonTestOperations.getVectorIndexDataSource().getConnection();
         PreparedStatement stmt = connection.prepareStatement("select IDX_PARAMS from vecsys.vector$index where IDX_NAME = ?")
    ) {
      stmt.setString(1, INDEX_NAME);
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
    IVFIndexBuilder ivfIndexBuilder = new IVFIndexBuilder();

    IllegalArgumentException exception;
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {ivfIndexBuilder.targetAccuracy(0);});
    Assertions.assertEquals("The target accuracy must be a value between 1 and 100.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {ivfIndexBuilder.targetAccuracy(101);});
    Assertions.assertEquals("The target accuracy must be a value between 1 and 100.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {ivfIndexBuilder.neighborPartitions(0);});
    Assertions.assertEquals("The maximum number of centroid partitions that are created by the IVF index cannot be lower than 1 or higher than 10000000.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {ivfIndexBuilder.neighborPartitions(10000001);});
    Assertions.assertEquals("The maximum number of centroid partitions that are created by the IVF index cannot be lower than 1 or higher than 10000000.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {ivfIndexBuilder.samplePerPartition(0);});
    Assertions.assertEquals("The maximum number of samples per partition must be 1 or higher.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {ivfIndexBuilder.minVectorsPerPartition(-1);});
    Assertions.assertEquals("The minimum number of vectors per partition must be positive.", exception.getMessage());

    HNSWIndexBuilder hnswIndexBuilder = new HNSWIndexBuilder();
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {hnswIndexBuilder.neighbors(-1);});
    Assertions.assertEquals("The maximum number of neighbors a vector can have on any layer on a HNSW index must be between 1 and 2048.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {hnswIndexBuilder.neighbors(2049);});
    Assertions.assertEquals("The maximum number of neighbors a vector can have on any layer on a HNSW index must be between 1 and 2048.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {hnswIndexBuilder.efConstruction(0);});
    Assertions.assertEquals("EFCONSTRUCTION should be value between 1 and 65535.", exception.getMessage());
    exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {hnswIndexBuilder.efConstruction(65536);});
    Assertions.assertEquals("EFCONSTRUCTION should be value between 1 and 65535.", exception.getMessage());

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
        Arguments.arguments(null, IndexType.IVF.toString(), -1, -1, -1, -1, -1, -1, -1),
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
