package com.westy.codmanager.geo.repository;

import com.westy.codmanager.geo.domain.Commune;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommuneRepository extends JpaRepository<Commune, Long> {

    List<Commune> findByWilayaCodeOrderByNameAsc(Short wilayaCode);
}
