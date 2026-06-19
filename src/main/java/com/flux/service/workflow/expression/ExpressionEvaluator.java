package com.flux.service.workflow.expression;

public interface ExpressionEvaluator {

    /**
     * Resolve template expressions in a string.
     * Expressions are delimited by {{ and }}.
     * If the entire string is a single expression, returns the raw typed value.
     * If mixed with literal text, returns a concatenated String.
     * If no expressions are present, returns the input unchanged.
     */
    Object resolveTemplate(String template, ExpressionScope scope);

    /**
     * Evaluate a raw expression and return the result.
     * Used by branch_on_field and other expression-driven steps.
     */
    Object evaluatePredicate(String expression, ExpressionScope scope);

    /**
     * True if the expression compiles as valid syntax (does not evaluate it).
     * Used by save-time validation to reject malformed trigger filters before
     * they degrade silently to null at runtime (known-issue #10).
     */
    boolean isValidExpression(String expression);
}
