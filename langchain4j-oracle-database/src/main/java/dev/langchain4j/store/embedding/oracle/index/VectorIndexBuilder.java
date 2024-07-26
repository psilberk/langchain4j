package dev.langchain4j.store.embedding.oracle.index;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.DistanceMetric;
import dev.langchain4j.store.embedding.oracle.index.DatabaseIndexBuilder;
import dev.langchain4j.store.embedding.oracle.index.IndexType;

public abstract class VectorIndexBuilder implements DatabaseIndexBuilder {

  protected IndexType indexType = IndexType.IVF;

  protected int targetAccuracy = -1;

  protected int degreeOfParallelism = -1;

  protected DistanceMetric distanceMetric = DistanceMetric.COSINE;

  protected CreateOption createOption = CreateOption.CREATE_NONE;

  /**
   * Configures the option to create (or not create) an index. The default is
   * {@link CreateOption#CREATE_NONE}, which means no attempt is made to create
   *  an index.
   *
   * @param createOption The create option.
   *
   * @return This builder.
   */
  public VectorIndexBuilder createOption(CreateOption createOption) {
    this.createOption = createOption;
    return this;
  }

  public VectorIndexBuilder distanceMetric(DistanceMetric distanceMetric) {
    this.distanceMetric = distanceMetric;
    return this;
  }

  /**
   * Configures the target accuracy.
   *
   * @param targetAccuracy Percentage value.
   * @return This builder.
   * @throws IllegalArgumentException If the target accuracy not between 1 and 100.
   */
  public VectorIndexBuilder targetAccuracy(int targetAccuracy) throws IllegalArgumentException {
    if (targetAccuracy <= 0 || targetAccuracy > 100) {
      throw new IllegalArgumentException("The target accuracy must be a value between 1 and 100.");
    }
    this.targetAccuracy = targetAccuracy;
    return this;
  }

  /**
   * Configures the degree of parallelism of the index.
   *
   * @param degreeOfParallelism The degree of parallelism.
   * @return This builder.
   */
  public VectorIndexBuilder degreeOfParallelism(int degreeOfParallelism) {
    this.degreeOfParallelism = degreeOfParallelism;
    return this;
  }

  /**
   * Generates the CREATE VECTOR INDEX statement.
   *
   * @return A SQL statement that can be used to create the index.
   */
  protected String generateCreateStatement(String tableName, String embeddingColumn) {
    String sqlStatement = "CREATE VECTOR INDEX IF NOT EXISTS " + getIndexName(tableName) +
        " ON " + tableName + "( " + embeddingColumn + " ) " +
        indexType.oragnization +
        (distanceMetric != null ? " WITH DISTANCE " + distanceMetric.toString() + " " : "") +
        (targetAccuracy > 0 ? " WITH TARGET ACCURACY " + targetAccuracy + " " : "") +
        (degreeOfParallelism >= 0 ? " PARALLEL " + degreeOfParallelism : "");
    return sqlStatement;
  }

  private String getIndexName(String tableName) {
    return tableName + "_vector_index";
  }

  @Override
  public String getDropStatement(String tableName) {
    if (createOption == CreateOption.CREATE_OR_REPLACE) {
      return "DROP INDEX IF EXISTS " + getIndexName(tableName);
    }
    return null;
  }
}
