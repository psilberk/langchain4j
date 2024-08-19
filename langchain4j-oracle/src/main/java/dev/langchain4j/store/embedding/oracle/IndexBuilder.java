package dev.langchain4j.store.embedding.oracle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Use this class to create indexes in other columns of the {@link EmbeddingTable} than
 * the embedding column. This class can be used to create an index in one or many
 * columns and on one or many keys of the metadata column of the {@link EmbeddingTable}.
 */
public class IndexBuilder implements DatabaseIndexBuilder {
  private final EmbeddingTable embeddingTable;

  private final UnaryOperator<String> metadataKeyMapper;

  private String indexName;

  private boolean isUnique;

  private boolean isBitmap;

  private CreateOption createOption = CreateOption.CREATE_IF_NOT_EXISTS;

  public final List<String> indexExpressions = new ArrayList<String>();

  protected IndexBuilder(EmbeddingTable embeddingTable, UnaryOperator<String> metadataKeyMapper) {
    this.embeddingTable = embeddingTable;
    this.metadataKeyMapper = metadataKeyMapper;
    this.indexName = embeddingTable.name() + UUID.randomUUID();
  }

  public enum Order {
    ASC,
    DESC
  }

  /**
   * Configures the option to create (or not create) an index. The default is
   * {@link CreateOption#CREATE_IF_NOT_EXISTS}, which means that an index will
   * be created if an index with the same name does not already exist.
   *
   * @param createOption The create option.
   *
   * @return This builder.
   */
  public IndexBuilder createOption(CreateOption createOption) {
    this.createOption = createOption;
    return this;
  }

  /**
   * Sets the name of the index, by defaut the name of the index will be the concatenation of the
   * table name and a random {@link java.util.UUID}
   * @param indexName The name of the index.
   * @return This builder.
   */
  public IndexBuilder indexName(String indexName) {
    this.indexName = indexName;
    return this;
  }

  /**
   * Specify UNIQUE to indicate that the value of the column (or columns) upon which the index is based must be unique.
   * Note that you cannot specify both UNIQUE and BITMAP.
   * @param isUnique True if the index should be UNIQUE otherwise false;
   * @return This builder.
   */
  public IndexBuilder isUnique(boolean isUnique) {
    this.isUnique = isUnique;
    return this;
  }

  /**
   * Specify BITMAP to indicate that index is to be created with a bitmap for each distinct key, rather than indexing each row separately.
   * @param isBitmap True if the index should be BITMAP otherwise false;
   * @return This builder.
   */
  public IndexBuilder isBitmap(boolean isBitmap) {
    this.isBitmap = isBitmap;
    return this;
  }

  /**
   * Adds a column to the index expression of the index with the given order.
   * @param column The column to index.
   * @param order The order.
   * @return This builder.
   */
  public IndexBuilder column(String column, Order order) {
    indexExpressions.add(column + " " + order);
    return this;
  }

  /**
   * Adds a column expression to the index expression that allows to index the
   * value of a given key of the JSON column.
   * @param key The key to index.
   * @param order The order.
   * @return This builder
   */
  public IndexBuilder key(String key, Order order) {
    indexExpressions.add(metadataKeyMapper.apply(key) + " " + order);
    return this;
  }


  @Override
  public String getCreateStatement() {
    if (createOption == CreateOption.CREATE_NONE) {
      return null;
    }
    return "CREATE " +
        (isUnique ? " UNIQUE " : "") +
        (isBitmap ? " BITMAP " : "") +
        " INDEX " + indexName +
        (createOption == CreateOption.CREATE_IF_NOT_EXISTS ? " IF NOT EXISTS " : "") +
        " ON " + embeddingTable.name() +
        "(" + getIndexExpression() + ")";
  }

  @Override
  public String getDropStatement() {
    if (createOption == CreateOption.CREATE_OR_REPLACE) {
      return "DROP INDEX " + indexName;
    }
    return null;
  }

  private String getIndexExpression() {
    return String.join(",", indexExpressions);
  }

}
