package dev.langchain4j.store.embedding.oracle.index;

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
   * <em>Inverted File Flat (IVF)</em>:  index is the only type of Neighbor Partition
   * vector index supported. Inverted File Flat Index (IVF Flat or simply IVF) is a
   * partitioned-based index which balance high search quality with reasonable speed.
   */
  IVF("ORGANIZATION NEIGHBOR PARTITIONS"),
  /**
   * <em>Hierarchical Navigable Small World (HNSW)</em>: is the only type of In-Memory
   * Neighbor Graph vector index supported. HNSW graphs are very efficient indexes for
   * vector approximate similarity search. HNSW graphs are structured using principles
   * from small world networks along with layered hierarchical organization.
   */
  HNSW("ORGANIZATION INMEMORY NEIGHBOR GRAPH");

  protected final String oragnization;
  private IndexType(String oragnization) {
    this.oragnization = oragnization;

  }
}
