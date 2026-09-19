-- Salary history: one row per salary change. Current salary is derived (latest
-- effective_date on or before today), never stored.
CREATE TABLE salary_record (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    employee_id    BIGINT        NOT NULL,
    amount         DECIMAL(15,2) NOT NULL,
    currency       CHAR(3)       NOT NULL,
    effective_date DATE          NOT NULL,
    -- UTC, set by the application. DATETIME rather than TIMESTAMP: no 2038 limit and no
    -- session time-zone conversion.
    created_at     DATETIME(6)   NOT NULL,
    CONSTRAINT pk_salary_record PRIMARY KEY (id),
    -- One salary per employee per date. Its leading employee_id column also serves the
    -- foreign key, history (ORDER BY effective_date DESC) and current-salary lookups,
    -- so no separate index is needed.
    CONSTRAINT uk_salary_record_employee_date UNIQUE (employee_id, effective_date),
    CONSTRAINT fk_salary_record_employee FOREIGN KEY (employee_id) REFERENCES employee (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_salary_record_amount CHECK (amount > 0),
    -- ISO 4217 format; 'c' makes the match case-sensitive despite the _ci collation.
    CONSTRAINT ck_salary_record_currency CHECK (REGEXP_LIKE(currency, '^[A-Z]{3}$', 'c'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
