package com.iitsm.backend.repository;

import com.iitsm.backend.domain.catalog.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByActiveTrueOrderByName();

    List<Category> findAllByOrderByName();
}
