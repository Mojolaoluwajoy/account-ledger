-- Fixed deposits can be withdrawn before their maturity date, with reduced
-- interest.
alter table fixed_deposits drop constraint if exists fixed_deposits_status_check;
alter table fixed_deposits
    add constraint fixed_deposits_status_check
        check (status in ('ACTIVE', 'MATURED', 'CLOSED', 'CLOSED_EARLY'));

-- The interest actually paid out: the agreed amount at maturity, or the
-- reduced amount when withdrawn early. Empty for deposits that ended before
-- this column existed; for those it is maturity_amount - principal_amount.
alter table fixed_deposits add column payout_interest numeric(38, 2);

-- The day a deposit was withdrawn early.
alter table fixed_deposits add column closed_on date;
