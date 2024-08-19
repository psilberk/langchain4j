package dev.langchain4j.store.embedding.oracle;

public class UserDefinedIndexBuilder implements DatabaseIndexBuilder {

  private String createIndexStatement;
  private String dropIndexStatement;

  public UserDefinedIndexBuilder() {}

  /**
   * Creates a user defined index given a CREATE INDEX statement and a DROP INDEX statement. The DROP
   * INDEX statement should only be provided if there is a need to drop the index before creating it.
   * @param createIndexStatement The CREATE INDEX statement.
   * @param dropIndexStatement The DROP INDEX statement or null if the INDEX should not be dropped
   *                           before it is created.
   */
  public UserDefinedIndexBuilder(String createIndexStatement, String dropIndexStatement) {
    this.createIndexStatement = createIndexStatement;
    this.dropIndexStatement = dropIndexStatement;
  }

  /**
   * Sets the CREATE INDEX statement.
   * @param createIndexStatement The CREATE INDEX statement.
   * @return This builder.
   */
  public UserDefinedIndexBuilder createIndexStatement (String createIndexStatement) {
    this.createIndexStatement = createIndexStatement;
    return this;
  }

  /**
   * Sets the DROP INDEX statement.
   * @param dropIndexStatement The DROP INDEX statement or null if the index should not be dropped.
   * @return This builder.
   */
  public UserDefinedIndexBuilder dropIndexStatement (String dropIndexStatement) {
    this.dropIndexStatement = dropIndexStatement;
    return this;
  }

  @Override
  public String getCreateStatement() {
    return createIndexStatement;
  }

  @Override
  public String getDropStatement() {
    return dropIndexStatement;
  }
}
