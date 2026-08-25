ALTER TABLE semantic_metric ADD (business_code VARCHAR2(100 CHAR));
UPDATE semantic_metric SET business_code=code WHERE business_code IS NULL;
ALTER TABLE semantic_metric MODIFY (business_code NOT NULL);
CREATE UNIQUE INDEX uq_metric_domain_business_code ON semantic_metric(domain,business_code);

