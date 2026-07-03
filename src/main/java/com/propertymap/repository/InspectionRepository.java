package com.propertymap.repository;

import com.propertymap.model.Inspection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    List<Inspection> findByPropertyIdOrderByInspectionDateDescIdDesc(Long propertyId);

    Optional<Inspection> findTopByPropertyIdOrderByInspectionDateDescIdDesc(Long propertyId);
}