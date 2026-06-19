package com.flux.service.workflow.spi;

import java.util.Map;

/**
 * Host-pluggable contributor that fills one named block of the per-step run
 * context (the bag JSONata expressions evaluate against). The engine ships
 * none; a host registers beans for whatever scopes it needs (e.g. {@code person},
 * {@code now}).
 *
 * <p>Invoked once per step build. Implementations MUST NOT cache across calls —
 * the per-step invocation is what lets a snapshot pick up a mid-run change and a
 * time-derived value re-cross a boundary. Never return {@code null}; return
 * {@link Map#of()} for an empty block.
 */
public interface RunContextContributor {

    /** Stable, unique top-level run-context key this contributor fills. */
    String key();

    Map<String, Object> contribute(RunContextRequest request);
}
