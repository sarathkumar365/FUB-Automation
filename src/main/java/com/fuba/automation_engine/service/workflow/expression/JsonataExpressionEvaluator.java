package com.fuba.automation_engine.service.workflow.expression;

import com.dashjoin.jsonata.Jsonata;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JsonataExpressionEvaluator implements ExpressionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(JsonataExpressionEvaluator.class);
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*(.+?)\\s*}}");
    private static final Pattern FULL_TEMPLATE_PATTERN = Pattern.compile("^\\{\\{\\s*(.+?)\\s*}}$");

    @Override
    public Object resolveTemplate(String template, ExpressionScope scope) {
        if (template == null) {
            return null;
        }

        Matcher fullMatch = FULL_TEMPLATE_PATTERN.matcher(template);
        if (fullMatch.matches()) {
            String expression = fullMatch.group(1);
            return evaluateExpression(expression, scope);
        }

        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        if (!matcher.find()) {
            return template;
        }

        matcher.reset();
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String expression = matcher.group(1);
            Object value = evaluateExpression(expression, scope);
            matcher.appendReplacement(result, Matcher.quoteReplacement(stringifyValue(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    @Override
    public Object evaluatePredicate(String expression, ExpressionScope scope) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        return evaluateExpression(expression, scope);
    }

    private Object evaluateExpression(String expression, ExpressionScope scope) {
        try {
            Jsonata jsonata = Jsonata.jsonata(expression);
            return jsonata.evaluate(scope.data());
        } catch (Exception ex) {
            // Known issue #10 (Docs/known-issues.md):
            // invalid JSONata currently degrades to null instead of failing fast.
            log.warn("JSONata expression evaluation failed expression='{}' error='{}'", expression, ex.getMessage());
            return null;
        }
    }

    private String stringifyValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }
}
