package com.eservice1.employee.repository;

import com.eservice1.employee.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import com.eservice1.employee.entity.TaskStatus;
import java.util.List;
import com.eservice1.employee.entity.Employee;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import com.eservice1.employee.dto.MonthlyRevenueDTO;
import com.eservice1.employee.dto.MonthlyCompletedDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
public interface TaskRepository
        extends JpaRepository<Task, Long>,
        JpaSpecificationExecutor<Task> {

    List<Task> findByEmployeeId(Long employeeId);

    Task findByRequestId(Long requestId);

    List<Task> findByRequestIdIn(List<Long> requestIds);

    long countByEmployeeId(
            Long employeeId
    );
    long countByEmployeeIdAndStatus(
            Long employeeId,
            TaskStatus status
    );
    List<Task> findByEmployeeIdOrderByIdDesc(
            Long employeeId
    );
    long countByStatus(TaskStatus status);

    long countByStatusNot(TaskStatus status);
    @Query("""
SELECT t
FROM Task t

WHERE t.employee.id = :employeeId

AND
(
    :search IS NULL

    OR

    LOWER(t.request.customerName)
    LIKE LOWER(CONCAT('%',:search,'%'))
)

AND
(
    :phone IS NULL

    OR

    t.request.phoneNumber
    LIKE CONCAT('%',:phone,'%')
)

AND
(
    :status IS NULL

    OR

    t.status = :status
)
""")
    Page<Task> searchEmployeeTasks(

            @Param("employeeId")
            Long employeeId,

            @Param("search")
            String search,

            @Param("phone")
            String phone,

            @Param("status")
            TaskStatus status,

            Pageable pageable

    );


    @Query("""
SELECT COALESCE(SUM(t.request.amount),0.0)
FROM Task t
WHERE t.employee.id=:employeeId
AND t.request.paymentStatus=
com.eservice1.submission.entity.PaymentStatus.PAID
""")
    Double getTotalRevenue(
            @Param("employeeId")
            Long employeeId
    );

    @Query("""
SELECT COUNT(t)
FROM Task t
WHERE t.employee.id=:employeeId
AND t.request.paymentStatus=
com.eservice1.submission.entity.PaymentStatus.PAID
""")
    long getPaidRequests(
            @Param("employeeId")
            Long employeeId
    );

    @Query("""
SELECT COALESCE(SUM(t.request.amount),0.0)
FROM Task t
WHERE t.employee.id=:employeeId
AND t.request.paymentStatus=
com.eservice1.submission.entity.PaymentStatus.PAID
AND t.request.createdAt>=:start
AND t.request.createdAt<:end
""")
    Double getMonthlyRevenue(

            @Param("employeeId")
            Long employeeId,

            @Param("start")
            LocalDateTime start,

            @Param("end")
            LocalDateTime end

    );

    @Query("""
SELECT COUNT(t)
FROM Task t
WHERE t.employee.id=:employeeId
AND t.request.createdAt>=:start
AND t.request.createdAt<:end
""")
    long getMonthlyRequests(

            @Param("employeeId")
            Long employeeId,

            @Param("start")
            LocalDateTime start,

            @Param("end")
            LocalDateTime end

    );
    @Query("""
SELECT

FUNCTION('MONTHNAME', t.request.createdAt),

COALESCE(SUM(t.request.amount),0.0)

FROM Task t

WHERE t.employee.id=:employeeId

AND t.request.paymentStatus=
com.eservice1.submission.entity.PaymentStatus.PAID

GROUP BY
YEAR(t.request.createdAt),
MONTH(t.request.createdAt),
FUNCTION('MONTHNAME', t.request.createdAt)

ORDER BY
YEAR(t.request.createdAt),
MONTH(t.request.createdAt)

""")
    List<Object[]> getMonthlyRevenueTrend(

            @Param("employeeId")
            Long employeeId

    );
    @Query("""
SELECT

FUNCTION('MONTHNAME', t.request.createdAt),

COUNT(t)

FROM Task t

WHERE t.employee.id=:employeeId

AND t.status=
com.eservice1.employee.entity.TaskStatus.COMPLETED

GROUP BY
YEAR(t.request.createdAt),
MONTH(t.request.createdAt),
FUNCTION('MONTHNAME', t.request.createdAt)

ORDER BY
YEAR(t.request.createdAt),
MONTH(t.request.createdAt)

""")
    List<Object[]> getMonthlyCompletedTrend(

            @Param("employeeId")
            Long employeeId

    );
    @Query("""
SELECT COALESCE(SUM(t.request.amount),0.0)

FROM Task t

WHERE t.employee.id=:employeeId

AND t.request.paymentStatus=
com.eservice1.submission.entity.PaymentStatus.PAID

AND t.request.createdAt >= :start

AND t.request.createdAt < :end

""")
    Double getCurrentMonthRevenue(

            @Param("employeeId")
            Long employeeId,

            @Param("start")
            LocalDateTime start,

            @Param("end")
            LocalDateTime end

    );
    @Query("""
SELECT COUNT(t)

FROM Task t

WHERE t.employee.id=:employeeId

AND t.status=
com.eservice1.employee.entity.TaskStatus.COMPLETED

AND t.request.createdAt >= :start

AND t.request.createdAt < :end

""")
    Long getCurrentMonthCompleted(

            @Param("employeeId")
            Long employeeId,

            @Param("start")
            LocalDateTime start,

            @Param("end")
            LocalDateTime end

    );
    @Query("""
SELECT

FUNCTION('MONTHNAME', t.request.createdAt),

COALESCE(SUM(t.request.amount),0.0)

FROM Task t

WHERE t.request.paymentStatus=
com.eservice1.submission.entity.PaymentStatus.PAID

GROUP BY
YEAR(t.request.createdAt),
MONTH(t.request.createdAt),
FUNCTION('MONTHNAME', t.request.createdAt)

ORDER BY
YEAR(t.request.createdAt),
MONTH(t.request.createdAt)

""")
    List<Object[]> getPortalRevenueTrend();
}