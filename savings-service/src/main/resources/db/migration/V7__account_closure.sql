-- When a savings account was closed. CLOSED was already an allowed status.
alter table savings_accounts add column closed_at timestamp(6);
