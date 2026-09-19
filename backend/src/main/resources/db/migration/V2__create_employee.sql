-- Employees are read-only in v1; data comes from the dev seed (not from migrations).
CREATE TABLE employee (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    employee_code     VARCHAR(20)  NOT NULL,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    job_title         VARCHAR(100) NOT NULL,
    department        VARCHAR(100) NOT NULL,
    country_code      CHAR(2)      NOT NULL,
    employment_status VARCHAR(20)  NOT NULL,
    hire_date         DATE         NOT NULL,
    CONSTRAINT pk_employee PRIMARY KEY (id),
    CONSTRAINT uk_employee_code UNIQUE (employee_code),
    CONSTRAINT uk_employee_email UNIQUE (email),
    -- ISO 3166-1 alpha-2 format; 'c' makes the match case-sensitive despite the _ci collation.
    CONSTRAINT ck_employee_country_code CHECK (REGEXP_LIKE(country_code, '^[A-Z]{2}$', 'c')),
    CONSTRAINT ck_employee_status CHECK (employment_status IN ('ACTIVE', 'ON_LEAVE', 'TERMINATED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Default list order (last_name, first_name, id): a page is read in index order instead of
-- sorting every row. InnoDB appends the primary key, so id is the final tie-breaker.
CREATE INDEX idx_employee_name ON employee (last_name, first_name);

-- Country and department filters, returned in the same order without a separate sort.
CREATE INDEX idx_employee_country_name ON employee (country_code, last_name, first_name);
CREATE INDEX idx_employee_department_name ON employee (department, last_name, first_name);

-- No index on employment_status: three values with ~90% ACTIVE is too unselective for
-- MySQL to use one. See docs/database-design.md.
