package dev.langchain4j.store.embedding.oracle;

import java.util.function.Function;

/**
 * <p>
 * Oracle AI Vector Search supports the following categories of vector indexing methods based on
 * approximate nearest-neighbors (ANN) search:
 * <ul>
 *   <li>Inverted File Flat (IVF)</li>
 *   <li>Hierarchical Navigable Small World (HNSW)</li>
 * </ul>
 * </p>
 */
public enum IndexType {
  /**
   * <p>
   *   This index type can be used to index the embedding column of the
   *   {@link EmbeddingTable}.
   * </p><p>
   *   <em>Inverted File Flat (IVF)</em>:  index is the only type of Neighbor Partition
   *   vector index supported. Inverted File Flat Index (IVF Flat or simply IVF) is a
   *   partitioned-based index which balance high search quality with reasonable speed.
   * </p>
   */
  IVF_VECTOR_INDEX( "ORGANIZATION NEIGHBOR PARTITIONS", (t) -> new IVFIndexBuilder(t)),
  /**
   * <p>
   *   This index type can be used to index the embedding column of the
   *   {@link EmbeddingTable}.
   * </p><p>
   *   <em>Hierarchical Navigable Small World (HNSW)</em>: is the only type of In-Memory
   *   Neighbor Graph vector index supported. HNSW graphs are very efficient indexes for
   *   vector approximate similarity search. HNSW graphs are structured using principles
   *   from small world networks along with layered hierarchical organization.
   * </p>
   */
  HNSW_VECTOR_INDEX("ORGANIZATION INMEMORY NEIGHBOR GRAPH", (t) -> new HNSWIndexBuilder(t)),
  /**
   * <p>
   *   This index type can be used to index the metadata column of the
   *   {@link EmbeddingTable}.
   * </p><p>
   *   It creates a function based index on one or several keys of the JSON document using
   *   the same function used by {@link OracleEmbeddingStore} to filter.
   * </p>
   *
   */
  FUNCTION_JSON_INDEX(null, (t) -> new JsonIndexBuilder(t));

  protected final String organization;

  private final Function<EmbeddingTable, IndexBuilder> builder;

  private IndexType(String organization, Function<EmbeddingTable, IndexBuilder> builder) {
    this.builder = builder;
    this.organization = organization;
  }

  /**
   * Returns a new instance of a builder for this type of index.
   * @param embeddingTable The embedding table the index should be created for.
   * @return The new instance of the index builder.
   */
  protected IndexBuilder getBuilder(EmbeddingTable embeddingTable) {
    return builder.apply(embeddingTable);
  }

}