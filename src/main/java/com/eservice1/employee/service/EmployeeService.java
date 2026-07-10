package com.eservice1.employee.service;

import com.eservice1.common.exception.DuplicateResourceException;
import com.eservice1.common.exception.InvalidOperationException;
import com.eservice1.common.exception.ResourceNotFoundException;
import com.eservice1.employee.dto.EmployeeProfileDTO;
import com.eservice1.employee.dto.UpdateEmployeeProfileDTO;
import com.eservice1.employee.entity.Employee;
import com.eservice1.employee.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import com.eservice1.user.entity.User;
import com.eservice1.user.repository.UserRepository;
import com.eservice1.common.Role;
import com.eservice1.employee.dto.EmployeePerformanceDTO;
import com.eservice1.employee.dto.RecentRequestDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import com.eservice1.employee.repository.TaskRepository;
import com.eservice1.submission.repository.CustomerRequestRepository;
import java.time.Month;

import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import com.eservice1.employee.dto.MonthlyRevenueDTO;
import com.eservice1.employee.dto.MonthlyCompletedDTO;
import com.eservice1.employee.dto.EmployeeOfMonthDTO;
import com.eservice1.employee.dto.EmployeeDashboardDTO;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.eservice1.employee.dto.CreateEmployeeRequest;
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerRequestRepository requestRepository;
    public EmployeeService(

            EmployeeRepository employeeRepository,

            UserRepository userRepository,

            TaskRepository taskRepository,

            CustomerRequestRepository requestRepository,

            PasswordEncoder passwordEncoder

    ) {

        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.requestRepository = requestRepository;
        this.passwordEncoder = passwordEncoder;
    }
    public EmployeeProfileDTO getMyProfile(
            String phoneNumber
    ) {

        Employee employee =
                employeeRepository
                        .findByPhoneNumber(phoneNumber);
        if (employee == null) {
            throw new ResourceNotFoundException(
                    "Employee not found."
            );
        }

        EmployeeProfileDTO dto =
                new EmployeeProfileDTO();

        dto.setId(employee.getId());
        dto.setName(employee.getName());
        dto.setPhoneNumber(employee.getPhoneNumber());
        dto.setDob(employee.getDob());
        dto.setGender(employee.getGender());
        dto.setEmail(employee.getEmail());
        dto.setAddress(employee.getAddress());
        dto.setJoinedDate(employee.getJoinedDate());
        dto.setProfileImage(employee.getProfileImage());
        dto.setActive(employee.getActive());

        return dto;
    }

    public EmployeeProfileDTO updateMyProfile(
            String phoneNumber,
            UpdateEmployeeProfileDTO request
    ) {

        Employee employee =
                employeeRepository
                        .findByPhoneNumber(phoneNumber);

        if (employee == null) {
            throw new ResourceNotFoundException(
                    "Employee not found."
            );
        }

        employee.setName(request.getName());
        employee.setDob(request.getDob());
        employee.setGender(request.getGender());
        employee.setEmail(request.getEmail());
        employee.setAddress(request.getAddress());

        employeeRepository.save(employee);

        User user =
                userRepository
                        .findByPhoneNumber(phoneNumber)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "User not found."
                                )
                        );
        user.setName(request.getName());

        userRepository.save(user);

        EmployeeProfileDTO dto =
                new EmployeeProfileDTO();

        dto.setId(employee.getId());
        dto.setName(employee.getName());
        dto.setPhoneNumber(employee.getPhoneNumber());
        dto.setDob(employee.getDob());
        dto.setGender(employee.getGender());
        dto.setEmail(employee.getEmail());
        dto.setAddress(employee.getAddress());
        dto.setJoinedDate(employee.getJoinedDate());
        dto.setProfileImage(employee.getProfileImage());
        dto.setActive(employee.getActive());

        return dto;
    }
    public String uploadProfileImage(
            String phoneNumber,
            MultipartFile file
    ) throws IOException {

        Employee employee =
                employeeRepository.findByPhoneNumber(phoneNumber);

        if (employee == null) {
            throw new ResourceNotFoundException(
                    "Employee not found."
            );        }

        String contentType = file.getContentType();

        if (contentType == null ||
                !contentType.startsWith("image/")) {

            throw new InvalidOperationException(
                    "Only image files are allowed."
            );
        }
        if (file.isEmpty()) {

            throw new InvalidOperationException(
                    "Please select an image."
            );

        }

        if (file.getSize() > 2 * 1024 * 1024) {

            throw new InvalidOperationException(
                    "Maximum image size is 2 MB."
            );

        }

        String uploadDir = "uploads/employees/";

        Files.createDirectories(
                Paths.get(uploadDir)
        );

        String originalName =
                file.getOriginalFilename();

        String extension = "";

        if (originalName != null &&
                originalName.contains(".")) {

            extension =
                    originalName.substring(
                            originalName.lastIndexOf(".")
                    );

        }

        String fileName =
                UUID.randomUUID() + extension;
        Path filePath =
                Paths.get(uploadDir, fileName);

        Files.copy(
                file.getInputStream(),
                filePath,
                StandardCopyOption.REPLACE_EXISTING
        );

        employee.setProfileImage(fileName);

        employeeRepository.save(employee);

        return fileName;
    }

    public Employee promoteUser(
            Long userId) {

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "User not found."
                                )
                        );
        if (user.getRole() ==
                Role.EMPLOYEE) {

            throw new DuplicateResourceException(
                    "User is already an employee."
            );
        }

        user.setRole(
                Role.EMPLOYEE
        );

        userRepository.save(user);
        Employee existing =
                employeeRepository
                        .findByPhoneNumber(
                                user.getPhoneNumber()
                        );

        if (existing != null) {

            throw new DuplicateResourceException(
                    "Employee already exists."
            );
        }

        Employee employee =
                new Employee();

        employee.setName(
                user.getName()
        );

        employee.setPhoneNumber(
                user.getPhoneNumber()
        );

        employee.setActive(true);

        return employeeRepository
                .save(employee);
    }
    public Employee save(Employee employee) {
        if (employeeRepository.findByPhoneNumber(
                employee.getPhoneNumber()) != null) {

            throw new DuplicateResourceException(
                    "Employee with this phone number already exists."
            );
        }
        if (userRepository.findByPhoneNumber(
                employee.getPhoneNumber()).isPresent()) {

            throw new DuplicateResourceException(
                    "User with this phone number already exists."
            );
        }
        employee.setActive(true);

        Employee savedEmployee =
                employeeRepository.save(employee);

        User user = new User();

        user.setName(
                employee.getName()
        );
        user.setPhoneNumber(
                employee.getPhoneNumber()
        );
        user.setPassword(
                passwordEncoder.encode(
                        employee.getPassword()
                )
        );


        user.setRole(
                Role.EMPLOYEE
        );

        userRepository.save(user);

        return savedEmployee;
    }
    public Employee createEmployee(
            CreateEmployeeRequest request) {
        if (request.getName() == null ||
                request.getName().isBlank()) {

            throw new InvalidOperationException(
                    "Employee name cannot be empty."
            );

        }

        if (request.getPassword() == null ||
                request.getPassword().length() < 6) {

            throw new InvalidOperationException(
                    "Password must contain at least 6 characters."
            );

        }
        if (employeeRepository.existsByPhoneNumber(
                request.getPhoneNumber())) {

            throw new DuplicateResourceException(
                    "Employee already exists."
            );
        }

        if (userRepository.findByPhoneNumber(
                request.getPhoneNumber()).isPresent()) {

            throw new DuplicateResourceException(
                    "Phone number already registered."
            );
        }

        Employee employee = new Employee();

        employee.setName(
                request.getName()
        );

        employee.setPhoneNumber(
                request.getPhoneNumber()
        );

        employee.setUsername(
                request.getPhoneNumber()
        );

        employee.setEmail(
                request.getEmail()
        );

        employee.setGender(
                request.getGender()
        );

        employee.setAddress(
                request.getAddress()
        );

        employee.setDob(
                request.getDob()
        );

        employee.setJoinedDate(
                LocalDate.now()
        );

        employee.setActive(true);

        Employee savedEmployee =
                employeeRepository.save(employee);

        User user = new User();

        user.setName(
                request.getName()
        );

        user.setPhoneNumber(
                request.getPhoneNumber()
        );

        user.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        user.setRole(
                Role.EMPLOYEE
        );

        userRepository.save(user);

        return savedEmployee;
    }
    public EmployeePerformanceDTO
    getEmployeePerformance(
            Long employeeId
    ) {
        //System.out.println("Employee Performance API called");


        Employee employee =
                employeeRepository.findById(employeeId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Employee not found."
                                )
                        );
        EmployeePerformanceDTO dto =
                new EmployeePerformanceDTO();


        Map<Month, Double> monthlyRevenue = new HashMap<>();

        taskRepository.findByEmployeeId(employeeId).forEach(task -> {

            if (task.getRequest().getPaymentStatus() ==
                    com.eservice1.submission.entity.PaymentStatus.PAID) {

                Month month =
                        task.getRequest()
                                .getCreatedAt()
                                .getMonth();

                double amount =
                        task.getRequest().getAmount() == null
                                ? 0
                                : task.getRequest().getAmount();

                monthlyRevenue.put(
                        month,
                        monthlyRevenue.getOrDefault(month, 0.0) + amount
                );
            }

        });

        Month bestMonth = null;
        double bestRevenue = 0;

        for (Map.Entry<Month, Double> entry : monthlyRevenue.entrySet()) {

            if (entry.getValue() > bestRevenue) {

                bestRevenue = entry.getValue();
                bestMonth = entry.getKey();

            }

        }

        dto.setBestMonth(
                bestMonth == null
                        ? "-"
                        : bestMonth.name()
        );
        // Revenue Trend

        List<MonthlyRevenueDTO> revenueTrend = new ArrayList<>();

        for (Object[] row : taskRepository.getMonthlyRevenueTrend(employeeId)) {

            MonthlyRevenueDTO item = new MonthlyRevenueDTO();

            item.setMonth(String.valueOf(row[0]));

            Number revenue = (Number) row[1];

            item.setRevenue(
                    revenue == null ? 0.0 : revenue.doubleValue()
            );

            revenueTrend.add(item);
        }

        dto.setRevenueTrend(revenueTrend);

// Completed Trend
        List<MonthlyCompletedDTO> completedTrend = new ArrayList<>();

        for (Object[] row : taskRepository.getMonthlyCompletedTrend(employeeId)) {

            MonthlyCompletedDTO item = new MonthlyCompletedDTO();

            item.setMonth(String.valueOf(row[0]));

            Number completed = (Number) row[1];

            item.setCompleted(
                    completed == null ? 0L : completed.longValue()
            );

            completedTrend.add(item);
        }

        dto.setCompletedTrend(completedTrend);

// Employee Of Month

        LocalDateTime start =
                LocalDate.now()
                        .withDayOfMonth(1)
                        .atStartOfDay();

        LocalDateTime end =
                start.plusMonths(1);

        List<Employee> ranking =
                employeeRepository.getEmployeeRanking(
                        start,
                        end
                );

        Employee topEmployee = null;

        if (!ranking.isEmpty()) {

            topEmployee = ranking.get(0);

        }

        if (topEmployee != null) {

            EmployeeOfMonthDTO top =
                    new EmployeeOfMonthDTO();

            top.setId(
                    topEmployee.getId()
            );

            top.setName(
                    topEmployee.getName()
            );

            top.setPhoneNumber(
                    topEmployee.getPhoneNumber()
            );

            top.setProfileImage(
                    topEmployee.getProfileImage()
            );

            Double topRevenue =
                    taskRepository.getCurrentMonthRevenue(
                            topEmployee.getId()
                    );

            if (topRevenue == null) {

                topRevenue = 0.0;

            }

            top.setRevenue(
                    topRevenue
            );

            Long completed =
                    taskRepository.getCurrentMonthCompleted(
                            topEmployee.getId()
                    );

            if (completed == null) {

                completed = 0L;

            }

            top.setCompletedTasks(
                    completed
            );

            long assigned =
                    taskRepository.countByEmployeeId(
                            topEmployee.getId()
                    );

            if (assigned == 0) {

                top.setCompletionPercentage(
                        0
                );

            }

            else {

                top.setCompletionPercentage(

                        (int)

                                (

                                        completed

                                                * 100

                                                /

                                                assigned

                                )

                );

            }

            String message;

            if (topRevenue >= 100000) {

                message =
                        "🏆 Outstanding Performer! Keep inspiring the team.";

            }

            else if (topRevenue >= 50000) {

                message =
                        "🚀 Excellent Growth! Keep pushing forward.";

            }

            else {

                message =
                        "⭐ Great Progress! Every completed request matters.";

            }

            top.setMessage(
                    message
            );

            dto.setEmployeeOfMonth(
                    top
            );

        }

        dto.setBestMonthRevenue(bestRevenue);

        dto.setId(employee.getId());

        dto.setName(
                employee.getName()
        );

        dto.setPhoneNumber(
                employee.getPhoneNumber()
        );

        dto.setActive(
                employee.getActive()
        );

        dto.setAssignedTasks(

                taskRepository.countByEmployeeId(
                        employeeId
                )

        );

        dto.setCompletedTasks(

                taskRepository.countByEmployeeIdAndStatus(
                        employeeId,
                        com.eservice1.employee.entity.TaskStatus.COMPLETED
                )

        );

        dto.setPendingTasks(

                taskRepository.countByEmployeeIdAndStatus(
                        employeeId,
                        com.eservice1.employee.entity.TaskStatus.PENDING
                )

        );

        dto.setInProgressTasks(

                taskRepository.countByEmployeeIdAndStatus(
                        employeeId,
                        com.eservice1.employee.entity.TaskStatus.IN_PROGRESS
                )

        );


        dto.setThisMonthRequests(

                taskRepository.getMonthlyRequests(

                        employeeId,

                        start,

                        end

                )

        );

        Double revenue =
                taskRepository.getTotalRevenue(
                        employeeId
                );

        if (revenue == null) {
            revenue = 0.0;
        }

        dto.setTotalRevenue(
                revenue
        );

        Double monthRevenue =
                taskRepository.getMonthlyRevenue(

                        employeeId,

                        start,

                        end

                );

        if (monthRevenue == null) {
            monthRevenue = 0.0;
        }

        dto.setMonthRevenue(
                monthRevenue
        );

        dto.setPaidRequests(

                taskRepository.getPaidRequests(
                        employeeId
                )

        );

        if (dto.getCompletedTasks() == 0) {

            dto.setAverageRevenue(0);

        } else {

            dto.setAverageRevenue(

                    dto.getTotalRevenue()

                            /

                            dto.getCompletedTasks()

            );

        }

        if (dto.getAssignedTasks() == 0) {

            dto.setCompletionPercentage(0);

        } else {

            dto.setCompletionPercentage(

                    (int)

                            (

                                    dto.getCompletedTasks()

                                            * 100

                                            /

                                            dto.getAssignedTasks()

                            )

            );

        }

        dto.setSuccessScore(

                dto.getCompletionPercentage()

        );


        List<RecentRequestDTO> recent =
                new ArrayList<>();

        taskRepository
                .findByEmployeeIdOrderByIdDesc(
                        employeeId
                )
                .stream()
                .limit(10)
                .forEach(task -> {

                    RecentRequestDTO r =
                            new RecentRequestDTO();

                    r.setId(
                            task.getRequest().getId()
                    );

                    r.setCustomerName(
                            task.getRequest()
                                    .getCustomerName()
                    );

                    r.setServiceName(
                            task.getRequest()
                                    .getService()
                                    .getServiceName()
                    );

                    r.setAmount(
                            task.getRequest()
                                    .getAmount()
                    );

                    if (task.getRequest().getPaymentStatus() != null) {

                        r.setPaymentStatus(
                                task.getRequest()
                                        .getPaymentStatus()
                                        .name()
                        );

                    } else {

                        r.setPaymentStatus(
                                "UNPAID"
                        );

                    }

                    r.setStatus(
                            task.getStatus()
                                    .name()
                    );

                    recent.add(r);

                });

        dto.setRecentRequests(
                recent
        );

        return dto;

    }
    public EmployeeDashboardDTO
    getEmployeeDashboard() {

        EmployeeDashboardDTO dto =
                new EmployeeDashboardDTO();

        LocalDateTime start =
                LocalDate.now()
                        .withDayOfMonth(1)
                        .atStartOfDay();

        LocalDateTime end =
                start.plusMonths(1);

        List<Employee> ranking =
                employeeRepository.getEmployeeRanking(
                        start,
                        end
                );
        if (!ranking.isEmpty()) {

            Employee employee =
                    ranking.get(0);

            EmployeeOfMonthDTO top =
                    new EmployeeOfMonthDTO();

            top.setId(
                    employee.getId()
            );

            top.setName(
                    employee.getName()
            );

            top.setPhoneNumber(
                    employee.getPhoneNumber()
            );

            top.setProfileImage(
                    employee.getProfileImage()
            );

            Double revenue =
                    taskRepository
                            .getCurrentMonthRevenue(
                                    employee.getId()
                            );

            if (revenue == null) {

                revenue = 0.0;

            }

            top.setRevenue(
                    revenue
            );

            Long completed =
                    taskRepository
                            .getCurrentMonthCompleted(
                                    employee.getId()
                            );

            if (completed == null) {

                completed = 0L;

            }

            top.setCompletedTasks(
                    completed
            );

            long assigned =
                    taskRepository
                            .countByEmployeeId(
                                    employee.getId()
                            );

            int percentage =
                    assigned == 0

                            ?

                            0

                            :

                            (int)

                            (

                                    completed

                                    * 100

                                    /

                                    assigned

                            );

            top.setCompletionPercentage(
                    percentage
            );

            top.setMessage(

                    "🏆 Outstanding Performer! Keep inspiring the team."

            );

            dto.setEmployeeOfMonth(
                    top
            );

        }

        List<MonthlyRevenueDTO> monthlyRevenue = new ArrayList<>();

        for (Object[] row : taskRepository.getPortalRevenueTrend()) {

            MonthlyRevenueDTO item = new MonthlyRevenueDTO();

            item.setMonth(String.valueOf(row[0]));

            Number revenue = (Number) row[1];

            item.setRevenue(
                    revenue == null ? 0.0 : revenue.doubleValue()
            );

            monthlyRevenue.add(item);
        }

        dto.setMonthlyRevenue(monthlyRevenue);
        return dto;

    }
}