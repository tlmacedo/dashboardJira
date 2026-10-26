-- Marcador de zeragem da contagem de falhas seguidas: só FALHAs com id maior que este valor contam.
-- Gravado no reinício do app (limparSuspensoes), sem depender de relógio.

ALTER TABLE sync_state ADD COLUMN falhas_desde_run_id bigint;
