CREATE TABLE IF NOT EXISTS test_entity (
  uuid          VARCHAR(255) NOT NULL PRIMARY KEY,
  created_date  TIMESTAMP WITH TIME ZONE NOT NULL,
  last_updated  TIMESTAMP WITH TIME ZONE NOT NULL,
  name          VARCHAR(255) NOT NULL,
  email         VARCHAR(255) NOT NULL,
  status        VARCHAR(50)  NOT NULL DEFAULT 'active',
  deleted       BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_test_entity_email
  ON test_entity (email);
