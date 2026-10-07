CREATE TABLE accounts (username varchar(60) PRIMARY KEY, password varchar(100) NOT NULL, role varchar(10) NOT NULL CHECK(role IN ('OWNER','TECH')));
CREATE TABLE jobs (id uuid PRIMARY KEY, customer varchar(160) NOT NULL, location varchar(300) NOT NULL, invoice varchar(100) NOT NULL, technician varchar(60) NOT NULL REFERENCES accounts(username), status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','SUBMITTED','CHANGES_REQUESTED','APPROVED')), reason varchar(1000) NOT NULL DEFAULT '', revision int NOT NULL DEFAULT 1, version int NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE units (id uuid PRIMARY KEY, job_id uuid NOT NULL REFERENCES jobs(id), label varchar(100) NOT NULL, cleaned boolean NOT NULL DEFAULT false, drain_checked boolean NOT NULL DEFAULT false, cooling_checked boolean NOT NULL DEFAULT false, actions varchar(2000) NOT NULL DEFAULT '');
CREATE TABLE photos (id uuid PRIMARY KEY, unit_id uuid NOT NULL REFERENCES units(id), kind varchar(6) NOT NULL CHECK(kind IN ('before','after')), request_key uuid NOT NULL UNIQUE, source_hash varchar(64) NOT NULL, bytes bytea NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
ALTER TABLE units ADD COLUMN before_id uuid REFERENCES photos(id), ADD COLUMN after_id uuid REFERENCES photos(id);
CREATE TABLE reports (job_id uuid NOT NULL REFERENCES jobs(id), revision int NOT NULL, snapshot jsonb NOT NULL, approved_by varchar(60) NOT NULL REFERENCES accounts(username), approved_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(job_id,revision));
CREATE TABLE events (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, job_id uuid NOT NULL REFERENCES jobs(id), actor varchar(60) NOT NULL, action varchar(30) NOT NULL, detail varchar(1000) NOT NULL DEFAULT '', created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX jobs_technician_idx ON jobs(technician);
CREATE INDEX units_job_idx ON units(job_id);
-- Approved snapshots are append-only even if an application bug attempts a mutation.
CREATE FUNCTION immutable_report() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Approved reports are immutable'; END $$;
CREATE TRIGGER reports_immutable BEFORE UPDATE OR DELETE ON reports FOR EACH ROW EXECUTE FUNCTION immutable_report();
