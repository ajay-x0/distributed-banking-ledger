CREATE TABLE wallet(
 id uuid PRIMARY KEY,owner varchar(100) NOT NULL,currency varchar(3) NOT NULL CHECK(currency='INR'),
 balance_minor bigint NOT NULL CHECK(balance_minor>=0),reserved_minor bigint NOT NULL DEFAULT 0 CHECK(reserved_minor>=0 AND reserved_minor<=balance_minor),version bigint NOT NULL DEFAULT 0
);
CREATE TABLE ledger_operation(
 id uuid PRIMARY KEY,owner varchar(100) NOT NULL,source uuid NOT NULL REFERENCES wallet(id),destination uuid NOT NULL REFERENCES wallet(id),
 amount_minor bigint NOT NULL CHECK(amount_minor>0),currency varchar(3) NOT NULL CHECK(currency='INR'),
 state varchar(20) NOT NULL CHECK(state IN ('RESERVED','POSTED','RELEASED','REJECTED')),
 created_at timestamptz NOT NULL DEFAULT now(),CHECK(source<>destination)
);
CREATE TABLE journal(
 id uuid PRIMARY KEY,operation_id uuid UNIQUE REFERENCES ledger_operation(id),kind varchar(20) NOT NULL CHECK(kind IN ('TRANSFER','OPENING')),
 currency varchar(3) NOT NULL CHECK(currency='INR'),created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE journal_entry(
 journal_id uuid NOT NULL REFERENCES journal(id),account_id uuid NOT NULL,
 delta_minor bigint NOT NULL CHECK(delta_minor<>0),PRIMARY KEY(journal_id,account_id)
);
-- Deferred: debit and credit can be inserted individually, but must balance at COMMIT.
CREATE FUNCTION balanced_journal() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE jid uuid; cnt integer; total numeric;
BEGIN
 IF TG_TABLE_NAME='journal' THEN jid:=NEW.id; ELSE jid:=NEW.journal_id; END IF;
 SELECT count(*),coalesce(sum(delta_minor),0) INTO cnt,total FROM journal_entry WHERE journal_id=jid;
 IF cnt<>2 OR total<>0 THEN RAISE EXCEPTION 'Journal must have two balanced entries'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER journal_balance AFTER INSERT ON journal DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION balanced_journal();
CREATE CONSTRAINT TRIGGER entry_balance AFTER INSERT ON journal_entry DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION balanced_journal();
CREATE FUNCTION immutable_journal() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Journal is append-only'; END $$;
CREATE TRIGGER immutable_journal BEFORE UPDATE OR DELETE ON journal FOR EACH ROW EXECUTE FUNCTION immutable_journal();
CREATE TRIGGER immutable_entry BEFORE UPDATE OR DELETE ON journal_entry FOR EACH ROW EXECUTE FUNCTION immutable_journal();
-- Clearly labelled simulated opening capital, offset against the demo funding account.
INSERT INTO wallet(id,owner,currency,balance_minor) VALUES
 ('00000000-0000-0000-0000-000000000001','alice','INR',10000000),
 ('00000000-0000-0000-0000-000000000002','bob','INR',10000000),
 ('00000000-0000-0000-0000-000000000003','charlie','INR',10000000);
INSERT INTO journal(id,kind,currency) VALUES
 ('10000000-0000-0000-0000-000000000001','OPENING','INR'),
 ('10000000-0000-0000-0000-000000000002','OPENING','INR'),
 ('10000000-0000-0000-0000-000000000003','OPENING','INR');
INSERT INTO journal_entry VALUES
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001',10000000),
 ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000000',-10000000),
 ('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000002',10000000),
 ('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000000',-10000000),
 ('10000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000003',10000000),
 ('10000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000000',-10000000);
