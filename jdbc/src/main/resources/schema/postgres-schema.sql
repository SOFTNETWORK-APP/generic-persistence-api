CREATE TABLE IF NOT EXISTS public.journal (
  ordering BIGSERIAL,
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  deleted BOOLEAN DEFAULT FALSE,
  tags VARCHAR(255) DEFAULT NULL,
  message BYTEA NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);

CREATE UNIQUE INDEX IF NOT EXISTS journal_ordering_idx ON public.journal(ordering);

CREATE TABLE IF NOT EXISTS public.snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,
  snapshot BYTEA NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);

CREATE TABLE IF NOT EXISTS public.event_processor_offsets (
  event_processor_id VARCHAR(255) NOT NULL,
  tag VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  PRIMARY KEY(event_processor_id, tag)
);
