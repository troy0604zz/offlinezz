CREATE TABLE data_domain (
    code VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE domain_data_source (
    domain_code VARCHAR(100) PRIMARY KEY,
    jdbc_url VARCHAR(1000) NOT NULL,
    username VARCHAR(200),
    password_cipher VARCHAR(4000),
    driver_class VARCHAR(300) NOT NULL DEFAULT 'oracle.jdbc.OracleDriver',
    validation_query VARCHAR(500) NOT NULL DEFAULT 'SELECT 1 FROM DUAL',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_domain_datasource_domain FOREIGN KEY (domain_code) REFERENCES data_domain(code)
);

CREATE TABLE domain_member (
    domain_code VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    can_query BOOLEAN NOT NULL DEFAULT FALSE,
    can_report BOOLEAN NOT NULL DEFAULT FALSE,
    can_train BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (domain_code, user_id),
    CONSTRAINT fk_domain_member_domain FOREIGN KEY (domain_code) REFERENCES data_domain(code),
    CONSTRAINT fk_domain_member_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

INSERT INTO data_domain(code,name,description,status,created_by)
VALUES ('sales','销售数据域','系统内置销售与晶圆代工 360 演示数据域','ACTIVE','system');

INSERT INTO domain_data_source(domain_code,jdbc_url,driver_class,validation_query)
VALUES ('sales','PLATFORM','oracle.jdbc.OracleDriver','SELECT 1 FROM DUAL');

INSERT INTO domain_member(domain_code,user_id,can_query,can_report,can_train)
SELECT 'sales',u.id,
       CASE WHEN SUM(CASE WHEN p.code='DATA_QUERY' THEN 1 ELSE 0 END)>0 THEN TRUE ELSE FALSE END,
       CASE WHEN SUM(CASE WHEN p.code='SMART_REPORT' THEN 1 ELSE 0 END)>0 THEN TRUE ELSE FALSE END,
       CASE WHEN SUM(CASE WHEN p.code='AI_TRAINING' THEN 1 ELSE 0 END)>0 THEN TRUE ELSE FALSE END
FROM app_user u
JOIN app_user_role ur ON ur.user_id=u.id
JOIN app_role_permission rp ON rp.role_id=ur.role_id
JOIN app_permission p ON p.id=rp.permission_id
GROUP BY u.id;

ALTER TABLE semantic_metric ADD COLUMN IF NOT EXISTS domain VARCHAR(100) NOT NULL DEFAULT 'sales';
ALTER TABLE semantic_relation ADD COLUMN IF NOT EXISTS domain VARCHAR(100) NOT NULL DEFAULT 'sales';
ALTER TABLE query_run ADD COLUMN IF NOT EXISTS domain VARCHAR(100) NOT NULL DEFAULT 'sales';
ALTER TABLE query_run ADD COLUMN IF NOT EXISTS created_by VARCHAR(100) NOT NULL DEFAULT 'system';
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS domain VARCHAR(100) NOT NULL DEFAULT 'sales';
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS created_by VARCHAR(100) NOT NULL DEFAULT 'system';
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS error_message VARCHAR(2000);

CREATE INDEX idx_semantic_metric_domain ON semantic_metric(domain);
CREATE INDEX idx_semantic_relation_domain ON semantic_relation(domain);
CREATE INDEX idx_query_run_domain_user ON query_run(domain,created_by);
CREATE INDEX idx_report_job_domain_user ON report_job(domain,created_by);
CREATE INDEX idx_schema_asset_domain ON schema_asset(domain);
CREATE INDEX idx_sql_example_domain ON sql_example(domain);
CREATE INDEX idx_golden_question_domain ON golden_question(domain);

