package com.fuba.automation_engine.service.workflow.trigger;

/** One (event kind, optional filter) subscription within a trigger. */
record TriggerEntry(String on, String filter) {
}
