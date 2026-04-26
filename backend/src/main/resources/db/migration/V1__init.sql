-- Flyway V1: baseline schema for IbizDrive backend.
-- Includes (a) Spring Session JDBC tables (managed here, not by Spring),
-- and (b) a minimal users stub so A1 (auth) has something to bind against.
-- Real domain tables (folders/files/permissions/audit_log) land in V2+.

-- =====================================================================
-- A) Spring Session JDBC schema
--    Source: org/springframework/session/jdbc/schema-postgresql.sql
--    (Spring Session 3.3.x). Copied here so Flyway owns the lifecycle —
--    application.yml sets `initialize-schema: never`.
-- =====================================================================

CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            CHAR(36) NOT NULL,
    SESSION_ID            CHAR(36) NOT NULL,
    CREATION_TIME         BIGINT   NOT NULL,
    LAST_ACCESS_TIME      BIGINT   NOT NULL,
    MAX_INACTIVE_INTERVAL INT      NOT NULL,
    EXPIRY_TIME           BIGINT   NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX        SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX        SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BYTEA        NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);

-- =====================================================================
-- B) Users stub for A1 auth wiring.
--    Final schema (with role / permission membership / SSO) arrives with
--    docs/03 §2 and §3. This is the minimum to compile & start.
-- =====================================================================

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100),  -- nullable: SSO users have no local password
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);

CREATE UNIQUE INDEX users_email_unique
    ON users (lower(email))
    WHERE deleted_at IS NULL;
