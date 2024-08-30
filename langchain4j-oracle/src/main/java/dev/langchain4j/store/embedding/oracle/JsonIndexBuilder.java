package dev.langchain4j.store.embedding.oracle;

import java.sql.SQLType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Use this class to create indexes on one or several keys of the metadata
 * column of the {@link EmbeddingTable}.
 */
public class JsonIndexBuilder extends IndexBuilder<JsonIndexBuilder> {

  /**
   * Indicates whether the index is unique.
   */
  private boolean isUnique;

  /**
   * Indicates whether the index is a bitmap index.
   */
  private boolean isBitmap;

  /**
   * Create option for the index, by default create if not exists;
   */
  private CreateOption createOption = CreateOption.CREATE_IF_NOT_EXISTS;

  /**
   * List of index expressions of the index. An expression is added for
   * each JSON key that is indexed.
   */
  private final List<String> indexExpressions = new ArrayList<String>();

  /**
   * Use ASC or DESC to indicate whether the index should be created in ascending or
   * descending order. Indexes on character data are created in ascending or descending
   * order of the character values in the database character set.
   */
  public enum Order {
    /**
     * Create the index on ascending order.
     */
    ASC,
    /**
     * Create the index on descending order.
     */
    DESC
  }

  protected JsonIndexBuilder(EmbeddingTable embeddingTable) {
    super(embeddingTable);
  }

  /**
   * Configures the option to create (or not create) an index. The default is
   * {@link CreateOption#CREATE_IF_NOT_EXISTS}, which means that an index will
   * be created if an index with the same name does not already exist.
   *
   * @param createOption The create option.
   *
   * @return This builder.
   *
   * @throws IllegalArgumentException If createOption is null.
   */
  public JsonIndexBuilder createOption(CreateOption createOption) {
    ensureNotNull(createOption, "createOption");
    this.createOption = createOption;
    return this;
  }


  /**
   * Specify UNIQUE to indicate that the value of the column (or columns) upon
   * which the index is based must be unique.
   * Note that you cannot specify both UNIQUE and BITMAP.
   * 
   * @param isUnique True if the index should be UNIQUE otherwise false;
   * @return This builder.
   */
  public JsonIndexBuilder isUnique(boolean isUnique) {
    this.isUnique = isUnique;
    return this;
  }

  /**
   * Specify BITMAP to indicate that index is to be created with a bitmap for each
   * distinct key, rather than indexing each row separately.
   * 
   * @param isBitmap True if the index should be BITMAP otherwise false;
   * @return This builder.
   */
  public JsonIndexBuilder isBitmap(boolean isBitmap) {
    this.isBitmap = isBitmap;
    return this;
  }



  /**
   * Adds a column expression to the index expression that allows to index the
   * value of a given key of the JSON column.
   * 
   * @param key   The key to index.
   * @param sqlType The type of the metadata column.
   * @param order The order the index should be created in.
   * @return This builder.
   * @throws IllegalArgumentException If the key is null or empty, if the sqlType is null or if the order is null
   */
  public JsonIndexBuilder key(String key, SQLType sqlType, Order order) {
    ensureNotBlank(key, "key");
    ensureNotNull(sqlType, "sqlType");
    ensureNotNull(order, "order");
    indexExpressions.add(embeddingTable.metadataKeyMapper().apply(key, sqlType) + " " + order);
    return this;
  }

  /**
   * Creates the {@link TableIndex} described by this builder.
   * @return A {@link TableIndex} for the index described by this builder.
   */
  @Override
  public TableIndex build() {
    if (indexName == null || indexName.length() == 0) {
      indexName = buildIndexName(
          embeddingTable.name(),
          "_" + UUID.randomUUID().toString().replace("-", "_").toUpperCase());
    }
    String createStatement = "CREATE " +
        (isUnique ? " UNIQUE " : "") +
        (isBitmap ? " BITMAP " : "") +
        " INDEX " + indexName +
        (createOption == CreateOption.CREATE_IF_NOT_EXISTS ? " IF NOT EXISTS " : "") +
        " ON " + embeddingTable.name() +
        "(" + getIndexExpression() + ")";
    String dropStatement = "DROP INDEX IF EXISTS " + indexName;
    return new TableIndex(createOption, createStatement, dropStatement, indexName);
  }



  private String getIndexExpression() {
    return String.join(",", indexExpressions);
  }

}