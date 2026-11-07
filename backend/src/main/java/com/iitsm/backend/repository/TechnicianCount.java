package com.iitsm.backend.repository;

/** Query result: a number of incidents per technician. Filled directly by a JPQL constructor expression. */
public record TechnicianCount(Long technicianId, Long count) {
}
