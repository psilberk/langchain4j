package dev.langchain4j.store.embedding.pgvector;

class JSONFilterMapper extends PgVectorFilterMapper {
    final String metadataColumn;

    public JSONFilterMapper(String metadataColumn) {
        this.metadataColumn = metadataColumn;
    }

    public String formatKey(String key, Class<?> valueType) {
        return String.format("(%s->>'%s')::%s", metadataColumn, key, SQL_TYPE_MAP.get(valueType));
    }

    public String formatKeyAsString(String key) {
        return metadataColumn + "->>'" + key + "'";
    }

}
