UPDATE finances.stock_lots
SET origin = 'REINVESTMENT'
FROM finances.reinvestments
WHERE reinvestments.destination_stock_holding_id = stock_lots.stock_holding_id
  AND reinvestments.created_at = stock_lots.created_at;

UPDATE finances.crypto_lots
SET origin = 'REINVESTMENT'
FROM finances.reinvestments
WHERE reinvestments.destination_crypto_holding_id = crypto_lots.crypto_holding_id
  AND reinvestments.created_at = crypto_lots.created_at;

UPDATE finances.fund_contributions
SET origin = 'REINVESTMENT'
FROM finances.reinvestments
WHERE reinvestments.destination_fund_holding_id = fund_contributions.fund_holding_id
  AND reinvestments.created_at = fund_contributions.created_at;

UPDATE finances.stock_lots
SET origin = 'MOVE'
FROM finances.wallet_moves
JOIN finances.stock_holdings ON stock_holdings.external_id = wallet_moves.destination_holding_external_id
WHERE stock_lots.stock_holding_id = stock_holdings.id
  AND stock_lots.created_at = wallet_moves.created_at
  AND stock_lots.origin = 'PURCHASE';

UPDATE finances.crypto_lots
SET origin = 'MOVE'
FROM finances.wallet_moves
JOIN finances.crypto_holdings ON crypto_holdings.external_id = wallet_moves.destination_holding_external_id
WHERE crypto_lots.crypto_holding_id = crypto_holdings.id
  AND crypto_lots.created_at = wallet_moves.created_at
  AND crypto_lots.origin = 'PURCHASE';

UPDATE finances.fund_contributions
SET origin = 'MOVE'
FROM finances.wallet_moves
JOIN finances.fund_holdings ON fund_holdings.external_id = wallet_moves.destination_holding_external_id
WHERE fund_contributions.fund_holding_id = fund_holdings.id
  AND fund_contributions.created_at = wallet_moves.created_at
  AND fund_contributions.origin = 'PURCHASE';
