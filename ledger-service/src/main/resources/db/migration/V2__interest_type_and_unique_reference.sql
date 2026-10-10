-- Allow the FIXED_DEPOSIT_INTEREST entry type.
alter table ledger_entries drop constraint if exists ledger_entries_transaction_type_check;
alter table ledger_entries
    add constraint ledger_entries_transaction_type_check
        check (transaction_type in ('SAVINGS_DEPOSIT', 'SAVINGS_WITHDRAWAL',
                                    'FIXED_DEPOSIT_CREATION', 'FIXED_DEPOSIT_MATURITY',
                                    'FIXED_DEPOSIT_INTEREST'));

-- A reference may only be posted once; this is what makes retries from the
-- savings and fixed-deposit services safe.
-- The first statement removes the auto-named constraint Hibernate created on
-- databases that ran under ddl-auto=update before Flyway was introduced.
alter table ledger_entries drop constraint if exists ukn8mvk7498chxmjojcaue0dkrc;
alter table ledger_entries
    add constraint uk_ledger_entries_transaction_reference unique (transaction_reference);
