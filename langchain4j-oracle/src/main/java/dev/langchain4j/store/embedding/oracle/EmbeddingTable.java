package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;


/**
 * <p>
 * Represents a database table where embeddings, text, and metadata are stored. The columns of this table are listed
 * below.
 * </p>
 * <table id="table-columns">
 *    <caption>
 *    Table Columns
 *    </caption>
 *    <tr>
 *    <th>Name</th>
 *    <th>Type</th>
 *    <th>Description</th>
 * </tr><tr>
 *     <td>id</td>
 *     <td>VARCHAR(36)</td>
 *     <td>
 *         Primary key. Used to store {@link UUID} strings which are generated by
 *         {@link OracleEmbeddingStore#add(Embedding)},
 *         {@link OracleEmbeddingStore#add(Embedding, TextSegment)}, {@link OracleEmbeddingStore#addAll(List)}, and
 *         {@link OracleEmbeddingStore#add(Embedding, TextSegment)}
 *     </td>
 *     </tr><tr>
 *     <td>embedding</td>
 *     <td>VECTOR(*, FLOAT32)</td>
 *     <td>
 *         Stores the {@link Embedding#vector()} passed to {@link OracleEmbeddingStore#add(Embedding)},
 *         {@link OracleEmbeddingStore#add(Embedding, TextSegment)}, {@link OracleEmbeddingStore#addAll(List)}, and
 *         {@link OracleEmbeddingStore#add(Embedding, TextSegment)}. Never stores NULL.
 *     </td>
 *     </tr><tr>
 *     <td>text</td>
 *     <td>CLOB</td>
 *     <td>
 *         Stores the {@link TextSegment#text()} passed to {@link OracleEmbeddingStore#add(Embedding, TextSegment)} and
 *         {@link OracleEmbeddingStore#addAll(List, List)}. Stores NULL when {@link OracleEmbeddingStore#add(Embedding)}
 *         and {@link OracleEmbeddingStore#addAll(List)} are called.
 *     </td>
 *     </tr><tr>
 *     <td>metadata</td>
 *     <td>JSON</td>
 *     <td>
 *         Stores the {@link TextSegment#metadata()} passed to {@link OracleEmbeddingStore#add(Embedding, TextSegment)}
 *         and {@link OracleEmbeddingStore#addAll(List, List)}. Stores NULL when
 *         {@link OracleEmbeddingStore#add(Embedding)} and {@link OracleEmbeddingStore#addAll(List)} are called.
 *     </td>
 * </tr></table>
 * <p>
 * The column names listed above are used by default, but alternative names may be configured using
 * {@link Builder} methods.
 * </p>
 *
 */
public final class EmbeddingTable {

    /** Option which configures how the {@link #create(DataSource)} method creates this table */
    private final CreateOption createOption;

    /** The name of this table */
    private final String name;

    /** Name of a column which stores an id. */
    private final String idColumn;

    /** Name of a column which stores an embedding. */
    private final String embeddingColumn;

    /** Name of a column which stores text. */
    private final String textColumn;

    /** Name of a column which stores metadata. */
    private final String metadataColumn;
    private List<TableIndex> tableIndexes = new ArrayList<>();

    /**
     * The mapping function for use with {@link SQLFilters#create(Filter, BiFunction)}. The function maps a
     * {@link Metadata} key to a field of the JSON "metadata" column. The builtin JSON_VALUE function is used to
     * evaluate a JSON path expression.
     */
    private final BiFunction<String, SQLType, String> metadataKeyMapper;

    private EmbeddingTable(Builder builder) {
        createOption = builder.createOption;
        name = builder.name;
        idColumn = builder.idColumn;
        embeddingColumn = builder.embeddingColumn;
        textColumn = builder.textColumn;
        metadataColumn = builder.metadataColumn;
        metadataKeyMapper = (key, type) -> "JSON_VALUE(" + metadataColumn + ", '$." + key + "' RETURNING "
                + type.getName() + ")";
    }


    /**
     * Creates a table configured by the {@link Builder} of this EmbeddingTable. No table is created if the Builder was
     * configured with {@link CreateOption#CREATE_NONE}.
     *
     * @param dataSource Data source that connects to an Oracle Database where the table is (possibly) created.
     *
     * @throws SQLException If an error prevents the table from being created.
     */
    void create(DataSource dataSource) throws SQLException {
        if (createOption == CreateOption.CREATE_NONE)
            return;

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
        ) {
            if (createOption == CreateOption.CREATE_OR_REPLACE)
                statement.addBatch("DROP TABLE IF EXISTS " + name);

            statement.addBatch("CREATE TABLE IF NOT EXISTS " + name
                    + "(" + idColumn + " VARCHAR(36) NOT NULL, "
                    + embeddingColumn + " VECTOR(*, FLOAT32) NOT NULL, "
                    + textColumn + " CLOB, "
                    + metadataColumn + " JSON, "
                    + "PRIMARY KEY (" + idColumn + "))");

            statement.executeBatch();
        }
    }

    /**
     * Returns the name of this table.
     *
     * @return The name of this table. Not null.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the name of this table's <a href="#table-columns">ID column</a>.
     *
     * @return The name of this table's ID column. Not null.
     */
    public String idColumn() {
        return idColumn;
    }

    /**
     * Returns the name of this table's <a href="#table-columns">embedding column</a>.
     *
     * @return The name of this table's embedding column. Not null.
     */
    public String embeddingColumn() {
        return embeddingColumn;
    }

    /**
     * Returns the name of this table's <a href="#table-columns">text column</a>.
     *
     * @return The name of this table's text column. Not null.
     */
    public String textColumn() {
        return textColumn;
    }

    /**
     * Returns the name of this table's <a href="#table-columns">metadata column</a>.
     *
     * @return The name of this table's text column. Not null.
     */
    public String metadataColumn() {
        return metadataColumn;
    }

    /**
     * Returns the mapping function for use with
     * {@link SQLFilters#create(Filter, BiFunction)}. The function maps a
     * {@link Metadata} key to a field of the JSON "metadata" column. The builtin
     * JSON_VALUE function is used to evaluate a JSON path expression.
     * 
     * @return the mapping function for use with
     *         {@link SQLFilters#create(Filter, BiFunction)}.
     */
    public BiFunction<String, SQLType, String> metadataKeyMapper() {
        return metadataKeyMapper;
    }

    /**
     * Returns an iterable of the table indexes that have been added to the embedding
     * table.
     * @return The iterable of the table indexes.
     */
    public Iterable<TableIndex> getTableIndexes() { return tableIndexes; }

    /**
     * Returns a builder that configures a new EmbeddingTable.
     *
     * @return An EmbeddingTable builder. Not null.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder that configures and builds an {@link EmbeddingTable}.
     */
    public static class Builder {

        // These fields are specified by method level JavaDocs
        private CreateOption createOption = CreateOption.CREATE_NONE;
        private String name;
        private String idColumn = "id";
        private String embeddingColumn = "embedding";
        private String textColumn = "text";
        private String metadataColumn = "metadata";

        private Builder() {}

        /**
         * Configures the option to create (or not create) a table. The default is {@link CreateOption#CREATE_NONE},
         * which means that no attempt is made to create a table.
         *
         * @param createOption Option for creating the index. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder createOption(CreateOption createOption) {
            ensureNotNull(createOption, "createOption");
            this.createOption = createOption;
            return this;
        }

        /**
         * Configures the name of a table where embeddings are stored and retrieved from. A name must be configured,
         * there is no default name.
         *
         * @param name Name of database table. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder name(String name) {
            ensureNotNull(name, "name");
            this.name = name;
            return this;
        }

        /**
         * Configures the name of a column which stores an id. The default name is "id".
         *
         * @param idColumn Name of the id column. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder idColumn(String idColumn) {
            ensureNotNull(idColumn, "idColumn");
            this.idColumn = idColumn;
            return this;
        }

        /**
         * Configures the name of a column which stores an embedding. The default name is "embedding".
         *
         * @param embeddingColumn Name of the id column. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder embeddingColumn(String embeddingColumn) {
            ensureNotNull(embeddingColumn, "embeddingColumn");
            this.embeddingColumn = embeddingColumn;
            return this;
        }

        /**
         * Configures the name of a column which stores text. The default name is "text".
         *
         * @param textColumn Name of the text column. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder textColumn(String textColumn) {
            ensureNotNull(textColumn, "textColumn");
            this.textColumn = textColumn;
            return this;
        }

        /**
         * Configures the name of a column which stores metadata. The default name is "metadata".
         *
         * @param metadataColumn Name of the metadata column. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder metadataColumn(String metadataColumn) {
            ensureNotNull(metadataColumn, "metadataColumn");
            this.metadataColumn = metadataColumn;
            return this;
        }

        /**
         * Returns a new EmbeddingTable configured by this builder.
         *
         * @return A new EmbeddingTable. Not null.
         *
         * @throws IllegalArgumentException If this builder is missing any required configuration.
         */
        public EmbeddingTable build() {
            // Check required parameters
            ensureNotNull(name, "name");

            return new EmbeddingTable(this);
        }

    }
}
