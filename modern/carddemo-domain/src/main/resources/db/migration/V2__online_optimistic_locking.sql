-- Online (CICS) modernization: optimistic-lock versions for the records the online programs
-- READ ... UPDATE and REWRITE (COACTUPC ACCTDAT/CUSTDAT, COCRDUPC CARDDAT, COBIL00C ACCTDAT).
-- app_user.password_hash (V1) holds the BCrypt hash; the clear-text USRSEC password is never stored.
ALTER TABLE account  ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE customer ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE card     ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
