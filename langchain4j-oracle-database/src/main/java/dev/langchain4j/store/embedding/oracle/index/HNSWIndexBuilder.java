package dev.langchain4j.store.embedding.oracle.index;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.DistanceMetric;

public class HNSWIndexBuilder extends VectorIndexBuilder {
  private int neighbors = -1;

  private int efConstruction = -1;

  public HNSWIndexBuilder() {
    this.indexType = IndexType.HNSW;
  }

  @Override
  public VectorIndexBuilder createOption(CreateOption createOption) {
    return (HNSWIndexBuilder)super.createOption(createOption);
  }

  @Override
  public HNSWIndexBuilder targetAccuracy(int targetAccuracy) throws IllegalArgumentException {
    return (HNSWIndexBuilder)super.targetAccuracy(targetAccuracy);
  }

  @Override
  public HNSWIndexBuilder degreeOfParallelism(int degreeOfParallelism) {
    return (HNSWIndexBuilder)super.degreeOfParallelism(degreeOfParallelism);
  }

  @Override
  public HNSWIndexBuilder distanceMetric(DistanceMetric distanceMetric) {
    return (HNSWIndexBuilder)super.distanceMetric(distanceMetric);
  }

  /**
   * Configures the number of neighbors.
   * <p>
   * This is a HNSW Specific Parameters. It represent the maximum number of neighbors a vector
   * can have on any layer. The last vertex has one additional flexibility that it can have up
   * to 2M neighbors.
   * </p>
   * <p>
   * <em>Note:</em> this parameters can only be set on HNSW index and the default index type is
   * IVF. Make sure to set the indexType to HNSW before setting this parameter.
   * </p>
   *
   * @param neighbors The maximum number of neighbors. This parameter accepts values between 1 and
   *                  2048. By default, this vector index parameter will not be set.
   * @return This builder.
   * @throws IllegalArgumentException If the number of neighbors is not between 1 and 2048, or if
   *                                  the vector type is not HNSW.
   */
  public HNSWIndexBuilder neighbors(int neighbors) throws IllegalArgumentException {
    if (neighbors <= 0 || neighbors > 2048) {
      throw new IllegalArgumentException("The maximum number of neighbors a vector can have " +
          "on any layer on a HNSW index must be between 1 and 2048.");
    }
    if (this.indexType != IndexType.HNSW) {
      throw new IllegalArgumentException("This parameter can only be set on an index of type HNSW.");
    }
    this.neighbors = neighbors;
    return this;
  }

  /**
   * Configures the EFCONSTRUCTION parameter.
   * <p>
   * This is a HNSW Specific Parameters. It represent the maximum number of closest vector
   * candidates considered at each step of the search during insertion.
   * </p>
   * <p>
   * <em>Note:</em> this parameters can only be set on HNSW index and the default index type is
   * IVF. Make sure to set the indexType to HNSW before setting this parameter.
   * </p>
   *
   * @param efConstruction The maximum number of closest vector candidates considered at each step
   *                       of the search during insertion.
   * @return This builder.
   * @throws IllegalArgumentException If EFCONSTRUCTION is not between 1 and 65535, or if the
   *                                  vector type is not HNSW.
   */
  public HNSWIndexBuilder efConstruction(int efConstruction) throws IllegalArgumentException {
    if (efConstruction < 1 || efConstruction > 65535) {
      throw new IllegalArgumentException("EFCONSTRUCTION should be value between 1 and 65535.");
    }
    if (this.indexType != IndexType.HNSW) {
      throw new IllegalArgumentException("This parameter can only be set on an index of type HNSW.");
    }
    this.efConstruction = efConstruction;
    return this;
  }

  @Override
  public String getCreateStatement(String tableName, String embeddingColumn) {
    if (createOption == CreateOption.CREATE_NONE) return null;
    return generateCreateStatement(tableName, embeddingColumn) + getHNSWParameters();
  }

  /**
   * Generates the PARAMETERS clause for a HNSW index.
   *
   * @return A string containing the PARAMETERS clause of the CREATE VECTOR INDEX statement.
   */
  private String getHNSWParameters() {
    if (neighbors == -1 && efConstruction == -1) {
      return " ";
    }
    return "PARAMETERS ( TYPE HNSW" +
        (neighbors != -1 ? ", NEIGHBORS " + neighbors : " ") +
        (efConstruction != -1 ? ", EFCONSTRUCTION " + efConstruction : " ") + ")";
  }
}
