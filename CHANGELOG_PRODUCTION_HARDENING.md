# CHANGELOG: Production Hardening
Project: Nellai / Vinayaga E-Service Portal

---

## [SEC-CRIT-01] OTP Privilege Escalation & Staff Account Takeover Prevention
- **Date**: 2026-09-20
- **Status**: FIXED & VERIFIED
- **Severity**: Critical
- **Files Modified**:
  - `src/main/java/com/eservice1/config/JwtService.java`
  - `src/main/java/com/eservice1/config/JwtFilter.java`
  - `src/main/java/com/eservice1/customer/service/OtpService.java`
  - `src/main/java/com/eservice1/user/service/UserService.java`
  - `src/test/java/com/eservice1/customer/OtpPrivilegeEscalationTest.java` (New)
- **Problem**:
  Customer OTP verification (`/customer/verify-otp`) allowed phone numbers belonging to `OWNER` or `EMPLOYEE` accounts to verify via OTP and receive tokens that resolved to staff authorities in `JwtFilter`, completely bypassing password authentication.
- **Fix Details**:
  1. `OtpService.sendOtp` and `verifyOtp` now verify whether the phone number is associated with an existing staff account (`OWNER` or `EMPLOYEE`). If so, the operation is rejected with an explicit message directing staff to the staff login portal.
  2. `OtpService.verifyOtp` generates JWTs explicitly scoped with `Role.CUSTOMER`.
  3. `UserService.login` generates JWTs explicitly scoped with the user's authentic database role (`OWNER` / `EMPLOYEE`).
  4. `JwtService` now encodes the `role` claim in the JWT payload and provides `extractRole(token)`.
  5. `JwtFilter` validates that if a token carries a role claim, it must match the database user's role. A `CUSTOMER`-scoped token cannot assume staff authority.
- **Tests Added & Verified**:
  - `testCustomerOtp_Success_CustomerTokenIssued`: Verifies legitimate customer OTP generates `Role.CUSTOMER` token.
  - `testOwnerPhone_CustomerOtp_MustNotBecomeOwner`: Proves owner phone number submitted to customer OTP is rejected without issuing a token.
  - `testEmployeePhone_CustomerOtp_MustNotBecomeEmployee`: Proves employee phone number submitted to customer OTP is rejected without issuing a token.
  - `testCustomerScopedToken_CannotAssumeOwnerRoleInJwtFilter`: Proves a token with `role=CUSTOMER` for an owner phone number is blocked by `JwtFilter` and rejected with 401 Unauthorized.
  - Test result: 4 tests run, 0 failures, BUILD SUCCESS.

---

## [SEC-CRIT-02] Dynamic Form Field Public Mutation Prevention
- **Date**: 2026-09-20
- **Status**: FIXED & VERIFIED
- **Severity**: Critical
- **Files Modified**:
  - `src/main/java/com/eservice1/config/SecurityConfig.java`
  - `eservice-frontend/src/pages/owner/ServiceFieldManager.jsx`
  - `src/test/java/com/eservice1/serviceform/FormFieldSecurityTest.java` (New)
- **Problem**:
  `PUBLIC_URL_PATTERNS` included `"/customer-form-fields/**"` and `"/service-form-fields/**"`, allowing anonymous public internet users to invoke mutating operations (`POST`, `PUT`, `DELETE`), corrupting form field schemas and deleting dynamic fields.
- **Fix Details**:
  1. Removed `"/customer-form-fields/**"` and `"/service-form-fields/**"` from `PUBLIC_URL_PATTERNS`.
  2. In `SecurityConfig.java`, separated HTTP READ from WRITE operations:
     - `GET /customer-form-fields`, `/customer-form-fields/**`: `permitAll()` (publicly readable for dynamic form rendering).
     - `GET /service-form-fields`, `/service-form-fields/**`: `permitAll()` (publicly readable for service intake form rendering).
     - `POST`, `PUT`, `DELETE` on `/customer-form-fields` and `/service-form-fields`: strictly restricted to `hasAuthority("OWNER")`.
  3. Updated `ServiceFieldManager.jsx` in frontend to attach the owner's `Authorization: Bearer <token>` header to all mutating operations (`saveField`, `deleteField`).
  4. Verified that dynamic form rendering for customers (`CustomerProfileEdit.jsx`, `ServiceDocuments.jsx`) and submission endpoints (`/customer-form-responses`, `/service-form-responses`) continue to operate uninterrupted.
- **Tests Added & Verified**:
  - `testPublicGet_ServiceFormFields_Allowed`: Confirms public unauthenticated GET returns 200 OK.
  - `testPublicGet_CustomerFormFields_Allowed`: Confirms public unauthenticated GET returns 200 OK.
  - `testAnonymousPost_ServiceFormFields_ForbiddenOrUnauthorized`: Confirms unauthenticated POST is rejected with 401.
  - `testAnonymousDelete_ServiceFormFields_ForbiddenOrUnauthorized`: Confirms unauthenticated DELETE is rejected with 401.
  - `testCustomerPost_ServiceFormFields_Forbidden`: Confirms authenticated CUSTOMER is rejected with 403 Forbidden.
  - `testOwnerPost_ServiceFormFields_Allowed`: Confirms authenticated OWNER is allowed with 200 OK.
  - Frontend build: `npm run build` completed with 0 errors.
  - Test result: 6 tests run, 0 failures, BUILD SUCCESS.

---

## [SEC-CRIT-03] Path Traversal, Arbitrary File Upload & Malicious File Prevention
- **Date**: 2026-09-20
- **Status**: FIXED & VERIFIED
- **Severity**: Critical
- **Files Modified**:
  - `src/main/java/com/eservice1/employee/service/TaskService.java`
  - `src/test/java/com/eservice1/employee/FileUploadSecurityTest.java` (New)
- **Problem**:
  In `TaskService.uploadResult`, client-supplied original filenames were concatenated directly onto the local filesystem (`uploadDir + "RESULT_" + file.getOriginalFilename()`). This allowed directory traversal attacks (`../../`, `..\..\`, `/etc/passwd`), arbitrary file overwrites, and the uploading of dangerous executable scripts (`.exe`, `.jsp`) without extension, MIME type, or file size validation.
- **Fix Details**:
  1. **Safe Storage Naming**: Replaced user-controlled filename usage with a cryptographically safe, server-generated unique filename: `RESULT_<UUID>.<extension>`. User input is never used as a filesystem path.
  2. **Path Traversal Guard**: Added path traversal sequence detection (`..`, `/`, `\`) and canonical path boundary checks ensuring target files reside strictly within the intended `uploads` directory.
  3. **Extension Whitelist**: Enforced strict extension whitelist: `pdf`, `jpg`, `jpeg`, `png`, `doc`, `docx`.
  4. **MIME Type Validation**: Enforced whitelist check on `Content-Type`: `application/pdf`, `image/jpeg`, `image/png`, `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`.
  5. **File Size Limit**: Added explicit checks ensuring files are non-empty and within the 20MB limit.
  6. **Display Name Sanitization**: Stored a sanitized display name (`RESULT_<safeName>`) in the database metadata for safe UI rendering.
- **Tests Added & Verified**:
  - `testValidUpload_Success_SafeStorageNameGenerated`: Verifies legitimate upload creates a UUID-based safe storage name within `uploads/`.
  - `testMaliciousFilename_PathTraversalDotDotSlash_Rejected`: Proves `../../test.txt` is rejected with `IllegalArgumentException`.
  - `testMaliciousFilename_PathTraversalBackslash_Rejected`: Proves `..\..\test.txt` is rejected with `IllegalArgumentException`.
  - `testMaliciousFilename_EtcPasswd_Rejected`: Proves `/etc/passwd` is rejected with `IllegalArgumentException`.
  - `testMaliciousFilename_WindowsSystem32_Rejected`: Proves `C:\Windows\System32\test.txt` is rejected with `IllegalArgumentException`.
  - `testDangerousExtension_Executable_Rejected`: Proves `.exe` uploads are rejected.
  - `testDangerousExtension_JspScript_Rejected`: Proves `.jsp` uploads are rejected.
  - `testEmptyFile_Rejected`: Proves empty file uploads are rejected.
  - Test result: 8 tests run, 0 failures, BUILD SUCCESS.

---

## [SEC-CRIT-04] BCrypt Password Hash & Internal User Data Exposure Prevention
- **Date**: 2026-09-20
- **Status**: FIXED & VERIFIED
- **Severity**: Critical
- **Files Modified**:
  - `src/main/java/com/eservice1/user/entity/User.java`
  - `src/main/java/com/eservice1/user/dto/UserResponse.java` (New)
  - `src/main/java/com/eservice1/user/controller/UserController.java`
  - `src/main/java/com/eservice1/user/controller/AuthController.java`
  - `src/test/java/com/eservice1/user/UserDataExposureTest.java` (New)
- **Problem**:
  `GET /users`, `POST /auth/register`, and `POST /auth/owner` returned raw JPA `User` entities directly. Jackson serialization exposed the `password` property containing BCrypt hashes over HTTP responses to anyone accessing the endpoints.
- **Fix Details**:
  1. **DTO Decoupling**: Created a dedicated `UserResponse` DTO exposing only safe non-sensitive fields (`id`, `name`, `phoneNumber`, `role`).
  2. **Controller Hardening**: Updated `UserController.getAllUsers()`, `AuthController.register()`, and `AuthController.createOwner()` to return `UserResponse` instead of raw JPA entities.
  3. **Entity-Level Defense**: Added `@JsonProperty(access = Access.WRITE_ONLY)` to the `password` field on `User.java` so that Jackson will never serialize the password hash under any circumstances even if the entity is inadvertently serialized.
  4. **Frontend Compatibility**: Verified that the frontend `Users.jsx` table consumes `id`, `name`, `phoneNumber`, `role`, maintaining 100% compatibility with no UI disruptions.
- **Tests Added & Verified**:
  - `testGetUsers_DoesNotExposePasswordOrHash`: Proves `GET /users` returns user profiles without the `password` field and contains no BCrypt hash strings.
  - `testUserEntity_JacksonSerialization_DoesNotIncludePassword`: Proves direct Jackson serialization of `User` omits the `password` field completely.
  - Test result: 2 tests run, 0 failures, BUILD SUCCESS.

---

## [SEC-CRIT-05] Cryptographic OTP Generation, Hashed Storage & Brute-Force Hardening
- **Date**: 2026-09-20
- **Status**: FIXED & VERIFIED
- **Severity**: Critical
- **Files Modified**:
  - `src/main/java/com/eservice1/customer/service/OtpService.java`
  - `src/test/java/com/eservice1/customer/OtpSecurityHardeningTest.java` (New)
  - `src/test/java/com/eservice1/customer/OtpPrivilegeEscalationTest.java` (Updated)
- **Problem**:
  1. OTPs were generated using `ThreadLocalRandom`, which is pseudo-random and not cryptographically secure.
  2. OTPs were stored in plaintext in the database `otp_verification` table, exposing all active OTPs upon database read/dump.
  3. Plaintext OTP comparison was vulnerable to timing attacks.
  4. Expired or excessively attempted OTP records were not cleaned up, leaving stale records in the database.
- **Fix Details**:
  1. **Cryptographic Randomness**: Switched OTP generation to `java.security.SecureRandom` (`100000 + SECURE_RANDOM.nextInt(900000)`).
  2. **Hashed Storage**: OTPs are hashed using BCrypt via `passwordEncoder.encode(otp)` before being saved to the database. Plaintext OTPs are never persisted.
  3. **Timing-Safe Verification**: OTP verification uses `passwordEncoder.matches(enteredOtp, hashedOtp)`.
  4. **Strict Attempt Limiting**: Maximum 5 attempts allowed. If attempts reach 5, the verification record is deleted and the user is locked out.
  5. **Auto-Cleanup on Expiry**: If an expired OTP is presented, the record is immediately deleted from the database.
  6. **Resend & Rate Limiting**: Enforced 60-second cooldown between resends and maximum 5 OTPs per phone number per hour.
  7. **No OTP Logging**: Verified that OTP values are never written to log files or standard output.
- **Tests Added & Verified**:
  - `testCorrectOtp_Success_HashedVerification`: Verifies correct OTP matches against BCrypt hash, deletes record, and succeeds.
  - `testIncorrectOtp_FailsAndIncrementsAttempts`: Verifies wrong OTP fails and increments attempt counter.
  - `testExpiredOtp_FailsAndDeletesRecord`: Verifies expired OTP is rejected and deleted from the database.
  - `testReusedOtp_Fails`: Verifies already verified OTP cannot be reused.
  - `testExcessiveAttempts_LockedOutAndDeleted`: Verifies user is locked out and record deleted after 5 failed attempts.
  - `testResendAbuse_CooldownActive_Rejected`: Verifies resend within 60-second cooldown is rejected.
  - `testResendAbuse_HourlyLimitExceeded_Rejected`: Verifies exceeding 5 OTPs per hour is rejected.
  - `testSendOtp_UsesHashedStorage`: Proves OTP is saved in database in BCrypt hashed form, never plaintext.
---

## [VERIFICATION-PASS] Storage Architecture & Schema Hardening
- **Date**: 2026-09-20
- **Status**: FIXED & VERIFIED
- **Severity**: Critical / Architectural Integrity
- **Files Modified**:
  - `src/main/java/com/eservice1/customer/entity/OtpVerification.java`
  - `src/main/java/com/eservice1/submission/controller/UploadedDocumentController.java`
  - `src/test/java/com/eservice1/submission/UploadedDocumentControllerTest.java` (New)
- **Problem**:
  1. **OTP Column Length**: BCrypt hashes require at least 60 characters of storage. `OtpVerification.otp` lacked an explicit length definition on its `@Column` annotation, creating a risk of schema truncation or validation failures on certain database drivers.
  2. **Task Result Upload/Download Disconnect**: Task result documents uploaded via `TaskService.uploadResult` are stored in the local `uploads/` directory on disk. However, `UploadedDocumentController.downloadDocument` exclusively delegated to `SupabaseStorageService.download()`, which threw a runtime exception when passed local filesystem paths. Consequently, task results could not be downloaded by clients.
- **Fix Details**:
  1. **Explicit OTP Column Length**: Updated `@Column(nullable = false)` to `@Column(nullable = false, length = 255)` on `OtpVerification.otp` ensuring guaranteed capacity for BCrypt hashes under Hibernate schema management.
  2. **Dual-Storage Download Resolution**: Updated `UploadedDocumentController.downloadDocument`:
     - Added `resolveLocalFilePath(rawPath)`: checks if the document's file path resolves to an existing file strictly within the canonical `uploads` directory.
     - If the file exists locally on disk, safely reads file bytes directly (`Files.readAllBytes`).
     - If not on disk (e.g. Supabase object path `customer/...`), delegates to `storageService.download(filePath)`.
     - Added `sanitizeFileName(fileName)` to prevent HTTP response splitting / header injection on `Content-Disposition`.
     - Strictly guards against path traversal (`..`, absolute paths outside `uploads/`).
- **Tests Added & Verified**:
  - `testDownloadDocument_LocalFile_Success`: Confirms local files in `uploads/` are read from disk without invoking Supabase storage.
  - `testDownloadDocument_RemoteSupabaseFile_Success`: Confirms remote Supabase documents delegate to `storageService.download`.
  - `testDownloadDocument_PathTraversalAttempt_DoesNotReadArbitraryLocalFile`: Confirms path traversal attempts cannot read arbitrary local files.
  - Test result: 3 tests run, 0 failures, BUILD SUCCESS.
  - Full test suite: 32 tests run, 0 failures, 1 skipped (`Eservice1ApplicationTests`), BUILD SUCCESS.
