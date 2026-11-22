package com.orderaudit.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RawMaterialRepository extends JpaRepository<RawMaterial, Long> {
    List<RawMaterial> findByActiveTrueOrderByNameAsc();
}
