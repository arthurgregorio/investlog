DROP VIEW finances.wallet_ledger;

CREATE VIEW finances.wallet_ledger AS
WITH reinvestment_destinations AS (
    SELECT
        reinvestments.result_id,
        COALESCE(
            destination_stock_holdings.ticker,
            destination_stock_holdings.name,
            destination_crypto_holdings.ticker,
            destination_crypto_holdings.name,
            destination_fund_holdings.name
        ) AS destination_name
    FROM finances.reinvestments
    LEFT JOIN finances.stock_holdings AS destination_stock_holdings
        ON destination_stock_holdings.id = reinvestments.destination_stock_holding_id
    LEFT JOIN finances.crypto_holdings AS destination_crypto_holdings
        ON destination_crypto_holdings.id = reinvestments.destination_crypto_holding_id
    LEFT JOIN finances.fund_holdings AS destination_fund_holdings
        ON destination_fund_holdings.id = reinvestments.destination_fund_holding_id
)
SELECT
    stock_holdings.wallet_id,
    stock_lots.external_id AS event_id,
    'PURCHASE'::TEXT AS event_type,
    stock_lots.lot_date AS event_date,
    'STOCKS'::finances.wallet_kind AS kind,
    stock_holdings.external_id AS holding_external_id,
    stock_holdings.name AS holding_name,
    stock_holdings.ticker,
    stock_lots.quantity,
    stock_lots.quantity * stock_lots.price AS amount,
    NULL::NUMERIC AS profit,
    NULL::TEXT AS counterpart_name,
    NULL::NUMERIC AS original_amount,
    NULL::TEXT AS original_currency,
    NULL::NUMERIC AS rate,
    stock_lots.created_at
FROM finances.stock_lots
JOIN finances.stock_holdings ON stock_holdings.id = stock_lots.stock_holding_id

UNION ALL

SELECT
    crypto_holdings.wallet_id,
    crypto_lots.external_id,
    'PURCHASE'::TEXT,
    crypto_lots.lot_date,
    'CRYPTO'::finances.wallet_kind,
    crypto_holdings.external_id,
    crypto_holdings.name,
    crypto_holdings.ticker,
    crypto_lots.quantity,
    crypto_lots.quantity * crypto_lots.price,
    NULL::NUMERIC,
    NULL::TEXT,
    NULL::NUMERIC,
    NULL::TEXT,
    NULL::NUMERIC,
    crypto_lots.created_at
FROM finances.crypto_lots
JOIN finances.crypto_holdings ON crypto_holdings.id = crypto_lots.crypto_holding_id

UNION ALL

SELECT
    fund_holdings.wallet_id,
    fund_contributions.external_id,
    'CONTRIBUTION'::TEXT,
    fund_contributions.contribution_date,
    'FUNDS'::finances.wallet_kind,
    fund_holdings.external_id,
    fund_holdings.name,
    NULL::TEXT,
    NULL::NUMERIC,
    fund_contributions.amount,
    NULL::NUMERIC,
    NULL::TEXT,
    NULL::NUMERIC,
    NULL::TEXT,
    NULL::NUMERIC,
    fund_contributions.created_at
FROM finances.fund_contributions
JOIN finances.fund_holdings ON fund_holdings.id = fund_contributions.fund_holding_id

UNION ALL

SELECT
    stock_holdings.wallet_id,
    results.external_id,
    results.result_type::TEXT,
    results.result_date,
    'STOCKS'::finances.wallet_kind,
    stock_holdings.external_id,
    stock_holdings.name,
    stock_holdings.ticker,
    results.quantity,
    results.net_amount,
    results.profit,
    reinvestment_destinations.destination_name,
    NULL::NUMERIC,
    NULL::TEXT,
    NULL::NUMERIC,
    results.created_at
FROM finances.results
JOIN finances.stock_holdings ON stock_holdings.id = results.stock_holding_id
LEFT JOIN reinvestment_destinations ON reinvestment_destinations.result_id = results.id

UNION ALL

SELECT
    crypto_holdings.wallet_id,
    results.external_id,
    results.result_type::TEXT,
    results.result_date,
    'CRYPTO'::finances.wallet_kind,
    crypto_holdings.external_id,
    crypto_holdings.name,
    crypto_holdings.ticker,
    results.quantity,
    results.net_amount,
    results.profit,
    reinvestment_destinations.destination_name,
    NULL::NUMERIC,
    NULL::TEXT,
    NULL::NUMERIC,
    results.created_at
FROM finances.results
JOIN finances.crypto_holdings ON crypto_holdings.id = results.crypto_holding_id
LEFT JOIN reinvestment_destinations ON reinvestment_destinations.result_id = results.id

UNION ALL

SELECT
    fund_holdings.wallet_id,
    results.external_id,
    results.result_type::TEXT,
    results.result_date,
    'FUNDS'::finances.wallet_kind,
    fund_holdings.external_id,
    fund_holdings.name,
    NULL::TEXT,
    results.quantity,
    results.net_amount,
    results.profit,
    reinvestment_destinations.destination_name,
    NULL::NUMERIC,
    NULL::TEXT,
    NULL::NUMERIC,
    results.created_at
FROM finances.results
JOIN finances.fund_holdings ON fund_holdings.id = results.fund_holding_id
LEFT JOIN reinvestment_destinations ON reinvestment_destinations.result_id = results.id

UNION ALL

SELECT
    wallet_moves.origin_wallet_id,
    wallet_moves.external_id,
    'MOVE_OUT'::TEXT,
    wallet_moves.moved_at,
    wallet_moves.kind,
    wallet_moves.origin_holding_external_id,
    wallet_moves.holding_name,
    wallet_moves.ticker,
    wallet_moves.quantity,
    NULL::NUMERIC,
    NULL::NUMERIC,
    destination_wallets.name,
    NULL::NUMERIC,
    NULL::TEXT,
    NULL::NUMERIC,
    wallet_moves.created_at
FROM finances.wallet_moves
LEFT JOIN finances.wallets AS destination_wallets
    ON destination_wallets.id = wallet_moves.destination_wallet_id

UNION ALL

SELECT
    wallet_moves.destination_wallet_id,
    wallet_moves.external_id,
    'MOVE_IN'::TEXT,
    wallet_moves.moved_at,
    wallet_moves.kind,
    wallet_moves.destination_holding_external_id,
    wallet_moves.holding_name,
    wallet_moves.ticker,
    wallet_moves.quantity,
    NULL::NUMERIC,
    NULL::NUMERIC,
    origin_wallets.name,
    NULL::NUMERIC,
    NULL::TEXT,
    NULL::NUMERIC,
    wallet_moves.created_at
FROM finances.wallet_moves
LEFT JOIN finances.wallets AS origin_wallets
    ON origin_wallets.id = wallet_moves.origin_wallet_id

UNION ALL

SELECT
    cash_transfers.origin_wallet_id,
    cash_transfers.external_id,
    'TRANSFER_OUT'::TEXT,
    cash_transfers.transfer_date,
    origin_wallets.kind,
    NULL::UUID,
    NULL::TEXT,
    NULL::TEXT,
    NULL::NUMERIC,
    cash_transfers.amount,
    NULL::NUMERIC,
    destination_wallets.name,
    NULL::NUMERIC,
    NULL::TEXT,
    NULL::NUMERIC,
    cash_transfers.created_at
FROM finances.cash_transfers
JOIN finances.wallets AS origin_wallets ON origin_wallets.id = cash_transfers.origin_wallet_id
JOIN finances.wallets AS destination_wallets ON destination_wallets.id = cash_transfers.destination_wallet_id

UNION ALL

SELECT
    cash_transfers.destination_wallet_id,
    cash_transfers.external_id,
    'TRANSFER_IN'::TEXT,
    cash_transfers.transfer_date,
    destination_wallets.kind,
    NULL::UUID,
    NULL::TEXT,
    NULL::TEXT,
    NULL::NUMERIC,
    cash_transfers.destination_amount,
    NULL::NUMERIC,
    origin_wallets.name,
    cash_transfers.amount,
    origin_wallets.currency,
    cash_transfers.rate,
    cash_transfers.created_at
FROM finances.cash_transfers
JOIN finances.wallets AS origin_wallets ON origin_wallets.id = cash_transfers.origin_wallet_id
JOIN finances.wallets AS destination_wallets ON destination_wallets.id = cash_transfers.destination_wallet_id

UNION ALL

SELECT
    cash_withdrawals.wallet_id,
    cash_withdrawals.external_id,
    'CASH_WITHDRAWAL'::TEXT,
    cash_withdrawals.withdrawal_date,
    wallets.kind,
    NULL::UUID,
    NULL::TEXT,
    NULL::TEXT,
    NULL::NUMERIC,
    cash_withdrawals.amount,
    NULL::NUMERIC,
    cash_withdrawals.bank_name,
    NULL::NUMERIC,
    NULL::TEXT,
    NULL::NUMERIC,
    cash_withdrawals.created_at
FROM finances.cash_withdrawals
JOIN finances.wallets ON wallets.id = cash_withdrawals.wallet_id;
