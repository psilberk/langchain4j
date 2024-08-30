package dev.langchain4j.store.embedding.oracle;

public abstract class IndexBuilder<T> {
  /**
   * The name of the index set by the user
   */
  String indexName;

  private static final int INDEX_NAME_MAX_LENGTH = 128;

  final EmbeddingTable embeddingTable;

  public IndexBuilder(EmbeddingTable embeddingTable) {
    this.embeddingTable = embeddingTable;
  }

  /**
   * <p>
   * Sets the name of the index.If no name is set, a name will be generated.
   * </p>
   * @param indexName The name of the index.
   * @return This builder.
   */
  public T indexName(String indexName) {
    this.indexName = indexName;
    return (T)this;
  }

  public abstract TableIndex build();


  /**
   * Creates an index name from the concatenation of the table name and a suffix.
   * @param tableName The table name.
   * @param suffix The suffix.
   * @return The index name.
   */
  String buildIndexName(String tableName, String suffix) {
    boolean isQuoted =  tableName.startsWith("\"") && tableName.endsWith("\"");
    // If the table name is a quoted identifier, then the index name must also be quoted.
    if (isQuoted) {
      tableName = unquoteTableName(tableName);
    }
    indexName = truncateIndexName(tableName + suffix, isQuoted);
    if (isQuoted) {
      indexName = "\"" + indexName + "\"";
    }
    return indexName;
  }

  private String truncateIndexName(String indexName, boolean isQuoted) {
    int maxLength = isQuoted ? INDEX_NAME_MAX_LENGTH - 2 : INDEX_NAME_MAX_LENGTH;
    if (indexName.length() > maxLength) {
      indexName = indexName.substring(0, maxLength);
    }
    return indexName;
  }

  private String unquoteTableName(String tableName) {
    return tableName.substring(1, tableName.length() - 1);
  }
}
