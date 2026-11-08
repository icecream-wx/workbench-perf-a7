-- 成员基础信息表：兼容 H2（MODE=MySQL）与 MySQL 8.x
CREATE TABLE IF NOT EXISTS member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_no VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    gender VARCHAR(16),
    phone VARCHAR(20),
    email VARCHAR(128),
    department VARCHAR(64),
    position VARCHAR(64),
    join_date DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_member PRIMARY KEY (id),
    CONSTRAINT uk_member_member_no UNIQUE (member_no)
);
