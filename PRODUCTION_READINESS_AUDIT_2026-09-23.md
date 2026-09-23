# Vinayaga E-Service Portal — Production-Readiness Audit

**Status: NOT READY FOR FINAL DEPLOYMENT REVIEW.**  This is a source review and isolated-test audit performed without starting the application, contacting MSG91/Supabase, or accessing a production database. No application source was modified.

## Verification performed

* `mvn test`: 32 passed, 1 intentionally skipped; the full context test is disabled because it requires a live PostgreSQL environment.
* `npm run build --prefix eservice-frontend`: passed. Vite reports a 892 kB minified JavaScript chunk.
* `mvnw.cmd test`: cannot run because the checked-in Maven wrapper fails before Maven starts.

## Confirmed security vulnerabilities

| ID | Severity | Evidence / affected endpoints | Reproduction and impact | Required remediation / test |
|---|---|---|---|---|
| SEC-01 | CRITICAL | `SecurityConfig.java:131-136` grants every `/admin/**` endpoint to `EMPLOYEE` as well as `OWNER`. | Log in as any employee and call `GET /admin/requests`, `GET /admin/requests/{id}`, `GET /admin/requests/phone/{phone}`, or service/document mutation endpoints. This exposes all customer request PII and permits catalogue/document changes. | Make each admin route owner-only unless an explicitly documented employee permission is required. Add role tests for every admin route. |
| SEC-02 | CRITICAL | `SecurityConfig.java:144-154` grants all `/employees/**` and `/employee/**` routes to employees; `EmployeeTaskController.java:28-140` does no role/ownership check. | Employee A can list every task, read Employee B's task list, accept/complete/reprioritize B's task, assign work, upload a result, or create a task by changing IDs in the request. This corrupts workflow state and exposes customer data. | Enforce owner-only assignment/creation and resolve employee identity from authentication for all employee actions. Check assigned-task ownership in the service, not only the UI. Add A/B/owner IDOR integration tests. |
| SEC-03 | CRITICAL | `EmployeeController.java:44-104`, `EmployeeService.java:261-312,357-451`, with broad `/employees/**` access. | Any employee can create staff accounts and promote an arbitrary user ID to `EMPLOYEE`; a promoted account can then exercise SEC-01/SEC-02. | Owner-only staff creation and promotion; use an explicit, validated promotion workflow. Add negative employee-role tests. |
| SEC-04 | HIGH | `/feedback/**` is public (`SecurityConfig.java:35`); `FeedbackController.java:21-28` binds no authentication; `FeedbackService.java:30-77` accepts any completed request ID. | An unauthenticated caller can submit feedback for any completed request ID, poisoning ratings and consuming the one-feedback slot. | Require `CUSTOMER`, verify authenticated phone equals the request customer, and retain a DB unique constraint. Add anonymous and customer-A/customer-B tests. |
| SEC-05 | HIGH | `/auth/owner` is public; `AuthController.java:49-57`; `UserService.java:131-146` only performs a non-atomic `existsByRole` check. | Before initial ownership is safely established—or with concurrent first requests—an unauthenticated caller can create an OWNER. The database has no uniqueness guarantee for the OWNER role. | Replace public bootstrap with one-time deployment provisioning; enforce a database-backed invariant/transaction. Test concurrent bootstrap attempts. |
| SEC-06 | HIGH | `CustomerRequestService.java:119-169` lets an assigned employee set `PAID` and arbitrary amount with no payment-provider verification/audit. | Assigned employee calls `POST /requests/{id}/payment?status=PAID&amount=...`; request financial state becomes paid. | Restrict to an audited owner workflow or signed payment webhook; validate currency/amount and make updates idempotent. |

## Confirmed reliability and integrity risks

| ID | Severity | Evidence / impact | Required remediation / test |
|---|---|---|---|
| REL-01 | HIGH | Task state transitions (`TaskService.java:45-72,186-297,403-420`) have no transaction, locking, state-machine guard, or ownership guard. Parallel accept/assign/complete calls can overwrite each other and leave request/task states inconsistent. | Add transaction boundaries, optimistic locking/versioning or conditional updates, and allowed transition checks. Race-test two employees. |
| REL-02 | HIGH | Request creation saves `CustomerRequest` then `Task` without a transaction (`CustomerRequestService.java:77-116`). A task-save failure leaves an unworkable request. Service creation similarly saves service/documents independently. | Make each workflow atomic and add failure-injection tests. |
| REL-03 | HIGH | `spring.jpa.hibernate.ddl-auto=update`; no Flyway/Liquibase migrations, backup policy, restore procedure, or rollback runbook is present. | Introduce versioned migrations, tested backup/restore, and deployment rollback procedure before launch. |
| REL-04 | MEDIUM | OTP resend/count/verify and password-reset verification use read-modify-write/delete without transaction/locking (`OtpService.java`). Simultaneous requests can evade limits or issue/reuse reset state. | Use a transactional conditional update/unique active OTP design and concurrency tests. |
| REL-05 | MEDIUM | Customer form responses are insert-only and have no unique `(phone, field)` constraint; service responses use application-level read-then-save only. Duplicates/races cause ambiguous autofill and data loss. | Add constraints and atomic upsert behavior. |
| REL-06 | MEDIUM | `SupabaseStorageService.java:65` materializes uploads as `file.getBytes()` and has no configured connect/read timeout. Downloads are also materialized fully before response. A slow service or concurrent 20–30 MB files can exhaust request threads/heap. | Stream uploads/downloads; configure timeouts/retries/circuit handling and load-test the configured limits. |
| REL-07 | MEDIUM | `GlobalExceptionHandler.java:175` and `TaskService.java:175` print stack traces; production logging/alerting/redaction is not configured. | Use structured, redacted logs with correlation IDs; define alerts for auth, storage, DB and OTP failures. |
| REL-08 | MEDIUM | Docker runs as root, copies the entire context, has no `.dockerignore`, health check, non-root user, multi-stage build, restart/volume policy, or compose/deployment manifest. Local `receipts/` storage is ephemeral in a container. | Harden image and deployment specification; persist storage intentionally; add readiness/liveness checks. |

## Additional confirmed gaps

* Upload acceptance relies primarily on client-supplied MIME type (`RealFileUploadService.java:81`; receipt/profile paths have the same pattern). Validate file signatures/content and malware-scan before serving files.
* JWTs are valid for 24 hours and are checked against the current role, but there is no disabled-user flag, token revocation, key rotation, or logout invalidation. A role change invalidates only when role differs; a deleted/deactivated concept does not exist.
* Page size and page numbers are not bounded on several pageable endpoints; task list `GET /employee/tasks` is unpaged and returns entities.
* Employee and customer data are returned as JPA entities from several endpoints, making future field additions easy to expose accidentally. Use response DTOs consistently.
* Frontend tokens are in `localStorage`, so any XSS can steal them. The frontend also has multiple raw Axios clients and hard-coded localhost fallbacks, increasing configuration and 401/403-handling inconsistency.
* The production build succeeds but has a 892 kB JavaScript chunk. Code-split non-critical owner/employee views.

## Authorization matrix (runtime policy and source checks)

`P` = public, `A` = authenticated, `C/E/O` = customer/employee/owner. “own” means a verified server-side ownership check.

| Method | Path | Runtime access | Ownership / result |
|---|---|---|---|
| POST | `/auth/login`, `/auth/owner` | P | login; public owner bootstrap (SEC-05) |
| POST | `/auth/register` | O | none; caller-selected role accepted |
| POST | `/customer/send-otp`, `/customer/verify-otp` | P | phone supplied by caller |
| POST | `/employee/forgot-password/{send-otp,verify-otp,reset}` | P | phone supplied by caller; OTP state is verifier |
| GET | `/services`, `/services/{serviceId}/documents` | P | public catalogue |
| GET | `/service-categories/active` | P | public catalogue |
| POST | `/feedback` | P | no customer ownership (SEC-04) |
| GET/POST | `/customer-form-fields/**` | P/O | GET public; POST owner only |
| GET | `/service-form-fields/**` | P | public fields |
| POST/PUT/DELETE | `/service-form-fields/**` | O | owner only |
| POST | `/requests` | C | identity bound to JWT |
| GET | `/requests/phone/{phone}` | C | own phone check |
| GET | `/requests/{id}` | C/E/O | customer own or assigned employee/owner |
| POST | `/requests/{id}/payment` | E/O | assigned employee or owner; arbitrary financial state (SEC-06) |
| POST | `/customer/profile`, `/customer-form-responses` | C | phone overwritten/bound to JWT |
| GET | `/customer/profile/{phone}`, `/customer-form-responses/{phone}`, `/customer-form-responses/autofill/{phone}` | C | own phone check |
| POST | `/service-form-responses` | C/E/O policy; effectively C | own-request checks on each item; no field/request consistency |
| GET | `/service-form-responses/{requestId}`, `/service-form-responses/request/{requestId}` | C/E/O | request access check |
| POST | `/documents/upload` | C/E/O | request access; result restricted to assigned employee/owner |
| GET | `/documents/request/{id}`, `/documents/request/{id}/results`, `/documents/download/{documentId}` | C/E/O | request access check |
| GET/POST | `/receipts/{id}/download`, `/receipts/{taskId}/upload` | E/O | assigned employee/owner check |
| GET | `/dashboard`, `/dashboard/service-analytics` | A | no explicit role: any customer can obtain business analytics |
| GET | `/users` | O | owner only |
| GET/POST/PUT/DELETE | `/service-categories/**` | O except `/active` P | owner mutations |
| GET/POST/PUT/DELETE | `/admin/services/**` | E/O | employee receives owner administration (SEC-01) |
| GET | `/admin/requests`, `/admin/requests/{id}`, `/admin/requests/phone/{phone}`, `/admin/dashboard/stats` | E/O | no per-object check (SEC-01) |
| GET/POST/PUT | `/employees`, `/employees/{id}/performance`, `/employees/me/**`, `/employees/dashboard` | E/O (dashboard O) | employee enumeration/performance and creation exposed; `/me` self-bound |
| POST | `/employees/promote/{userId}` | E/O | employee may promote (SEC-03) |
| GET | `/employees/{id}/profile-image` | E/O | owner or own image check |
| GET/POST | `/employee/tasks`, `/employee/tasks/{employeeId}`, `/{taskId}/{accept,complete,priority}`, `/{requestId}/{self-assign,assign/{employeeId}}`, `/upload-result`, `/dashboard/stats` | E/O | only self-assign and dashboard bind identity; all other task operations lack ownership (SEC-02) |
| GET | `/health` | P | basic unauthenticated endpoint |

## Test coverage gaps

Existing tests mostly unit/mock DTO and upload checks. There are no isolated PostgreSQL integration tests for the complete matrix, IDOR cases, task/payment concurrency, real security-filter/JWT behavior, backup/restore, migrations, storage/MSG91 failures, owner bootstrap race, CORS, upload content validation, or frontend API compatibility.

## Go-live blockers

1. Correct SEC-01, SEC-02, SEC-03, SEC-04 and SEC-05, then prove them with authenticated HTTP integration tests.
2. Implement a protected, auditable payment workflow (SEC-06).
3. Add transactional/locked task and OTP state transitions and test races.
4. Replace Hibernate `update` with versioned migrations and establish tested backup/restore/rollback procedures.
5. Provide a hardened container deployment: non-root image, `.dockerignore`, health/readiness, timeout/error handling, and durable upload/receipt storage.
6. Establish a production-safe test configuration and fix the Maven wrapper so CI can run the full suite.
