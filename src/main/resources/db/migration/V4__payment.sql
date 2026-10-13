alter table users add column balance_cents integer not null default 50000;

alter table orders add column payment_key varchar(80);
