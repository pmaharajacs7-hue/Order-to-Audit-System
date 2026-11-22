package com.orderaudit.production;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductionRunRepository extends JpaRepository<ProductionRun, Long> {
    List<ProductionRun> findAllByOrderByIdDesc();
}
