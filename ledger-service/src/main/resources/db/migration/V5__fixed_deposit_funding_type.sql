-- Allow the entry for a fixed deposit paid for from a savings account.
alter table ledger_entries drop constraint if exists ledger_entries_transaction_type_check;
alter table ledger_entries
    add constraint ledger_entries_transaction_type_check
        check (transaction_type in ('SAVINGS_DEPOSIT', 'SAVINGS_WITHDRAWAL',
                                    'FIXED_DEPOSIT_CREATION', 'FIXED_DEPOSIT_MATURITY',
                                    'FIXED_DEPOSIT_INTEREST', 'FIXED_DEPOSIT_PAYOUT',
                                    'FIXED_DEPOSIT_INTEREST_PAYOUT', 'SAVINGS_INTEREST',
                                    'FIXED_DEPOSIT_FUNDING'));
