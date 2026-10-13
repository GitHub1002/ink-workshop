create table stock_releases (
  idempotency_key varchar(120) primary key,
  fingerprint varchar(64) not null,
  order_id bigint not null
);
