CREATE TABLE account_profile(id uuid PRIMARY KEY,owner varchar(100) NOT NULL UNIQUE,display_name varchar(100) NOT NULL,version bigint NOT NULL DEFAULT 0);
INSERT INTO account_profile(id,owner,display_name) VALUES
 ('00000000-0000-0000-0000-000000000001','alice','Alice'),
 ('00000000-0000-0000-0000-000000000002','bob','Bob'),
 ('00000000-0000-0000-0000-000000000003','charlie','Charlie');
