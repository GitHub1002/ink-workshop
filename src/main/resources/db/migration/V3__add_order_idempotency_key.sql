alter table orders add column idempotency_key varchar(80);
alter table orders add column line_fingerprint varchar(64);

create unique index orders_user_idempotency_idx on orders (user_id, idempotency_key);
