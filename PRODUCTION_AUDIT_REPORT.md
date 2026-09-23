# PRODUCTION AUDIT REPORT
**Project**: Nellai / Vinayaga E-Service Portal  
**Date**: September 20, 2026  
**Auditor**: Antigravity Senior Principal Systems & Security Engineer  
**Audit Scope**: Full-stack codebase audit (Spring Boot 3.5.14 Java backend + React/Vite frontend) focusing on Security, Reliability, Performance, Configuration, Error Handling, and Deployment Readiness.  
**Strict Mandate**: Read-only audit phase. No architectural redesign, no feature removal, no business logic alteration.

---

## Table of Contents
1. [Section A: Critical Findings](#section-a-critical-findings)
2. [Section B: High Findings](#section-b-high-findings)
3. [Section C: Medium Findings](#section-c-medium-findings)
4. [Section D: Low Findings](#section-d-low-findings)
5. [Section E: Existing Functionality Map](#section-e-existing-functionality-map)
6. [Section F: Authentication Flow](#section-f-authentication-flow)
7. [Section G: Authorization Matrix](#section-g-authorization-matrix)
8. [Section H: Data-Flow and Security Risks](#section-h-data-flow-and-security-risks)
9. [Section I: Production Deployment Risks](#section-i-production-deployment-risks)
10. [Section J: Recommended Fixes & Hardening Strategy](#section-j-recommended-fixes--hardening-strategy)
11. [Section K: Files Requiring Modification](#section-k-files-requiring-modification)
12. [Section L: Expected Behavioral Impact of Each Fix](#section-l-expected-behavioral-impact-of-each-fix)

---

## Section A: Critical Findings

### SEC-CRIT-01: OTP Authentication Privilege Escalation / Staff Account Takeover
- **Severity**: Critical
- **File**: `src/main/java/com/eservice1/customer/service/OtpService.java`
- **Class / Function**: `OtpService.verifyOtp(String phoneNumber, String otp)`
- **Problem**: When a customer verifies an OTP via `POST /customer/verify-otp`, the code queries `userRepository.findByPhoneNumber(phoneNumber)`. If a record exists (including staff accounts with role `OWNER` or `EMPLOYEE` that share or use that phone number), it issues a JWT for that phone number. In `JwtFilter.java`, the filter resolves roles directly from the database for the subject phone number and assigns `ROLE_OWNER` or `ROLE_EMPLOYEE`. Consequently, any attacker who initiates an OTP login for an Owner's or Employee's phone number and intercepts or bypasses the OTP can obtain a JWT endowed with full `OWNER` or `EMPLOYEE` permissions, completely bypassing the secure password authentication scheme.
- **Why it matters**: Direct account takeover of administrators and staff via the unauthenticated customer OTP portal. An attacker gains administrative control over services, users, and financials.
- **Recommended fix**: In `OtpService.verifyOtp`, check the existing user's role. If the user exists and holds role `OWNER` or `EMPLOYEE`, refuse customer OTP login with an explicit error indicating that staff accounts must authenticate via `/auth/login`, or enforce that customer OTP authentication exclusively generates a token with `CUSTOMER` authority and validates against the `CustomerProfile`.
- **Whether functionality changes**: No. Legitimate customers continue to log in via OTP seamlessly. Staff members continue to use `/login` as designed.
- **Risk of implementing fix**: Very low. Prevents cross-role account hijacking.

---

### SEC-CRIT-02: Public Unauthenticated Modification & Deletion of Form Schemas
- **Severity**: Critical
- **File**: `src/main/java/com/eservice1/config/SecurityConfig.java`, `src/main/java/com/eservice1/serviceform/controller/ServiceFormFieldController.java`, `src/main/java/com/eservice1/customerform/controller/CustomerFormFieldController.java`
- **Class / Function**: `SecurityConfig.PUBLIC_URL_PATTERNS`, `ServiceFormFieldController`, `CustomerFormFieldController`
- **Problem**: `SecurityConfig.java` defines `"/customer-form-fields/**"` and `"/service-form-fields/**"` inside `PUBLIC_URL_PATTERNS`. This allows unauthenticated anonymous users to invoke mutating HTTP methods:
  - `POST /service-form-fields`
  - `PUT /service-form-fields/{id}`
  - `DELETE /service-form-fields/{id}`
  - `POST /customer-form-fields`
  - `PUT /customer-form-fields/{id}`
  - `DELETE /customer-form-fields/{id}`
- **Why it matters**: Any anonymous user on the public internet can delete, modify, or inject malicious form fields for all government services and customer profiles, corrupting citizen intake forms and destroying schema integrity.
- **Recommended fix**: In `SecurityConfig.java`, split the permissions: allow unauthenticated `GET` requests (`HttpMethod.GET, "/service-form-fields/**"`, `HttpMethod.GET, "/customer-form-fields/**"`) so dynamic forms can be rendered publicly, but restrict `POST`, `PUT`, `DELETE` exclusively to `hasAuthority("OWNER")` (or `hasAnyAuthority("OWNER", "EMPLOYEE")` as appropriate).
- **Whether functionality changes**: No. Public form rendering continues to work. Only administrative mutation requires authentication.
- **Risk of implementing fix**: Very low.

---

### SEC-CRIT-03: Path Traversal & Arbitrary File Upload in Task Results
- **Severity**: Critical
- **File**: `src/main/java/com/eservice1/employee/service/TaskService.java`
- **Class / Function**: `TaskService.uploadResult(Long taskId, MultipartFile file)`
- **Problem**: The method resolves the output file path using unvalidated user input:
  ```java
  Path targetLocation = targetDir.resolve("RESULT_" + file.getOriginalFilename());
  Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
  ```
  If an attacker passes a filename such as `../../../../etc/passwd` or `..\..\..\Windows\System32\...`, it can overwrite arbitrary files on the server. In addition, there is no file extension whitelist, MIME type inspection, or file size validation. Furthermore, the endpoint does not verify whether the authenticated employee is actually assigned to the task.
- **Why it matters**: Arbitrary file overwrite, path traversal, potential remote code execution (RCE) via web shell upload, and unauthorized task tampering.
- **Recommended fix**:
  1. Sanitize the filename using `StringUtils.cleanPath` and generate a collision-resistant UUID filename while preserving only safe whitelisted extensions (`.pdf`, `.jpg`, `.jpeg`, `.png`).
  2. Verify that the resolved path is strictly within `targetDir`.
  3. Validate caller ownership: ensure the authenticated employee matches the task's assigned employee.
- **Whether functionality changes**: No. Employees can still upload result documents; files are stored safely.
- **Risk of implementing fix**: Low.

---

### SEC-CRIT-04: Password Hash & Sensitive PII Leakage in User Endpoints
- **Severity**: Critical
- **File**: `src/main/java/com/eservice1/user/controller/UserController.java`, `src/main/java/com/eservice1/user/controller/AuthController.java`
- **Class / Function**: `UserController.getAllUsers()`, `AuthController.register()`, `AuthController.createOwner()`
- **Problem**: `UserController.getAllUsers()` returns `List<User>` directly. The JPA `User` entity has the `password` field mapped directly to JSON serialization. Any authenticated user (or attacker with an employee token) calling `GET /users` receives the full list of all registered users, including their BCrypt password hashes, phone numbers, and internal IDs. Similarly, `AuthController.register()` and `createOwner()` return the `User` entity containing the password hash.
- **Why it matters**: Massive information disclosure. Exposes BCrypt password hashes of administrators and employees to offline dictionary/rainbow attacks and violates privacy compliance.
- **Recommended fix**:
  1. Add `@JsonIgnore` or `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` to the `password` field on `User.java`.
  2. Return a dedicated `UserResponse` DTO (e.g., `id`, `name`, `phoneNumber`, `role`, `createdAt`) from `UserController` and `AuthController` instead of raw JPA entities.
- **Whether functionality changes**: No. Clients do not consume the password hash; authentication and user listing remain functional.
- **Risk of implementing fix**: Low.

---

### SEC-CRIT-05: Plaintext OTP Storage and Cryptographically Insecure Generation
- **Severity**: Critical
- **File**: `src/main/java/com/eservice1/customer/entity/OtpVerification.java`, `src/main/java/com/eservice1/customer/service/OtpService.java`
- **Class / Function**: `OtpService.generateOtp()`, `OtpVerification` entity
- **Problem**:
  1. OTPs are generated using `ThreadLocalRandom.current().nextInt(100000, 1000000)`. `ThreadLocalRandom` is pseudo-random and not cryptographically secure, making OTP sequences potentially predictable.
  2. OTPs are stored in plaintext in the `otp_verification` table (`otp` column). Any read access to the database or SQL injection allows immediate extraction of active OTPs.
  3. There is no verification attempt counter (rate limit per OTP record). An attacker can attempt 100,000 combinations within the 5-minute expiry window without lockout.
- **Why it matters**: OTP interception and brute-force vulnerability leading to total citizen account compromise.
- **Recommended fix**:
  1. Use `java.security.SecureRandom` for OTP generation.
  2. Hash the OTP using a fast cryptographic hash (e.g., SHA-256 with salt) before storing in the database, or BCrypt.
  3. Add an `attempts` counter to `OtpVerification` (max 3 or 5 attempts before invalidation).
- **Whether functionality changes**: No. Citizen receives SMS and submits the 6-digit code exactly as before.
- **Risk of implementing fix**: Low.

---

## Section B: High Findings

### SEC-HIGH-01: Broken Access Control & Privilege Escalation in Employee Management
- **Severity**: High
- **File**: `src/main/java/com/eservice1/config/SecurityConfig.java`, `src/main/java/com/eservice1/employee/controller/EmployeeController.java`
- **Class / Function**: `SecurityConfig.filterChain()`, `EmployeeController.createEmployee()`, `EmployeeController.promoteUserToEmployee()`
- **Problem**: `SecurityConfig.java` configures `"/employees/**"` to permit `hasAnyAuthority("OWNER", "EMPLOYEE")`. However, `EmployeeController` contains:
  - `POST /employees` (create employee)
  - `POST /employees/promote/{userId}` (promote user to employee)
  - `PUT /employees/{id}` (update employee details)
  - `DELETE /employees/{id}` (delete employee)
  As a result, any standard employee can create new employees, promote arbitrary customer users to employee status, and delete other employees.
- **Why it matters**: Unauthorized privilege escalation and administrative disruption by any employee credential.
- **Recommended fix**: In `SecurityConfig.java` or via method security (`@PreAuthorize("hasAuthority('OWNER')")`), restrict `POST /employees`, `POST /employees/promote/**`, `DELETE /employees/**`, and `PUT /employees/**` strictly to `OWNER`. Only `GET /employees` or individual profile views should be accessible to employees.
- **Whether functionality changes**: No. Owner retains full management. Employees can view necessary directory info.
- **Risk of implementing fix**: Low.

---

### SEC-HIGH-02: Service Management Exposed to Standard Employees
- **Severity**: High
- **File**: `src/main/java/com/eservice1/config/SecurityConfig.java`, `src/main/java/com/eservice1/service/controller/AdminServiceController.java`, `src/main/java/com/eservice1/service/controller/AdminDocumentController.java`
- **Class / Function**: `SecurityConfig.filterChain()`, `AdminServiceController`, `AdminDocumentController`
- **Problem**: `SecurityConfig.java` permits `"/admin/**"` to `hasAnyAuthority("OWNER", "EMPLOYEE")`. This allows any employee to:
  - Create, update, or delete government service offerings (`POST/PUT/DELETE /admin/services/**`)
  - Create, update, or delete required document requirements (`POST/PUT/DELETE /admin/services/{serviceId}/documents/**`)
  These actions define the portal's catalogue and compliance rules, which are business-level configurations.
- **Why it matters**: Employees can delete or alter services, disrupt service availability, or alter document prerequisites without owner authorization.
- **Recommended fix**: Restrict mutating endpoints (`POST`, `PUT`, `DELETE`) under `/admin/services/**` to `hasAuthority("OWNER")`. Allow `GET` requests to `hasAnyAuthority("OWNER", "EMPLOYEE")`.
- **Whether functionality changes**: No. Owner continues to configure services. Employees can view services.
- **Risk of implementing fix**: Low.

---

### SEC-HIGH-03: Insecure Direct Object Reference (IDOR) in Task Lifecycle
- **Severity**: High
- **File**: `src/main/java/com/eservice1/employee/controller/EmployeeTaskController.java`, `src/main/java/com/eservice1/employee/service/TaskService.java`
- **Class / Function**: `EmployeeTaskController`, `TaskService.getTasksByEmployee()`, `TaskService.acceptTask()`, `TaskService.completeTask()`, `TaskService.updatePriority()`
- **Problem**:
  1. `GET /employee/tasks/{employeeId}`: Any authenticated employee can view the assigned tasks of any other employee simply by changing the path variable `employeeId`.
  2. `POST /employee/tasks/{taskId}/accept`: Does not verify that the calling employee is assigned to the task. Any employee can accept tasks assigned to others.
  3. `POST /employee/tasks/{taskId}/complete`: Does not verify task assignment.
  4. `PUT /employee/tasks/{taskId}/priority`: Allows any employee to alter task priority.
- **Why it matters**: Employees can tamper with colleagues' workloads, complete tasks prematurely without doing the work, and view sensitive citizen applications assigned to others.
- **Recommended fix**: In `TaskService`, extract the authenticated user's ID/phone number from `SecurityContextHolder`. Ensure that for employee actions (`accept`, `complete`, `uploadResult`), the task's assigned employee ID matches the authenticated employee's ID (unless the caller is `OWNER`).
- **Whether functionality changes**: No. Employees can still accept and complete their own tasks.
- **Risk of implementing fix**: Low.

---

### SEC-HIGH-04: Business & Financial Analytics Exposed to All Authenticated Users
- **Severity**: High
- **File**: `src/main/java/com/eservice1/config/SecurityConfig.java`, `src/main/java/com/eservice1/dashboard/controller/DashboardController.java`
- **Class / Function**: `SecurityConfig.filterChain()`, `DashboardController.getDashboardOverview()`, `DashboardController.getServiceAnalytics()`
- **Problem**: In `SecurityConfig.java`, `/dashboard/**` is omitted from specific role matchers and falls into `.anyRequest().authenticated()`. Consequently, any authenticated `CUSTOMER` or `EMPLOYEE` can call:
  - `GET /dashboard`: Exposes total revenue, total requests, total customers, employee count, and monthly revenue trends.
  - `GET /dashboard/service-analytics`: Exposes granular revenue and completion metrics per service.
- **Why it matters**: Confidential business and financial intelligence is leaked to citizens and non-administrative staff.
- **Recommended fix**: Restrict `/dashboard/**` in `SecurityConfig.java` to `hasAuthority("OWNER")` (or allow employees restricted read-only operational metrics if specifically required, but strictly deny `CUSTOMER`).
- **Whether functionality changes**: No. Owner retains dashboard access. Customers are blocked from financial data.
- **Risk of implementing fix**: Low.

---

### SEC-HIGH-05: Unauthenticated Feedback Submission (Metrics Poisoning & IDOR)
- **Severity**: High
- **File**: `src/main/java/com/eservice1/config/SecurityConfig.java`, `src/main/java/com/eservice1/feedback/controller/FeedbackController.java`
- **Class / Function**: `SecurityConfig.PUBLIC_URL_PATTERNS`, `FeedbackController.submitFeedback()`
- **Problem**: `"/feedback/**"` is listed in `PUBLIC_URL_PATTERNS`. `POST /feedback` accepts a `FeedbackRequest` containing `requestId`, `rating`, and `comment`. It performs no authentication check, nor does it verify that the submitter is the owner of the `requestId`.
- **Why it matters**: Malicious actors or bots can spam feedback, submit fake 1-star or 5-star reviews on any request ID, skewing employee performance ratings and portal analytics.
- **Recommended fix**:
  1. Remove `"/feedback/**"` from `PUBLIC_URL_PATTERNS`.
  2. Require `hasAuthority("CUSTOMER")`.
  3. Validate that the authenticated customer's phone number matches the customer who submitted the request associated with `requestId`.
- **Whether functionality changes**: No. Citizens who submitted requests can still provide feedback after service completion.
- **Risk of implementing fix**: Low.

---

### SEC-HIGH-06: Phone Number / User Account Enumeration via Timing & Response Divergence
- **Severity**: High
- **File**: `src/main/java/com/eservice1/user/service/UserService.java`
- **Class / Function**: `UserService.login(LoginRequest request)`
- **Problem**: In `UserService.login`:
  ```java
  User user = userRepository.findByPhoneNumber(request.getPhoneNumber())
      .orElseThrow(() -> new NoSuchElementException("User not found"));
  if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new InvalidCredentialsException("Invalid credentials");
  }
  ```
  When a phone number does not exist, `NoSuchElementException` is thrown, returning HTTP 500 (or 404). When the phone number exists but the password is wrong, `InvalidCredentialsException` is thrown, returning HTTP 401.
- **Why it matters**: Attackers can systematically enumerate all registered user phone numbers (citizens, employees, owners) by probing the `/auth/login` endpoint.
- **Recommended fix**: Throw a uniform `InvalidCredentialsException("Invalid phone number or password")` in both cases, and ensure `GlobalExceptionHandler` maps it to HTTP 401.
- **Whether functionality changes**: No. Legitimate users with correct credentials log in normally.
- **Risk of implementing fix**: Low.

---

### SEC-HIGH-07: Unrestricted File Types & Inadequate MIME Validation on Document Uploads
- **Severity**: High
- **File**: `src/main/java/com/eservice1/submission/service/RealFileUploadService.java`, `src/main/java/com/eservice1/submission/controller/UploadedDocumentController.java`
- **Class / Function**: `RealFileUploadService.upload()`, `UploadedDocumentController.previewDocument()`
- **Problem**: Document upload accepts any file supplied by the user. While Supabase storage is used for citizen documents, there is no server-side MIME type or magic byte validation. If a user uploads an HTML file containing malicious JavaScript or an SVG with embedded scripts, serving it back via `previewDocument` without a strict `Content-Security-Policy` or `Content-Disposition: attachment` can lead to Stored Cross-Site Scripting (XSS) in the context of the portal.
- **Why it matters**: Stored XSS against employees/owners reviewing submitted citizen documents, session hijacking, or malicious file distribution.
- **Recommended fix**:
  1. Restrict allowed upload extensions and MIME types to safe formats: `image/jpeg`, `image/png`, `application/pdf`.
  2. In `previewDocument`, enforce `X-Content-Type-Options: nosniff` and appropriate `Content-Security-Policy: default-src 'none'`.
- **Whether functionality changes**: No. Valid citizen application documents (PDFs, JPGs, PNGs) continue to be uploaded and previewed.
- **Risk of implementing fix**: Low.

---

## Section C: Medium Findings

### SEC-MED-01: Stack Trace Leakage & Unhandled Security Exceptions
- **Severity**: Medium
- **File**: `src/main/java/com/eservice1/common/exception/GlobalExceptionHandler.java`
- **Class / Function**: `GlobalExceptionHandler.handleGlobalException(Exception ex)`
- **Problem**:
  1. `handleGlobalException` executes `ex.printStackTrace()`. In production logs or responses, unformatted stack traces pollute logs and leak internal class structures.
  2. `GlobalExceptionHandler` does not catch `org.springframework.security.access.AccessDeniedException` or `org.springframework.security.core.AuthenticationException`. When a user lacks permissions, Spring Security throws `AccessDeniedException`, which gets intercepted by the generic `Exception` handler and returns HTTP 500 Internal Server Error instead of HTTP 403 Forbidden.
- **Why it matters**: Masking 403 as 500 confuses clients and monitoring systems; `ex.printStackTrace()` is anti-pattern in production logging.
- **Recommended fix**:
  1. Replace `ex.printStackTrace()` with `log.error("Unhandled exception: ", ex)` using SLF4J.
  2. Add dedicated `@ExceptionHandler(AccessDeniedException.class)` returning HTTP 403 Forbidden with a standardized `ApiResponse`.
  3. Add dedicated `@ExceptionHandler(BadCredentialsException.class)` returning HTTP 401 Unauthorized.
- **Whether functionality changes**: No. Improves error reporting accuracy.
- **Risk of implementing fix**: Low.

---

### SEC-MED-02: Flawed Frontend Route Guarding for Customer Portal
- **Severity**: Medium
- **File**: `eservice-frontend/src/components/CustomerProtectedRoute.jsx`
- **Class / Function**: `CustomerProtectedRoute`
- **Problem**: The route guard verifies authentication solely by checking `localStorage.getItem("customerPhone")`. It does not check for the presence of `localStorage.getItem("token")` or validate token expiration. A user can inject an arbitrary string into `customerPhone` in localStorage and access protected customer routes.
- **Why it matters**: Bypasses client-side navigation security, exposing customer UI views and causing unexpected errors when downstream API calls fail.
- **Recommended fix**: Check both `token` and `customerPhone`, and check if `token` is expired (via JWT exp decode). If missing or invalid, redirect to `/customer-login`.
- **Whether functionality changes**: No. Legitimate logged-in customers pass the check.
- **Risk of implementing fix**: Low.

---

### SEC-MED-03: Hardcoded Staff Login Redirection for Citizens on Token Expiry
- **Severity**: Medium
- **File**: `eservice-frontend/src/api/secureApi.js`
- **Class / Function**: `axiosInstance.interceptors.response`
- **Problem**: When any API call returns HTTP 401 or 403, `secureApi.js` executes:
  ```javascript
  window.location.href = '/login';
  ```
  The `/login` route is the Employee/Owner password login page. If a citizen's OTP session expires while using the customer portal, they are redirected to the staff login page instead of `/customer-login`.
- **Why it matters**: Poor user experience and confusion for citizens, who attempt to log in with OTP on a password form.
- **Recommended fix**: Detect the current route or user role. If the path starts with `/customer` or the stored role is `CUSTOMER`, redirect to `/customer-login`; otherwise redirect to `/login`.
- **Whether functionality changes**: No. Fixes the broken redirect flow.
- **Risk of implementing fix**: Low.

---

### SEC-MED-04: Missing Transaction Boundaries on Multi-Entity DB Operations
- **Severity**: Medium
- **File**: `src/main/java/com/eservice1/user/service/UserService.java`, `src/main/java/com/eservice1/employee/service/EmployeeService.java`
- **Class / Function**: `UserService.register()`, `EmployeeService.createEmployee()`
- **Problem**: Methods performing multi-step entity creation (e.g., creating a `User` record, setting credentials, and creating an associated `Employee` record) lack `@Transactional`. If an exception occurs during employee profile creation or role assignment, the `User` record is committed, leaving orphaned and inconsistent database records.
- **Why it matters**: Database corruption and phantom records leading to unique constraint violations on retry.
- **Recommended fix**: Add `@Transactional` to `UserService.register()`, `EmployeeService.createEmployee()`, `EmployeeService.deleteEmployee()`, and `TaskService` workflow methods.
- **Whether functionality changes**: No. Ensures atomicity.
- **Risk of implementing fix**: Low.

---

### SEC-MED-05: Missing Rate Limiting on SMS OTP Dispatch
- **Severity**: Medium
- **File**: `src/main/java/com/eservice1/customer/controller/OtpController.java`
- **Class / Function**: `OtpController.sendOtp(OtpRequest request)`
- **Problem**: `POST /customer/send-otp` is completely unthrottled. An attacker can write a script to trigger thousands of OTP requests in seconds against arbitrary phone numbers.
- **Why it matters**: SMS toll fraud, exhaustion of MSG91 account balance, SMS flooding/harassment of citizens, and denial of service.
- **Recommended fix**: Implement an in-memory or bucket-based rate limiter (e.g., maximum 3 OTP requests per phone number per 10 minutes, and maximum 10 requests per client IP per minute).
- **Whether functionality changes**: No. Normal users requesting 1-2 OTPs are unaffected.
- **Risk of implementing fix**: Low.

---

### SEC-MED-06: Permissive CORS Configuration in Production
- **Severity**: Medium
- **File**: `src/main/java/com/eservice1/config/SecurityConfig.java`
- **Class / Function**: `SecurityConfig.corsConfigurationSource()`
- **Problem**: The CORS configuration explicitly hardcodes `http://localhost:5173` and `http://localhost:3000`. In a production environment, if developers change this to `*` or leave it unconfigurable via properties, it either blocks production frontend domains or permits cross-origin theft.
- **Why it matters**: Prevents seamless deployment to staging/production domains and risks insecure cross-origin policies.
- **Recommended fix**: Read allowed CORS origins from `application.properties` (e.g. `${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}`), allowing environment variables to configure production domains without code changes.
- **Whether functionality changes**: No.
- **Risk of implementing fix**: Low.

---

## Section D: Low Findings

### SEC-LOW-01: Dead Endpoints and Orphaned Security Matchers
- **Severity**: Low
- **File**: `src/main/java/com/eservice1/config/SecurityConfig.java`
- **Class / Function**: `SecurityConfig.PUBLIC_URL_PATTERNS`
- **Problem**: `SecurityConfig` includes `"/admin/requests/test"` in `PUBLIC_URL_PATTERNS`, but no such controller endpoint exists in the codebase.
- **Why it matters**: Dead configuration lines create clutter and false assumptions during security audits.
- **Recommended fix**: Clean up unused URL patterns from `SecurityConfig.java`.
- **Whether functionality changes**: No.
- **Risk of implementing fix**: None.

---

### SEC-LOW-02: Hibernate `ddl-auto=update` Enabled in Production Properties
- **Severity**: Low
- **File**: `src/main/resources/application.properties`
- **Class / Function**: Configuration property `spring.jpa.hibernate.ddl-auto`
- **Problem**: `spring.jpa.hibernate.ddl-auto=update` is set in `application.properties`. In production, automatic schema update can cause unintended table lockups, schema divergence, or accidental column type changes.
- **Why it matters**: Production database stability risk.
- **Recommended fix**: Ensure production profile uses `spring.jpa.hibernate.ddl-auto=validate` or `none` and encourage migration scripts.
- **Whether functionality changes**: No.
- **Risk of implementing fix**: Low.

---

### SEC-LOW-03: Hardcoded JWT Expiration Duration
- **Severity**: Low
- **File**: `src/main/java/com/eservice1/config/JwtService.java`
- **Class / Function**: `JwtService.generateToken()`
- **Problem**: Token validity is hardcoded as `1000 * 60 * 60 * 24` (24 hours). It cannot be configured per environment or shortened for high-security environments.
- **Why it matters**: Stolen tokens remain valid for a full 24 hours with no revocation or configurable window.
- **Recommended fix**: Externalize JWT expiration duration to `application.properties` (`app.jwt.expiration-ms`).
- **Whether functionality changes**: No.
- **Risk of implementing fix**: Low.

---

### SEC-LOW-04: Inconsistent API Response Structures
- **Severity**: Low
- **File**: Multiple controllers (`ServiceController`, `CustomerRequestController`, `OtpController`)
- **Class / Function**: Various controller methods
- **Problem**: Some endpoints return `ApiResponse<T>`, some return raw entity strings (`ResponseEntity.ok("OTP sent successfully")`), and others return raw domain entities (`List<ServiceCategory>`).
- **Why it matters**: Inconsistent response payloads make frontend error handling and typing brittle.
- **Recommended fix**: Standardize controller responses using the existing `ApiResponse<T>` wrapper.
- **Whether functionality changes**: No, provided frontend response interceptors are handled carefully.
- **Risk of implementing fix**: Low.

---

## Section E: Existing Functionality Map

The application consists of three primary user personas: **CUSTOMER** (citizens), **EMPLOYEE** (service operators), and **OWNER** (portal administrator).

```
+----------------------------------------------------------------------------------------------------+
|                                    NELLAI / VINAYAGA E-SERVICE PORTAL                             |
+----------------------------------------------------------------------------------------------------+
|                                                                                                    |
|  [ CITIZEN / CUSTOMER FLOW ]                                                                       |
|  1. Public Browsing: View categories & services (GET /service-categories, GET /services/**)         |
|  2. Authentication: Request OTP (POST /customer/send-otp) -> Verify OTP (POST /customer/verify-otp)|
|  3. Profile Setup: Complete Customer Profile & Dynamic Form Fields (GET/POST /customer/profile)    |
|  4. Service Intake: Fill dynamic service form fields & upload required docs (POST /customer/requests)|
|  5. Tracking: Check request status & download receipts (GET /customer/requests/my, /receipts/**)   |
|  6. Feedback: Submit rating & comments upon request completion (POST /feedback)                    |
|                                                                                                    |
|  [ EMPLOYEE FLOW ]                                                                                 |
|  1. Authentication: Password login (POST /auth/login) with Phone + Password                        |
|  2. Dashboard: View assigned requests and queue (GET /employee/tasks/my-tasks)                     |
|  3. Task Processing: Accept task -> Process document -> Upload result file (POST /employee/tasks/**) |
|  4. Receipt Generation: Generate & issue citizen receipt (POST /receipts/**)                       |
|  5. Performance: View completed task counts and turnaround metrics                                 |
|                                                                                                    |
|  [ OWNER / ADMIN FLOW ]                                                                            |
|  1. Authentication: Password login (POST /auth/login) with Phone + Password                        |
|  2. Business Analytics: View revenue, metrics, service trends (GET /dashboard/**)                 |
|  3. Service Management: Add/edit/delete categories, services, document requirements, and fields     |
|  4. Employee Management: Create staff accounts, assign roles, monitor staff performance            |
|  5. Request Oversight: View all customer submissions, reassign tasks, override status              |
+----------------------------------------------------------------------------------------------------+
```

---

## Section F: Authentication Flow

### Current Staff Authentication Flow (`/auth/login`)
1. Client sends `POST /auth/login` with `{ phoneNumber, password }`.
2. `AuthController` delegates to `UserService.login(...)`.
3. `UserService` looks up user by `phoneNumber` via `UserRepository`.
4. If not found, throws `NoSuchElementException` (leads to HTTP 500).
5. If found, verifies BCrypt hash via `passwordEncoder.matches(...)`.
6. Generates JWT with claims: `subject: phoneNumber`, `role: user.getRole().name()`.
7. Returns JWT and user details.
8. On subsequent requests, `JwtFilter` parses `Authorization: Bearer <token>`, validates signature, extracts username, queries `UserRepository` from DB, and sets `SecurityContextHolder`.

### Current Customer Authentication Flow (`/customer/send-otp` & `/customer/verify-otp`)
1. Citizen enters phone number on `/customer-login`.
2. Client sends `POST /customer/send-otp` with `{ phoneNumber }`.
3. `OtpService` generates 6-digit random code using `ThreadLocalRandom`.
4. Saves plaintext OTP in `otp_verification` table with 5-minute expiry.
5. Dispatches SMS via MSG91 API (`Msg91ServiceImpl`).
6. Citizen enters 6-digit OTP on frontend.
7. Client sends `POST /customer/verify-otp` with `{ phoneNumber, otp }`.
8. `OtpService` verifies matching code and checks expiration.
9. If valid, deletes/marks OTP as verified.
10. Checks `userRepository.findByPhoneNumber(phoneNumber)`.
    - **VULNERABILITY HERE**: If user exists (even if `OWNER` or `EMPLOYEE`), it issues a JWT for that phone number.
    - If user does not exist, it creates a new `User` with role `CUSTOMER`.
11. Generates JWT and returns `{ token, role, phoneNumber }`.

---

## Section G: Authorization Matrix

| Endpoint / Pattern | Intended Roles | Current Config (`SecurityConfig.java`) | Actual Security State |
|---|---|---|---|
| `POST /auth/login` | Public | `PUBLIC_URL_PATTERNS` | **Correct** |
| `POST /auth/register` | OWNER | `PUBLIC_URL_PATTERNS` | **Vulnerable**: Anyone can register staff |
| `POST /auth/create-owner` | System Init | `PUBLIC_URL_PATTERNS` | **Caution**: Needs one-time guard |
| `POST /customer/send-otp` | Public | `PUBLIC_URL_PATTERNS` | **Correct** (Needs rate limiting) |
| `POST /customer/verify-otp`| Public | `PUBLIC_URL_PATTERNS` | **Vulnerable**: Staff account takeover |
| `GET /services/**` | Public / All | `PUBLIC_URL_PATTERNS` | **Correct** |
| `GET /service-categories/**` | Public / All | `PUBLIC_URL_PATTERNS` | **Correct** |
| `POST/PUT/DELETE /service-form-fields/**` | OWNER | `PUBLIC_URL_PATTERNS` | **Critical**: Unauthenticated tampering |
| `POST/PUT/DELETE /customer-form-fields/**`| OWNER | `PUBLIC_URL_PATTERNS` | **Critical**: Unauthenticated tampering |
| `POST /feedback` | CUSTOMER | `PUBLIC_URL_PATTERNS` | **Vulnerable**: Unauthenticated spam |
| `GET /dashboard/**` | OWNER | Not configured (Any Authenticated) | **High**: Customers & Employees see revenue |
| `POST /employees` | OWNER | `hasAnyAuthority("OWNER", "EMPLOYEE")`| **High**: Employees can create employees |
| `POST /employees/promote/**` | OWNER | `hasAnyAuthority("OWNER", "EMPLOYEE")`| **High**: Employees can promote users |
| `POST/PUT/DELETE /admin/services/**` | OWNER | `hasAnyAuthority("OWNER", "EMPLOYEE")`| **High**: Employees can alter services |
| `GET /employee/tasks/{empId}` | EMPLOYEE (Self), OWNER | `hasAnyAuthority("OWNER", "EMPLOYEE")`| **High**: IDOR between employees |
| `POST /employee/tasks/{id}/accept` | Assigned EMPLOYEE | `hasAnyAuthority("OWNER", "EMPLOYEE")`| **High**: IDOR task stealing |
| `POST /employee/tasks/{id}/complete` | Assigned EMPLOYEE | `hasAnyAuthority("OWNER", "EMPLOYEE")`| **High**: IDOR task completion |
| `POST /employee/tasks/{id}/result` | Assigned EMPLOYEE | `hasAnyAuthority("OWNER", "EMPLOYEE")`| **Critical**: Path traversal & IDOR |
| `GET /users` | OWNER | Not configured (Any Authenticated) | **Critical**: Leaks all password hashes |

---

## Section H: Data-Flow and Security Risks

```
[ Citizen Browser ] 
        |
        | (Multipart Upload / Dynamic Form Data)
        v
[ Nginx / Reverse Proxy ]
        |
        v
[ Spring Boot Filter Chain: CorsFilter -> JwtFilter ]
        |
        +---> [ SecurityContextHolder ] (Subject: Phone, Authorities: [ROLE_*])
        |
        v
[ Controllers & Services ]
        |
        +---> [ File Upload ] ---> Local Disk ("RESULT_...") [RISK: Path Traversal]
        |                     ---> Supabase Bucket [RISK: Unvalidated MIME]
        |
        +---> [ Database (PostgreSQL/MySQL) ]
                    |---> users (password: BCrypt) [RISK: Serialized in GET /users]
                    |---> otp_verification (otp: Plaintext) [RISK: Plaintext OTP]
                    |---> customer_requests & tasks [RISK: Unchecked IDOR]
```

1. **Untrusted File System Writes**: Task result uploads concatenate unvalidated user filenames directly onto the host filesystem, opening path traversal vulnerabilities.
2. **Database Leaks via Object Serialization**: Returning JPA entity models directly from Spring controllers causes internal fields (passwords, internal IDs, audit timestamps) to be leaked into JSON responses.
3. **Missing Authorization Context in Services**: Service methods accept entity IDs (`taskId`, `employeeId`, `requestId`) from path variables without verifying if the caller owns or is assigned to the entity.

---

## Section I: Production Deployment Risks

1. **Database Schema Mutation on Boot**: `spring.jpa.hibernate.ddl-auto=update` in `application.properties` will alter tables automatically upon application startup. If multiple backend replicas start simultaneously, race conditions and deadlocks can occur.
2. **Missing Health and Liveness Probes**: Lack of configured Spring Boot Actuator health endpoints prevents Kubernetes, Docker Swarm, or cloud load balancers from detecting failed instances or deadlocks.
3. **CORS Blocking in Production**: Hardcoded `localhost` origins in `SecurityConfig.java` will block production frontend traffic unless configured via environment variables.
4. **Third-Party SMS Outage Handling**: `Msg91ServiceImpl` does not gracefully isolate external SMS gateway timeouts or 5xx responses, potentially hanging HTTP worker threads during SMS provider downtime.

---

## Section J: Recommended Fixes & Hardening Strategy

### 1. Hardening Authentication & Authorization
- **Fix SEC-CRIT-01**: In `OtpService.verifyOtp`, verify that the phone number does not belong to an `OWNER` or `EMPLOYEE`. If it does, reject the OTP login with an error message instructing the user to log in via `/auth/login`. Ensure that tokens issued from OTP verification strictly carry `ROLE_CUSTOMER`.
- **Fix SEC-CRIT-02**: In `SecurityConfig.java`, remove `"/customer-form-fields/**"` and `"/service-form-fields/**"` from `PUBLIC_URL_PATTERNS`. Add specific `antMatchers(HttpMethod.GET, ...)` for public form display, while locking `POST/PUT/DELETE` to `OWNER`.
- **Fix SEC-HIGH-01 & SEC-HIGH-02**: In `SecurityConfig.java`, separate administrative operations:
  - Require `hasAuthority("OWNER")` for `POST/PUT/DELETE /employees/**`, `/employees/promote/**`, and `POST/PUT/DELETE /admin/**`.
  - Allow `hasAnyAuthority("OWNER", "EMPLOYEE")` for operational routes like `GET /employees`, `GET /admin/services`.
- **Fix SEC-HIGH-04**: Restrict `/dashboard/**` strictly to `hasAuthority("OWNER")`.
- **Fix SEC-HIGH-05**: Require authentication and ownership verification for `POST /feedback`.

### 2. Eliminating Path Traversal & Securing File Operations
- **Fix SEC-CRIT-03 & SEC-HIGH-07**:
  - In `TaskService.uploadResult`:
    1. Verify that the authenticated user is the assigned employee for `taskId`.
    2. Sanitize filenames using `UUID.randomUUID().toString() + safeExtension`.
    3. Validate that the target path resolves inside the intended root directory (`targetLocation.normalize().startsWith(targetDir.normalize())`).
    4. Whitelist permitted MIME types (`image/jpeg`, `image/png`, `application/pdf`).

### 3. Preventing Sensitive Data & Password Hash Exposure
- **Fix SEC-CRIT-04**:
  - Annotate `User.password` with `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)`.
  - Create a `UserResponseDTO` to encapsulate user details without sensitive fields.
  - Return `UserResponseDTO` from `UserController.getAllUsers()` and `AuthController`.

### 4. Hardening OTP Generation & Storage
- **Fix SEC-CRIT-05**:
  - Replace `ThreadLocalRandom` with `java.security.SecureRandom`.
  - Hash OTP values in `otp_verification` or store them with salted hashing.
  - Add an attempt counter (max 3 attempts per OTP record) to prevent brute force.

### 5. Fixing Error Handling & Information Leakage
- **Fix SEC-HIGH-06**: Harmonize `UserService.login` to return a uniform error on both missing phone number and wrong password (`InvalidCredentialsException`).
- **Fix SEC-MED-01**: In `GlobalExceptionHandler`, replace `ex.printStackTrace()` with structured SLF4J logging, and add explicit handlers for `AccessDeniedException` (HTTP 403) and `BadCredentialsException` (HTTP 401).

### 6. Frontend Resilience & Route Guarding
- **Fix SEC-MED-02**: Update `CustomerProtectedRoute.jsx` to verify both `token` and `customerPhone`, checking JWT expiration before rendering protected routes.
- **Fix SEC-MED-03**: Update `secureApi.js` interceptor to redirect expired customer sessions to `/customer-login` rather than staff `/login`.

---

## Section L: Expected Behavioral Impact of Each Fix

| Fix ID | Affected Component | Does it change business workflow? | User / System Behavioral Impact |
|---|---|---|---|
| **SEC-CRIT-01** | `OtpService` | No | Staff cannot log into the citizen OTP portal; citizens log in as normal. |
| **SEC-CRIT-02** | `SecurityConfig` | No | Unauthenticated visitors can view form fields but cannot tamper with or delete them. |
| **SEC-CRIT-03** | `TaskService` | No | Employees upload task results normally; files are securely named and stored safely without path traversal. |
| **SEC-CRIT-04** | `User.java` / `UserController` | No | `GET /users` returns user profiles without the BCrypt password hash. |
| **SEC-CRIT-05** | `OtpService` | No | Citizens receive and enter 6-digit OTP as usual; brute-force attacks are blocked. |
| **SEC-HIGH-01** | `SecurityConfig` / `EmployeeController` | No | Only Owners can create/promote employees; employees can still view their directory. |
| **SEC-HIGH-02** | `SecurityConfig` / `AdminServiceController` | No | Only Owners can create/delete services; employees can view services as before. |
| **SEC-HIGH-03** | `EmployeeTaskController` / `TaskService` | No | Employees can only accept, complete, and view tasks assigned to them. |
| **SEC-HIGH-04** | `DashboardController` | No | Owners view revenue and metrics; citizens and staff are prevented from viewing financial data. |
| **SEC-HIGH-05** | `FeedbackController` | No | Citizens submit feedback for their own completed requests; unauthenticated spam is blocked. |
| **SEC-HIGH-06** | `UserService` | No | Users with valid credentials log in normally; attackers cannot enumerate phone numbers. |
| **SEC-HIGH-07** | `FileUploadService` / `UploadedDocumentController` | No | Citizens upload PDFs and images normally; malicious scripts and executables are blocked. |
| **SEC-MED-01** | `GlobalExceptionHandler` | No | Clean JSON error responses (401/403/500); stack traces hidden from clients. |
| **SEC-MED-02** | `CustomerProtectedRoute.jsx` | No | Unauthenticated users cannot access customer UI routes by faking localStorage keys. |
| **SEC-MED-03** | `secureApi.js` | No | Expired customer sessions redirect to `/customer-login` instead of staff `/login`. |

---

## Section K: Files Requiring Modification

### Backend Files
1. `src/main/java/com/eservice1/config/SecurityConfig.java`
   - Tighten `PUBLIC_URL_PATTERNS` (split GET vs mutating methods for form fields).
   - Restrict `/dashboard/**` to `OWNER`.
   - Restrict `/employees` creation and promotion to `OWNER`.
   - Restrict `/admin/**` service/document mutation to `OWNER`.
   - Configure externalized CORS properties.
2. `src/main/java/com/eservice1/customer/service/OtpService.java`
   - Enforce role check in `verifyOtp` to prevent staff account takeover.
   - Use `SecureRandom` for OTP generation.
   - Implement attempt counter and hashed OTP storage.
3. `src/main/java/com/eservice1/customer/entity/OtpVerification.java`
   - Add `attempts` field for brute-force mitigation.
4. `src/main/java/com/eservice1/employee/service/TaskService.java`
   - Sanitize file upload path against traversal.
   - Whitelist allowed file extensions.
   - Enforce caller assignment check on task operations.
5. `src/main/java/com/eservice1/employee/controller/EmployeeTaskController.java`
   - Pass authenticated user identity to service layer to prevent IDOR.
6. `src/main/java/com/eservice1/user/entity/User.java`
   - Add `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` to `password`.
7. `src/main/java/com/eservice1/user/controller/UserController.java` & `AuthController.java`
   - Use `UserResponseDTO` to eliminate password hash serialization.
8. `src/main/java/com/eservice1/user/service/UserService.java`
   - Return uniform `InvalidCredentialsException` for both missing user and bad password.
   - Add `@Transactional` to registration operations.
9. `src/main/java/com/eservice1/feedback/controller/FeedbackController.java`
   - Validate that authenticated customer owns the request being reviewed.
10. `src/main/java/com/eservice1/common/exception/GlobalExceptionHandler.java`
    - Add explicit handling for `AccessDeniedException` (403) and `BadCredentialsException` (401).
    - Replace `printStackTrace()` with SLF4J logging.
11. `src/main/resources/application.properties`
    - Externalize JWT expiration and CORS origins.

### Frontend Files
1. `eservice-frontend/src/components/CustomerProtectedRoute.jsx`
   - Validate both `token` and `customerPhone`, checking token expiration.
2. `eservice-frontend/src/api/secureApi.js`
   - Context-aware 401/403 redirection (`/customer-login` for customers, `/login` for staff).

---

## Conclusion & Next Steps
This audit comprehensively identifies all critical, high, medium, and low security and reliability concerns across the Nellai / Vinayaga E-Service Portal. 

**MANDATORY PAUSE**: In accordance with the instructions, execution is stopped here. No modifications to source code have been made. Please review this audit report. Upon your approval, we will proceed to Phase 2 to systematically implement and verify these hardening fixes without disrupting any existing business workflows.
