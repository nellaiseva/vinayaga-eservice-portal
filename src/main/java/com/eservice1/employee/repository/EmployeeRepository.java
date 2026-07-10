package com.eservice1.employee.repository;

import com.eservice1.employee.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmployeeRepository
        extends JpaRepository<Employee, Long> {

    Employee findByPhoneNumber(
            String phoneNumber
    );
    boolean existsByPhoneNumber(String phoneNumber);

    @Query("""
SELECT t.employee
FROM Task t
WHERE t.request.paymentStatus =
com.eservice1.submission.entity.PaymentStatus.PAID
AND t.request.createdAt >= :start
AND t.request.createdAt < :end
GROUP BY t.employee
ORDER BY SUM(t.request.amount) DESC
""")
    List<Employee> getEmployeeRanking(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

}