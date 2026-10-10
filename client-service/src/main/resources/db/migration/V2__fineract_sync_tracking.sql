-- Track what still has to reach Fineract, and why the last attempt failed.
alter table clients add column fineract_sync_pending boolean not null default false;
alter table clients add column fineract_sync_error varchar(500);

-- Fineract refuses two clients with the same mobile number, so we refuse it
-- up front instead of leaving the second client stuck waiting for Fineract.
-- This fails if existing clients already share a number; fix those rows first.
alter table clients add constraint uk_clients_phone_number unique (phone_number);
