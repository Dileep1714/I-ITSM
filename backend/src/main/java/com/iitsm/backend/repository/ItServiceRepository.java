package com.iitsm.backend.repository;

import com.iitsm.backend.domain.catalog.ItService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItServiceRepository extends JpaRepository<ItService, Long> {

    List<ItService> findAllByOrderByName();
}
