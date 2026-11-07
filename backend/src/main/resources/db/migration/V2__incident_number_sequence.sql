-- Source of the running number in human-readable incident references (INC-2026-000042).
-- A database sequence is safe when many incidents are reported at the same moment: no two get the same number.
CREATE SEQUENCE incident_number_seq START WITH 1 INCREMENT BY 1;
