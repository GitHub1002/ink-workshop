create table stock_deductions (
  idempotency_key varchar(120) primary key,
  fingerprint varchar(64) not null
);
