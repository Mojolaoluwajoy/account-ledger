-- A posting the ledger service rejects as invalid is marked FAILED instead of
-- being retried forever.
alter table ledger_postings drop constraint if exists ledger_postings_status_check;
alter table ledger_postings
    add constraint ledger_postings_status_check check (status in ('PENDING', 'POSTED', 'FAILED'));
