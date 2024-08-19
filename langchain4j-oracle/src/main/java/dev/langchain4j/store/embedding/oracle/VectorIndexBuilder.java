package dev.langchain4j.store.embedding.oracle;

/**
 * Builder that configures a vector index.
 *
 * This class configures parameters that are common to the different types of vector index:
 * <ul>
 *   <li>Distance metric</li>
 *   <li>Target accuracy</li>
 *   <li>Degree of parallelism</li>
 * </ul>
 *
 * It is extended by classes that implement builders that configure specific types of vector indexes
 * {@link IVFIndexBuilder} and {@link HNSWIndexBuilder}.
 */
public abstract class VectorIndexBuilder<T> implements DatabaseIndexBuilder {

  /**
   * Suffix used when naming the index.
   */
  private String indexNameSufix = "_vector_index";
  /**
   * CreateOption for the index. By default, the index will not be created.
   */
  private CreateOption createOption = CreateOption.CREATE_NONE;

  protected IndexType indexType = IndexType.IVF;

  protected int targetAccuracy = -1;

  protected int degreeOfParallelism = -1;

  protected DistanceMetric distanceMetric = DistanceMetric.COSINE;

  private EmbeddingTable embeddingTable;

  /**
   * Configures the option to create (or not create) an index. The default is
   * {@link CreateOption#CREATE_NONE}, which means no attempt is made to create
   *  an index.
   *
   * @param createOption The create option.
   *
   * @return This builder.
   */
  public T createOption(CreateOption createOption) {
    this.createOption = createOption;
    this.indexNameSufix = "_vector_index";
    return (T)this;
  }

  /**
   * Configures the distance metric that will be used by the index. The default is
   * {@link DistanceMetric#COSINE}.
   *
   * @param distanceMetric The distance metric.
   *
   * @return This builder.
   */
  public T distanceMetric(DistanceMetric distanceMetric) {
    this.distanceMetric = distanceMetric;
    return (T)this;
  }

  /**
   * Configures the target accuracy.
   *
   * @param targetAccuracy Percentage value.
   * @return This builder.
   * @throws IllegalArgumentException If the target accuracy not between 1 and 100.
   */
  public T targetAccuracy(int targetAccuracy) throws IllegalArgumentException {
    if (targetAccuracy <= 0 || targetAccuracy > 100) {
      throw new IllegalArgumentException("The target accuracy must be a value between 1 and 100.");
    }
    this.targetAccuracy = targetAccuracy;
    return (T)this;
  }

  /**
   * Configures the degree of parallelism of the index.
   *
   * @param degreeOfParallelism The degree of parallelism.
   * @return This builder.
   */
  public T degreeOfParallelism(int degreeOfParallelism) {
    this.degreeOfParallelism = degreeOfParallelism;
    return (T)this;
  }

  protected void setEmbeddingTable(EmbeddingTable embeddingTable) {
    this.embeddingTable = embeddingTable;
  }

  /**
   * If CreateOption is equal to {@link CreateOption#CREATE_OR_REPLACE} or {@link CreateOption#CREATE_IF_NOT_EXISTS},
   * this method generates the CREATE VECTOR INDEX, otherwise it returns null.
   *
   * @return A SQL statement that can be used to create the index, or null if no index should be created.
   */
  @Override
  public String getCreateStatement() {
    if (createOption == CreateOption.CREATE_NONE) {
      return null;
    }
    String sqlStatement = "CREATE VECTOR INDEX " +
        (createOption == CreateOption.CREATE_IF_NOT_EXISTS ? "IF NOT EXISTS " : "") +
        getIndexName(embeddingTable.name()) +
        " ON " + embeddingTable.name() + "( " + embeddingTable.embeddingColumn() + " ) " +
        indexType.oragnization +
        (distanceMetric != null ? " WITH DISTANCE " + distanceMetric.toString() + " " : "") +
        (targetAccuracy > 0 ? " WITH TARGET ACCURACY " + targetAccuracy + " " : "") +
        (degreeOfParallelism >= 0 ? " PARALLEL " + degreeOfParallelism : "") +
        getIndexParameters();
    return sqlStatement;
  }

  /**
   * If CreateOption is equal to {@link CreateOption#CREATE_OR_REPLACE}, this method will return a SQL statement that
   * can be used to drop the index on the specified table, otherwise it returns null.
   *
   * @return A SQL statement that can be used to drop the index, or null if the index should not be dropped.
   */
  @Override
  public String getDropStatement() {
    if (createOption != CreateOption.CREATE_OR_REPLACE) {
      return null;
    }
    return "DROP INDEX IF EXISTS " + getIndexName(embeddingTable.embeddingColumn());
  }

  private String getIndexName(String tableName) {
    return tableName + indexNameSufix;
  }

  /**
   * Generates the PARAMETERS clause of the vector index. Implementation depends on the type of vector index.
   * @return A string containing the PARAMETERS clause of the index.
   */
  abstract String getIndexParameters();

}
