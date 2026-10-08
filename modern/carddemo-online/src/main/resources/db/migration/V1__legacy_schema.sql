-- Schema derived 1:1 from the CardDemo copybooks (app/cpy). Table/column names follow the
-- copybook field names so the online and batch modules share one physical model.

-- CVACT01Y ACCOUNT-RECORD (ACCTDATA KSDS, key ACCT-ID)
CREATE TABLE account (
    acct_id                 BIGINT        PRIMARY KEY,      -- 9(11)
    acct_active_status      CHAR(1)       NOT NULL,
    acct_curr_bal           NUMERIC(12,2) NOT NULL,         -- S9(10)V99
    acct_credit_limit       NUMERIC(12,2) NOT NULL,
    acct_cash_credit_limit  NUMERIC(12,2) NOT NULL,
    acct_open_date          VARCHAR(10)   NOT NULL,         -- X(10) YYYY-MM-DD
    acct_expiraion_date     VARCHAR(10)   NOT NULL,         -- legacy spelling kept
    acct_reissue_date       VARCHAR(10)   NOT NULL,
    acct_curr_cyc_credit    NUMERIC(12,2) NOT NULL,
    acct_curr_cyc_debit     NUMERIC(12,2) NOT NULL,
    acct_addr_zip           VARCHAR(10)   NOT NULL,
    acct_group_id           VARCHAR(10)   NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0
);

-- CVCUS01Y CUSTOMER-RECORD (CUSTDATA KSDS, key CUST-ID)
CREATE TABLE customer (
    cust_id                  BIGINT      PRIMARY KEY,        -- 9(09)
    cust_first_name          VARCHAR(25) NOT NULL,
    cust_middle_name         VARCHAR(25) NOT NULL,
    cust_last_name           VARCHAR(25) NOT NULL,
    cust_addr_line_1         VARCHAR(50) NOT NULL,
    cust_addr_line_2         VARCHAR(50) NOT NULL,
    cust_addr_line_3         VARCHAR(50) NOT NULL,
    cust_addr_state_cd       VARCHAR(2)  NOT NULL,
    cust_addr_country_cd     VARCHAR(3)  NOT NULL,
    cust_addr_zip            VARCHAR(10) NOT NULL,
    cust_phone_num_1         VARCHAR(15) NOT NULL,
    cust_phone_num_2         VARCHAR(15) NOT NULL,
    cust_ssn                 BIGINT      NOT NULL,           -- 9(09)
    cust_govt_issued_id      VARCHAR(20) NOT NULL,
    cust_dob_yyyy_mm_dd      VARCHAR(10) NOT NULL,
    cust_eft_account_id      VARCHAR(10) NOT NULL,
    cust_pri_card_holder_ind CHAR(1)     NOT NULL,
    cust_fico_credit_score   INTEGER     NOT NULL,           -- 9(03)
    version                  BIGINT      NOT NULL DEFAULT 0
);

-- CVACT02Y CARD-RECORD (CARDDATA KSDS, key CARD-NUM, AIX CARD-ACCT-ID)
CREATE TABLE card (
    card_num             VARCHAR(16) PRIMARY KEY,
    card_acct_id         BIGINT      NOT NULL,
    card_cvv_cd          INTEGER     NOT NULL,
    card_embossed_name   VARCHAR(50) NOT NULL,
    card_expiraion_date  VARCHAR(10) NOT NULL,
    card_active_status   CHAR(1)     NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX card_acct_aix ON card (card_acct_id);

-- CVACT03Y CARD-XREF-RECORD (CARDXREF KSDS, key XREF-CARD-NUM, AIX XREF-ACCT-ID = CXACAIX)
CREATE TABLE card_xref (
    xref_card_num  VARCHAR(16) PRIMARY KEY,
    xref_cust_id   BIGINT      NOT NULL,
    xref_acct_id   BIGINT      NOT NULL
);
CREATE INDEX card_xref_acct_aix ON card_xref (xref_acct_id);

-- CVTRA05Y TRAN-RECORD (TRANSACT KSDS, key TRAN-ID)
CREATE TABLE transaction (
    tran_id             VARCHAR(16)   PRIMARY KEY,
    tran_type_cd        VARCHAR(2)    NOT NULL,
    tran_cat_cd         INTEGER       NOT NULL,              -- 9(04)
    tran_source         VARCHAR(10)   NOT NULL,
    tran_desc           VARCHAR(100)  NOT NULL,
    tran_amt            NUMERIC(11,2) NOT NULL,              -- S9(09)V99
    tran_merchant_id    BIGINT        NOT NULL,              -- 9(09)
    tran_merchant_name  VARCHAR(50)   NOT NULL,
    tran_merchant_city  VARCHAR(50)   NOT NULL,
    tran_merchant_zip   VARCHAR(10)   NOT NULL,
    tran_card_num       VARCHAR(16)   NOT NULL,
    tran_orig_ts        VARCHAR(26)   NOT NULL,
    tran_proc_ts        VARCHAR(26)   NOT NULL
);
CREATE INDEX transaction_card_idx ON transaction (tran_card_num);

-- CSUSR01Y SEC-USER-DATA (USRSEC KSDS, key SEC-USR-ID). Password is stored as a BCrypt hash
-- (ONL-SEC-02 tech debt remediated); the legacy clear-text value never reaches this table.
CREATE TABLE user_security (
    sec_usr_id        VARCHAR(8)   PRIMARY KEY,
    sec_usr_fname     VARCHAR(20)  NOT NULL,
    sec_usr_lname     VARCHAR(20)  NOT NULL,
    sec_usr_pwd_hash  VARCHAR(100) NOT NULL,
    sec_usr_type      CHAR(1)      NOT NULL
);

-- CVTRA03Y TRAN-TYPE-RECORD
CREATE TABLE tran_type (
    tran_type       VARCHAR(2)  PRIMARY KEY,
    tran_type_desc  VARCHAR(50) NOT NULL
);

-- CVTRA04Y TRAN-CAT-RECORD
CREATE TABLE tran_category (
    tran_type_cd        VARCHAR(2)  NOT NULL,
    tran_cat_cd         INTEGER     NOT NULL,
    tran_cat_type_desc  VARCHAR(50) NOT NULL,
    PRIMARY KEY (tran_type_cd, tran_cat_cd)
);

-- CVTRA01Y TRAN-CAT-BAL-RECORD (TCATBALF)
CREATE TABLE tran_cat_balance (
    trancat_acct_id  BIGINT        NOT NULL,
    trancat_type_cd  VARCHAR(2)    NOT NULL,
    trancat_cd       INTEGER       NOT NULL,
    tran_cat_bal     NUMERIC(11,2) NOT NULL,
    PRIMARY KEY (trancat_acct_id, trancat_type_cd, trancat_cd)
);

-- CVTRA02Y DIS-GROUP-RECORD (DISCGRP)
CREATE TABLE disclosure_group (
    dis_acct_group_id  VARCHAR(10)  NOT NULL,
    dis_tran_type_cd   VARCHAR(2)   NOT NULL,
    dis_tran_cat_cd    INTEGER      NOT NULL,
    dis_int_rate       NUMERIC(6,2) NOT NULL,
    PRIMARY KEY (dis_acct_group_id, dis_tran_type_cd, dis_tran_cat_cd)
);
