package com.fuba.automation_engine.service.workflow.spi;

import java.util.List;
import java.util.Map;

/**
 * Host-pluggable, workflow-scoped validation of a trigger config at save time
 * (distinct from {@link GraphValidationRule}, which is per-node). The engine
 * ships a no-op default that accepts any trigger; a host plugs in its own
 * trigger rules. Returns errors (empty = valid); never null. Only invoked with
 * a non-null trigger — trigger-presence policy is the caller's.
 */
public interface TriggerValidator {

    List<String> validate(Map<String, Object> trigger);
}
