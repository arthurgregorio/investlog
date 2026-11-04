DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM system.users WHERE email = 'admin@admin.com') THEN
        RAISE EXCEPTION 'admin@admin.com not found — start the stack and let the server finish migrating first';
    END IF;
END $$;

-- Every date is an offset from the load day, so the daily demo reset always ends on a full history. The app's "today"
-- is São Paulo's, so CURRENT_DATE has to be too, or a late-evening load dates everything tomorrow.
SET TIME ZONE 'America/Sao_Paulo';

CREATE FUNCTION pg_temp.seed_date(month_offset INTEGER, day_of_month INTEGER) RETURNS DATE
    LANGUAGE sql STABLE
    AS $$
        SELECT LEAST(CURRENT_DATE,
                     (date_trunc('month', CURRENT_DATE) + make_interval(months => month_offset))::date + (day_of_month - 1))
    $$;

INSERT INTO finances.segments (name) VALUES
    ('Bancos'),
    ('Seguradoras'),
    ('Serviços Financeiros'),
    ('Energia Elétrica'),
    ('Água e Saneamento'),
    ('Papel e Celulose'),
    ('Telecomunicações'),
    ('Bens de Capital'),
    ('Agronegócio'),
    ('Petróleo e Gás'),
    ('Logística e Mobilidade'),
    ('Varejo'),
    ('Tecnologia'),
    ('Shoppings'),
    ('Logística'),
    ('Híbrido'),
    ('Recebíveis'),
    ('Pós-fixado'),
    ('Inflação'),
    ('Prefixado'),
    ('Core'),
    ('Satélite'),
    ('Money Market'),
    ('Bonds High Grade'),
    ('Reserva de Valor'),
    ('Smart Contracts'),
    ('Oráculos'),
    ('Multimercado'),
    ('Renda Variável'),
    ('Crédito Privado'),
    ('Estruturado')
ON CONFLICT (name) DO NOTHING;

INSERT INTO finances.holding_types (name, kinds) VALUES
    ('Ações', '{STOCKS}'),
    ('FII', '{STOCKS}'),
    ('ETF', '{STOCKS}'),
    ('CDB', '{FUNDS}'),
    ('LCA', '{FUNDS}'),
    ('Tesouro Direto', '{FUNDS}'),
    ('Debênture', '{FUNDS}'),
    ('Fundo de Ações', '{FUNDS}'),
    ('Fundo Multimercado', '{FUNDS}'),
    ('Fundo de Renda Fixa', '{FUNDS}'),
    ('COE', '{FUNDS}'),
    ('Cripto', '{CRYPTO}')
ON CONFLICT (name) DO NOTHING;

INSERT INTO finances.wallets (user_id, name, kind, currency)
SELECT u.id, v.name, v.kind::finances.wallet_kind, v.currency
FROM system.users u
CROSS JOIN (VALUES
    ('Ações e FIIs', 'STOCKS', 'BRL'),
    ('Renda Fixa', 'FUNDS', 'BRL'),
    ('Fundos', 'FUNDS', 'BRL'),
    ('Exterior', 'STOCKS', 'USD'),
    ('Cripto', 'CRYPTO', 'BRL')
) AS v(name, kind, currency)
WHERE u.email = 'admin@admin.com';

INSERT INTO finances.strategies (user_id, name)
SELECT id, 'Carteira Moderada'
FROM system.users
WHERE email = 'admin@admin.com';

INSERT INTO finances.strategy_classes (strategy_id, name, target_percentage, position)
SELECT s.id, v.class_name, v.target_percentage, v.position
FROM (VALUES
    ('Renda Fixa', 50, 0),
    ('Ações Nacionais', 25, 1),
    ('Fundos Imobiliários', 14, 2),
    ('Internacional (Equity)', 5, 3),
    ('Internacional (Renda Fixa)', 5, 4),
    ('Criptomoedas', 1, 5)
) AS v(class_name, target_percentage, position)
JOIN finances.strategies s ON s.name = 'Carteira Moderada'
    AND s.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com');

INSERT INTO finances.stock_holdings (wallet_id, holding_type_id, segment_id, ticker, name, current_price,
                                     strategy_class_id, class_target_percentage, frozen, status)
SELECT w.id, ht.id, sg.id, v.ticker, v.name, v.current_price, sc.id, v.class_target_percentage, v.frozen,
       v.status::finances.holding_status
FROM (VALUES
    ('Ações e FIIs', 'Ações', 'Bancos', 'BBAS3', 'Banco do Brasil ON', 23.10, 'Ações Nacionais', 10, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Bancos', 'BBDC4', 'Bradesco PN', 18.40, 'Ações Nacionais', NULL, TRUE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Bancos', 'BPAC11', 'BTG Pactual Unit', 48.90, 'Ações Nacionais', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Seguradoras', 'BBSE3', 'BB Seguridade ON', 36.80, 'Ações Nacionais', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Seguradoras', 'PSSA3', 'Porto Seguro ON', 46.20, 'Ações Nacionais', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Serviços Financeiros', 'B3SA3', 'B3 ON', 13.90, 'Ações Nacionais', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Água e Saneamento', 'SAPR4', 'Sanepar PN', 6.45, 'Ações Nacionais', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Energia Elétrica', 'CPLE3', 'Copel ON', 12.80, 'Ações Nacionais', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Energia Elétrica', 'ALUP11', 'Alupar Unit', 33.40, 'Ações Nacionais', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Energia Elétrica', 'EGIE3', 'Engie Brasil ON', 41.70, 'Ações Nacionais', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Papel e Celulose', 'KLBN4', 'Klabin PN', 3.95, 'Ações Nacionais', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Telecomunicações', 'VIVT3', 'Telefônica Brasil ON', 33.60, 'Ações Nacionais', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Bens de Capital', 'WEGE3', 'WEG ON', 49.50, 'Ações Nacionais', 12, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Agronegócio', 'AGRO3', 'BrasilAgro ON', 21.80, 'Ações Nacionais', NULL, TRUE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Petróleo e Gás', 'RECV3', 'PetroReconcavo ON', 12.10, 'Ações Nacionais', NULL, TRUE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Bens de Capital', 'TUPY3', 'Tupy ON', 15.20, 'Ações Nacionais', NULL, TRUE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Varejo', 'VIVA3', 'Vivara ON', 24.30, 'Ações Nacionais', NULL, TRUE, 'ACTIVE'),
    ('Ações e FIIs', 'Ações', 'Tecnologia', 'FIQE3', 'Unifique ON', 3.95, 'Ações Nacionais', NULL, FALSE, 'COMPLETED'),
    ('Ações e FIIs', 'FII', 'Shoppings', 'VISC11', 'Vinci Shopping Centers FII', 107.50, 'Fundos Imobiliários', 15, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'FII', 'Shoppings', 'XPML11', 'XP Malls FII', 104.20, 'Fundos Imobiliários', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'FII', 'Shoppings', 'PMLL11', 'Pátria Malls FII', 98.40, 'Fundos Imobiliários', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'FII', 'Logística', 'XPLG11', 'XP Log FII', 97.80, 'Fundos Imobiliários', 20, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'FII', 'Logística', 'BTLG11', 'BTG Pactual Logística FII', 101.30, 'Fundos Imobiliários', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'FII', 'Logística', 'HGLG11', 'Pátria Log FII', 156.90, 'Fundos Imobiliários', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'FII', 'Híbrido', 'TRXF11', 'TRX Real Estate FII', 102.60, 'Fundos Imobiliários', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'FII', 'Híbrido', 'KNRI11', 'Kinea Renda Imobiliária FII', 146.10, 'Fundos Imobiliários', NULL, FALSE, 'ACTIVE'),
    ('Ações e FIIs', 'FII', 'Recebíveis', 'KNCR11', 'Kinea Rendimentos Imobiliários FII', 104.80, 'Fundos Imobiliários', NULL, TRUE, 'ACTIVE'),
    ('Ações e FIIs', 'FII', 'Recebíveis', 'MCCI11', 'Mauá Capital Recebíveis FII', 88.20, 'Fundos Imobiliários', NULL, TRUE, 'ACTIVE'),
    ('Exterior', 'ETF', 'Core', 'VGK', 'Vanguard FTSE Europe ETF', 78.40, 'Internacional (Equity)', 30, FALSE, 'ACTIVE'),
    ('Exterior', 'ETF', 'Core', 'VWO', 'Vanguard FTSE Emerging Markets ETF', 51.20, 'Internacional (Equity)', 25, FALSE, 'ACTIVE'),
    ('Exterior', 'ETF', 'Satélite', 'SPHQ', 'Invesco S&P 500 Quality ETF', 71.30, 'Internacional (Equity)', 20, FALSE, 'ACTIVE'),
    ('Exterior', 'ETF', 'Satélite', 'AVUV', 'Avantis U.S. Small Cap Value ETF', 99.80, 'Internacional (Equity)', 15, FALSE, 'ACTIVE'),
    ('Exterior', 'ETF', 'Satélite', 'IAU', 'iShares Gold Trust', 74.50, 'Internacional (Equity)', 10, FALSE, 'ACTIVE'),
    ('Exterior', 'ETF', 'Money Market', 'SGOV', 'iShares 0-3 Month Treasury Bond ETF', 100.55, 'Internacional (Renda Fixa)', 60, FALSE, 'ACTIVE'),
    ('Exterior', 'ETF', 'Bonds High Grade', 'VCIT', 'Vanguard Intermediate-Term Corporate Bond ETF', 83.10, 'Internacional (Renda Fixa)', 40, FALSE, 'ACTIVE')
) AS v(wallet_name, type_name, segment_name, ticker, name, current_price, class_name, class_target_percentage, frozen, status)
JOIN finances.wallets w ON w.name = v.wallet_name
    AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com')
JOIN finances.holding_types ht ON ht.name = v.type_name
JOIN finances.segments sg ON sg.name = v.segment_name
JOIN finances.strategies s ON s.name = 'Carteira Moderada' AND s.user_id = w.user_id
JOIN finances.strategy_classes sc ON sc.strategy_id = s.id AND sc.name = v.class_name;

INSERT INTO finances.stock_lots (stock_holding_id, lot_date, quantity, price)
SELECT sh.id, pg_temp.seed_date(v.month_offset, v.day_of_month), v.quantity, v.price
FROM (VALUES
    ('Ações e FIIs', 'BBAS3', -21, 8, 200, 26.40),
    ('Ações e FIIs', 'BBAS3', -12, 14, 150, 24.10),
    ('Ações e FIIs', 'BBAS3', -4, 12, 200, 21.70),
    ('Ações e FIIs', 'BBAS3', -1, 6, 100, 22.40),
    ('Ações e FIIs', 'BBDC4', -22, 15, 300, 13.20),
    ('Ações e FIIs', 'BBDC4', -15, 10, 200, 15.80),
    ('Ações e FIIs', 'BPAC11', -4, 12, 60, 40.10),
    ('Ações e FIIs', 'BPAC11', -2, 20, 40, 44.60),
    ('Ações e FIIs', 'BBSE3', -4, 13, 80, 34.20),
    ('Ações e FIIs', 'BBSE3', -1, 6, 40, 36.10),
    ('Ações e FIIs', 'PSSA3', -4, 13, 50, 41.30),
    ('Ações e FIIs', 'B3SA3', -20, 22, 400, 11.80),
    ('Ações e FIIs', 'B3SA3', -9, 15, 300, 12.60),
    ('Ações e FIIs', 'SAPR4', -4, 14, 500, 5.70),
    ('Ações e FIIs', 'SAPR4', 0, 1, 300, 6.40),
    ('Ações e FIIs', 'CPLE3', -19, 6, 400, 9.10),
    ('Ações e FIIs', 'CPLE3', -8, 18, 300, 10.70),
    ('Ações e FIIs', 'ALUP11', -3, 5, 60, 31.20),
    ('Ações e FIIs', 'EGIE3', -3, 5, 40, 40.50),
    ('Ações e FIIs', 'KLBN4', -18, 20, 1500, 4.30),
    ('Ações e FIIs', 'KLBN4', -6, 11, 1000, 3.75),
    ('Ações e FIIs', 'VIVT3', -2, 21, 50, 32.10),
    ('Ações e FIIs', 'WEGE3', -22, 20, 80, 38.40),
    ('Ações e FIIs', 'WEGE3', -14, 16, 60, 47.20),
    ('Ações e FIIs', 'WEGE3', -6, 11, 40, 44.10),
    ('Ações e FIIs', 'WEGE3', 0, 1, 20, 45.20),
    ('Ações e FIIs', 'AGRO3', -17, 12, 200, 26.10),
    ('Ações e FIIs', 'RECV3', -19, 18, 500, 16.20),
    ('Ações e FIIs', 'RECV3', -10, 7, 300, 13.40),
    ('Ações e FIIs', 'TUPY3', -16, 9, 200, 24.50),
    ('Ações e FIIs', 'VIVA3', -15, 23, 150, 27.80),
    ('Ações e FIIs', 'FIQE3', -21, 13, 1000, 4.90),
    ('Ações e FIIs', 'FIQE3', -11, 20, 800, 3.60),
    ('Ações e FIIs', 'VISC11', -20, 16, 30, 112.30),
    ('Ações e FIIs', 'VISC11', -8, 12, 20, 104.60),
    ('Ações e FIIs', 'VISC11', -2, 12, 10, 103.80),
    ('Ações e FIIs', 'XPML11', -18, 22, 30, 101.90),
    ('Ações e FIIs', 'XPML11', -6, 18, 10, 99.40),
    ('Ações e FIIs', 'PMLL11', -3, 18, 40, 95.10),
    ('Ações e FIIs', 'XPLG11', -1, 12, 20, 96.30),
    ('Ações e FIIs', 'BTLG11', -3, 18, 30, 99.20),
    ('Ações e FIIs', 'BTLG11', 0, 1, 10, 100.80),
    ('Ações e FIIs', 'HGLG11', -21, 9, 20, 162.40),
    ('Ações e FIIs', 'HGLG11', -11, 23, 10, 158.30),
    ('Ações e FIIs', 'TRXF11', -3, 19, 30, 100.40),
    ('Ações e FIIs', 'KNRI11', -2, 13, 20, 141.20),
    ('Ações e FIIs', 'KNCR11', -19, 15, 40, 98.60),
    ('Ações e FIIs', 'KNCR11', -12, 19, 30, 101.20),
    ('Ações e FIIs', 'MCCI11', -17, 10, 50, 94.10),
    ('Exterior', 'VGK', -3, 12, 6, 72.10),
    ('Exterior', 'VGK', -1, 15, 1, 75.80),
    ('Exterior', 'VWO', -3, 12, 8, 47.30),
    ('Exterior', 'SGOV', -3, 12, 5, 100.40),
    ('Exterior', 'SGOV', 0, 1, 2, 100.50),
    ('Exterior', 'SPHQ', -3, 13, 4, 67.20),
    ('Exterior', 'IAU', -3, 13, 4, 62.10),
    ('Exterior', 'VCIT', -3, 13, 4, 81.60),
    ('Exterior', 'AVUV', -1, 15, 2, 94.50)
) AS v(wallet_name, ticker, month_offset, day_of_month, quantity, price)
JOIN finances.wallets w ON w.name = v.wallet_name
    AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com')
JOIN finances.stock_holdings sh ON sh.wallet_id = w.id AND sh.ticker = v.ticker;

INSERT INTO finances.crypto_holdings (wallet_id, holding_type_id, segment_id, ticker, name, current_price,
                                      strategy_class_id, class_target_percentage)
SELECT w.id, ht.id, sg.id, v.ticker, v.name, v.current_price, sc.id, v.class_target_percentage
FROM (VALUES
    ('Reserva de Valor', 'BTC', 'Bitcoin', 452000.00, 60),
    ('Smart Contracts', 'ETH', 'Ethereum', 15800.00, 25),
    ('Smart Contracts', 'SOL', 'Solana', 980.00, 10),
    ('Oráculos', 'LINK', 'Chainlink', 92.00, 5)
) AS v(segment_name, ticker, name, current_price, class_target_percentage)
JOIN finances.wallets w ON w.name = 'Cripto'
    AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com')
JOIN finances.holding_types ht ON ht.name = 'Cripto'
JOIN finances.segments sg ON sg.name = v.segment_name
JOIN finances.strategies s ON s.name = 'Carteira Moderada' AND s.user_id = w.user_id
JOIN finances.strategy_classes sc ON sc.strategy_id = s.id AND sc.name = 'Criptomoedas';

INSERT INTO finances.crypto_lots (crypto_holding_id, lot_date, quantity, price)
SELECT ch.id, pg_temp.seed_date(v.month_offset, v.day_of_month), v.quantity, v.price
FROM (VALUES
    ('BTC', -22, 18, 0.008, 535000.00),
    ('BTC', -18, 7, 0.006, 470000.00),
    ('BTC', -12, 22, 0.004, 590000.00),
    ('BTC', -6, 14, 0.004, 420000.00),
    ('ETH', -21, 11, 0.25, 12800.00),
    ('ETH', -14, 19, 0.2, 11200.00),
    ('ETH', -8, 25, 0.15, 20600.00),
    ('SOL', -20, 6, 3, 820.00),
    ('SOL', -10, 2, 2, 1150.00),
    ('LINK', -16, 3, 20, 78.00),
    ('LINK', -5, 27, 15, 105.00),
    ('LINK', -1, 18, 10, 96.00)
) AS v(ticker, month_offset, day_of_month, quantity, price)
JOIN finances.wallets w ON w.name = 'Cripto'
    AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com')
JOIN finances.crypto_holdings ch ON ch.wallet_id = w.id AND ch.ticker = v.ticker;

INSERT INTO finances.fund_holdings (wallet_id, holding_type_id, segment_id, name, current_value, administration_fee_rate,
                                    performance_fee_rate, strategy_class_id, class_target_percentage, status)
SELECT w.id, ht.id, sg.id, v.name, v.current_value, v.administration_fee_rate, v.performance_fee_rate, sc.id,
       v.class_target_percentage, v.status::finances.holding_status
FROM (VALUES
    ('Renda Fixa', 'CDB', 'Pós-fixado', 'CDB Liquidez Diária 100% CDI', 61840.00, NULL, NULL, 'Renda Fixa', 35, 'ACTIVE'),
    ('Renda Fixa', 'CDB', 'Pós-fixado', 'CDB 112% CDI 2028', 23650.00, NULL, NULL, 'Renda Fixa', NULL, 'ACTIVE'),
    ('Renda Fixa', 'LCA', 'Pós-fixado', 'LCA 94% CDI 2027', 11050.00, NULL, NULL, 'Renda Fixa', NULL, 'ACTIVE'),
    ('Renda Fixa', 'Tesouro Direto', 'Inflação', 'Tesouro IPCA+ 2035', 26480.00, NULL, NULL, 'Renda Fixa', 25, 'ACTIVE'),
    ('Renda Fixa', 'Debênture', 'Inflação', 'Debênture Taesa IPCA+ 6,80% 2032', 6890.00, NULL, NULL, 'Renda Fixa', NULL, 'ACTIVE'),
    ('Renda Fixa', 'Debênture', 'Inflação', 'Debênture Copasa IPCA+ 6,45% 2030', 6210.00, NULL, NULL, 'Renda Fixa', NULL, 'ACTIVE'),
    ('Renda Fixa', 'Tesouro Direto', 'Prefixado', 'Tesouro Prefixado 2029', 10820.00, NULL, NULL, 'Renda Fixa', NULL, 'ACTIVE'),
    ('Fundos', 'Fundo de Ações', 'Renda Variável', 'BTG Pactual Int Lo FIF Ações', 27350.00, 2.00, 20.00, NULL, NULL, 'ACTIVE'),
    ('Fundos', 'Fundo de Ações', 'Renda Variável', 'RR Palmeiras I FIF Ações', 0, 2.00, 20.00, NULL, NULL, 'COMPLETED'),
    ('Fundos', 'Fundo Multimercado', 'Multimercado', 'Growth Numeric Unlimited FIM IE', 15900.00, 1.90, 20.00, NULL, NULL, 'ACTIVE'),
    ('Fundos', 'Fundo Multimercado', 'Multimercado', 'Hawker FIF Classe FIM CP RL', 19650.00, 2.00, 20.00, NULL, NULL, 'ACTIVE'),
    ('Fundos', 'Fundo de Renda Fixa', 'Crédito Privado', 'Oak FI RF CP', 30150.00, 0.90, NULL, NULL, NULL, 'ACTIVE'),
    ('Fundos', 'Fundo de Renda Fixa', 'Crédito Privado', 'BB TOP RF Ativa LP FIF RL', 34400.00, 0.70, NULL, NULL, NULL, 'ACTIVE'),
    ('Fundos', 'COE', 'Estruturado', 'COE Autocallable Ibovespa 2029', 0, NULL, NULL, NULL, NULL, 'COMPLETED')
) AS v(wallet_name, type_name, segment_name, name, current_value, administration_fee_rate, performance_fee_rate,
       class_name, class_target_percentage, status)
JOIN finances.wallets w ON w.name = v.wallet_name
    AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com')
JOIN finances.holding_types ht ON ht.name = v.type_name
JOIN finances.segments sg ON sg.name = v.segment_name
JOIN finances.strategies s ON s.name = 'Carteira Moderada' AND s.user_id = w.user_id
LEFT JOIN finances.strategy_classes sc ON sc.strategy_id = s.id AND sc.name = v.class_name;

INSERT INTO finances.fund_contributions (fund_holding_id, contribution_date, amount)
SELECT fh.id, pg_temp.seed_date(v.month_offset, v.day_of_month), v.amount
FROM (VALUES
    ('CDB Liquidez Diária 100% CDI', -20, 5, 15000.00),
    ('CDB Liquidez Diária 100% CDI', -14, 5, 10000.00),
    ('CDB Liquidez Diária 100% CDI', -5, 22, 14000.00),
    ('CDB Liquidez Diária 100% CDI', -2, 16, 10000.00),
    ('CDB Liquidez Diária 100% CDI', 0, 1, 8500.00),
    ('CDB 112% CDI 2028', -18, 12, 12000.00),
    ('CDB 112% CDI 2028', -11, 9, 8000.00),
    ('LCA 94% CDI 2027', -10, 14, 10000.00),
    ('Tesouro IPCA+ 2035', -1, 10, 6000.00),
    ('Debênture Taesa IPCA+ 6,80% 2032', -16, 20, 6000.00),
    ('Debênture Copasa IPCA+ 6,45% 2030', -13, 6, 5500.00),
    ('Tesouro Prefixado 2029', -5, 23, 10000.00),
    ('BTG Pactual Int Lo FIF Ações', -22, 10, 15000.00),
    ('BTG Pactual Int Lo FIF Ações', -15, 15, 10000.00),
    ('BTG Pactual Int Lo FIF Ações', -9, 20, 8000.00),
    ('RR Palmeiras I FIF Ações', -21, 24, 12000.00),
    ('RR Palmeiras I FIF Ações', -13, 17, 8000.00),
    ('Growth Numeric Unlimited FIM IE', -22, 27, 20000.00),
    ('Growth Numeric Unlimited FIM IE', -16, 4, 12000.00),
    ('Hawker FIF Classe FIM CP RL', -20, 14, 15000.00),
    ('Hawker FIF Classe FIM CP RL', -12, 15, 10000.00),
    ('Hawker FIF Classe FIM CP RL', -7, 22, 6000.00),
    ('Oak FI RF CP', -22, 13, 25000.00),
    ('Oak FI RF CP', -17, 28, 15000.00),
    ('Oak FI RF CP', -8, 28, 10000.00),
    ('BB TOP RF Ativa LP FIF RL', -21, 24, 30000.00),
    ('BB TOP RF Ativa LP FIF RL', -14, 8, 15000.00),
    ('COE Autocallable Ibovespa 2029', -19, 5, 10000.00)
) AS v(name, month_offset, day_of_month, amount)
JOIN finances.fund_holdings fh ON fh.name = v.name
JOIN finances.wallets w ON w.id = fh.wallet_id
    AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com');

-- Resgates on stocks, FIIs and crypto. Cost basis is the average cost of what was held that day, the way
-- PositionExitService computes it; FIIs pay 20% IR on the profit, stock and crypto sales stay under the exemption.
INSERT INTO finances.results (stock_holding_id, crypto_holding_id, result_type, result_date, quantity, gross_amount,
                              fees, taxes, cost_basis)
WITH exits AS (
    SELECT h.kind, h.holding_id, pg_temp.seed_date(v.month_offset, v.day_of_month) AS result_date, v.quantity,
           v.quantity * v.unit_price AS gross_amount, v.fees, v.tax_rate
    FROM (VALUES
        ('Ações e FIIs', 'B3SA3', -2, 9, 200, 13.60, 4.10, 0),
        ('Ações e FIIs', 'VIVA3', -1, 14, 50, 25.10, 3.80, 0),
        ('Ações e FIIs', 'KNCR11', -1, 20, 30, 104.10, 2.50, 0.20),
        ('Cripto', 'SOL', -1, 4, 1, 1010.00, 5.05, 0)
    ) AS v(wallet_name, ticker, month_offset, day_of_month, quantity, unit_price, fees, tax_rate)
    JOIN finances.wallets w ON w.name = v.wallet_name
        AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com')
    JOIN (
        SELECT 'STOCKS' AS kind, id AS holding_id, wallet_id, ticker FROM finances.stock_holdings
        UNION ALL
        SELECT 'CRYPTO', id, wallet_id, ticker FROM finances.crypto_holdings
    ) h ON h.wallet_id = w.id AND h.ticker = v.ticker
),
costed AS (
    SELECT e.*, ROUND(e.quantity * held.cost / held.quantity, 10) AS cost_basis
    FROM exits e
    CROSS JOIN LATERAL (
        SELECT SUM(l.quantity) AS quantity, SUM(l.quantity * l.price) AS cost
        FROM (
            SELECT 'STOCKS' AS kind, stock_holding_id AS holding_id, lot_date, quantity, price FROM finances.stock_lots
            UNION ALL
            SELECT 'CRYPTO', crypto_holding_id, lot_date, quantity, price FROM finances.crypto_lots
        ) l
        WHERE l.kind = e.kind AND l.holding_id = e.holding_id AND l.lot_date <= e.result_date
    ) held
)
SELECT CASE WHEN kind = 'STOCKS' THEN holding_id END, CASE WHEN kind = 'CRYPTO' THEN holding_id END, 'WITHDRAWAL',
       result_date, quantity, gross_amount, fees, ROUND(tax_rate * GREATEST(gross_amount - fees - cost_basis, 0), 2),
       cost_basis
FROM costed;

-- Resgates of the legacy funds being phased out. Cost basis is the invested amount scaled by the share of the fund's
-- value taken out (value_before is the fund's value that day); a full exit takes all of it. 15% IR on the profit.
INSERT INTO finances.results (fund_holding_id, result_type, result_date, gross_amount, taxes, cost_basis)
SELECT fh.id, 'WITHDRAWAL', costed.result_date, costed.gross_amount,
       ROUND(0.15 * GREATEST(costed.gross_amount - costed.cost_basis, 0), 2), costed.cost_basis
FROM (VALUES
    ('Oak FI RF CP', -5, 20, 25000.00, 56300.00),
    ('RR Palmeiras I FIF Ações', -4, 10, 18500.00, 18500.00),
    ('Hawker FIF Classe FIM CP RL', -3, 8, 14000.00, 34300.00),
    ('COE Autocallable Ibovespa 2029', -2, 14, 10650.00, 10650.00),
    ('BTG Pactual Int Lo FIF Ações', 0, 1, 9000.00, 37100.00)
) AS v(name, month_offset, day_of_month, gross_amount, value_before)
JOIN finances.fund_holdings fh ON fh.name = v.name
JOIN finances.wallets w ON w.id = fh.wallet_id
    AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com')
CROSS JOIN LATERAL (
    SELECT pg_temp.seed_date(v.month_offset, v.day_of_month) AS result_date, v.gross_amount,
           ROUND((SELECT SUM(fc.amount)
                  FROM finances.fund_contributions fc
                  WHERE fc.fund_holding_id = fh.id
                    AND fc.contribution_date <= pg_temp.seed_date(v.month_offset, v.day_of_month))
                 * v.gross_amount / v.value_before, 10) AS cost_basis
) costed;

-- Reinvestments out of stocks and crypto, written the way ReinvestmentService does it: a REINVESTMENT result on the
-- source, a REINVESTMENT lot of the net amount at the destination's price, and the row joining them.
WITH moves AS (
    SELECT source.kind, source.holding_id AS source_id, destination.holding_id AS destination_id,
           pg_temp.seed_date(v.month_offset, v.day_of_month) AS reinvestment_date, v.quantity,
           v.quantity * v.unit_price AS gross_amount, v.fees, v.destination_price
    FROM (VALUES
        ('Ações e FIIs', 'FIQE3', 'VIVT3', -4, 19, 1800, 3.95, 5.60, 31.40),
        ('Ações e FIIs', 'RECV3', 'BPAC11', -3, 22, 300, 11.80, 4.20, 43.00),
        ('Cripto', 'ETH', 'BTC', 0, 1, 0.15, 15600.00, 11.70, 505000.00)
    ) AS v(wallet_name, source_ticker, destination_ticker, month_offset, day_of_month, quantity, unit_price, fees,
           destination_price)
    JOIN finances.wallets w ON w.name = v.wallet_name
        AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com')
    JOIN (
        SELECT 'STOCKS' AS kind, id AS holding_id, wallet_id, ticker FROM finances.stock_holdings
        UNION ALL
        SELECT 'CRYPTO', id, wallet_id, ticker FROM finances.crypto_holdings
    ) source ON source.wallet_id = w.id AND source.ticker = v.source_ticker
    JOIN (
        SELECT 'STOCKS' AS kind, id AS holding_id, wallet_id, ticker FROM finances.stock_holdings
        UNION ALL
        SELECT 'CRYPTO', id, wallet_id, ticker FROM finances.crypto_holdings
    ) destination ON destination.wallet_id = w.id AND destination.ticker = v.destination_ticker
),
costed AS (
    SELECT m.*,
           CASE WHEN m.quantity = held.quantity THEN held.cost
                ELSE ROUND(m.quantity * held.cost / held.quantity, 10)
           END AS cost_basis
    FROM moves m
    CROSS JOIN LATERAL (
        SELECT SUM(l.quantity) AS quantity, SUM(l.quantity * l.price) AS cost
        FROM (
            SELECT 'STOCKS' AS kind, stock_holding_id AS holding_id, lot_date, quantity, price FROM finances.stock_lots
            UNION ALL
            SELECT 'CRYPTO', crypto_holding_id, lot_date, quantity, price FROM finances.crypto_lots
        ) l
        WHERE l.kind = m.kind AND l.holding_id = m.source_id AND l.lot_date <= m.reinvestment_date
    ) held
),
new_results AS (
    INSERT INTO finances.results (stock_holding_id, crypto_holding_id, result_type, result_date, quantity, gross_amount,
                                  fees, cost_basis)
    SELECT CASE WHEN kind = 'STOCKS' THEN source_id END, CASE WHEN kind = 'CRYPTO' THEN source_id END, 'REINVESTMENT',
           reinvestment_date, quantity, gross_amount, fees, cost_basis
    FROM costed
    RETURNING id, CASE WHEN stock_holding_id IS NOT NULL THEN 'STOCKS' ELSE 'CRYPTO' END AS kind,
              COALESCE(stock_holding_id, crypto_holding_id) AS source_id, result_date, net_amount
),
new_stock_lots AS (
    INSERT INTO finances.stock_lots (stock_holding_id, lot_date, quantity, price, origin)
    SELECT c.destination_id, c.reinvestment_date, ROUND(r.net_amount / c.destination_price, 8), c.destination_price,
           'REINVESTMENT'
    FROM costed c
    JOIN new_results r ON r.kind = c.kind AND r.source_id = c.source_id
    WHERE c.kind = 'STOCKS'
    RETURNING id
),
new_crypto_lots AS (
    INSERT INTO finances.crypto_lots (crypto_holding_id, lot_date, quantity, price, origin)
    SELECT c.destination_id, c.reinvestment_date, ROUND(r.net_amount / c.destination_price, 8), c.destination_price,
           'REINVESTMENT'
    FROM costed c
    JOIN new_results r ON r.kind = c.kind AND r.source_id = c.source_id
    WHERE c.kind = 'CRYPTO'
    RETURNING id
)
INSERT INTO finances.reinvestments (result_id, destination_stock_holding_id, destination_crypto_holding_id, amount,
                                    reinvestment_date)
SELECT r.id, CASE WHEN c.kind = 'STOCKS' THEN c.destination_id END, CASE WHEN c.kind = 'CRYPTO' THEN c.destination_id END,
       r.net_amount, r.result_date
FROM new_results r
JOIN costed c ON c.kind = r.kind AND c.source_id = r.source_id;

-- Reinvestments out of legacy funds: into Tesouro IPCA+ (a contribution in another FUNDS wallet) and into XPLG11
-- (a lot at the FII's price that day).
WITH moves AS (
    SELECT source.id AS source_id, destination_fund.id AS destination_fund_id,
           destination_stock.id AS destination_stock_id, v.destination_price,
           pg_temp.seed_date(v.month_offset, v.day_of_month) AS reinvestment_date, v.gross_amount,
           ROUND((SELECT SUM(fc.amount)
                  FROM finances.fund_contributions fc
                  WHERE fc.fund_holding_id = source.id
                    AND fc.contribution_date <= pg_temp.seed_date(v.month_offset, v.day_of_month))
                 * v.gross_amount / v.value_before, 10) AS cost_basis
    FROM (VALUES
        ('Growth Numeric Unlimited FIM IE', 'Renda Fixa', 'Tesouro IPCA+ 2035', -5, 12, 20000.00, 36800.00, NULL),
        ('BB TOP RF Ativa LP FIF RL', 'Ações e FIIs', 'XPLG11', -4, 18, 15000.00, 50600.00, 95.20)
    ) AS v(source_name, destination_wallet_name, destination_name, month_offset, day_of_month, gross_amount,
           value_before, destination_price)
    JOIN finances.wallets source_wallet ON source_wallet.name = 'Fundos'
        AND source_wallet.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com')
    JOIN finances.fund_holdings source ON source.wallet_id = source_wallet.id AND source.name = v.source_name
    JOIN finances.wallets destination_wallet ON destination_wallet.name = v.destination_wallet_name
        AND destination_wallet.user_id = source_wallet.user_id
    LEFT JOIN finances.fund_holdings destination_fund ON destination_fund.wallet_id = destination_wallet.id
        AND destination_fund.name = v.destination_name
    LEFT JOIN finances.stock_holdings destination_stock ON destination_stock.wallet_id = destination_wallet.id
        AND destination_stock.ticker = v.destination_name
),
new_results AS (
    INSERT INTO finances.results (fund_holding_id, result_type, result_date, gross_amount, taxes, cost_basis)
    SELECT source_id, 'REINVESTMENT', reinvestment_date, gross_amount,
           ROUND(0.15 * GREATEST(gross_amount - cost_basis, 0), 2), cost_basis
    FROM moves
    RETURNING id, fund_holding_id AS source_id, result_date, net_amount
),
new_contributions AS (
    INSERT INTO finances.fund_contributions (fund_holding_id, contribution_date, amount, origin)
    SELECT m.destination_fund_id, m.reinvestment_date, r.net_amount, 'REINVESTMENT'
    FROM moves m
    JOIN new_results r ON r.source_id = m.source_id
    WHERE m.destination_fund_id IS NOT NULL
    RETURNING id
),
new_lots AS (
    INSERT INTO finances.stock_lots (stock_holding_id, lot_date, quantity, price, origin)
    SELECT m.destination_stock_id, m.reinvestment_date, ROUND(r.net_amount / m.destination_price, 8),
           m.destination_price, 'REINVESTMENT'
    FROM moves m
    JOIN new_results r ON r.source_id = m.source_id
    WHERE m.destination_stock_id IS NOT NULL
    RETURNING id
)
INSERT INTO finances.reinvestments (result_id, destination_stock_holding_id, destination_fund_holding_id, amount,
                                    reinvestment_date)
SELECT r.id, m.destination_stock_id, m.destination_fund_id, r.net_amount, r.result_date
FROM new_results r
JOIN moves m ON m.source_id = r.source_id;

-- The fund resgates' cash moves on to where the plan puts it: fixed income, stocks and FIIs, and the international
-- wallet (BRL to USD). What is left in Fundos stays unreconciled for the reconciliation page.
INSERT INTO finances.cash_transfers (origin_wallet_id, destination_wallet_id, transfer_date, amount, rate)
SELECT origin.id, destination.id, pg_temp.seed_date(v.month_offset, v.day_of_month), v.amount, v.rate
FROM (VALUES
    ('Fundos', 'Renda Fixa', -5, 21, 24000.00, 1),
    ('Fundos', 'Ações e FIIs', -4, 11, 18000.00, 1),
    ('Fundos', 'Exterior', -3, 10, 13000.00, 0.1866),
    ('Fundos', 'Renda Fixa', -2, 15, 10000.00, 1),
    ('Fundos', 'Renda Fixa', 0, 1, 8500.00, 1)
) AS v(origin_name, destination_name, month_offset, day_of_month, amount, rate)
JOIN finances.wallets origin ON origin.name = v.origin_name
    AND origin.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com')
JOIN finances.wallets destination ON destination.name = v.destination_name AND destination.user_id = origin.user_id;

INSERT INTO finances.cash_withdrawals (wallet_id, withdrawal_date, amount, bank_name)
SELECT w.id, pg_temp.seed_date(-1, 25), 2000.00, 'Itaú'
FROM finances.wallets w
WHERE w.name = 'Ações e FIIs'
  AND w.user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com');

UPDATE finances.wallets
SET cash_reconciled = FALSE
WHERE name = 'Fundos'
  AND user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com');

-- Daily value of every holding over its whole life: weekly until 90 days ago, then daily up to today. Units are priced
-- by interpolating between the prices they were bought and sold at and today's price; funds grow their invested amount
-- by the value/cost ratio seen at each resgate and today. Small noise keeps the lines alive and vanishes today, so
-- today's point matches holdings_overview exactly. Wallet and holding snapshots both come from this one table.
CREATE TEMP TABLE seed_holding_days AS
WITH admin_wallets AS (
    SELECT w.id
    FROM finances.wallets w
    JOIN system.users u ON u.id = w.user_id
    WHERE u.email = 'admin@admin.com'
),
unit_holdings AS (
    SELECT 'STOCKS'::finances.wallet_kind AS kind, sh.id AS holding_id, sh.wallet_id, sh.status, sh.current_price,
           0.015 AS noise
    FROM finances.stock_holdings sh
    WHERE sh.wallet_id IN (SELECT id FROM admin_wallets)
    UNION ALL
    SELECT 'CRYPTO', ch.id, ch.wallet_id, ch.status, ch.current_price, 0.025
    FROM finances.crypto_holdings ch
    WHERE ch.wallet_id IN (SELECT id FROM admin_wallets)
),
unit_events AS (
    SELECT 'STOCKS'::finances.wallet_kind AS kind, stock_holding_id AS holding_id, lot_date AS event_date,
           quantity, quantity * price AS cost, price
    FROM finances.stock_lots
    UNION ALL
    SELECT 'CRYPTO', crypto_holding_id, lot_date, quantity, quantity * price, price
    FROM finances.crypto_lots
    UNION ALL
    SELECT 'STOCKS', stock_holding_id, result_date, -quantity, -cost_basis, gross_amount / quantity
    FROM finances.results
    WHERE stock_holding_id IS NOT NULL
    UNION ALL
    SELECT 'CRYPTO', crypto_holding_id, result_date, -quantity, -cost_basis, gross_amount / quantity
    FROM finances.results
    WHERE crypto_holding_id IS NOT NULL
),
price_knots AS (
    SELECT e.kind, e.holding_id, e.event_date AS knot_date, e.price AS knot_value
    FROM unit_events e
    JOIN unit_holdings h ON h.kind = e.kind AND h.holding_id = e.holding_id
    UNION ALL
    SELECT kind, holding_id, CURRENT_DATE, current_price
    FROM unit_holdings
    WHERE status = 'ACTIVE'
),
fund_holdings_of_admin AS (
    SELECT fh.id AS holding_id, fh.wallet_id, fh.status, fh.current_value
    FROM finances.fund_holdings fh
    WHERE fh.wallet_id IN (SELECT id FROM admin_wallets)
),
fund_events AS (
    SELECT fund_holding_id AS holding_id, contribution_date AS event_date, amount AS cost
    FROM finances.fund_contributions
    UNION ALL
    SELECT fund_holding_id, result_date, -cost_basis
    FROM finances.results
    WHERE fund_holding_id IS NOT NULL
),
ratio_knots AS (
    SELECT holding_id, MIN(contribution_date) AS knot_date, 1.0 AS knot_value
    FROM finances.fund_contributions fc
    JOIN fund_holdings_of_admin f ON f.holding_id = fc.fund_holding_id
    GROUP BY holding_id
    UNION ALL
    SELECT r.fund_holding_id, r.result_date, r.gross_amount / r.cost_basis
    FROM finances.results r
    JOIN fund_holdings_of_admin f ON f.holding_id = r.fund_holding_id
    UNION ALL
    SELECT f.holding_id, CURRENT_DATE, f.current_value / held.cost
    FROM fund_holdings_of_admin f
    CROSS JOIN LATERAL (SELECT SUM(e.cost) AS cost FROM fund_events e WHERE e.holding_id = f.holding_id) held
    WHERE f.status = 'ACTIVE'
),
wallet_first_dates AS (
    SELECT h.wallet_id, MIN(e.event_date) AS first_date
    FROM unit_events e
    JOIN unit_holdings h ON h.kind = e.kind AND h.holding_id = e.holding_id
    GROUP BY h.wallet_id
    UNION ALL
    SELECT f.wallet_id, MIN(e.event_date)
    FROM fund_events e
    JOIN fund_holdings_of_admin f ON f.holding_id = e.holding_id
    GROUP BY f.wallet_id
),
snapshot_days AS (
    SELECT f.wallet_id, d::date AS snapshot_date
    FROM wallet_first_dates f
    CROSS JOIN LATERAL generate_series(f.first_date, CURRENT_DATE - 90, INTERVAL '7 days') AS d
    UNION
    SELECT f.wallet_id, d::date
    FROM wallet_first_dates f
    CROSS JOIN LATERAL generate_series(GREATEST(f.first_date, CURRENT_DATE - 89), CURRENT_DATE, INTERVAL '1 day') AS d
),
unit_days AS (
    SELECT h.kind, h.holding_id, h.wallet_id, h.status, d.snapshot_date, held.quantity, held.cost,
           CASE WHEN d.snapshot_date = CURRENT_DATE THEN h.current_price
                ELSE (CASE WHEN following.knot_date IS NULL THEN preceding.knot_value
                           ELSE preceding.knot_value + (following.knot_value - preceding.knot_value)
                                * (d.snapshot_date - preceding.knot_date)::numeric
                                / (following.knot_date - preceding.knot_date)
                      END)
                     * (1 + h.noise * (0.7 * sin(((d.snapshot_date - CURRENT_DATE) * 0.23 + h.holding_id)::float8)
                                       + 0.3 * sin(((d.snapshot_date - CURRENT_DATE) * 0.9 + h.holding_id * 1.7)::float8))::numeric)
           END AS price
    FROM snapshot_days d
    JOIN unit_holdings h ON h.wallet_id = d.wallet_id
    CROSS JOIN LATERAL (
        SELECT SUM(e.quantity) AS quantity, SUM(e.cost) AS cost
        FROM unit_events e
        WHERE e.kind = h.kind AND e.holding_id = h.holding_id AND e.event_date <= d.snapshot_date
    ) held
    CROSS JOIN LATERAL (
        SELECT k.knot_date, k.knot_value
        FROM price_knots k
        WHERE k.kind = h.kind AND k.holding_id = h.holding_id AND k.knot_date <= d.snapshot_date
        ORDER BY k.knot_date DESC
        LIMIT 1
    ) preceding
    LEFT JOIN LATERAL (
        SELECT k.knot_date, k.knot_value
        FROM price_knots k
        WHERE k.kind = h.kind AND k.holding_id = h.holding_id AND k.knot_date > d.snapshot_date
        ORDER BY k.knot_date
        LIMIT 1
    ) following ON TRUE
    WHERE held.quantity > 0
),
fund_days AS (
    SELECT f.holding_id, f.wallet_id, f.status, d.snapshot_date, held.cost,
           CASE WHEN d.snapshot_date = CURRENT_DATE THEN f.current_value
                ELSE held.cost
                     * (CASE WHEN following.knot_date IS NULL THEN preceding.knot_value
                             ELSE preceding.knot_value + (following.knot_value - preceding.knot_value)
                                  * (d.snapshot_date - preceding.knot_date)::numeric
                                  / (following.knot_date - preceding.knot_date)
                        END)
                     * (1 + 0.004 * sin(((d.snapshot_date - CURRENT_DATE) * 0.31 + f.holding_id)::float8)::numeric)
           END AS current_value
    FROM snapshot_days d
    JOIN fund_holdings_of_admin f ON f.wallet_id = d.wallet_id
    CROSS JOIN LATERAL (
        SELECT SUM(e.cost) AS cost
        FROM fund_events e
        WHERE e.holding_id = f.holding_id AND e.event_date <= d.snapshot_date
    ) held
    CROSS JOIN LATERAL (
        SELECT k.knot_date, k.knot_value
        FROM ratio_knots k
        WHERE k.holding_id = f.holding_id AND k.knot_date <= d.snapshot_date
        ORDER BY k.knot_date DESC
        LIMIT 1
    ) preceding
    LEFT JOIN LATERAL (
        SELECT k.knot_date, k.knot_value
        FROM ratio_knots k
        WHERE k.holding_id = f.holding_id AND k.knot_date > d.snapshot_date
        ORDER BY k.knot_date
        LIMIT 1
    ) following ON TRUE
    WHERE held.cost > 0
)
SELECT kind, holding_id, wallet_id, status, snapshot_date, price AS current_price, quantity * price AS current_value,
       cost AS cost_basis
FROM unit_days
UNION ALL
SELECT 'FUNDS', holding_id, wallet_id, status, snapshot_date, NULL, current_value, cost
FROM fund_days;

INSERT INTO finances.wallet_daily_snapshots (wallet_id, snapshot_date, current_value, total_invested, gain, gain_pct)
SELECT wallet_id, snapshot_date, current_value, total_invested, current_value - total_invested,
       ROUND((current_value - total_invested) / total_invested * 100, 4)
FROM (
    SELECT wallet_id, snapshot_date, ROUND(SUM(current_value), 2) AS current_value,
           ROUND(SUM(cost_basis), 2) AS total_invested
    FROM seed_holding_days
    GROUP BY wallet_id, snapshot_date
) wallet_days;

INSERT INTO finances.holding_daily_snapshots (kind, holding_id, wallet_id, snapshot_date, current_price, current_value, cost_basis)
SELECT kind, holding_id, wallet_id, snapshot_date, ROUND(current_price, 4), ROUND(current_value, 2), ROUND(cost_basis, 2)
FROM seed_holding_days
WHERE status = 'ACTIVE'
  AND snapshot_date BETWEEN CURRENT_DATE - 45 AND CURRENT_DATE - 1;

DROP TABLE seed_holding_days;
