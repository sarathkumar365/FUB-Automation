-- Phase 0 of agent-followup-enforcement: rename the misleading
-- NormalizedDomain.ASSIGNMENT to LEAD (CRM-agnostic; "person" / "contact" /
-- "lead" are the same concept across FUB, HubSpot, Salesforce, Pipedrive,
-- GoHighLevel, etc., and "assignment" was never the right word — assignment
-- is just one possible reason a lead gets updated).
--
-- Storage: webhook_events.normalized_domain is a varchar persisting the Java
-- enum's name. Schema unchanged; only the enum string is renamed.
--
-- The legacy policy engine (automation_policies table) also used 'ASSIGNMENT'
-- but is fully retired — V12 dropped those tables. Nothing else in the system
-- stores 'ASSIGNMENT' as a domain value.

UPDATE webhook_events
SET normalized_domain = 'LEAD'
WHERE normalized_domain = 'ASSIGNMENT';

