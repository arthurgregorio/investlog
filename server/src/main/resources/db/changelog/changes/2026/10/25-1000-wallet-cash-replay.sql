CREATE FUNCTION finances.wallet_cash_replay(p_wallet_id BIGINT)
RETURNS TABLE (
    sequence INTEGER,
    event_type TEXT,
    event_id UUID,
    event_date DATE,
    created_at TIMESTAMPTZ,
    amount NUMERIC,
    cash_used NUMERIC,
    new_money NUMERIC,
    balance NUMERIC
)
LANGUAGE plpgsql
STABLE
AS $$
#variable_conflict use_column
DECLARE
    cash_event RECORD;
    running_balance NUMERIC := 0;
    event_position INTEGER := 0;
BEGIN
    FOR cash_event IN
        WITH events AS (
            SELECT 'PURCHASE'::TEXT AS event_kind, 'FLEXIBLE_DEBIT'::TEXT AS effect,
                   stock_lots.external_id AS identifier, stock_lots.lot_date AS happened_on,
                   stock_lots.created_at AS recorded_at, stock_lots.quantity * stock_lots.price AS value
            FROM finances.stock_lots
            JOIN finances.stock_holdings ON stock_holdings.id = stock_lots.stock_holding_id
            WHERE stock_holdings.wallet_id = p_wallet_id
              AND stock_lots.origin = 'PURCHASE'

            UNION ALL

            SELECT 'PURCHASE', 'FLEXIBLE_DEBIT',
                   crypto_lots.external_id, crypto_lots.lot_date,
                   crypto_lots.created_at, crypto_lots.quantity * crypto_lots.price
            FROM finances.crypto_lots
            JOIN finances.crypto_holdings ON crypto_holdings.id = crypto_lots.crypto_holding_id
            WHERE crypto_holdings.wallet_id = p_wallet_id
              AND crypto_lots.origin = 'PURCHASE'

            UNION ALL

            SELECT 'CONTRIBUTION', 'FLEXIBLE_DEBIT',
                   fund_contributions.external_id, fund_contributions.contribution_date,
                   fund_contributions.created_at, fund_contributions.amount
            FROM finances.fund_contributions
            JOIN finances.fund_holdings ON fund_holdings.id = fund_contributions.fund_holding_id
            WHERE fund_holdings.wallet_id = p_wallet_id
              AND fund_contributions.origin = 'PURCHASE'

            UNION ALL

            SELECT 'WITHDRAWAL', 'CREDIT',
                   results.external_id, results.result_date,
                   results.created_at, results.net_amount
            FROM finances.results
            LEFT JOIN finances.stock_holdings ON stock_holdings.id = results.stock_holding_id
            LEFT JOIN finances.crypto_holdings ON crypto_holdings.id = results.crypto_holding_id
            LEFT JOIN finances.fund_holdings ON fund_holdings.id = results.fund_holding_id
            WHERE results.result_type = 'WITHDRAWAL'
              AND COALESCE(stock_holdings.wallet_id, crypto_holdings.wallet_id, fund_holdings.wallet_id) = p_wallet_id

            UNION ALL

            SELECT 'TRANSFER_IN', 'CREDIT',
                   cash_transfers.external_id, cash_transfers.transfer_date,
                   cash_transfers.created_at, cash_transfers.destination_amount
            FROM finances.cash_transfers
            WHERE cash_transfers.destination_wallet_id = p_wallet_id

            UNION ALL

            SELECT 'TRANSFER_OUT', 'FIXED_DEBIT',
                   cash_transfers.external_id, cash_transfers.transfer_date,
                   cash_transfers.created_at, cash_transfers.amount
            FROM finances.cash_transfers
            WHERE cash_transfers.origin_wallet_id = p_wallet_id

            UNION ALL

            SELECT 'CASH_WITHDRAWAL', 'FIXED_DEBIT',
                   cash_withdrawals.external_id, cash_withdrawals.withdrawal_date,
                   cash_withdrawals.created_at, cash_withdrawals.amount
            FROM finances.cash_withdrawals
            WHERE cash_withdrawals.wallet_id = p_wallet_id
        )
        SELECT events.*
        FROM events
        ORDER BY events.happened_on,
                 CASE WHEN events.effect = 'CREDIT' THEN 0 ELSE 1 END,
                 events.recorded_at,
                 events.identifier
    LOOP
        event_position := event_position + 1;
        sequence := event_position;
        event_type := cash_event.event_kind;
        event_id := cash_event.identifier;
        event_date := cash_event.happened_on;
        created_at := cash_event.recorded_at;
        amount := cash_event.value;
        cash_used := NULL;
        new_money := NULL;

        IF cash_event.effect = 'CREDIT' THEN
            running_balance := running_balance + cash_event.value;
        ELSIF cash_event.effect = 'FIXED_DEBIT' THEN
            running_balance := running_balance - cash_event.value;
        ELSE
            cash_used := GREATEST(LEAST(running_balance, cash_event.value), 0);
            new_money := cash_event.value - cash_used;
            running_balance := running_balance - cash_used;
        END IF;

        balance := running_balance;
        RETURN NEXT;
    END LOOP;
END;
$$;
