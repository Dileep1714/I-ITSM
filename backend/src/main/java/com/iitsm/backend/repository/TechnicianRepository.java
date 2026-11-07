package com.iitsm.backend.repository;

import com.iitsm.backend.domain.user.SupportLevel;
import com.iitsm.backend.domain.user.Technician;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TechnicianRepository extends JpaRepository<Technician, Long> {

    List<Technician> findByActiveTrueOrderByFullName();

    List<Technician> findBySupportLevelAndActiveTrue(SupportLevel supportLevel);
}
