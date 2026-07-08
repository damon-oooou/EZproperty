package com.propertymap.repository;

import com.propertymap.model.RoomCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoomConditionRepository extends JpaRepository<RoomCondition, Long> {

    /**
     * join fetch 是必须的：room 是 LAZY 关联，v0.2 已经踩过一次
     * Hibernate 代理序列化失败的坑，这里从一开始就取回完整实体。
     */
    @Query("select rc from RoomCondition rc join fetch rc.room " +
           "where rc.inspection.id = :inspectionId")
    List<RoomCondition> findByInspectionIdWithRoom(@Param("inspectionId") Long inspectionId);
}