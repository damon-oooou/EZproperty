package com.propertymap.security;

import com.propertymap.model.Inspection;
import com.propertymap.model.Property;
import com.propertymap.repository.InspectionRepository;
import com.propertymap.repository.PropertyRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 租户隔离的单一入口:所有 service 加载 property/inspection 都走这里。
 * 不属于当前 agency 的资源抛 EntityNotFoundException(404 而不是 403),
 * 不向外泄露"这个 id 存在但不是你的"。
 * 更深层的资源(room/photo/condition)不单独检查:它们的所有访问路径
 * 都先经过 property 或 inspection,守住这两个入口即守住全部。
 */
@Component
@RequiredArgsConstructor
public class TenantGuard {

    private final PropertyRepository propertyRepository;
    private final InspectionRepository inspectionRepository;

    public Property property(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new EntityNotFoundException("Property not found: " + propertyId));
        check(property);
        return property;
    }

    public Inspection inspection(Long inspectionId) {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("Inspection not found: " + inspectionId));
        check(inspection.getProperty());
        return inspection;
    }

    /** getAgency().getId() 只读代理主键,不触发懒加载查询。 */
    public void check(Property property) {
        if (!property.getAgency().getId().equals(CurrentUser.agencyId())) {
            throw new EntityNotFoundException("Property not found: " + property.getId());
        }
    }
}
