CREATE TABLE outbox (
 id uuid PRIMARY KEY, aggregate_id uuid NOT NULL, topic varchar(80) NOT NULL,
 payload text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 published_at timestamptz, attempts integer NOT NULL DEFAULT 0,
 next_attempt_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX outbox_pending ON outbox(next_attempt_at) WHERE published_at IS NULL;
