-- Allow the two sides of a transfer between savings accounts.
alter table savings_transactions
    drop constraint if exists savings_transactions_transaction_type_check;
alter table savings_transactions
    add constraint savings_transactions_transaction_type_check
        check (transaction_type in ('WITHDRAWAL', 'DEPOSIT', 'TRANSFER_OUT', 'TRANSFER_IN'));
