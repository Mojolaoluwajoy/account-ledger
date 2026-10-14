-- Pay a matured deposit into the client's savings account.
-- Deposits created before this have no payout account and keep being paid out
-- in cash, so the column stays nullable.
alter table fixed_deposits add column payout_account_id bigint;
alter table fixed_deposits add column payout_status varchar(255);
alter table fixed_deposits add column payout_attempts integer not null default 0;
alter table fixed_deposits add column payout_error varchar(255);
alter table fixed_deposits add column paid_out_at timestamp(6);
alter table fixed_deposits
    add constraint fixed_deposits_payout_status_check
        check (payout_status in ('PENDING', 'PAID', 'FAILED'));

-- The payout job polls for PENDING deposits every few seconds.
create index idx_fixed_deposits_payout_status on fixed_deposits (payout_status, id);
