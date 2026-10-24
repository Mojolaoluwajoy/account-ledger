-- Fixed deposits paid for from a savings account.
alter table savings_transactions
    drop constraint if exists savings_transactions_transaction_type_check;
alter table savings_transactions
    add constraint savings_transactions_transaction_type_check
        check (transaction_type in ('WITHDRAWAL', 'DEPOSIT', 'TRANSFER_OUT', 'TRANSFER_IN',
                                    'FIXED_DEPOSIT_PAYOUT', 'INTEREST',
                                    'FIXED_DEPOSIT_FUNDING'));
