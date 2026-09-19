CREATE TABLE processed_event(event_id uuid PRIMARY KEY,processed_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE notification(payment_id uuid PRIMARY KEY,state varchar(30) NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE analytics_counter(name varchar(80) PRIMARY KEY,value bigint NOT NULL DEFAULT 0);
