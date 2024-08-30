package dev.langchain4j.store.embedding.oracle;

public class TableIndex {
  private CreateOption createOption;
  private String createIndexStatement;
  private String dropIndexStatement;

  private String indexName;

  TableIndex(CreateOption createOption, String createIndexStatement, String dropIndexStatement, String indexName) {
    this.createOption = createOption;
    this.createIndexStatement = createIndexStatement;
    this.dropIndexStatement = dropIndexStatement;
    this.indexName = indexName;
  }

  /**
   * Gets the {@link CreateOption} for the index.
   */
  public CreateOption getCreateOption()  {
    return createOption;
  }

  /**
   * Gets the CREATE INDEX statement.
   *
   * @return A SQL statement to create the index.
   * {@link CreateOption#CREATE_NONE}
   */
  public String getCreateIndexStatement() {
    if (createOption == CreateOption.CREATE_NONE) return null;
    return createIndexStatement;
  }

  /**
   * Gets the DROP INDEX statement.
   * @return  A SQL statement to drop the index.
   */
  public String getDropIndexStatement() {
    if (createOption == CreateOption.CREATE_OR_REPLACE) return dropIndexStatement;
    return null;
  }

  /**
   * Get the name of the index.
   * @return The index name.
   */
  public String getIndexName() {
    return indexName;
  }

}
