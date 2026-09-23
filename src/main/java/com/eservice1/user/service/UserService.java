package com.eservice1.user.service;

import com.eservice1.common.Role;
import com.eservice1.common.exception.InvalidCredentialsException;
import com.eservice1.user.dto.LoginRequest;
import com.eservice1.user.entity.User;
import com.eservice1.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import com.eservice1.employee.repository.EmployeeRepository;
import com.eservice1.employee.entity.Employee;


import com.eservice1.common.exception.DuplicateResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.eservice1.config.JwtService;
import com.eservice1.user.dto.AuthResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.eservice1.security.service.LoginAttemptService;
import com.eservice1.common.exception.AccountLockedException;
import com.eservice1.user.dto.RegisterRequest;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${owner.bootstrap.token:#{null}}")
    private String ownerBootstrapToken;

    public UserService(
            UserRepository userRepository,
            JwtService jwtService,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttemptService) {
        this(userRepository, jwtService, employeeRepository, passwordEncoder, loginAttemptService, null);
    }

    @Autowired
    public UserService(
            UserRepository userRepository,
            JwtService jwtService,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttemptService,
            @Autowired(required = false) JdbcTemplate jdbcTemplate) {

        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void setOwnerBootstrapToken(String ownerBootstrapToken) {
        this.ownerBootstrapToken = ownerBootstrapToken;
    }

    public User register(RegisterRequest request) {

        if (request.getRole() == Role.OWNER) {
            throw new IllegalArgumentException("Registration cannot assign the OWNER role.");
        }

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
                request.getRole() != null ? request.getRole() : Role.CUSTOMER
        );

        return userRepository.save(user);

    }
    public AuthResponse login(LoginRequest request) {

        User user =
                userRepository.findByPhoneNumber(
                        request.getPhoneNumber()
                ).orElseThrow();

        if (loginAttemptService.isLocked(
                request.getPhoneNumber())) {

            long minutes = loginAttemptService
                    .getRemainingLockMinutes(request.getPhoneNumber());

            throw new AccountLockedException(
                    "Account is temporarily locked. Try again in "
                            + minutes + " minute(s)."
            );

        }
        // System.out.println("LOGIN USER = " + user.getName());
        //System.out.println("ROLE = " + user.getRole());
        //System.out.println("DB PASSWORD = " + user.getPassword());
        //System.out.println("REQUEST PASSWORD = " + request.getPassword());
        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {

            loginAttemptService.loginFailed(
                    request.getPhoneNumber()
            );

            throw new InvalidCredentialsException(
                    "Invalid phone number or password."
            );
        }
        loginAttemptService.loginSucceeded(
                request.getPhoneNumber()
        );
        String token =
                jwtService.generateToken(
                        user.getPhoneNumber(),
                        user.getRole()
                );

        Long employeeId = null;

        if (user.getRole() == Role.EMPLOYEE) {

            Employee employee =
                    employeeRepository
                            .findByPhoneNumber(
                                    user.getPhoneNumber()
                            );

            if (employee != null) {
                employeeId =
                        employee.getId();
            }
        }

        return new AuthResponse(
                token,
                user.getRole().name(),
                employeeId
        );
    }
    public User createOwner(RegisterRequest request) {
        return createOwner(request, null);
    }

    @Transactional
    public User createOwner(RegisterRequest request, String bootstrapToken) {
        if (ownerBootstrapToken == null || ownerBootstrapToken.isBlank()) {
            throw new AccessDeniedException("Owner bootstrap is disabled in production.");
        }

        if (bootstrapToken == null || !MessageDigest.isEqual(
                ownerBootstrapToken.trim().getBytes(StandardCharsets.UTF_8),
                bootstrapToken.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("Invalid or missing bootstrap token.");
        }

        if (userRepository.existsByRole(Role.OWNER)) {
            throw new DuplicateResourceException("Owner account already exists. Bootstrap is permanently locked.");
        }

        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.execute("SELECT pg_advisory_xact_lock(hashtext('owner_bootstrap'))");
            } catch (Exception ignored) {
                // Non-PostgreSQL dialect fallback
            }
        }

        if (userRepository.existsByRole(Role.OWNER)) {
            throw new DuplicateResourceException("Owner account already exists. Bootstrap is permanently locked.");
        }

        User owner = new User();
        owner.setName(request.getName());
        owner.setPhoneNumber(request.getPhoneNumber());
        owner.setPassword(
                passwordEncoder.encode(request.getPassword())
        );
        owner.setRole(Role.OWNER);

        try {
            return userRepository.saveAndFlush(owner);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Owner account already exists. Bootstrap is permanently locked.");
        }
    }
}