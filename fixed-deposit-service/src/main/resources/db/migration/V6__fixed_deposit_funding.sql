-- Fixed deposits can be paid for from a savings account. Until the money has
-- been taken the deposit is PENDING_FUNDING; if the account refuses, it is
-- FUNDING_FAILED. Deposits without a funding account are paid in cash as before.
alter table fixed_deposits drop constraint if exists fixed_deposits_status_check;
alter table fixed_deposits
    add constraint fixed_deposits_status_check
        check (status in ('ACTIVE', 'MATURED', 'CLOSED', 'CLOSED_EARLY',
                          'PENDING_FUNDING', 'FUNDING_FAILED'));

alter table fixed_deposits add column funding_account_id bigint;
alter table fixed_deposits add column funding_attempts integer not null default 0;
alter table fixed_deposits add column funding_error varchar(255);

-- The funding job polls for PENDING_FUNDING deposits.
create index idx_fixed_deposits_status on fixed_deposits (status, id);
