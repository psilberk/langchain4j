package dev.langchain4j.store.embedding.oracle.index;

public class DatabaseIndexBuilderFactory {
  public static DatabaseIndexBuilder getDatabaseIndexBuilder(IndexType indexType) {
    switch (indexType) {
      case IVF:
        return new IVFIndexBuilder();
      case HNSW:
        return new HNSWIndexBuilder();
    }
    return null;
  }
}
