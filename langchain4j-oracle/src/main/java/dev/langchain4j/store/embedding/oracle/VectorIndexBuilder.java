package dev.langchain4j.store.embedding.oracle;

import static dev.langchain4j.internal.ValidationUtils.*;

/**
 * Builder that configures a vector index.
 *
 * This class configures parameters that are common to the different types of vector index:
 * <ul>
 *   <li>Create Option</li>
 *   <li>Target accuracy</li>
 *   <li>Degree of parallelism</li>
 * </ul>
 *
 * It is extended by classes that implement builders that configure specific types of vector indexes
 * {@link IVFIndexBuilder} and {@link HNSWIndexBuilder}.
 */
public abstract class VectorIndexBuilder<T> extends IndexBuilder<T> {

  private IndexType indexType;
  /**
   * Suffix used when naming the index.
   */
  private static final String INDEX_NAME_SUFIX = "_VECTOR_INDEX";
  /**
   * CreateOption for the index. By default, the index will not be created.
   */
  private CreateOption createOption = CreateOption.CREATE_NONE;

  protected int targetAccuracy = -1;

  protected int degreeOfParallelism = -1;

  public VectorIndexBuilder(IndexType indexType, EmbeddingTable embeddingTable) {
    super(embeddingTable);
    this.indexType = indexType;
  }

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
    ensureNotNull(createOption, "createOption");
    this.createOption = createOption;
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
    ensureBetween(targetAccuracy, 0, 100, "targetAccuracy");
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
    ensureGreaterThanZero(degreeOfParallelism, "degreeOfParallelism");
    this.degreeOfParallelism = degreeOfParallelism;
    return (T)this;
  }

  public TableIndex build() {
    if (indexName == null || indexName.length() == 0) {
      indexName = buildIndexName(embeddingTable.name(), INDEX_NAME_SUFIX);
    }
    String createIndexStatement = "CREATE VECTOR INDEX " +
        (createOption == CreateOption.CREATE_IF_NOT_EXISTS ? "IF NOT EXISTS " : "") +
        indexName +
        " ON " + embeddingTable.name() + "( " + embeddingTable.embeddingColumn() + " ) " +
        indexType.organization +
        " WITH DISTANCE COSINE " +
        (targetAccuracy > 0 ? " WITH TARGET ACCURACY " + targetAccuracy + " " : "") +
        (degreeOfParallelism >= 0 ? " PARALLEL " + degreeOfParallelism : "") +
        getIndexParameters();
    String dropIndexStatement = "DROP INDEX IF EXISTS " + indexName;
    return new TableIndex(createOption, createIndexStatement, dropIndexStatement, indexName);
  }


  /**
   * Generates the PARAMETERS clause of the vector index. Implementation depends on the type of vector index.
   * @return A string containing the PARAMETERS clause of the index.
   */
  abstract String getIndexParameters();

}