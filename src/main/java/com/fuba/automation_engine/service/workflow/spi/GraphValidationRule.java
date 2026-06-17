package com.fuba.automation_engine.service.workflow.spi;

import java.util.List;
import java.util.Map;

/**
 * Host-pluggable per-node validation, run at workflow-save time after the
 * engine's own structural checks (schema, transitions, reachability, cycles).
 * The engine ships none; a host registers rules for its domain-specific
 * constraints. Append human-readable errors to the supplied list.
 */
public interface GraphValidationRule {

    void validateNode(Map<String, Object> node, String nodeId, List<String> errors);
}
