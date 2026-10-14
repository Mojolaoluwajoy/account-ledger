-- Fixed deposits paid into a savings account at maturity.
alter table savings_transactions
    drop constraint if exists savings_transactions_transaction_type_check;
alter table savings_transactions
    add constraint savings_transactions_transaction_type_check
        check (transaction_type in ('WITHDRAWAL', 'DEPOSIT', 'TRANSFER_OUT', 'TRANSFER_IN',
                                    'FIXED_DEPOSIT_PAYOUT'));

-- The payout's reference; unique so a retried payout is never credited twice.
alter table savings_transactions add column reference varchar(255);
alter table savings_transactions
    add constraint uk_savings_transactions_reference unique (reference);
