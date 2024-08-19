package dev.langchain4j.store.embedding.oracle;

/**
 * Enumeration of <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/vector-distance-metrics.html">
 * distance metrics supported by Oracle Database
 * </a>.
 */
public enum DistanceMetric {

    /**
     * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/cosine-similarity.html">
     * The cosine distance between two vectors
     * </a>. This is the default used by Oracle Database.
     */
    COSINE,

    /**
     * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/dot-product-similarity.html">
     * The negated dot product of two vectors
     * </a>. This metric is also referred to as the "inner product".
     */
    DOT_PRODUCT("DOT"),

    /**
     * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/euclidean-and-squared-euclidean-distances.html">
     * The Euclidean distance between two vectors
     * </a>. This metric is also referred to as the "L2 distance".
     */
    EUCLIDEAN,

    /**
     * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/euclidean-and-squared-euclidean-distances.html">
     * The Euclidean distance between two vectors, without taking the square root
     * </a>. This metric is also referred to as the "L2 squared distance".
     */
    EUCLIDEAN_SQUARED,

    /**
     * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/hamming-similarity.html">
     * The hamming distance between two vectors
     * </a>
    HAMMING,
     TODO: Uncomment this if the embedding store adds support for INT8 or BINARY dimension types; It's not compatible
        with FLOAT32 or FLOAT64.
     */

    /**
     * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/manhattan-distance.html">
     * The Manhattan distance between two vectors
     * </a>. This metric is also referred to as the "L1 distance" or "taxicab distance".
     */
    MANHATTAN;

    /**
     * The name for this distance metric that is recognized in Oracle SQL statements, such as the
     * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/vector_distance-vecse.html">
     * VECTOR_DISTANCE
     * </a> function or a CREATE VECTOR INDEX command.
     */
    private final String sqlName;

    /**
     * Constructs a distance metric with a {@link #sqlName} that is identical to the enum {@link #name()}.
     */
    DistanceMetric() {
        this.sqlName = name();
    }

    /**
     * Constructs a distance metric with a specified {@link #sqlName}.
     *
     * @param sqlName Name of the metric which is recognized by the VECTOR_DISTANCE function.
     */
    DistanceMetric(String sqlName) {
       this.sqlName = sqlName;
    }

    /**
     * The name for this distance metric that is recognized in Oracle SQL statements, such as the
     * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/vector_distance-vecse.html">
     * VECTOR_DISTANCE
     * </a> function or a CREATE VECTOR INDEX command.
     *
     * @return The name recognized by VECTOR_DISTANCE. Not null.
     */
    String sqlName() {
        return sqlName;
    }
}
