package dev.langchain4j.store.embedding.oracle;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;

/**
 * <p>
 * Builder that configures a Hierarchical Navigable Small World (HNSW) index
 * on the embedding column of the {@link EmbeddingTable}. The following
 * parameters can be configured:
 * <ul>
 *   <li>Create Option</li>
 *   <li>Target accuracy</li>
 *   <li>Degree of parallelism</li>
 *   <li>Neighbors</li>
 *   <li>EFCONSTRUCTION</li>
 * </ul>
 * </p><p>
 * Note that running Data Manipulation Language (DML) on tables with in-memory
 * neighbor graph vector index is not supported.
 * </p>
 */
public class HNSWIndexBuilder extends VectorIndexBuilder<HNSWIndexBuilder> {
  private int neighbors = -1;

  private int efConstruction = -1;

  public HNSWIndexBuilder(EmbeddingTable embeddingTable) {
    super(IndexType.HNSW_VECTOR_INDEX, embeddingTable);


  }

  /**
   * Configures the number of neighbors.
   * <p>
   * This is a HNSW Specific Parameters. It represent the maximum number of neighbors a vector
   * can have on any layer. The last vertex has one additional flexibility that it can have up
   * to 2M neighbors.
   * </p>
   * <p>
   * <em>Note:</em> this parameters can only be set on HNSW index and the default index type is
   * IVF. Make sure to set the indexType to HNSW before setting this parameter.
   * </p>
   *
   * @param neighbors The maximum number of neighbors. This parameter accepts values between 1 and
   *                  2048. By default, this vector index parameter will not be set.
   * @return This builder.
   * @throws IllegalArgumentException If the number of neighbors is not between 1 and 2048, or if
   *                                  the vector type is not HNSW.
   */
  public HNSWIndexBuilder neighbors(int neighbors) throws IllegalArgumentException {
    ensureBetween(neighbors, 0, 2048, "neighbors");
    this.neighbors = neighbors;
    return this;
  }

  /**
   * Configures the EFCONSTRUCTION parameter.
   * <p>
   * This is a HNSW Specific Parameters. It represent the maximum number of closest vector
   * candidates considered at each step of the search during insertion.
   * </p>
   * <p>
   * <em>Note:</em> this parameters can only be set on HNSW index and the default index type is
   * IVF. Make sure to set the indexType to HNSW before setting this parameter.
   * </p>
   *
   * @param efConstruction The maximum number of closest vector candidates considered at each step
   *                       of the search during insertion.
   * @return This builder.
   * @throws IllegalArgumentException If EFCONSTRUCTION is not between 1 and 65535, or if the
   *                                  vector type is not HNSW.
   */
  public HNSWIndexBuilder efConstruction(int efConstruction) throws IllegalArgumentException {
    ensureBetween(efConstruction, 1, 65535, "efConstruction");
    this.efConstruction = efConstruction;
    return this;
  }

  /**
   * Generates the PARAMETERS clause for a HNSW index.
   *
   * @return A string containing the PARAMETERS clause of the CREATE VECTOR INDEX statement.
   */
  @Override
  String getIndexParameters() {
    if (neighbors == -1 && efConstruction == -1) {
      return " ";
    }
    return "PARAMETERS ( TYPE HNSW" +
        (neighbors != -1 ? ", NEIGHBORS " + neighbors : " ") +
        (efConstruction != -1 ? ", EFCONSTRUCTION " + efConstruction : " ") + ")";
  }
}