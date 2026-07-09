package com.propertymap.repository;

import com.propertymap.model.Inspection;
import com.propertymap.model.InspectionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    List<Inspection> findByPropertyIdOrderByInspectionDateDescIdDesc(Long propertyId);

    /**
     * 继承来源查询:传 InspectionType.ROUTINE,即"最近一次非 ROUTINE"。
     * Entry→Exit→下一个Entry 自然衔接;Routine 不参与继承(创建界面不提供该选项)。
     */
    Optional<Inspection> findTopByPropertyIdAndTypeNotOrderByInspectionDateDescIdDesc(
            Long propertyId, InspectionType type);
}