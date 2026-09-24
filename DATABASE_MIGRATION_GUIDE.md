# Database Migration & Deployment Architecture Guide (Phase 5A)

## Overview
As part of Phase 5A security and production hardening, database schema management has been transitioned from automatic Hibernate schema mutation (`ddl-auto=update`) to deterministic, version-controlled database migrations managed by **Flyway**.

---

## 1. Migration Framework & Dependencies
- **Tool**: Flyway Community Edition with PostgreSQL database support (`org.flywaydb:flyway-core` & `org.flywaydb:flyway-database-postgresql`).
- **Dependency Management**: Versions are centrally managed via Spring Boot dependency management (`11.7.2`). No rogue or superfluous third-party libraries were added.
- **Runtime Compatibility**: Tested and verified on Java 17/25 and PostgreSQL 18.6.

---

## 2. Migration Location & Structure
All migration scripts reside in the standard Flyway classpath directory:
```
src/main/resources/db/migration/
```

- **Naming Convention**: `V<version>__<description>.sql` (e.g., `V1__initial_schema.sql`, `V2__add_new_audit_field.sql`).
- **Initial Baseline Script**: `V1__initial_schema.sql` defines the comprehensive schema of the application, including:
  - 19 tables: `services`, `service_categories`, `category_services`, `customer_form_fields`, `customer_form_responses`, `customer_profiles`, `employees`, `customer_requests`, `tasks`, `receipts`, `required_documents`, `service_form_fields`, `service_form_responses`, `feedback`, `login_attempts`, `otp_verification`, `payment_audit_logs`, `uploaded_documents`, `users`.
  - Security constraints:
    - Single OWNER partial unique index: `uk_users_single_owner` ON `users (role) WHERE (role = 'OWNER')`.
    - Optimistic locking version column: `customer_requests.version` (BIGINT NOT NULL DEFAULT 0).
    - Append-only audit table: `payment_audit_logs` with index `idx_pal_request_id`.
    - Feedback per request uniqueness: `uk_feedback_request_id` ON `feedback (request_id)`.
    - Check constraints for roles, statuses, and OTP purposes.
    - Foreign keys and unique constraints across all related entities.

---

## 3. Baseline Strategy & Existing Database Adoption
The local and production environments already possess an existing database (`eservice1`) with active data and existing schema objects. To prevent destructive re-creation while guaranteeing deterministic migration execution:

- **Flyway Configuration**:
  ```properties
  spring.flyway.enabled=true
  spring.flyway.baseline-on-migrate=true
  spring.flyway.baseline-version=1
  spring.flyway.baseline-description=Initial baseline
  ```
- **How Existing Databases Are Adopted**:
  When Flyway connects to an existing database containing schema objects but lacking a `flyway_schema_history` table:
  1. `baseline-on-migrate=true` instructs Flyway to create `flyway_schema_history`.
  2. A baseline record at version `1` (`Initial baseline`) is recorded with type `BASELINE` and state `SUCCESS`.
  3. Flyway treats `V1__initial_schema.sql` as already applied, avoiding redundant and conflicting DDL execution against already-existing tables.
  4. Subsequent migrations (`V2__...`, `V3__...`) are applied on top of the baselined schema.

---

## 4. Fresh Database Initialization
When setting up a new environment (e.g., new deployment, fresh developer workstation, CI/CD pipeline):
1. An empty database or schema is provisioned.
2. Flyway detects an empty schema.
3. Because the schema is empty, `baseline-on-migrate` is skipped automatically.
4. Flyway executes `V1__initial_schema.sql` in its entirety, deterministically creating all 19 tables, sequences, foreign keys, unique constraints, and partial indexes.
5. All future migrations (`V2`, etc.) execute in ascending order.
6. `flyway_schema_history` records `V1` as type `SQL` and state `SUCCESS`.

---

## 5. Hibernate DDL-Auto Configuration
Automatic schema modification by Hibernate has been eliminated in production and default configurations:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

- **Behavior**:
  - `validate` verifies that JPA entities conform exactly to the underlying schema objects created by Flyway migrations (column names, types, nullability).
  - Hibernate will **never** execute `CREATE TABLE`, `ALTER TABLE`, or `DROP` statements at runtime.
  - If a mismatch exists between the code entities and migration schema, the application will fail fast on startup with a detailed validation error rather than mutating the database.

### Environment Profile Separation
- **`application.properties` (Default / Local)**:
  - `spring.jpa.hibernate.ddl-auto=validate`
  - `spring.flyway.enabled=true`
  - `spring.flyway.baseline-on-migrate=true`
  - `spring.flyway.baseline-version=1`
- **`application-prod.properties` (Production - `spring.profiles.active=prod`)**:
  - `spring.jpa.hibernate.ddl-auto=validate`
  - `spring.flyway.enabled=true`
  - `spring.flyway.baseline-on-migrate=true`
  - `spring.flyway.baseline-version=1`
- **`src/test/resources/application.properties` (Test - `spring.profiles.active=test`)**:
  - `spring.jpa.hibernate.ddl-auto=validate`
  - `spring.flyway.enabled=true`
  - `spring.flyway.baseline-on-migrate=true`
  - `spring.flyway.baseline-version=1`

---

## 6. Guidelines for Adding Future Migrations
When making future schema changes:
1. **Never edit an existing migration file**: Once a migration has run or been deployed, its checksum is validated. Editing it will cause checksum validation failures.
2. **Create a new versioned migration**:
   - File path: `src/main/resources/db/migration/V<NextInteger>__<descriptive_name>.sql`
   - Example: `src/main/resources/db/migration/V2__add_index_customer_phone.sql`
3. **Safety rules**:
   - Do NOT use destructive statements (`DROP TABLE`, `DROP COLUMN`) without explicit stakeholder review and data archival.
   - Use non-blocking DDL where applicable for high-availability production databases.
   - Update corresponding JPA entity classes to match the schema changes.
   - Always run the full regression test suite (`.\mvnw.cmd test -Dspring.profiles.active=test`) to verify both migration execution and Hibernate schema validation.
