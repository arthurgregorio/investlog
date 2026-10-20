WITH residues AS (
    SELECT
        holdings_overview.kind,
        holdings_overview.holding_id,
        holdings_overview.cost_basis AS residue
    FROM finances.holdings_overview
    WHERE holdings_overview.status = 'COMPLETED'
      AND holdings_overview.kind IN ('STOCKS', 'CRYPTO')
      AND holdings_overview.cost_basis <> 0
      AND ABS(holdings_overview.cost_basis) < 0.000001
),
latest_results AS (
    SELECT DISTINCT ON (residues.kind, residues.holding_id)
        results.id AS result_id,
        residues.residue
    FROM residues
    JOIN finances.results
        ON (residues.kind = 'STOCKS' AND results.stock_holding_id = residues.holding_id)
        OR (residues.kind = 'CRYPTO' AND results.crypto_holding_id = residues.holding_id)
    ORDER BY residues.kind, residues.holding_id, results.result_date DESC, results.id DESC
)
UPDATE finances.results
SET cost_basis = results.cost_basis + latest_results.residue
FROM latest_results
WHERE results.id = latest_results.result_id
  AND results.cost_basis + latest_results.residue >= 0;
