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
import org.springframework.data.domain.Sort;
import com.eservice1.common.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Objects;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final CustomerRequestRepository requestRepository;
    private final UploadedDocumentRepository uploadedDocumentRepository;

    private void requireTaskOperateAccess(Task task, Authentication authentication) {
        if (authentication == null) {
            throw new AccessDeniedException("You are not authorized to operate on this task.");
        }
        boolean isOwner = authentication.getAuthorities().stream()
                .anyMatch(a -> "OWNER".equals(a.getAuthority()));
        if (isOwner) {
            return;
        }
        Employee employee = employeeRepository.findByPhoneNumber(authentication.getName());
        if (employee == null
                || task.getEmployee() == null
                || !Objects.equals(task.getEmployee().getId(), employee.getId())) {
            throw new AccessDeniedException("You are not authorized to operate on this task.");
        }
    }
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
        return getTasks(
                employeeId,
                page,
                size,
                search,
                phone,
                status,
                SecurityContextHolder.getContext().getAuthentication()
        );
    }

    public PageResponseDTO<Task> getTasks(

            Long employeeId,

            int page,

            int size,

            String search,

            String phone,

            String status,
            Authentication authentication

    ) {

        if (authentication == null) {
            throw new AccessDeniedException("You are not authorized to access these tasks.");
        }
        boolean isOwner = authentication.getAuthorities().stream()
                .anyMatch(a -> "OWNER".equals(a.getAuthority()));
        if (!isOwner) {
            Employee employee = employeeRepository.findByPhoneNumber(authentication.getName());
            if (employee == null || !Objects.equals(employeeId, employee.getId())) {
                throw new AccessDeniedException("You are not authorized to view tasks for this employee.");
            }
        }

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

        return getTasks(employeeId, SecurityContextHolder.getContext().getAuthentication());
    }

    public List<Task> getTasks(Long employeeId, Authentication authentication) {

        if (authentication == null) {
            throw new AccessDeniedException("You are not authorized to access these tasks.");
        }
        boolean isOwner = authentication.getAuthorities().stream()
                .anyMatch(a -> "OWNER".equals(a.getAuthority()));
        if (!isOwner) {
            Employee employee = employeeRepository.findByPhoneNumber(authentication.getName());
            if (employee == null || !Objects.equals(employeeId, employee.getId())) {
                throw new AccessDeniedException("You are not authorized to view tasks for this employee.");
            }
        }

        return taskRepository.findByEmployeeId(employeeId);
    }
    public Task acceptTask(Long taskId) {

        return acceptTask(taskId, SecurityContextHolder.getContext().getAuthentication());
    }

    public Task acceptTask(Long taskId, Authentication authentication) {

        Task task =
                taskRepository.findById(taskId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Task not found."
                                )
                        );

        requireTaskOperateAccess(task, authentication);

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

        return updatePriority(taskId, priority, SecurityContextHolder.getContext().getAuthentication());
    }

    public Task updatePriority(
            Long taskId,
            Priority priority,
            Authentication authentication) {

        Task task =
                taskRepository.findById(taskId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Task not found."
                                )
                        );

        requireTaskOperateAccess(task, authentication);

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

        return completeTask(taskId, SecurityContextHolder.getContext().getAuthentication());
    }

    public Task completeTask(Long taskId, Authentication authentication) {

        Task task =
                taskRepository.findById(taskId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Task not found."
                                )
                        );

        requireTaskOperateAccess(task, authentication);

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
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of(
            "pdf", "jpg", "jpeg", "png", "doc", "docx"
    );

    private static final java.util.Set<String> ALLOWED_MIME_TYPES = java.util.Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 MB

    public void uploadResult(
            Long taskId,
            MultipartFile file)
            throws IOException {
        uploadResultInternal(taskId, file, null, false);
    }

    public void uploadResult(
            Long taskId,
            MultipartFile file,
            Authentication authentication)
            throws IOException {
        uploadResultInternal(taskId, file, authentication, true);
    }

    private void uploadResultInternal(
            Long taskId,
            MultipartFile file,
            Authentication authentication,
            boolean requireAuth)
            throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file cannot be null or empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed limit of 20MB.");
        }

        // Validate MIME type
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            if (!ALLOWED_MIME_TYPES.contains(contentType.toLowerCase().trim())) {
                throw new IllegalArgumentException("File MIME type not allowed: " + contentType);
            }
        }

        // Extract and validate extension from original filename
        String rawOriginalFilename = file.getOriginalFilename();
        if (rawOriginalFilename == null || rawOriginalFilename.isBlank()) {
            throw new IllegalArgumentException("Invalid file name.");
        }

        // Strip any path traversal sequences from original filename
        String simpleName = new File(rawOriginalFilename).getName();
        if (rawOriginalFilename.contains("..") || rawOriginalFilename.contains("/") || rawOriginalFilename.contains("\\")) {
            throw new IllegalArgumentException("Malicious path sequence detected in filename.");
        }

        int dotIndex = simpleName.lastIndexOf('.');
        if (dotIndex == -1) {
            throw new IllegalArgumentException("File must have a valid extension.");
        }

        String extension = simpleName.substring(dotIndex + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File extension not allowed: ." + extension);
        }

        Task task =
                taskRepository.findById(
                        taskId
                ).orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Task not found."
                        )
                );

        if (requireAuth) {
            requireTaskOperateAccess(task, authentication);
        }

        // Safe storage directory
        File baseDir = new File(System.getProperty("user.dir"), "uploads").getCanonicalFile();
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }

        // Generate safe unique storage name (never uses user-controlled string directly as path)
        String safeStorageFileName = "RESULT_" + java.util.UUID.randomUUID().toString() + "." + extension;
        File destinationFile = new File(baseDir, safeStorageFileName).getCanonicalFile();

        // Canonical path traversal guard
        if (!destinationFile.getParentFile().equals(baseDir)) {
            throw new SecurityException("Potential path traversal detected.");
        }

        file.transferTo(destinationFile);

        String safeDisplayName = "RESULT_" + simpleName.replaceAll("[^a-zA-Z0-9._-]", "_");

        UploadedDocument document =
                new UploadedDocument();

        document.setDocumentName(
                safeDisplayName
        );

        document.setFileName(
                safeStorageFileName
        );

        document.setFilePath(
                destinationFile.getAbsolutePath()
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