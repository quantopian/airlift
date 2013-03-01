package com.facebook.presto.sql;

import com.facebook.presto.sql.tree.AliasedExpression;
import com.facebook.presto.sql.tree.AllColumns;
import com.facebook.presto.sql.tree.ArithmeticExpression;
import com.facebook.presto.sql.tree.AstVisitor;
import com.facebook.presto.sql.tree.BetweenPredicate;
import com.facebook.presto.sql.tree.BooleanLiteral;
import com.facebook.presto.sql.tree.Cast;
import com.facebook.presto.sql.tree.CoalesceExpression;
import com.facebook.presto.sql.tree.ComparisonExpression;
import com.facebook.presto.sql.tree.CurrentTime;
import com.facebook.presto.sql.tree.DoubleLiteral;
import com.facebook.presto.sql.tree.Expression;
import com.facebook.presto.sql.tree.Extract;
import com.facebook.presto.sql.tree.FrameBound;
import com.facebook.presto.sql.tree.FunctionCall;
import com.facebook.presto.sql.tree.InListExpression;
import com.facebook.presto.sql.tree.InPredicate;
import com.facebook.presto.sql.tree.IsNotNullPredicate;
import com.facebook.presto.sql.tree.IsNullPredicate;
import com.facebook.presto.sql.tree.LikePredicate;
import com.facebook.presto.sql.tree.LogicalBinaryExpression;
import com.facebook.presto.sql.tree.LongLiteral;
import com.facebook.presto.sql.tree.NegativeExpression;
import com.facebook.presto.sql.tree.Node;
import com.facebook.presto.sql.tree.NotExpression;
import com.facebook.presto.sql.tree.NullIfExpression;
import com.facebook.presto.sql.tree.NullLiteral;
import com.facebook.presto.sql.tree.QualifiedNameReference;
import com.facebook.presto.sql.tree.SearchedCaseExpression;
import com.facebook.presto.sql.tree.SimpleCaseExpression;
import com.facebook.presto.sql.tree.StringLiteral;
import com.facebook.presto.sql.tree.TimestampLiteral;
import com.facebook.presto.sql.tree.WhenClause;
import com.facebook.presto.sql.tree.Window;
import com.facebook.presto.sql.tree.WindowFrame;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

import static com.facebook.presto.sql.SqlFormatter.orderByFormatterFunction;
import static com.google.common.collect.Iterables.transform;

public class ExpressionFormatter
{
    public static String toString(Expression expression)
    {
        return new Formatter().process(expression, null);
    }

    public static Function<Expression, String> expressionFormatterFunction()
    {
        return new Function<Expression, String>()
        {
            @Override
            public String apply(Expression input)
            {
                return ExpressionFormatter.toString(input);
            }
        };
    }

    public static class Formatter
            extends AstVisitor<String, Void>
    {
        @Override
        protected String visitNode(Node node, Void context)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected String visitExpression(Expression node, Void context)
        {
            throw new UnsupportedOperationException(String.format("not yet implemented: %s.visit%s", getClass().getName(), node.getClass().getSimpleName()));
        }

        @Override
        protected String visitCurrentTime(CurrentTime node, Void context)
        {
            StringBuilder builder = new StringBuilder();
            switch (node.getType()) {
                case TIME:
                    builder.append("current_time");
                    break;
                case DATE:
                    builder.append("current_date");
                    break;
                case TIMESTAMP:
                    builder.append("current_timestamp");
                    break;
                default:
                    throw new UnsupportedOperationException("not yet implemented: " + node.getType());
            }

            if (node.getPrecision() != null) {
                builder.append('(')
                        .append(node.getPrecision())
                        .append(')');
            }

            return builder.toString();
        }

        @Override
        protected String visitExtract(Extract node, Void context)
        {
            return "EXTRACT(" + node.getField() + " FROM " + process(node.getExpression(), context) + ")";
        }

        @Override
        protected String visitBooleanLiteral(BooleanLiteral node, Void context)
        {
            return node.getValue();
        }

        @Override
        protected String visitStringLiteral(StringLiteral node, Void context)
        {
            return "'" + node.getValue() + "'";
        }

        @Override
        protected String visitLongLiteral(LongLiteral node, Void context)
        {
            return Long.toString(node.getValue());
        }

        @Override
        protected String visitDoubleLiteral(DoubleLiteral node, Void context)
        {
            return Double.toString(node.getValue());
        }

        @Override
        protected String visitTimestampLiteral(TimestampLiteral node, Void context)
        {
            return "'" + node.getValue() + "'";
        }

        @Override
        protected String visitNullLiteral(NullLiteral node, Void context)
        {
            return "null";
        }

        @Override
        protected String visitQualifiedNameReference(QualifiedNameReference node, Void context)
        {
            return node.getName().toString();
        }

        @Override
        protected String visitAliasedExpression(AliasedExpression node, Void context)
        {
            return process(node.getExpression(), null) + ' ' + node.getAlias();
        }

        @Override
        protected String visitFunctionCall(FunctionCall node, Void context)
        {
            StringBuilder builder = new StringBuilder();

            builder.append(node.getName())
                    .append('(').append(joinExpressions(node.getArguments())).append(')');

            if (node.getWindow().isPresent()) {
                builder.append(" OVER ").append(visitWindow(node.getWindow().get(), null));
            }

            return builder.toString();
        }

        @Override
        protected String visitLogicalBinaryExpression(LogicalBinaryExpression node, Void context)
        {
            return formatBinaryExpression(node.getType().toString(), node.getLeft(), node.getRight());
        }

        @Override
        protected String visitNotExpression(NotExpression node, Void context)
        {
            return "(NOT " + process(node.getValue(), null) + ")";
        }

        @Override
        protected String visitComparisonExpression(ComparisonExpression node, Void context)
        {
            return formatBinaryExpression(node.getType().getValue(), node.getLeft(), node.getRight());
        }

        @Override
        protected String visitIsNullPredicate(IsNullPredicate node, Void context)
        {
            return "(" + process(node.getValue(), null) + " IS NULL)";
        }

        @Override
        protected String visitIsNotNullPredicate(IsNotNullPredicate node, Void context)
        {
            return "(" + process(node.getValue(), null) + " IS NOT NULL)";
        }

        @Override
        protected String visitNullIfExpression(NullIfExpression node, Void context)
        {
            return "NULLIF(" + process(node.getFirst(), null) + ", " + process(node.getSecond(), null) + ')';
        }

        @Override
        protected String visitCoalesceExpression(CoalesceExpression node, Void context)
        {
            return "COALESCE(" + joinExpressions(node.getOperands()) + ")";
        }

        @Override
        protected String visitNegativeExpression(NegativeExpression node, Void context)
        {
            return "-" + process(node.getValue(), null);
        }

        @Override
        protected String visitArithmeticExpression(ArithmeticExpression node, Void context)
        {
            return formatBinaryExpression(node.getType().getValue(), node.getLeft(), node.getRight());
        }

        @Override
        protected String visitLikePredicate(LikePredicate node, Void context)
        {
            StringBuilder builder = new StringBuilder();

            builder.append('(')
                    .append(process(node.getValue(), null))
                    .append(" LIKE ")
                    .append(process(node.getPattern(), null));

            if (node.getEscape() != null) {
                builder.append(" ESCAPE ")
                        .append(process(node.getEscape(), null));
            }

            builder.append(')');

            return builder.toString();
        }

        @Override
        protected String visitAllColumns(AllColumns node, Void context)
        {
            if (node.getPrefix().isPresent()) {
                return node.getPrefix().get() + ".*";
            }

            return "*";
        }

        @Override
        public String visitCast(Cast node, Void context)
        {
            return "CAST(" + process(node.getExpression(), context) + " AS " + node.getType() + ")";
        }

        @Override
        protected String visitSearchedCaseExpression(SearchedCaseExpression node, Void context)
        {
            ImmutableList.Builder<String> parts = ImmutableList.builder();
            parts.add("CASE");
            for (WhenClause whenClause : node.getWhenClauses()) {
                parts.add(process(whenClause, context));
            }
            if (node.getDefaultValue() != null) {
                parts.add("ELSE")
                        .add(process(node.getDefaultValue(), context));
            }
            parts.add("END");

            return "(" + Joiner.on(' ').join(parts.build()) + ")";
        }

        @Override
        protected String visitSimpleCaseExpression(SimpleCaseExpression node, Void context)
        {
            ImmutableList.Builder<String> parts = ImmutableList.builder();

            parts.add("CASE")
                    .add(process(node.getOperand(), context));

            for (WhenClause whenClause : node.getWhenClauses()) {
                parts.add(process(whenClause, context));
            }
            if (node.getDefaultValue() != null) {
                parts.add("ELSE")
                        .add(process(node.getDefaultValue(), context));
            }
            parts.add("END");

            return "(" + Joiner.on(' ').join(parts.build()) + ")";
        }

        @Override
        protected String visitWhenClause(WhenClause node, Void context)
        {
            return "WHEN " + process(node.getOperand(), context) + " THEN " + process(node.getResult(), context);
        }

        @Override
        protected String visitBetweenPredicate(BetweenPredicate node, Void context)
        {
            return "(" + process(node.getValue(), context) + " BETWEEN " +
                    process(node.getMin(), context) + " AND " + process(node.getMax(), context) + ")";
        }

        @Override
        protected String visitInPredicate(InPredicate node, Void context)
        {
            return "(" + process(node.getValue(), context) + " IN " + process(node.getValueList(), context) + ")";
        }

        @Override
        protected String visitInListExpression(InListExpression node, Void context)
        {
            return "(" + joinExpressions(node.getValues()) + ")";
        }

        // TODO: add tests for window clause formatting, as these are not really expressions
        @Override
        public String visitWindow(Window node, Void context)
        {
            List<String> parts = new ArrayList<>();

            if (!node.getPartitionBy().isEmpty()) {
                parts.add("PARTITION BY " + joinExpressions(node.getPartitionBy()));
            }
            if (!node.getOrderBy().isEmpty()) {
                parts.add("ORDER BY " + Joiner.on(", ").join(transform(node.getOrderBy(), orderByFormatterFunction())));
            }
            if (node.getFrame().isPresent()) {
                parts.add(process(node.getFrame().get(), null));
            }

            return '(' + Joiner.on(' ').join(parts) + ')';
        }

        @Override
        public String visitWindowFrame(WindowFrame node, Void context)
        {
            StringBuilder builder = new StringBuilder();

            builder.append(node.getType().toString()).append(' ');

            if (node.getEnd().isPresent()) {
                builder.append("BETWEEN ")
                        .append(process(node.getStart(), null))
                        .append(" AND ")
                        .append(process(node.getEnd().get(), null));
            }
            else {
                builder.append(process(node.getStart(), null));
            }

            return builder.toString();
        }

        @Override
        public String visitFrameBound(FrameBound node, Void context)
        {
            switch (node.getType()) {
                case UNBOUNDED_PRECEDING:
                    return "UNBOUNDED PRECEDING";
                case PRECEDING:
                    return process(node.getValue().get(), null) + " PRECEDING";
                case CURRENT_ROW:
                    return "CURRENT ROW";
                case FOLLOWING:
                    return process(node.getValue().get(), null) + " FOLLOWING";
                case UNBOUNDED_FOLLOWING:
                    return "UNBOUNDED FOLLOWING";
            }
            throw new IllegalArgumentException("unhandled type: " + node.getType());
        }

        private String formatBinaryExpression(String operator, Expression left, Expression right)
        {
            return '(' + process(left, null) + ' ' + operator + ' ' + process(right, null) + ')';
        }

        private String joinExpressions(List<Expression> expressions)
        {
            return Joiner.on(", ").join(transform(expressions, new Function<Expression, Object>()
            {
                @Override
                public Object apply(Expression input)
                {
                    return process(input, null);
                }
            }));
        }
    }
}