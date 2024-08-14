package dev.langchain4j.store.embedding.oracle;

/**
 * Builder that configures an Inverted File Flat (IVF) index. If extends
 * {@link VectorIndexBuilder} and configures parameters that are specific
 * to an IVF index:
 * <ul>
 *   <li>Neighbor partitions</li>
 *   <li>Samples per partition</li>
 *   <li>Minimum number of vectors per partition</li>
 * </ul>
 *
 */
public class IVFIndexBuilder extends VectorIndexBuilder<IVFIndexBuilder> {

  private int neighborPartitions = -1;

  private int samplePerPartition = -1;

  private int minVectorsPerPartition = -1;

  public IVFIndexBuilder() {
    this.indexType = IndexType.IVF;
  }

  /**
   * Configures the number of neighbor partitions.
   * <p>
   * This is a IVF Specific Parameters. It  determines the number of centroid partitions that are
   * created by the index.
   * </p>
   *
   * @param neighborPartitions The number of neighbor partitions.
   * @return This builder.
   * @throws IllegalArgumentException If the number of neighbor partitions is not between 1 and
   *                                  10000000, or if the vector type is not IVF.
   */
  public IVFIndexBuilder neighborPartitions(int neighborPartitions) throws IllegalArgumentException {
    if (neighborPartitions < 1 || neighborPartitions > 10000000) {
      throw new IllegalArgumentException("The maximum number of centroid partitions that are " +
          "created by the IVF index cannot be lower than 1 or higher than 10000000.");
    }
    this.neighborPartitions = neighborPartitions;
    return this;
  }

  /**
   * Configures the total number of vectors that are passed to the clustering algorithm.
   * <p>
   * This is a IVF Specific Parameters. It  decides the total number of vectors that are passed to
   * the clustering algorithm (number of samples per partition times the number of neighbor
   * partitions).
   * </p>
   * <p>
   * <em>Note,</em> that passing all the vectors would significantly increase the total time to
   * create the index. Instead, aim to pass a subset of vectors that can capture the data
   * distribution.
   * </p>
   *
   * @param samplePerPartition The total number of vectors that are passed to the clustering algorithm.
   * @return This builder.
   * @throws IllegalArgumentException If the number of samples per partition is lower than 1, or if the
   *                                  vector type is not IVF.
   */
  public IVFIndexBuilder samplePerPartition(int samplePerPartition) throws IllegalArgumentException {
    if (samplePerPartition < 1) {
      throw new IllegalArgumentException("The maximum number of samples per partition must be 1 or " +
          "higher.");
    }
    this.samplePerPartition = samplePerPartition;
    return this;
  }

  /**
   * Configures the target minimum number of vectors per partition.
   * <p>
   * This is a IVF Specific Parameters. It represents the target minimum number of vectors per
   * partition. Aim to trim out any partition that can end up with fewer than 100 vectors. This
   * may result in lesser number of centroids. Its values can range from 0 (no trimming of
   * centroids) to num_vectors (would result in 1 neighbor partition).
   * </p>
   *
   * @param minVectorsPerPartition The target minimum number of vectors per partition.
   * @return This builder.
   * @throws IllegalArgumentException If the target minimum number of vectors per partition is lower
   *                                  than 0, or if the vector type is not IVF.
   */
  public IVFIndexBuilder minVectorsPerPartition(int minVectorsPerPartition) throws IllegalArgumentException {
    if (minVectorsPerPartition < 0) {
      throw new IllegalArgumentException("The minimum number of vectors per partition must be positive.");
    }
    this.minVectorsPerPartition = minVectorsPerPartition;
    return this;
  }

  /**
   * Generates the PARAMETERS clause for a IVF index.
   *
   * @return A string containing the PARAMETERS clause of the CREATE VECTOR INDEX statement.
   */
  @Override
  String getIndexParameters() {
    if (neighborPartitions == -1 && samplePerPartition == -1 && minVectorsPerPartition == -1) {
      return " ";
    }
    return "PARAMETERS ( TYPE IVF" +
        (neighborPartitions != -1 ? ", NEIGHBOR PARTITIONS " + neighborPartitions + " " : "") +
        (samplePerPartition != -1 ? ", SAMPLES_PER_PARTITION " + samplePerPartition + " " : "") +
        (minVectorsPerPartition != -1 ? ", MIN_VECTORS_PER_PARTITION " + minVectorsPerPartition + " " : "") + ")";
  }
}
