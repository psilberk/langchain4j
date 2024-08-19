package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.store.embedding.oracle.CreateOption;

/**
 * This interface is used to generate SQL statements to create and drop database indexes.
 */
public interface DatabaseIndexBuilder {

  /**
   * Generates a SQL statement that can be used to create an index on the {@link EmbeddingTable}
   *
   * @return The SQL statement allowing to create the index on specified table
   * and columns.
   */
  public String getCreateStatement();

  /**
   * Generates a SQL statement that can be used to drop an index on the {@link EmbeddingTable}
   * table.
   *
   * @return A SQL statement that can be used to drop the index.
   */
  String getDropStatement();

}

