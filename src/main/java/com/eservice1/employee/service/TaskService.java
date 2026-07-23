package com.eservice1.employee.service;

import com.eservice1.common.exception.InvalidOperationException;
import com.eservice1.employee.dto.EmployeeDashboardStatsDTO;
import com.eservice1.employee.entity.Employee;
import com.eservice1.employee.entity.Priority;
import com.eservice1.employee.entity.Task;
import com.eservice1.employee.entity.TaskStatus;
import com.eservice1.employee.repository.EmployeeRepository;
import com.eservice1.employee.repository.TaskRepository;
import org.springframework.stereotype.Service;
import com.eservice1.submission.entity.RequestStatus;
import com.eservice1.submission.repository.CustomerRequestRepository;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;

import com.eservice1.submission.entity.UploadedDocument;
import com.eservice1.submission.repository.UploadedDocumentRepository;
import java.util.List;
import com.eservice1.common.dto.PageResponseDTO;
import com.eservice1.common.util.PaginationMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;import com.eservice1.common.exception.ResourceNotFoundException;
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final CustomerRequestRepository requestRepository;
    private final UploadedDocumentRepository uploadedDocumentRepository;
    public Task selfAssign(
            Long requestId,
            String phoneNumber) {

        Task task =
                taskRepository.findByRequestId(
                        requestId
                );

        Employee employee =
                employeeRepository
                        .findByPhoneNumber(
                                phoneNumber
                        );

        if (task.getEmployee() != null) {

            throw new InvalidOperationException(
                    "Task is already assigned."
            );
        }

        task.setEmployee(employee);

        task.setStatus(
                TaskStatus.ACCEPTED
        );

        task.getRequest()
                .setStatus(
                        RequestStatus.IN_PROGRESS
                );

        requestRepository.save(
                task.getRequest()
        );

        return taskRepository.save(task);
    }
    public Task createTask(Task task) {

        task.setStatus(TaskStatus.PENDING);

        return taskRepository.save(task);
    }
    public TaskService(
            TaskRepository taskRepository,
            EmployeeRepository employeeRepository,
            CustomerRequestRepository requestRepository,
            UploadedDocumentRepository uploadedDocumentRepository) {

        this.taskRepository = taskRepository;

        this.employeeRepository = employeeRepository;

        this.requestRepository = requestRepository;

        this.uploadedDocumentRepository = uploadedDocumentRepository;
    }
    public List<Task> getAllTasks() {

        return taskRepository.findAll();
    }
    public PageResponseDTO<Task> getTasks(

            Long employeeId,

            int page,

            int size,

            String search,

            String phone,

            String status

    ) {

        search = (search == null) ? "" : search.trim();
        phone  = (phone == null) ? "" : phone.trim();

        if (status != null) {
            status = status.trim();

            if (status.isBlank() || status.equalsIgnoreCase("ALL")) {
                status = null;
            }
        }

        if (status != null) {

            status = status.trim();

            if (status.isBlank() || status.equalsIgnoreCase("ALL")) {

                status = null;

            }

        }
        try {
        Pageable pageable =

                PageRequest.of(

                        page,

                        size,

                        Sort.by("request.createdAt").descending()

                );

        TaskStatus taskStatus = null;

        if (status != null) {

            taskStatus = TaskStatus.valueOf(status);

        }

        Page<Task> tasks =

                taskRepository.searchEmployeeTasks(

                        employeeId,

                        search,

                        phone,

                        taskStatus,

                        pageable

                );

        return PaginationMapper.toResponse(tasks);
        } catch (Exception e) {

            e.printStackTrace();

            throw e;

        }

    }
    public List<Task> getTasks(Long employeeId) {

        return taskRepository.findByEmployeeId(employeeId);
    }
    public Task acceptTask(Long taskId) {

        Task task =
                taskRepository.findById(taskId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Task not found."
                                )
                        );

        task.setStatus(
                TaskStatus.IN_PROGRESS
        );

        task.getRequest()
                .setStatus(
                        RequestStatus.IN_PROGRESS
                );

        requestRepository.save(
                task.getRequest()
        );

        return taskRepository.save(task);
    }

    public Task updatePriority(
            Long taskId,
            Priority priority) {

        Task task =
                taskRepository.findById(taskId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Task not found."
                                )
                        );

        task.setPriority(priority);

        return taskRepository.save(task);
    }

    public Task assignEmployee(
            Long requestId,
            Long employeeId) {

       // System.out.println("ASSIGN SERVICE HIT");
       // System.out.println("REQUEST ID = " + requestId);
       // System.out.println("EMPLOYEE ID = " + employeeId);

        Task task =
                taskRepository.findByRequestId(
                        requestId
                );

        if (task == null) {
            throw new ResourceNotFoundException(
                    "Task not found "
            );
        }

        Employee employee =
                employeeRepository.findById(
                        employeeId
                ).orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found."
                        )
                );

        task.setEmployee(employee);

        task.setStatus(
                TaskStatus.PENDING
        );

        task.getRequest()
                .setStatus(
                        RequestStatus.ASSIGNED
                );

        requestRepository.save(
                task.getRequest()
        );

        return taskRepository.save(task);
    }
    public Task completeTask(Long taskId) {

        Task task =
                taskRepository.findById(taskId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Task not found."
                                )
                        );

        task.setStatus(
                TaskStatus.COMPLETED
        );

        task.getRequest()
                .setStatus(
                        RequestStatus.COMPLETED
                );

        requestRepository.save(
                task.getRequest()
        );

        return taskRepository.save(task);
    }
    public void uploadResult(
            Long taskId,
            MultipartFile file)
            throws IOException {

        Task task =
                taskRepository.findById(
                        taskId
                ).orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Task not found."
                        )
                );

        String uploadDir =
                System.getProperty("user.dir")
                        + File.separator
                        + "uploads"
                        + File.separator;

        File dir =
                new File(uploadDir);

        if (!dir.exists()) {

            dir.mkdirs();
        }

        String filePath =
                uploadDir +
                        "RESULT_" +
                        file.getOriginalFilename();

        file.transferTo(
                new File(filePath)
        );

        UploadedDocument document =
                new UploadedDocument();

        document.setDocumentName(
                "RESULT_" +
                        file.getOriginalFilename()
        );

        document.setFileName(
                "RESULT_" +
                        file.getOriginalFilename()
        );

        document.setFilePath(
                filePath
        );

        document.setRequest(
                task.getRequest()
        );
        document.setResultDocument(true);
        uploadedDocumentRepository.save(
                document
        );

        task.setStatus(
                TaskStatus.COMPLETED
        );

        task.getRequest()
                .setStatus(
                        RequestStatus.COMPLETED
                );

        requestRepository.save(
                task.getRequest()
        );

        taskRepository.save(task);
    }
    public EmployeeDashboardStatsDTO getDashboardStats(
            String phoneNumber
    ) {

        Employee employee =
                employeeRepository.findByPhoneNumber(
                        phoneNumber
                );

        return new EmployeeDashboardStatsDTO(

                taskRepository.countByEmployeeIdAndStatus(
                        employee.getId(),
                        TaskStatus.PENDING
                ),

                taskRepository.countByEmployeeIdAndStatus(
                        employee.getId(),
                        TaskStatus.ACCEPTED
                ),

                taskRepository.countByEmployeeIdAndStatus(
                        employee.getId(),
                        TaskStatus.IN_PROGRESS
                ),

                taskRepository.countByEmployeeIdAndStatus(
                        employee.getId(),
                        TaskStatus.COMPLETED
                )

        );

    }
}