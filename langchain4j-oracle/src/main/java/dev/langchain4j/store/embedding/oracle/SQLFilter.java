package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.store.embedding.filter.Filter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * <p>
 * A SQL expression which evaluates to a boolean result. This interface is used generate SQL expressions that perform
 * the operation of a {@link Filter} interface. This allows filtering operations to occur in the database, rather than
 * locally via {@link Filter#test(Object)}.
 * </p><p>
 * This interface is designed to interoperate with the JDBC API. The String returned by {@link #asSQLExpression()} can be
 * embedded within SQL that is passed to {@link Connection#prepareStatement(String)}. The String may include "?"
 * parameter markers. Parameter values are set when the <code>PreparedStatement</code> is passed to
 * {@link #setParameters(PreparedStatement, int)}. The use of parameter markers can offer a performance benefit when the
 * same SQL is repeatedly executed, only with different parameter values. A JDBC driver may cache the statement for
 * reuse, and the database may avoid parsing of the same SQL statement
 * </p>
 */
public interface SQLFilter {

    /**
     * <p>
     * Returns this filter as a SQL conditional expression. The expression returned by this method can appear within the
     * WHERE clause of a SELECT query. The expression may contain "?" characters that a {@link PreparedStatement} will
     * recognize as parameter markers. The values of any parameters are set when a <code>PreparedStatement</code> is
     * passed to the {@link #setParameters(PreparedStatement, int)} method of this filter.
     * </p><p>
     * An optional namespace parameter is applied to any identifiers which appear in the expression. This can be used
     * when the identifier must be prefixed with a table name. In the example below, sqlExpression has the value of
     * "example.x=?"
     * <pre>{@code
     * ResultSet example(Connection connection) {
     *   Filter filter = IsEqualTo("x", 9);
     *   SQLFilter sqlFilter = SQLFilter.fromFilter(filter);
     *
     *   // Prepare: "SELECT y FROM example WHERE x = ?"
     *   PreparedStatement preparedStatement = connection.prepareStatement(
     *     "SELECT y FROM example WHERE " + sqlFilter.getSQL();
     *
     *   // Set the parameter at index 1. (1 is the lowest index for JDBC)
     *   sqlFilter.set(preparedStatement, 1);
     *
     *   preparedStatement.closeOnCompletion();
     *   return preparedStatement.executeQuery();
     * }
     * }</pre>
     * </p>
     *
     * @param idMapper Function which maps {@link dev.langchain4j.data.document.Metadata} keys to SQL identifiers. Not
     *                 null.
     *
     * @return SQL expression which evaluates to the result of this filter. Not null.
     */
    String asSQLExpression(UnaryOperator<String> idMapper);

    /**
     * Returns this SQL filter as a WHERE clause, or returns an empty string if this is the {@link #EMPTY} filter.
     *
     * @param idMapper Function which maps {@link dev.langchain4j.data.document.Metadata} keys to SQL identifiers. Not
     *                 null.
     *
     * @return SQL expression that evaluates to the result of this filter. Not null.
     */
    default String asWhereClause(UnaryOperator<String> idMapper) {
        return " WHERE " + asSQLExpression(idMapper);
    }

    /**
     * Sets the value of any parameter markers in the SQL expression returned by {@link #asSQLExpression()}.
     *
     * @param preparedStatement Statement to set with a value. Not null.
     * @param parameterIndex Index to set with a value.
     *
     * @return The number of parameters which were set. May be 0.
     *
     * @throws SQLException If one is thrown from the PreparedStatement.
     */
    int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException;

    /**
     * Returns a SQL filter that evaluates to the same result as a <code>Filter</code>.
     *
     * @param filter Filter to map into a SQLFilter. May be null.
     *
     * @return The equivalent SQLFilter, which may be {@link #EMPTY} if the input <code>Filter</code> is null.
     *
     * @throws IllegalArgumentException If the Filter is not recognized.
     */
    static SQLFilter fromFilter(Filter filter) {

        if (filter == null)
            return EMPTY;

        Function<? super Filter, ? extends SQLFilter> function = SQLFilters.FILTER_MAP.get(filter.getClass());

        if (function == null)
            throw new IllegalArgumentException("Unrecognized filter: " + filter);

        return function.apply(filter);
    }

    SQLFilter EMPTY = new SQLFilter() {

        @Override
        public String asSQLExpression(UnaryOperator<String> idMapper) {
            return "";
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
            return 0;
        }

        @Override
        public String asWhereClause(UnaryOperator<String> idMapper) {
            return "";
        }
    };

}