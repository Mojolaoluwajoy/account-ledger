-- Optimistic locking: every save bumps the version, and a save based on an
-- older version is refused.
alter table clients add column version bigint not null default 0;
