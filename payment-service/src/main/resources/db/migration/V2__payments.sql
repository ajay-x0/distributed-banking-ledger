CREATE TABLE payment(
 id uuid PRIMARY KEY,owner varchar(100) NOT NULL,idempotency_key varchar(100) NOT NULL,
 source uuid NOT NULL,destination uuid NOT NULL,amount_minor bigint NOT NULL CHECK(amount_minor>0),currency varchar(3) NOT NULL CHECK(currency='INR'),
 state varchar(30) NOT NULL DEFAULT 'NEW' CHECK(state IN ('NEW','RESERVED','APPROVED','RELEASING','COMPLETED','REJECTED','MANUAL_REVIEW')),
 resume_state varchar(30),reason varchar(150),attempts integer NOT NULL DEFAULT 0,
 next_attempt_at timestamptz NOT NULL DEFAULT now(),created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(owner,idempotency_key),CHECK(source<>destination)
);
CREATE INDEX payment_work ON payment(next_attempt_at) WHERE state IN ('NEW','RESERVED','APPROVED','RELEASING');
CREATE TABLE payment_audit(id bigserial PRIMARY KEY,payment_id uuid NOT NULL REFERENCES payment(id),actor varchar(100) NOT NULL,action varchar(100) NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
