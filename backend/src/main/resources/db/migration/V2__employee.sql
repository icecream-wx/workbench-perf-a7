-- 员工基础信息表：兼容 H2（MODE=MySQL）与 MySQL 8.x
CREATE TABLE IF NOT EXISTS employee (
    id BIGINT NOT NULL AUTO_INCREMENT,
    employee_no VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    gender VARCHAR(16),
    phone VARCHAR(32),
    email VARCHAR(128),
    department VARCHAR(64),
    position VARCHAR(64),
    hire_date DATE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_employee PRIMARY KEY (id),
    CONSTRAINT uk_employee_employee_no UNIQUE (employee_no)
);
