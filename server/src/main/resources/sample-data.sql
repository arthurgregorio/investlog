-- Sample data for InvestLog development / demonstration
--
-- Prerequisites: the app must have been started at least once so that Liquibase
-- runs all migrations (including the dev-user seed in 14-1050-seed-dev-data.xml).
-- Run this script manually in your database client against the local dev database.
--
-- The dev user has google_sub = 'dev-user' and is created by Liquibase.
-- Currency rates (BRL base, USD, EUR) are also seeded by Liquibase.

DO $$
DECLARE
    v_user_id         BIGINT;
    v_stocks_wallet   BIGINT;
    v_crypto_wallet   BIGINT;
    v_funds_wallet    BIGINT;
    v_stock_type_acoes BIGINT;
    v_stock_type_bdrs  BIGINT;
    v_fund_type_fi    BIGINT;
    v_fund_type_fii   BIGINT;
    v_sh_petr4        BIGINT;
    v_sh_vale3        BIGINT;
    v_sh_itub4        BIGINT;
    v_sh_mglu3        BIGINT;
    v_ch_btc          BIGINT;
    v_ch_eth          BIGINT;
    v_fh_xpml11       BIGINT;
    v_fh_mxrf11       BIGINT;
    v_fh_cdi          BIGINT;
BEGIN
    SELECT id INTO v_user_id FROM system.users WHERE google_sub = 'dev-user';

    -- Wallets
    INSERT INTO finances.wallets (user_id, name, kind, currency)
    VALUES (v_user_id, 'Carteira de Ações', 'stocks', 'BRL')
    RETURNING id INTO v_stocks_wallet;

    INSERT INTO finances.wallets (user_id, name, kind, currency)
    VALUES (v_user_id, 'Crypto Portfolio', 'crypto', 'USD')
    RETURNING id INTO v_crypto_wallet;

    INSERT INTO finances.wallets (user_id, name, kind, currency)
    VALUES (v_user_id, 'Fundos e FIIs', 'funds', 'BRL')
    RETURNING id INTO v_funds_wallet;

    -- Stock types
    INSERT INTO finances.stock_types (user_id, name)
    VALUES (v_user_id, 'Ação Ordinária') RETURNING id INTO v_stock_type_acoes;

    INSERT INTO finances.stock_types (user_id, name)
    VALUES (v_user_id, 'BDR Nível I') RETURNING id INTO v_stock_type_bdrs;

    -- Fund types
    INSERT INTO finances.fund_types (user_id, name)
    VALUES (v_user_id, 'Fundo Imobiliário') RETURNING id INTO v_fund_type_fii;

    INSERT INTO finances.fund_types (user_id, name)
    VALUES (v_user_id, 'Renda Fixa CDI') RETURNING id INTO v_fund_type_fi;

    -- Stock holdings
    INSERT INTO finances.stock_holdings (wallet_id, stock_type_id, ticker, name, current_price)
    VALUES (v_stocks_wallet, v_stock_type_acoes, 'PETR4', 'Petrobras PN', 38.50)
    RETURNING id INTO v_sh_petr4;

    INSERT INTO finances.stock_holdings (wallet_id, stock_type_id, ticker, name, current_price)
    VALUES (v_stocks_wallet, v_stock_type_acoes, 'VALE3', 'Vale ON', 62.00)
    RETURNING id INTO v_sh_vale3;

    INSERT INTO finances.stock_holdings (wallet_id, stock_type_id, ticker, name, current_price)
    VALUES (v_stocks_wallet, v_stock_type_acoes, 'ITUB4', 'Itaú Unibanco PN', 34.20)
    RETURNING id INTO v_sh_itub4;

    INSERT INTO finances.stock_holdings (wallet_id, stock_type_id, ticker, name, current_price)
    VALUES (v_stocks_wallet, v_stock_type_bdrs, 'MGLU3', 'Magazine Luiza ON', 2.10)
    RETURNING id INTO v_sh_mglu3;

    -- Stock lots
    INSERT INTO finances.stock_lots (stock_holding_id, lot_date, quantity, price) VALUES
        (v_sh_petr4, '2023-06-10', 100, 25.00),
        (v_sh_petr4, '2023-11-20', 200, 30.50),
        (v_sh_petr4, '2024-03-05', 150, 33.80);

    INSERT INTO finances.stock_lots (stock_holding_id, lot_date, quantity, price) VALUES
        (v_sh_vale3, '2023-08-15', 50, 70.00),
        (v_sh_vale3, '2024-01-10', 80, 65.50);

    INSERT INTO finances.stock_lots (stock_holding_id, lot_date, quantity, price) VALUES
        (v_sh_itub4, '2023-09-22', 200, 28.00),
        (v_sh_itub4, '2024-04-18', 100, 31.50);

    INSERT INTO finances.stock_lots (stock_holding_id, lot_date, quantity, price) VALUES
        (v_sh_mglu3, '2024-02-01', 500, 5.00),
        (v_sh_mglu3, '2024-06-15', 300, 3.20);

    -- Crypto holdings (wallet currency is USD, rates are stored in currency_rates)
    INSERT INTO finances.crypto_holdings (wallet_id, ticker, name, current_price)
    VALUES (v_crypto_wallet, 'BTC', 'Bitcoin', 65000.00)
    RETURNING id INTO v_ch_btc;

    INSERT INTO finances.crypto_holdings (wallet_id, ticker, name, current_price)
    VALUES (v_crypto_wallet, 'ETH', 'Ethereum', 3200.00)
    RETURNING id INTO v_ch_eth;

    -- Crypto lots (amounts in USD since wallet is USD)
    INSERT INTO finances.crypto_lots (crypto_holding_id, lot_date, quantity, price) VALUES
        (v_ch_btc, '2023-01-15', 0.05, 17000.00),
        (v_ch_btc, '2023-07-20', 0.03, 30000.00),
        (v_ch_btc, '2024-01-08', 0.02, 45000.00);

    INSERT INTO finances.crypto_lots (crypto_holding_id, lot_date, quantity, price) VALUES
        (v_ch_eth, '2023-03-10', 0.5, 1700.00),
        (v_ch_eth, '2023-10-25', 0.3, 1600.00),
        (v_ch_eth, '2024-02-14', 0.2, 2400.00);

    -- Fund holdings
    INSERT INTO finances.fund_holdings (wallet_id, fund_type_id, name, current_value)
    VALUES (v_funds_wallet, v_fund_type_fii, 'XPML11 - XP Malls', 14500.00)
    RETURNING id INTO v_fh_xpml11;

    INSERT INTO finances.fund_holdings (wallet_id, fund_type_id, name, current_value)
    VALUES (v_funds_wallet, v_fund_type_fii, 'MXRF11 - Maxi Renda', 5800.00)
    RETURNING id INTO v_fh_mxrf11;

    INSERT INTO finances.fund_holdings (wallet_id, fund_type_id, name, current_value)
    VALUES (v_funds_wallet, v_fund_type_fi, 'Fundo CDI Plus', 52000.00)
    RETURNING id INTO v_fh_cdi;

    -- Fund contributions
    INSERT INTO finances.fund_contributions (fund_holding_id, contribution_date, amount) VALUES
        (v_fh_xpml11, '2023-05-10', 5000.00),
        (v_fh_xpml11, '2023-11-15', 4000.00),
        (v_fh_xpml11, '2024-04-20', 4500.00);

    INSERT INTO finances.fund_contributions (fund_holding_id, contribution_date, amount) VALUES
        (v_fh_mxrf11, '2023-08-01', 3000.00),
        (v_fh_mxrf11, '2024-02-10', 2500.00);

    INSERT INTO finances.fund_contributions (fund_holding_id, contribution_date, amount) VALUES
        (v_fh_cdi, '2022-12-01', 20000.00),
        (v_fh_cdi, '2023-06-01', 15000.00),
        (v_fh_cdi, '2024-01-15', 12000.00);

    RAISE NOTICE 'Sample data inserted successfully for user %', v_user_id;
END $$;
