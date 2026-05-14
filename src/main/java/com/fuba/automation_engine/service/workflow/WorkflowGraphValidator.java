package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.service.lead.LeadUpsertService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WorkflowGraphValidator {

    /**
     * Captures references like {@code lead.assignedUserId} inside any JSONata
     * expression string. The {@code \b} ensures we don't match {@code mislead.}
     * substrings. Only the first segment after {@code lead.} is captured because
     * {@code SNAPSHOT_FIELDS} is top-level today; nested references such as
     * {@code lead.customFields.foo} validate against the top-level head.
     */
    private static final Pattern LEAD_EXPRESSION_PATTERN =
            Pattern.compile("\\blead\\.([a-zA-Z][a-zA-Z0-9_]*)");

    /**
     * Captures references inside templates like {@code {{ lead.firstName }}}.
     */
    private static final Pattern LEAD_TEMPLATE_PATTERN =
            Pattern.compile("\\{\\{\\s*lead\\.([a-zA-Z][a-zA-Z0-9_]*)");

    private final WorkflowStepRegistry stepRegistry;

    public WorkflowGraphValidator(WorkflowStepRegistry stepRegistry) {
        this.stepRegistry = stepRegistry;
    }

    public GraphValidationResult validate(Map<String, Object> graph) {
        if (graph == null || graph.isEmpty()) {
            return GraphValidationResult.failure("Graph must not be null or empty");
        }

        List<String> errors = new ArrayList<>();

        Object schemaVersionObj = graph.get("schemaVersion");
        if (!(schemaVersionObj instanceof Number num) || num.intValue() != 1) {
            errors.add("schemaVersion must be 1");
        }

        Object nodesObj = graph.get("nodes");
        if (!(nodesObj instanceof List<?> nodesList) || nodesList.isEmpty()) {
            errors.add("nodes must be a non-empty list");
            return GraphValidationResult.failure(errors);
        }

        String entryNode = graph.get("entryNode") instanceof String s ? s : null;

        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Map<String, Object>> nodesById = new HashMap<>();
        Set<String> nodeIds = new HashSet<>();

        for (Object item : nodesList) {
            if (!(item instanceof Map<?, ?> rawNode)) {
                errors.add("Each node must be a JSON object");
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> node = (Map<String, Object>) rawNode;
            nodes.add(node);

            String id = node.get("id") instanceof String s ? s : null;
            if (id == null || id.isBlank()) {
                errors.add("Each node must have a non-empty 'id'");
                continue;
            }
            if (!nodeIds.add(id)) {
                errors.add("Duplicate node id: " + id);
                continue;
            }
            nodesById.put(id, node);
        }

        if (!errors.isEmpty()) {
            return GraphValidationResult.failure(errors);
        }

        if (entryNode == null || entryNode.isBlank()) {
            errors.add("entryNode must be a non-empty string");
        } else if (!nodeIds.contains(entryNode)) {
            errors.add("entryNode '" + entryNode + "' does not reference an existing node");
        }

        for (Map<String, Object> node : nodes) {
            String id = (String) node.get("id");
            String type = node.get("type") instanceof String s ? s : null;

            if (type == null || type.isBlank()) {
                errors.add("Node '" + id + "' must have a non-empty 'type'");
                continue;
            }

            Optional<WorkflowStepType> stepTypeOpt = stepRegistry.get(type);
            if (stepTypeOpt.isEmpty()) {
                errors.add("Node '" + id + "' references unknown step type: " + type);
                continue;
            }

            WorkflowStepType stepType = stepTypeOpt.get();
            validateTransitions(node, id, stepType, nodeIds, errors);
            validateConfig(node, id, stepType, errors);
            validateLeadFieldReferences(node, id, errors);
        }

        if (!errors.isEmpty()) {
            return GraphValidationResult.failure(errors);
        }

        if (entryNode != null) {
            checkReachability(entryNode, nodesById, nodeIds, errors);
            checkCycles(entryNode, nodesById, errors);
        }

        return errors.isEmpty() ? GraphValidationResult.success() : GraphValidationResult.failure(errors);
    }

    private void validateTransitions(
            Map<String, Object> node,
            String nodeId,
            WorkflowStepType stepType,
            Set<String> allNodeIds,
            List<String> errors) {
        Object transitionsObj = node.get("transitions");
        if (!(transitionsObj instanceof Map<?, ?> transitions)) {
            errors.add("Node '" + nodeId + "' must have a 'transitions' map");
            return;
        }

        Set<String> declaredCodes = stepType.declaredResultCodes();
        boolean dynamicResultCodes = declaredCodes.isEmpty();

        for (Map.Entry<?, ?> entry : transitions.entrySet()) {
            String resultCode = String.valueOf(entry.getKey());
            if (!dynamicResultCodes && !declaredCodes.contains(resultCode)) {
                errors.add("Node '" + nodeId + "' transition uses undeclared result code: " + resultCode
                        + ". Declared: " + declaredCodes);
            }

            Object value = entry.getValue();
            if (value instanceof Map<?, ?> terminalMap) {
                if (!terminalMap.containsKey("terminal")) {
                    errors.add("Node '" + nodeId + "' transition '" + resultCode + "' map must contain 'terminal' key");
                }
            } else if (value instanceof List<?> nextNodes) {
                for (Object target : nextNodes) {
                    String targetId = String.valueOf(target);
                    if (!allNodeIds.contains(targetId)) {
                        errors.add("Node '" + nodeId + "' transition '" + resultCode
                                + "' references unknown target node: " + targetId);
                    }
                }
            } else {
                errors.add("Node '" + nodeId + "' transition '" + resultCode
                        + "' must be either a terminal object or a list of node IDs");
            }
        }
    }

    private void validateConfig(
            Map<String, Object> node,
            String nodeId,
            WorkflowStepType stepType,
            List<String> errors) {
        Map<String, Object> schema = stepType.configSchema();
        if (schema == null || schema.isEmpty()) {
            return;
        }

        Object configObj = node.get("config");
        Map<String, Object> config = Map.of();
        if (configObj instanceof Map<?, ?> rawConfig) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) rawConfig;
            config = cast;
        }

        Object requiredObj = schema.get("required");
        if (requiredObj instanceof List<?> requiredKeys) {
            // configSchema currently enforces only presence of required keys here.
            // Step execute() methods remain responsible for strict runtime checks.
            for (Object key : requiredKeys) {
                String keyStr = String.valueOf(key);
                if (!config.containsKey(keyStr)) {
                    errors.add("Node '" + nodeId + "' config is missing required key: " + keyStr);
                }
            }
        }
    }

    private void checkReachability(
            String entryNode,
            Map<String, Map<String, Object>> nodesById,
            Set<String> allNodeIds,
            List<String> errors) {
        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(entryNode);
        reachable.add(entryNode);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            Map<String, Object> node = nodesById.get(current);
            if (node == null) {
                continue;
            }
            for (String target : collectTransitionTargets(node)) {
                if (reachable.add(target)) {
                    queue.add(target);
                }
            }
        }

        for (String nodeId : allNodeIds) {
            if (!reachable.contains(nodeId)) {
                errors.add("Node '" + nodeId + "' is unreachable from entry node '" + entryNode + "'");
            }
        }
    }

    private void checkCycles(
            String entryNode,
            Map<String, Map<String, Object>> nodesById,
            List<String> errors) {
        // DFS with coloring: WHITE (unvisited), GRAY (in-stack), BLACK (done)
        Map<String, Integer> color = new HashMap<>();
        for (String id : nodesById.keySet()) {
            color.put(id, 0); // WHITE
        }
        if (dfsHasCycle(entryNode, nodesById, color)) {
            errors.add("Graph contains a cycle");
        }
    }

    private boolean dfsHasCycle(
            String nodeId,
            Map<String, Map<String, Object>> nodesById,
            Map<String, Integer> color) {
        color.put(nodeId, 1); // GRAY
        Map<String, Object> node = nodesById.get(nodeId);
        if (node != null) {
            for (String target : collectTransitionTargets(node)) {
                Integer targetColor = color.get(target);
                if (targetColor == null) {
                    continue;
                }
                if (targetColor == 1) {
                    return true; // back edge → cycle
                }
                if (targetColor == 0 && dfsHasCycle(target, nodesById, color)) {
                    return true;
                }
            }
        }
        color.put(nodeId, 2); // BLACK
        return false;
    }

    private List<String> collectTransitionTargets(Map<String, Object> node) {
        List<String> targets = new ArrayList<>();
        Object transitionsObj = node.get("transitions");
        if (!(transitionsObj instanceof Map<?, ?> transitions)) {
            return targets;
        }
        for (Object value : transitions.values()) {
            if (value instanceof List<?> nextNodes) {
                for (Object target : nextNodes) {
                    targets.add(String.valueOf(target));
                }
            }
            // terminal transitions have no targets
        }
        return targets;
    }

    /**
     * Walks every string value reachable from this node's {@code config} map and
     * flags references to {@code lead.<field>} where {@code <field>} is not
     * captured by {@link LeadUpsertService#capturedFieldNames()}.
     *
     * <p>Two patterns are matched: bare JSONata expressions
     * ({@code $boolean(lead.assignedUserId)}) and template strings
     * ({@code "Hi, {{ lead.firstName }}"}). Both forms appear in real
     * workflow JSON — see {@code Docs/features/agent-followup-enforcement/workflow.json}.
     *
     * <p>Rationale: without this check, a workflow author can ship a workflow
     * that silently never fires because its trigger filter or expression
     * references a field we don't snapshot — the diff and the runtime resolution
     * both see null, and the failure is invisible until production. Phase 1 of
     * the domain-events feature catches the mistake at save time.
     */
    private void validateLeadFieldReferences(
            Map<String, Object> node, String nodeId, List<String> errors) {
        Object configObj = node.get("config");
        if (!(configObj instanceof Map<?, ?> config)) {
            return;
        }
        Set<String> referenced = new LinkedHashSet<>();
        collectLeadReferences(config, referenced);
        if (referenced.isEmpty()) {
            return;
        }
        Set<String> captured = LeadUpsertService.capturedFieldNames();
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
                            + "' references unknown lead field 'lead."
                            + field
                            + "' — not in LeadUpsertService.SNAPSHOT_FIELDS. "
                            + "If this is a new field, add it to SNAPSHOT_FIELDS and update the validator tests.");
        }
    }

    /**
     * Recursively descends into the supplied value, scanning every string for
     * {@code lead.<field>} references in both JSONata and template form and
     * accumulating the captured field names into {@code referenced}.
     */
    private void collectLeadReferences(Object value, Set<String> referenced) {
        if (value instanceof String s) {
            scanString(s, referenced);
        } else if (value instanceof Map<?, ?> map) {
            for (Object child : map.values()) {
                collectLeadReferences(child, referenced);
            }
        } else if (value instanceof List<?> list) {
            for (Object child : list) {
                collectLeadReferences(child, referenced);
            }
        }
        // Numbers, booleans, null: no references possible.
    }

    private void scanString(String s, Set<String> referenced) {
        Matcher exprMatcher = LEAD_EXPRESSION_PATTERN.matcher(s);
        while (exprMatcher.find()) {
            referenced.add(exprMatcher.group(1));
        }
        Matcher templateMatcher = LEAD_TEMPLATE_PATTERN.matcher(s);
        while (templateMatcher.find()) {
            referenced.add(templateMatcher.group(1));
        }
    }
}
