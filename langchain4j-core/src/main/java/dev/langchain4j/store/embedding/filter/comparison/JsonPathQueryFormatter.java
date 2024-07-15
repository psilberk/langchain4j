package dev.langchain4j.store.embedding.filter.comparison;

import java.util.Collection;


public interface JsonPathQueryFormatter {

  String formatKey(String key, Class<?> valueType);
  String formatKeyAsString(String key);
  String formatValue(Object value);
  String formatValuesAsString(Collection<?> values);

  String isEqualToExpression();
  String isGreatherThanExpression();
  String isGreatherThanOrEqualToExpression();
  String isInExpression();
  String isLessThanExpression();
  String isLessThanOrEqualToExpression();
  String isNotEqualToExpression();
  String isNotInExpression();

  String orOperator();
  String andOperator();
  String notOperator();

}
