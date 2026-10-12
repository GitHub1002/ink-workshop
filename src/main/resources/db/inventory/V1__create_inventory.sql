create table inventory (
  product_id bigint primary key,
  available integer not null check (available >= 0)
);

create table stock_deductions (
  idempotency_key varchar(120) primary key,
  fingerprint varchar(64) not null
);

insert into inventory (product_id, available) values
  (1, 10),
  (2, 10),
  (3, 10),
  (4, 10);
