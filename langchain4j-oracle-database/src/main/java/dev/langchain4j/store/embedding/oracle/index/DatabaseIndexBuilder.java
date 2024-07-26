package dev.langchain4j.store.embedding.oracle.index;

import dev.langchain4j.store.embedding.oracle.DistanceMetric;

public interface DatabaseIndexBuilder {
  String getCreateStatement(String tableName, String embeddingColumn);
  String getDropStatement(String tableName);
}

