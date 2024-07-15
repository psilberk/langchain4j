package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.AbstractMap.SimpleEntry;

abstract class PgVectorFilterMapper implements JsonPathQueryFormatter {

    static final Map<Class<?>, String> SQL_TYPE_MAP = Stream.of(
            new SimpleEntry<>(Integer.class, "int"),
            new SimpleEntry<>(Long.class, "bigint"),
            new SimpleEntry<>(Float.class, "float"),
            new SimpleEntry<>(Double.class, "float8"),
            new SimpleEntry<>(String.class, "text"),
            new SimpleEntry<>(UUID.class, "uuid"),
            new SimpleEntry<>(Boolean.class, "boolean"),
            // Default
            new SimpleEntry<>(Object.class, "text"))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    public String map(Filter filter) {
        try {
            return filter.toJSONPath(this);
        } catch (Exception ex) {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName(), ex);
        }
    }

    @Override
    public abstract String formatKey(String key, Class<?> valueType);

    @Override
    public abstract String formatKeyAsString(String key);

    @Override
    public String formatValue(Object value) {
        if (value instanceof String || value instanceof UUID) {
            return "'" + value + "'";
        } else {
            return value.toString();
        }
    }

    @Override
    public String formatValuesAsString(Collection<?> values) {
        return "(" + values.stream().map(v -> String.format("'%s'", v))
            .collect(Collectors.joining(",")) + ")";
    }

    @Override
    public String isEqualToExpression() {
        return "%1$s is not null and %1$s = %2$s";
    }

    @Override
    public String isGreatherThanExpression() {
        return "%s > %s";
    }

    @Override
    public String isGreatherThanOrEqualToExpression() {
        return "%s >= %s";
    }

    @Override
    public String isInExpression() {
        return "%s in %s";
    }

    @Override
    public String isLessThanExpression() {
        return "%s < %s";
    }

    @Override
    public String isLessThanOrEqualToExpression() {
        return "%s <= %s";
    }

    @Override
    public String isNotEqualToExpression() {
        return "%1$s is null or %1$s != %2$s";
    }

    @Override
    public String isNotInExpression() {
        return "%1$s is null or %1$s not in %2$s";
    }

    @Override
    public String orOperator() {
        return "(%s or %s)";
    }

    @Override
    public String andOperator() {
        return "%s and %s";
    }

    @Override
    public String notOperator() {
        return "not(%s)";
    }

}
