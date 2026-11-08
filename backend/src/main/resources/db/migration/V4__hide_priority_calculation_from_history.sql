-- The priority calculation (score and formula) is internal to support staff. Older notifications copied it into
-- text that reporters read, e.g.
--   "Classified as Network, priority P1 Critical (Impact EXTENSIVE(4) × Urgency CRITICAL(4) × … → P1 Critical)"
-- Strip the bracketed calculation from those notifications. New ones no longer contain it.
--
-- audit_entries is deliberately NOT touched: it is append-only (enforced by a trigger), so history is never
-- rewritten. Old audit descriptions are cleaned when an employee reads them instead (IncidentService.history).
-- The formula never contains a line break, so "[^\n]*" stops at the end of the first line.

UPDATE notifications
SET message = regexp_replace(message, ' \((Impact|Matrix: Impact) [^\n]*\)', '')
WHERE type IN ('CLASSIFIED', 'PRIORITY_CHANGED')
  AND message ~ ' \((Impact|Matrix: Impact) ';
