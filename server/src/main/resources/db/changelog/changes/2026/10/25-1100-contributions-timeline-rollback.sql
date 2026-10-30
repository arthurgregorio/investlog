CREATE VIEW finances.contributions_timeline AS
SELECT
    wallets.id AS wallet_id,
    wallets.user_id,
    'STOCKS'::finances.wallet_kind AS kind,
    date_trunc('month', stock_lots.lot_date::TIMESTAMP)::DATE AS month,
    stock_lots.quantity * stock_lots.price AS wallet_amount,
    COALESCE(currency_rates.rate, 1) AS rate,
    stock_lots.quantity * stock_lots.price * COALESCE(currency_rates.rate, 1) AS amount
FROM finances.stock_lots
JOIN finances.stock_holdings ON stock_holdings.id = stock_lots.stock_holding_id
JOIN finances.wallets ON wallets.id = stock_holdings.wallet_id
LEFT JOIN finances.currency_rates ON currency_rates.currency_code = wallets.currency

UNION ALL

SELECT
    wallets.id AS wallet_id,
    wallets.user_id,
    'CRYPTO'::finances.wallet_kind AS kind,
    date_trunc('month', crypto_lots.lot_date::TIMESTAMP)::DATE AS month,
    crypto_lots.quantity * crypto_lots.price AS wallet_amount,
    COALESCE(currency_rates.rate, 1) AS rate,
    crypto_lots.quantity * crypto_lots.price * COALESCE(currency_rates.rate, 1) AS amount
FROM finances.crypto_lots
JOIN finances.crypto_holdings ON crypto_holdings.id = crypto_lots.crypto_holding_id
JOIN finances.wallets ON wallets.id = crypto_holdings.wallet_id
LEFT JOIN finances.currency_rates ON currency_rates.currency_code = wallets.currency

UNION ALL

SELECT
    wallets.id AS wallet_id,
    wallets.user_id,
    'FUNDS'::finances.wallet_kind AS kind,
    date_trunc('month', fund_contributions.contribution_date::TIMESTAMP)::DATE AS month,
    fund_contributions.amount AS wallet_amount,
    COALESCE(currency_rates.rate, 1) AS rate,
    fund_contributions.amount * COALESCE(currency_rates.rate, 1) AS amount
FROM finances.fund_contributions
JOIN finances.fund_holdings ON fund_holdings.id = fund_contributions.fund_holding_id
JOIN finances.wallets ON wallets.id = fund_holdings.wallet_id
LEFT JOIN finances.currency_rates ON currency_rates.currency_code = wallets.currency;
