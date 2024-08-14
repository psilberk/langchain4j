package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.store.embedding.oracle.CreateOption;

/**
 * This interface is used to generate SQL statements to create and drop database indexes.
 */
public interface DatabaseIndexBuilder {

  /**
   * Generates a SQL statement that can be used to create an index on the specified
   * table and column.
   *
   * @param tableName The name of the table the index should be created on.
   * @param columnName The name of the column to be indexed.
   * @return The SQL statement allowing to create the index on specified table
   * and columns.
   */
  public abstract String getCreateStatement(String tableName, String columnName);

  /**
   * Generates a SQL statement that can be used to drop an index on the specified
   * table.
   *
   * @param tableName the name of the table
   * @return A SQL statement that can be used to drop the index.
   */
  String getDropStatement(String tableName);

}

