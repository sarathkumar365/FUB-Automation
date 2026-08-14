package com.flux.service.workflow.rule;

import com.flux.service.person.PersonUpsertService;
import com.flux.service.workflow.spi.GraphValidationRule;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Host rule: flags {@code person.<field>} references in node config that aren't
 * captured by {@link PersonUpsertService#capturedFieldNames()}, so a workflow
 * can't silently never fire on a field we don't snapshot. Extracted from
 * WorkflowGraphValidator behind the {@link GraphValidationRule} SPI.
 */
@Component
public class PersonFieldGraphValidationRule implements GraphValidationRule {

    /**
     * Captures references like {@code person.assignedUserId} inside any JSONata
     * expression string. The {@code \b} ensures we don't match {@code aperson.}
     * substrings. Only the first segment after {@code person.} is captured because
     * {@code SNAPSHOT_FIELDS} is top-level today; nested references such as
     * {@code person.customFields.foo} validate against the top-level head.
     */
    private static final Pattern PERSON_EXPRESSION_PATTERN =
            Pattern.compile("\\bperson\\.([a-zA-Z][a-zA-Z0-9_]*)");

    /**
     * Captures references inside templates like {@code {{ person.firstName }}}.
     */
    private static final Pattern PERSON_TEMPLATE_PATTERN =
            Pattern.compile("\\{\\{\\s*person\\.([a-zA-Z][a-zA-Z0-9_]*)");

    @Override
    public void validateNode(Map<String, Object> node, String nodeId, List<String> errors) {
        Object configObj = node.get("config");
        if (!(configObj instanceof Map<?, ?> config)) {
            return;
        }
        Set<String> referenced = new LinkedHashSet<>();
        collectPersonReferences(config, referenced);
        if (referenced.isEmpty()) {
            return;
        }
        Set<String> captured = PersonUpsertService.capturedFieldNames();
        Set<String> unknown = new TreeSet<>();
        for (String field : referenced) {
            if (!captured.contains(field)) {
                unknown.add(field);
            }
        }
        for (String field : unknown) {
            errors.add(
                    "Node '"
                            + nodeId
                            + "' references unknown person field 'person."
                            + field
                            + "' — not in PersonUpsertService.capturedFieldNames(). "
                            + "If this is a new field, add it to SNAPSHOT_FIELDS and update the validator tests.");
        }
    }

    private void collectPersonReferences(Object value, Set<String> referenced) {
        if (value instanceof String s) {
            scanString(s, referenced);
        } else if (value instanceof Map<?, ?> map) {
            for (Object child : map.values()) {
                collectPersonReferences(child, referenced);
            }
        } else if (value instanceof List<?> list) {
            for (Object child : list) {
                collectPersonReferences(child, referenced);
            }
        }
    }

    private void scanString(String s, Set<String> referenced) {
        Matcher exprMatcher = PERSON_EXPRESSION_PATTERN.matcher(s);
        while (exprMatcher.find()) {
            referenced.add(exprMatcher.group(1));
        }
        Matcher templateMatcher = PERSON_TEMPLATE_PATTERN.matcher(s);
        while (templateMatcher.find()) {
            referenced.add(templateMatcher.group(1));
        }
    }
}
