package com.eservice1.employee.controller;

import com.eservice1.employee.dto.*;
import com.eservice1.employee.entity.Employee;
import com.eservice1.employee.repository.EmployeeRepository;
import com.eservice1.employee.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.eservice1.employee.repository.TaskRepository;
import com.eservice1.employee.entity.TaskStatus;
import org.springframework.security.core.Authentication;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.File;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import com.eservice1.employee.dto.CreateEmployeeRequest;
@RestController
@RequestMapping("/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final TaskRepository
            taskRepository;public EmployeeController(
            EmployeeService employeeService,
            EmployeeRepository employeeRepository,
            TaskRepository taskRepository) {

        this.employeeService =
                employeeService;

        this.employeeRepository =
                employeeRepository;

        this.taskRepository =
                taskRepository;
    }

    @PostMapping
    public Employee createEmployee(

            @Valid
            @RequestBody CreateEmployeeRequest request

    ) {

        return employeeService.createEmployee(request);
    }
    @GetMapping
    public List<EmployeeDTO> getEmployees() {

        return employeeRepository
                .findAll()
                .stream()
                .map(employee -> {

                    EmployeeDTO dto =
                            new EmployeeDTO();

                    dto.setId(
                            employee.getId()
                    );

                    dto.setName(
                            employee.getName()
                    );

                    dto.setPhoneNumber(
                            employee.getPhoneNumber()
                    );

                    dto.setActive(
                            employee.getActive()
                    );

                    dto.setTaskCount(
                            taskRepository
                                    .countByEmployeeId(
                                            employee.getId()
                                    )
                    );
                    dto.setCompletedTasks(
                            taskRepository
                                    .countByEmployeeIdAndStatus(
                                            employee.getId(),
                                            TaskStatus.COMPLETED
                                    )
                    );
                    return dto;

                })
                .toList();
    }

    @PostMapping("/promote/{userId}")
    public Employee promoteUser(
            @PathVariable Long userId) {

        return employeeService
                .promoteUser(userId);
    }
    @GetMapping("/{employeeId}/profile-image")
    public ResponseEntity<Resource> getProfileImage(
            @PathVariable Long employeeId,
            Authentication authentication) {

        File image = employeeService.getProfileImage(
                employeeId,
                authentication
        );

        return ResponseEntity.ok(new FileSystemResource(image));
    }
    @GetMapping("/{employeeId}/performance")
    public EmployeePerformanceDTO getEmployeePerformance(
            @PathVariable Long employeeId) {

        //System.out.println("========== PERFORMANCE API ==========");
        //System.out.println(employeeId);

        return employeeService.getEmployeePerformance(employeeId);
    }
    @GetMapping("/me")
    public EmployeeProfileDTO getMyProfile(
            Authentication authentication
    ) {

        return employeeService.getMyProfile(
                authentication.getName()
        );
    }

    @PutMapping("/me")
    public EmployeeProfileDTO updateMyProfile(

            Authentication authentication,

            @Valid
            @RequestBody UpdateEmployeeProfileDTO request

    ) {

        return employeeService.updateMyProfile(
                authentication.getName(),
                request
        );
    }
    @PostMapping("/me/profile-image")
    public String uploadProfileImage(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) throws IOException {

      //  System.out.println("UPLOAD API HIT");
      //  System.out.println(authentication);

        return employeeService.uploadProfileImage(
                authentication.getName(),
                file
        );
    }@GetMapping("/dashboard")
    public EmployeeDashboardDTO getDashboard(
            Authentication authentication
    ) {
        //System.out.println("EMPLOYEE DASHBOARD API HIT");

        //System.out.println(authentication.getName());

       // authentication.getAuthorities()
         //       .forEach(System.out::println);

        return employeeService.getEmployeeDashboard();

    }



}
