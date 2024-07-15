package dev.langchain4j.store.embedding.filter.logical;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.JsonPathQueryFormatter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@ToString
@EqualsAndHashCode
public class Not implements Filter {

    private final Filter expression;

    public Not(Filter expression) {
        this.expression = ensureNotNull(expression, "expression");
    }

    public Filter expression() {
        return expression;
    }

    @Override
    public boolean test(Object object) {
        return !expression.test(object);
    }

    @Override
    public String toJSONPath(JsonPathQueryFormatter formatter) throws Exception {
        return String.format(formatter.notOperator(), expression().toJSONPath(formatter));
    }
}
