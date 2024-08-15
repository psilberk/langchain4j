package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import oracle.jdbc.datasource.OracleDataSource;
import oracle.sql.CHAR;
import oracle.sql.CharacterSet;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.testcontainers.oracle.OracleContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;
import java.util.logging.Logger;

/**
 * A collection of operations which are shared by tests in this package.
 */
final class CommonTestOperations {

    /**
     * Model used to generate embeddings for this test. The all-MiniLM-L6-v2 model is chosen for consistency with other
     * implementations of EmbeddingStoreIT.
     */
    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();

    /** Name of a database table used by tests */
    public static final String TABLE_NAME = "langchain4j_embedding_store";

    /**
     * Seed for random numbers. When a test fails, "-Ddev.langchain4j.store.embedding.oracle.SEED=..." can be used to
     * re-execute it with the same random numbers.
     */
    private static final long SEED = Long.getLong(
            "dev.langchain4j.store.embedding.oracle.SEED", System.currentTimeMillis());
    static {
        Logger.getLogger(CommonTestOperations.class.getName())
                .info("dev.langchain4j.store.embedding.oracle.SEED=" + SEED);
    }

    /**
     * Used to generate random numbers, such as those for an embedding vector.
     */
    private static final Random RANDOM = new Random(SEED);

    private CommonTestOperations() {}

    private static final PoolDataSource DATA_SOURCE = PoolDataSourceFactory.getPoolDataSource();

    static {
        try {
            DATA_SOURCE.setConnectionFactoryClassName("oracle.jdbc.datasource.impl.OracleDataSource");
            String urlFromEnv = System.getenv("ORACLE_JDBC_URL");

            if (urlFromEnv == null) {
                // The Ryuk component is relied upon to stop this container.
                OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.4-slim-faststart")
                    .withDatabaseName("pdb1")
                    .withUsername("testuser")
                    .withPassword("testpwd");
                oracleContainer.start();

                DATA_SOURCE.setURL(oracleContainer.getJdbcUrl());
                DATA_SOURCE.setUser(oracleContainer.getUsername());
                DATA_SOURCE.setPassword(oracleContainer.getPassword());
            }
            else {
                DATA_SOURCE.setURL(urlFromEnv);
                DATA_SOURCE.setUser(System.getenv("ORACLE_JDBC_USER"));
                DATA_SOURCE.setPassword(System.getenv("ORACLE_JDBC_PASSWORD"));
            }

        } catch (
                SQLException sqlException) {
            throw new AssertionError(sqlException);
        }
    }

    static EmbeddingModel getEmbeddingModel() {
        return EMBEDDING_MODEL;
    }

    static DataSource getDataSource() {
        return DATA_SOURCE;
    }

    /**
     * Returns an embedding store configured to use a table with the common {@link #TABLE_NAME}. Any existing table
     * with this name is dropped and recreated. Tests should make use of {@link #dropTable()} to clean up after they're
     * finished.
     *
     * @return An embedding store configured to use a new table. Not null.
     */
    static OracleEmbeddingStore newEmbeddingStore() {
        return newEmbeddingStoreBuilder().build();
    }

    /**
     * Returns a builder configured to use a table with the common {@link #TABLE_NAME}. Any existing table
     * with this name is dropped and recreated when build() is called. Tests should make use of {@link #dropTable()} to
     * clean up after they're finished.
     *
     * @return A builder configured to use a new table. Not null.
     */
    static OracleEmbeddingStore.Builder newEmbeddingStoreBuilder() {
        return OracleEmbeddingStore.builder()
                .dataSource(getDataSource())
                .embeddingTable(TABLE_NAME, CreateOption.CREATE_OR_REPLACE);
    }

    /**
     * Drops the table and index created by {@link #newEmbeddingStore()}. Tests can call this method in
     * {@link org.junit.jupiter.api.AfterAll} or {@link org.junit.jupiter.api.AfterEach} methods to their tables.
     *
     * @throws SQLException If a database error prevents the drop.
     */
    static void dropTable() throws SQLException {
        dropTable(TABLE_NAME);
    }

    /**
     * Drops the table and index created by an embedding store. Tests can call this method in
     * {@link org.junit.jupiter.api.AfterAll} or {@link org.junit.jupiter.api.AfterEach} methods to their tables.
     *
     * @param tableName Name of table to drop. Not null.
     *
     * @throws SQLException If a database error prevents the drop.
     */
    static void dropTable(String tableName) throws SQLException {
        try (Connection connection = DATA_SOURCE.getConnection();
             Statement statement = connection.createStatement()) {
            statement.addBatch("DROP INDEX IF EXISTS " + tableName + "_embedding_index");
            statement.addBatch("DROP TABLE IF EXISTS " + tableName);
            statement.executeBatch();
        }
    }

    /**
     * Returns the character set of the database. This can be used in {@link org.junit.jupiter.api.Assumptions} that
     * require a unicode character set.
     */
    static CharacterSet getCharacterSet() throws SQLException {
        try (Connection connection = CommonTestOperations.getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 'c' FROM sys.dual")) {
            resultSet.next();
            return resultSet.getObject(1, CHAR.class).getCharacterSet();
        }
    }

    /**
     * Returns an array of random floats, which can be used to generate test embedding vectors.
     *
     * @param length Array length.
     * @return Array of random floats. Not null.
     */
    static float[] randomFloats(int length) {
        float[] floats = new float[length];

        for (int i = 0; i < floats.length; i++)
            floats[i] = RANDOM.nextFloat();

        return floats;
    }
}