-- Predictive SLA: remember that an early "breach likely" warning was raised, so it is sent once per incident.
ALTER TABLE incidents
    ADD COLUMN sla_breach_predicted BOOLEAN NOT NULL DEFAULT FALSE;
