DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM system.users WHERE email = 'admin@admin.com') THEN
        RAISE EXCEPTION 'admin@admin.com not found — start the stack and let the server finish migrating first';
    END IF;
END $$;

INSERT INTO finances.stock_segments (name) VALUES
    ('Tecnologia da Informação'),
    ('Serviços de Comunicação'),
    ('Consumo Discricionário'),
    ('Consumo Básico'),
    ('Financeiro'),
    ('Saúde'),
    ('Industrial'),
    ('Energia'),
    ('Materiais'),
    ('Imobiliário'),
    ('Utilidade Pública'),
    ('Logística'),
    ('Recebíveis'),
    ('Shoppings'),
    ('Imóveis Comerciais'),
    ('Índice Brasil'),
    ('Índice EUA'),
    ('Índice Nasdaq-100');

INSERT INTO finances.stock_types (name) VALUES
    ('Ações'),
    ('FII'),
    ('REIT'),
    ('ETF'),
    ('BDR');

INSERT INTO finances.fund_types (name) VALUES
    ('Ações'),
    ('Multimercado'),
    ('Renda Fixa');

INSERT INTO finances.wallets (user_id, name, kind, currency)
SELECT u.id, v.name, v.kind::finances.wallet_kind, v.currency
FROM system.users u
CROSS JOIN (VALUES
    ('Ações Brasil', 'STOCKS', 'BRL'),
    ('Ações EUA', 'STOCKS', 'USD'),
    ('Fundos', 'FUNDS', 'BRL'),
    ('Cripto (USD)', 'CRYPTO', 'USD'),
    ('Cripto (BRL)', 'CRYPTO', 'BRL')
) AS v(name, kind, currency)
WHERE u.email = 'admin@admin.com';

INSERT INTO finances.stock_holdings (wallet_id, stock_type_id, stock_segment_id, ticker, name, current_price)
SELECT w.id, st.id, sg.id, v.ticker, v.name, v.current_price
FROM (VALUES
    ('Ações Brasil', 'Ações', 'Energia', 'PETR4', 'Petrobras PN', 49.12),
    ('Ações Brasil', 'Ações', 'Materiais', 'VALE3', 'Vale ON', 69.91),
    ('Ações Brasil', 'Ações', 'Financeiro', 'ITUB4', 'Itaú Unibanco PN', 44.28),
    ('Ações Brasil', 'Ações', 'Financeiro', 'BBDC4', 'Bradesco PN', 18.51),
    ('Ações Brasil', 'Ações', 'Financeiro', 'BBAS3', 'Banco do Brasil ON', 23.07),
    ('Ações Brasil', 'Ações', 'Financeiro', 'B3SA3', 'B3 ON', 18.34),
    ('Ações Brasil', 'Ações', 'Industrial', 'WEGE3', 'WEG ON', 49.46),
    ('Ações Brasil', 'Ações', 'Consumo Básico', 'ABEV3', 'Ambev ON', 15.36),
    ('Ações Brasil', 'Ações', 'Materiais', 'SUZB3', 'Suzano ON', 44.20),
    ('Ações Brasil', 'Ações', 'Utilidade Pública', 'SBSP3', 'Sabesp ON', 27.43),
    ('Ações EUA', 'Ações', 'Tecnologia da Informação', 'NVDA', 'NVIDIA', 227.21),
    ('Ações EUA', 'Ações', 'Tecnologia da Informação', 'AAPL', 'Apple', 329.40),
    ('Ações EUA', 'Ações', 'Serviços de Comunicação', 'GOOGL', 'Alphabet', 337.32),
    ('Ações EUA', 'Ações', 'Tecnologia da Informação', 'MSFT', 'Microsoft', 508.96),
    ('Ações EUA', 'Ações', 'Consumo Discricionário', 'AMZN', 'Amazon', 246.67),
    ('Ações EUA', 'Ações', 'Serviços de Comunicação', 'META', 'Meta Platforms', 738.79),
    ('Ações EUA', 'Ações', 'Tecnologia da Informação', 'AVGO', 'Broadcom', 355.10),
    ('Ações EUA', 'Ações', 'Consumo Discricionário', 'TSLA', 'Tesla', 352.84),
    ('Ações EUA', 'Ações', 'Financeiro', 'BRK.B', 'Berkshire Hathaway', 502.35),
    ('Ações EUA', 'Ações', 'Financeiro', 'JPM', 'JPMorgan Chase', 334.98),
    ('Ações Brasil', 'FII', 'Logística', 'HGLG11', 'CSHG Logística FII', 154.00),
    ('Ações Brasil', 'FII', 'Recebíveis', 'MXRF11', 'Maxi Renda FII', 9.36),
    ('Ações Brasil', 'FII', 'Shoppings', 'XPML11', 'XP Malls FII', 105.49),
    ('Ações Brasil', 'ETF', 'Índice Brasil', 'BOVA11', 'iShares Ibovespa ETF', 202.50),
    ('Ações Brasil', 'BDR', 'Tecnologia da Informação', 'AAPL34', 'Apple BDR', 83.20),
    ('Ações EUA', 'REIT', 'Imóveis Comerciais', 'O', 'Realty Income', 54.30),
    ('Ações EUA', 'REIT', 'Logística', 'PLD', 'Prologis', 129.84),
    ('Ações EUA', 'ETF', 'Índice EUA', 'VOO', 'Vanguard S&P 500 ETF', 700.86),
    ('Ações EUA', 'ETF', 'Índice Nasdaq-100', 'QQQ', 'Invesco QQQ Trust', 739.77)
) AS v(wallet_name, type_name, segment_name, ticker, name, current_price)
JOIN finances.wallets w ON w.name = v.wallet_name
    AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com')
JOIN finances.stock_types st ON st.name = v.type_name
JOIN finances.stock_segments sg ON sg.name = v.segment_name;

INSERT INTO finances.stock_lots (stock_holding_id, lot_date, quantity, price)
SELECT sh.id, v.lot_date, v.quantity, v.price
FROM (VALUES
    ('PETR4', DATE '2025-02-12', 200, 37.40),
    ('PETR4', DATE '2025-06-18', 150, 31.85),
    ('PETR4', DATE '2026-04-09', 100, 43.20),
    ('VALE3', DATE '2025-03-05', 100, 56.30),
    ('VALE3', DATE '2025-10-14', 80, 62.40),
    ('VALE3', DATE '2026-05-11', 60, 74.10),
    ('ITUB4', DATE '2025-01-29', 150, 33.80),
    ('ITUB4', DATE '2025-08-20', 120, 38.50),
    ('ITUB4', DATE '2026-03-18', 100, 41.25),
    ('BBDC4', DATE '2025-04-02', 300, 12.40),
    ('BBDC4', DATE '2025-11-06', 200, 17.05),
    ('BBAS3', DATE '2025-02-27', 150, 26.10),
    ('BBAS3', DATE '2025-07-23', 200, 21.45),
    ('BBAS3', DATE '2026-06-03', 100, 22.60),
    ('B3SA3', DATE '2025-05-14', 400, 12.65),
    ('B3SA3', DATE '2025-12-02', 250, 15.10),
    ('B3SA3', DATE '2026-07-15', 150, 17.20),
    ('WEGE3', DATE '2025-03-20', 100, 44.30),
    ('WEGE3', DATE '2025-10-08', 120, 42.10),
    ('ABEV3', DATE '2025-06-04', 300, 12.15),
    ('ABEV3', DATE '2026-02-12', 200, 14.30),
    ('SUZB3', DATE '2025-01-14', 120, 58.40),
    ('SUZB3', DATE '2025-09-02', 100, 52.30),
    ('SUZB3', DATE '2026-08-05', 80, 45.10),
    ('SBSP3', DATE '2025-04-23', 150, 22.80),
    ('SBSP3', DATE '2026-02-25', 100, 26.10),
    ('NVDA', DATE '2025-02-05', 20, 124.80),
    ('NVDA', DATE '2025-08-12', 15, 180.40),
    ('NVDA', DATE '2026-03-10', 10, 192.00),
    ('AAPL', DATE '2025-01-16', 25, 229.50),
    ('AAPL', DATE '2025-06-11', 20, 198.70),
    ('AAPL', DATE '2026-02-04', 12, 262.30),
    ('GOOGL', DATE '2025-03-13', 20, 168.40),
    ('GOOGL', DATE '2025-10-21', 15, 252.00),
    ('MSFT', DATE '2025-01-22', 10, 446.90),
    ('MSFT', DATE '2025-07-08', 8, 497.20),
    ('MSFT', DATE '2026-04-14', 6, 478.30),
    ('AMZN', DATE '2025-04-09', 25, 181.50),
    ('AMZN', DATE '2025-11-18', 15, 236.10),
    ('META', DATE '2025-02-19', 8, 703.80),
    ('META', DATE '2025-09-24', 6, 755.60),
    ('META', DATE '2026-05-27', 4, 681.40),
    ('AVGO', DATE '2025-05-07', 15, 211.30),
    ('AVGO', DATE '2026-01-13', 10, 335.00),
    ('TSLA', DATE '2025-04-15', 12, 254.20),
    ('TSLA', DATE '2025-12-09', 8, 441.00),
    ('BRK.B', DATE '2025-02-26', 10, 481.60),
    ('BRK.B', DATE '2025-10-29', 8, 497.40),
    ('JPM', DATE '2025-03-27', 15, 235.60),
    ('JPM', DATE '2026-01-28', 10, 318.20),
    ('JPM', DATE '2026-07-22', 8, 329.10),
    ('HGLG11', DATE '2025-03-06', 20, 156.80),
    ('HGLG11', DATE '2025-09-11', 15, 157.20),
    ('HGLG11', DATE '2026-05-21', 10, 151.40),
    ('MXRF11', DATE '2025-02-20', 300, 9.85),
    ('MXRF11', DATE '2025-08-13', 400, 9.30),
    ('MXRF11', DATE '2026-03-05', 250, 9.55),
    ('XPML11', DATE '2025-04-17', 40, 99.60),
    ('XPML11', DATE '2025-12-11', 30, 107.40),
    ('BOVA11', DATE '2025-05-28', 40, 137.20),
    ('BOVA11', DATE '2025-11-19', 30, 156.40),
    ('BOVA11', DATE '2026-04-28', 20, 181.90),
    ('AAPL34', DATE '2025-06-25', 100, 60.40),
    ('AAPL34', DATE '2026-01-14', 80, 72.10),
    ('O', DATE '2025-03-12', 40, 56.10),
    ('O', DATE '2025-09-17', 30, 57.80),
    ('O', DATE '2026-04-01', 25, 55.20),
    ('PLD', DATE '2025-05-14', 15, 103.50),
    ('PLD', DATE '2026-02-18', 12, 126.80),
    ('VOO', DATE '2025-01-23', 6, 550.40),
    ('VOO', DATE '2025-07-16', 5, 574.20),
    ('VOO', DATE '2026-03-31', 4, 641.30),
    ('QQQ', DATE '2025-02-12', 8, 530.40),
    ('QQQ', DATE '2025-10-02', 5, 600.90),
    ('QQQ', DATE '2026-05-06', 4, 699.10)
) AS v(ticker, lot_date, quantity, price)
JOIN finances.stock_holdings sh ON sh.ticker = v.ticker
JOIN finances.wallets w ON w.id = sh.wallet_id
    AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com');

INSERT INTO finances.crypto_holdings (wallet_id, ticker, name, current_price)
SELECT w.id, v.ticker, v.name, v.current_price
FROM (VALUES
    ('Cripto (USD)', 'BTC', 'Bitcoin', 84550.00),
    ('Cripto (USD)', 'ETH', 'Ethereum', 2705.00),
    ('Cripto (BRL)', 'BTC', 'Bitcoin', 437970.00),
    ('Cripto (BRL)', 'ETH', 'Ethereum', 14012.90),
    ('Cripto (BRL)', 'SOL', 'Solana', 615.28)
) AS v(wallet_name, ticker, name, current_price)
JOIN finances.wallets w ON w.name = v.wallet_name
    AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com');

INSERT INTO finances.crypto_lots (crypto_holding_id, lot_date, quantity, price)
SELECT ch.id, v.lot_date, v.quantity, v.price
FROM (VALUES
    ('Cripto (USD)', 'BTC', DATE '2025-01-20', 0.05, 104800.00),
    ('Cripto (USD)', 'BTC', DATE '2025-04-22', 0.04, 87400.00),
    ('Cripto (USD)', 'BTC', DATE '2025-11-10', 0.03, 105600.00),
    ('Cripto (USD)', 'BTC', DATE '2026-06-16', 0.02, 79800.00),
    ('Cripto (USD)', 'ETH', DATE '2025-02-03', 1.2, 2780.00),
    ('Cripto (USD)', 'ETH', DATE '2025-08-19', 0.8, 4290.00),
    ('Cripto (USD)', 'ETH', DATE '2026-04-07', 0.6, 2320.00),
    ('Cripto (BRL)', 'BTC', DATE '2025-03-11', 0.015, 480500.00),
    ('Cripto (BRL)', 'BTC', DATE '2025-09-30', 0.01, 612300.00),
    ('Cripto (BRL)', 'BTC', DATE '2026-05-19', 0.008, 405000.00),
    ('Cripto (BRL)', 'ETH', DATE '2025-02-18', 0.5, 15800.00),
    ('Cripto (BRL)', 'ETH', DATE '2025-08-26', 0.4, 23900.00),
    ('Cripto (BRL)', 'ETH', DATE '2026-03-24', 0.3, 12100.00),
    ('Cripto (BRL)', 'SOL', DATE '2025-04-30', 8, 770.00),
    ('Cripto (BRL)', 'SOL', DATE '2025-10-07', 6, 1090.00),
    ('Cripto (BRL)', 'SOL', DATE '2026-06-09', 10, 540.00)
) AS v(wallet_name, ticker, lot_date, quantity, price)
JOIN finances.wallets w ON w.name = v.wallet_name
    AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com')
JOIN finances.crypto_holdings ch ON ch.wallet_id = w.id AND ch.ticker = v.ticker;

INSERT INTO finances.fund_holdings (wallet_id, fund_type_id, name, current_value)
SELECT w.id, ft.id, v.name, v.current_value
FROM (VALUES
    ('Ações', 'BTG Pactual Int Lo FIF Ações', 24100.00),
    ('Ações', 'RR Palmeiras I FIF Ações', 12650.00),
    ('Ações', 'Equitas HIGH Convictions FIF Ações RL', 16200.00),
    ('Multimercado', 'Growth Numeric Unlimited FIM IE', 20300.00),
    ('Multimercado', 'Hawker FIF Classe FIM CP RL', 20100.00),
    ('Multimercado', 'Lagoa FIF Multimercado', 11050.00),
    ('Renda Fixa', 'Oak FI RF CP', 37950.00),
    ('Renda Fixa', 'V7 Incentivado Investimento Infraestrutura RF', 22900.00),
    ('Renda Fixa', 'BB TOP RF Ativa LP FIF RL', 47300.00)
) AS v(type_name, name, current_value)
JOIN finances.fund_types ft ON ft.name = v.type_name
JOIN finances.wallets w ON w.name = 'Fundos'
    AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com');

INSERT INTO finances.fund_contributions (fund_holding_id, contribution_date, amount)
SELECT fh.id, v.contribution_date, v.amount
FROM (VALUES
    ('BTG Pactual Int Lo FIF Ações', DATE '2025-02-10', 8000.00),
    ('BTG Pactual Int Lo FIF Ações', DATE '2025-09-15', 6000.00),
    ('BTG Pactual Int Lo FIF Ações', DATE '2026-04-20', 5000.00),
    ('RR Palmeiras I FIF Ações', DATE '2025-03-24', 5000.00),
    ('RR Palmeiras I FIF Ações', DATE '2025-11-17', 5000.00),
    ('Equitas HIGH Convictions FIF Ações RL', DATE '2025-05-06', 6000.00),
    ('Equitas HIGH Convictions FIF Ações RL', DATE '2026-01-26', 4000.00),
    ('Equitas HIGH Convictions FIF Ações RL', DATE '2026-07-13', 3000.00),
    ('Growth Numeric Unlimited FIM IE', DATE '2025-01-27', 10000.00),
    ('Growth Numeric Unlimited FIM IE', DATE '2025-08-04', 7500.00),
    ('Hawker FIF Classe FIM CP RL', DATE '2025-04-14', 8000.00),
    ('Hawker FIF Classe FIM CP RL', DATE '2025-12-15', 6000.00),
    ('Hawker FIF Classe FIM CP RL', DATE '2026-06-22', 4000.00),
    ('Lagoa FIF Multimercado', DATE '2025-06-09', 5000.00),
    ('Lagoa FIF Multimercado', DATE '2026-02-09', 5000.00),
    ('Oak FI RF CP', DATE '2025-01-13', 15000.00),
    ('Oak FI RF CP', DATE '2025-07-28', 10000.00),
    ('Oak FI RF CP', DATE '2026-03-30', 8000.00),
    ('V7 Incentivado Investimento Infraestrutura RF', DATE '2025-03-03', 12000.00),
    ('V7 Incentivado Investimento Infraestrutura RF', DATE '2025-10-06', 8000.00),
    ('BB TOP RF Ativa LP FIF RL', DATE '2025-02-24', 20000.00),
    ('BB TOP RF Ativa LP FIF RL', DATE '2025-09-08', 10000.00),
    ('BB TOP RF Ativa LP FIF RL', DATE '2026-05-04', 12000.00)
) AS v(name, contribution_date, amount)
JOIN finances.fund_holdings fh ON fh.name = v.name
JOIN finances.wallets w ON w.id = fh.wallet_id
    AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com');

INSERT INTO finances.wallet_daily_snapshots (wallet_id, snapshot_date, current_value, total_invested, gain, gain_pct)
WITH admin_wallets AS (
    SELECT w.id
    FROM finances.wallets w
    JOIN system.users u ON u.id = w.user_id
    WHERE u.email = 'admin@admin.com'
),
unit_lots AS (
    SELECT sh.wallet_id, 'S' AS holding_kind, sh.id AS holding_id, sl.lot_date, sl.quantity, sl.price, sh.current_price
    FROM finances.stock_lots sl
    JOIN finances.stock_holdings sh ON sh.id = sl.stock_holding_id
    WHERE sh.wallet_id IN (SELECT id FROM admin_wallets)
    UNION ALL
    SELECT ch.wallet_id, 'C', ch.id, cl.lot_date, cl.quantity, cl.price, ch.current_price
    FROM finances.crypto_lots cl
    JOIN finances.crypto_holdings ch ON ch.id = cl.crypto_holding_id
    WHERE ch.wallet_id IN (SELECT id FROM admin_wallets)
),
unit_holdings AS (
    SELECT DISTINCT wallet_id, holding_kind, holding_id, current_price FROM unit_lots
),
price_knots AS (
    SELECT holding_kind, holding_id, lot_date AS knot_date, price AS knot_price FROM unit_lots
    UNION ALL
    SELECT holding_kind, holding_id, CURRENT_DATE, current_price FROM unit_holdings
),
fund_holdings_totals AS (
    SELECT fh.wallet_id, fh.id AS holding_id, fh.current_value,
           SUM(fc.amount) AS total_amount, MIN(fc.contribution_date) AS first_date
    FROM finances.fund_holdings fh
    JOIN finances.fund_contributions fc ON fc.fund_holding_id = fh.id
    WHERE fh.wallet_id IN (SELECT id FROM admin_wallets)
    GROUP BY fh.wallet_id, fh.id, fh.current_value
),
wallet_first_dates AS (
    SELECT wallet_id, MIN(lot_date) AS first_date FROM unit_lots GROUP BY wallet_id
    UNION ALL
    SELECT wallet_id, MIN(first_date) FROM fund_holdings_totals GROUP BY wallet_id
),
snapshot_days AS (
    SELECT f.wallet_id, d::date AS snapshot_date
    FROM wallet_first_dates f
    CROSS JOIN LATERAL generate_series(f.first_date, CURRENT_DATE, INTERVAL '7 days') AS d
    UNION
    SELECT wallet_id, CURRENT_DATE FROM wallet_first_dates
),
unit_values AS (
    SELECT
        d.wallet_id,
        d.snapshot_date,
        held.quantity * (
            CASE WHEN following.knot_date IS NULL THEN preceding.knot_price
                 ELSE preceding.knot_price + (following.knot_price - preceding.knot_price)
                      * (d.snapshot_date - preceding.knot_date)::numeric / (following.knot_date - preceding.knot_date)
            END
            * (1 + CASE WHEN following.knot_date IS NULL THEN 0
                        ELSE (CASE h.holding_kind WHEN 'C' THEN 0.04 ELSE 0.015 END)
                             * sin(((d.snapshot_date - DATE '2025-01-01') / 7.0 * 1.3 + h.holding_id)::float8)::numeric
                   END)
        ) AS holding_value,
        held.cost AS holding_cost
    FROM snapshot_days d
    JOIN unit_holdings h ON h.wallet_id = d.wallet_id
    CROSS JOIN LATERAL (
        SELECT SUM(l.quantity) AS quantity, SUM(l.quantity * l.price) AS cost
        FROM unit_lots l
        WHERE l.holding_kind = h.holding_kind AND l.holding_id = h.holding_id AND l.lot_date <= d.snapshot_date
    ) held
    CROSS JOIN LATERAL (
        SELECT k.knot_date, k.knot_price
        FROM price_knots k
        WHERE k.holding_kind = h.holding_kind AND k.holding_id = h.holding_id AND k.knot_date <= d.snapshot_date
        ORDER BY k.knot_date DESC
        LIMIT 1
    ) preceding
    LEFT JOIN LATERAL (
        SELECT k.knot_date, k.knot_price
        FROM price_knots k
        WHERE k.holding_kind = h.holding_kind AND k.holding_id = h.holding_id AND k.knot_date > d.snapshot_date
        ORDER BY k.knot_date
        LIMIT 1
    ) following ON TRUE
    WHERE held.quantity IS NOT NULL
),
fund_values AS (
    SELECT
        d.wallet_id,
        d.snapshot_date,
        held.invested
            * (1 + (t.current_value / t.total_amount - 1)
                   * LEAST(1, (d.snapshot_date - t.first_date)::numeric / NULLIF(CURRENT_DATE - t.first_date, 0)))
            * (1 + CASE WHEN d.snapshot_date = CURRENT_DATE THEN 0
                        ELSE 0.004 * sin(((d.snapshot_date - DATE '2025-01-01') / 7.0 * 0.9 + t.holding_id)::float8)::numeric
                   END) AS holding_value,
        held.invested AS holding_cost
    FROM snapshot_days d
    JOIN fund_holdings_totals t ON t.wallet_id = d.wallet_id
    CROSS JOIN LATERAL (
        SELECT SUM(fc.amount) AS invested
        FROM finances.fund_contributions fc
        WHERE fc.fund_holding_id = t.holding_id AND fc.contribution_date <= d.snapshot_date
    ) held
    WHERE held.invested IS NOT NULL
),
wallet_totals AS (
    SELECT wallet_id, snapshot_date,
           ROUND(SUM(holding_value), 2) AS current_value,
           ROUND(SUM(holding_cost), 2) AS total_invested
    FROM (SELECT * FROM unit_values UNION ALL SELECT * FROM fund_values) AS all_values
    GROUP BY wallet_id, snapshot_date
)
SELECT wallet_id, snapshot_date, current_value, total_invested,
       current_value - total_invested,
       ROUND((current_value - total_invested) / total_invested * 100, 4)
FROM wallet_totals;
