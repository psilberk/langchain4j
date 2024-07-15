package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;

import java.util.Collection;
import java.util.stream.Collectors;

public class OracleJSONPathFilterMapper implements JsonPathQueryFormatter {

    /**
     * Generates a where clause using a JSON path expression generated from a Filter.
     * @param filter Filter to map to JSON path expression.
     * @return Where clause with appended JSON path expression.
     */
    public String whereClause(Filter filter) {
        final String jsonExistsClause = "where json_exists(metadata, '$?(%s)')";
        return String.format(jsonExistsClause, map(filter));
    }

    /**
     * Maps a Filter to a JSON path expression
     * @param filter Filter to map.
     * @return JSON path expression String.
     */
    private String map(Filter filter) {
        try {
            return filter.toJSONPath(this);
        } catch (Exception ex) {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName(), ex);
        }
    }

    @Override
    public String formatKey(String key, Class<?> valueType) {
        return "@." + key;
    }

    @Override
    public String formatKeyAsString(String key) {
        return "@." + key;
    }

    @Override
    public String formatValue(Object v) {
        if (v instanceof String) {
            return String.format("\"%s\"", v);
        } else {
            return v.toString();
        }
    }

    public String formatValuesAsString(Collection<?> values) {
        return "(" + values.stream().map(this::formatValue)
                .collect(Collectors.joining(",")) + ")";
    }

    @Override
    public String isEqualToExpression() {
        return "%s == %s";
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
        return "%s != %s";
    }

    @Override
    public String isNotInExpression() {
        return "!(%s in %s)";
    }

    @Override
    public String orOperator() {
        return "(%s || %s)";
    }

    @Override
    public String andOperator() {
        return "%s && %s";
    }

    @Override
    public String notOperator() {
        return "!(%s)";
    }

}
