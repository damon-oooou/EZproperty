package com.propertymap.repository;

import com.propertymap.model.Property;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    /** v0.5:列表查询只返回当前 agency 的房源。 */
    List<Property> findByAgencyIdOrderByCreatedAtDesc(Long agencyId);
}
