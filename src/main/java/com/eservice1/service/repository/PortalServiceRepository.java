package com.eservice1.service.repository;

import com.eservice1.service.entity.PortalService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PortalServiceRepository extends JpaRepository<PortalService, Long> {
    @Query("""
SELECT s
FROM PortalService s
WHERE
LOWER(s.serviceName)
    LIKE LOWER(CONCAT('%', :search, '%'))
OR
LOWER(s.description)
    LIKE LOWER(CONCAT('%', :search, '%'))
""")
    Page<PortalService> searchServices(

            @Param("search")
            String search,

            Pageable pageable

    );
    @Query("""
SELECT s
FROM PortalService s
WHERE s.active = true
AND (
    LOWER(s.serviceName)
        LIKE LOWER(CONCAT('%', :search, '%'))
    OR
    LOWER(s.description)
        LIKE LOWER(CONCAT('%', :search, '%'))
)
""")
    Page<PortalService> searchActiveServices(
            @Param("search") String search,
            Pageable pageable
    );
    Page<PortalService> findByActiveTrue(Pageable pageable);
}