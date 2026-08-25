CREATE TABLE data_domain (
    code VARCHAR2(100 CHAR) PRIMARY KEY,
    name VARCHAR2(200 CHAR) NOT NULL,
    description VARCHAR2(2000 CHAR),
    status VARCHAR2(30 CHAR) DEFAULT 'ACTIVE' NOT NULL,
    created_by VARCHAR2(100 CHAR) NOT NULL,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE domain_data_source (
    domain_code VARCHAR2(100 CHAR) PRIMARY KEY,
    jdbc_url VARCHAR2(1000 CHAR) NOT NULL,
    username VARCHAR2(200 CHAR),
    password_cipher VARCHAR2(4000 CHAR),
    driver_class VARCHAR2(300 CHAR) DEFAULT 'oracle.jdbc.OracleDriver' NOT NULL,
    validation_query VARCHAR2(500 CHAR) DEFAULT 'SELECT 1 FROM DUAL' NOT NULL,
    updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_domain_datasource_domain FOREIGN KEY (domain_code) REFERENCES data_domain(code)
);

CREATE TABLE domain_member (
    domain_code VARCHAR2(100 CHAR) NOT NULL,
    user_id NUMBER NOT NULL,
    can_query NUMBER(1) DEFAULT 0 NOT NULL,
    can_report NUMBER(1) DEFAULT 0 NOT NULL,
    can_train NUMBER(1) DEFAULT 0 NOT NULL,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    PRIMARY KEY (domain_code, user_id),
    CONSTRAINT ck_domain_member_query CHECK (can_query IN (0,1)),
    CONSTRAINT ck_domain_member_report CHECK (can_report IN (0,1)),
    CONSTRAINT ck_domain_member_train CHECK (can_train IN (0,1)),
    CONSTRAINT fk_domain_member_domain FOREIGN KEY (domain_code) REFERENCES data_domain(code),
    CONSTRAINT fk_domain_member_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

INSERT INTO data_domain(code,name,description,status,created_by)
VALUES ('sales','销售数据域','系统内置销售与晶圆代工 360 演示数据域','ACTIVE','system');

INSERT INTO domain_data_source(domain_code,jdbc_url,driver_class,validation_query)
VALUES ('sales','PLATFORM','oracle.jdbc.OracleDriver','SELECT 1 FROM DUAL');

INSERT INTO domain_member(domain_code,user_id,can_query,can_report,can_train)
SELECT 'sales',u.id,
       MAX(CASE WHEN p.code='DATA_QUERY' THEN 1 ELSE 0 END),
       MAX(CASE WHEN p.code='SMART_REPORT' THEN 1 ELSE 0 END),
       MAX(CASE WHEN p.code='AI_TRAINING' THEN 1 ELSE 0 END)
FROM app_user u
JOIN app_user_role ur ON ur.user_id=u.id
JOIN app_role_permission rp ON rp.role_id=ur.role_id
JOIN app_permission p ON p.id=rp.permission_id
GROUP BY u.id;

ALTER TABLE semantic_metric ADD (domain VARCHAR2(100 CHAR) DEFAULT 'sales' NOT NULL);
ALTER TABLE semantic_relation ADD (domain VARCHAR2(100 CHAR) DEFAULT 'sales' NOT NULL);
ALTER TABLE query_run ADD (domain VARCHAR2(100 CHAR) DEFAULT 'sales' NOT NULL);
ALTER TABLE query_run ADD (created_by VARCHAR2(100 CHAR) DEFAULT 'system' NOT NULL);
ALTER TABLE report_job ADD (domain VARCHAR2(100 CHAR) DEFAULT 'sales' NOT NULL);
ALTER TABLE report_job ADD (created_by VARCHAR2(100 CHAR) DEFAULT 'system' NOT NULL);
ALTER TABLE report_job ADD (error_message VARCHAR2(2000 CHAR));

CREATE INDEX idx_semantic_metric_domain ON semantic_metric(domain);
CREATE INDEX idx_semantic_relation_domain ON semantic_relation(domain);
CREATE INDEX idx_query_run_domain_user ON query_run(domain,created_by);
CREATE INDEX idx_report_job_domain_user ON report_job(domain,created_by);
CREATE INDEX idx_schema_asset_domain ON schema_asset(domain);
CREATE INDEX idx_sql_example_domain ON sql_example(domain);
CREATE INDEX idx_golden_question_domain ON golden_question(domain);

