ALTER TABLE semantic_metric ADD COLUMN IF NOT EXISTS business_code VARCHAR(100);
UPDATE semantic_metric SET business_code=code WHERE business_code IS NULL;
ALTER TABLE semantic_metric ALTER COLUMN business_code SET NOT NULL;
CREATE UNIQUE INDEX uq_metric_domain_business_code ON semantic_metric(domain,business_code);

