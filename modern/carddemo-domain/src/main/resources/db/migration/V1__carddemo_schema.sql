-- CardDemo core schema, derived from the VSAM KSDS copybooks.
-- Portable SQL only: runs unchanged on PostgreSQL 14+, AlloyDB, Cloud SQL for PostgreSQL
-- and H2 in PostgreSQL compatibility mode.

CREATE TABLE account (
    account_id           BIGINT        NOT NULL,
    active_status        VARCHAR(1),
    current_balance      NUMERIC(12,2) NOT NULL,
    credit_limit         NUMERIC(12,2) NOT NULL,
    cash_credit_limit    NUMERIC(12,2) NOT NULL,
    open_date            DATE,
    expiration_date      DATE,
    reissue_date         DATE,
    current_cycle_credit NUMERIC(12,2) NOT NULL,
    current_cycle_debit  NUMERIC(12,2) NOT NULL,
    address_zip          VARCHAR(10),
    group_id             VARCHAR(10),
    CONSTRAINT pk_account PRIMARY KEY (account_id)
);

CREATE TABLE customer (
    customer_id          BIGINT       NOT NULL,
    first_name           VARCHAR(25),
    middle_name          VARCHAR(25),
    last_name            VARCHAR(25),
    address_line_1       VARCHAR(50),
    address_line_2       VARCHAR(50),
    address_line_3       VARCHAR(50),
    state_code           VARCHAR(2),
    country_code         VARCHAR(3),
    zip                  VARCHAR(10),
    phone_number_1       VARCHAR(15),
    phone_number_2       VARCHAR(15),
    ssn                  VARCHAR(9),
    government_issued_id VARCHAR(20),
    date_of_birth        DATE,
    eft_account_id       VARCHAR(10),
    primary_card_holder  VARCHAR(1),
    fico_credit_score    INTEGER,
    CONSTRAINT pk_customer PRIMARY KEY (customer_id)
);

CREATE TABLE card (
    card_number     VARCHAR(16) NOT NULL,
    account_id      BIGINT      NOT NULL,
    cvv_code        INTEGER     NOT NULL,
    embossed_name   VARCHAR(50),
    expiration_date DATE,
    active_status   VARCHAR(1),
    CONSTRAINT pk_card PRIMARY KEY (card_number)
);
CREATE INDEX ix_card_account ON card (account_id);

-- No FK to account/customer: the legacy files are not referentially enforced and the
-- batch stream must reproduce what happens with orphaned cross references.
CREATE TABLE card_xref (
    card_number VARCHAR(16) NOT NULL,
    customer_id BIGINT      NOT NULL,
    account_id  BIGINT      NOT NULL,
    CONSTRAINT pk_card_xref PRIMARY KEY (card_number)
);
CREATE INDEX ix_card_xref_account ON card_xref (account_id);

CREATE TABLE transaction_type (
    type_code   VARCHAR(2)  NOT NULL,
    description VARCHAR(50),
    CONSTRAINT pk_transaction_type PRIMARY KEY (type_code)
);

CREATE TABLE transaction_category (
    type_code     VARCHAR(2) NOT NULL,
    category_code INTEGER    NOT NULL,
    description   VARCHAR(50),
    CONSTRAINT pk_transaction_category PRIMARY KEY (type_code, category_code)
);

CREATE TABLE disclosure_group (
    account_group_id          VARCHAR(10)  NOT NULL,
    transaction_type_code     VARCHAR(2)   NOT NULL,
    transaction_category_code INTEGER      NOT NULL,
    interest_rate             NUMERIC(6,2) NOT NULL,
    CONSTRAINT pk_disclosure_group PRIMARY KEY (account_group_id, transaction_type_code, transaction_category_code)
);

CREATE TABLE transaction_category_balance (
    account_id    BIGINT        NOT NULL,
    type_code     VARCHAR(2)    NOT NULL,
    category_code INTEGER       NOT NULL,
    balance       NUMERIC(11,2) NOT NULL,
    CONSTRAINT pk_transaction_category_balance PRIMARY KEY (account_id, type_code, category_code)
);

CREATE TABLE card_transaction (
    transaction_id       VARCHAR(16)   NOT NULL,
    type_code            VARCHAR(2)    NOT NULL,
    category_code        INTEGER       NOT NULL,
    source               VARCHAR(10),
    description          VARCHAR(100),
    amount               NUMERIC(11,2) NOT NULL,
    merchant_id          BIGINT        NOT NULL,
    merchant_name        VARCHAR(50),
    merchant_city        VARCHAR(50),
    merchant_zip         VARCHAR(10),
    card_number          VARCHAR(16)   NOT NULL,
    originated_at        TIMESTAMP(6)  NOT NULL,
    originated_at_format VARCHAR(3)    NOT NULL,
    processed_at         TIMESTAMP(6),
    CONSTRAINT pk_card_transaction PRIMARY KEY (transaction_id)
);
CREATE INDEX ix_card_transaction_card ON card_transaction (card_number);
CREATE INDEX ix_card_transaction_processed ON card_transaction (processed_at);

CREATE TABLE app_user (
    user_id       VARCHAR(8)   NOT NULL,
    first_name    VARCHAR(20),
    last_name     VARCHAR(20),
    password_hash VARCHAR(100),
    user_type     VARCHAR(1)   NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (user_id)
);
