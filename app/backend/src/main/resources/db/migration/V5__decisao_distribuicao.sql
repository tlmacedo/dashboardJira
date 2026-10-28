-- Decisões de Distribuição e Rebalanceamento (Plano 7, bloco 7b, Tarefa 11). A V4 é do Plano 6b (worklog); esta é a próxima livre.
-- É o ÚNICO dado que o painel grava sobre a Distribuição: quem decidiu o quê sobre uma sugestão. Nada vai ao Jira (modo sombra).
-- `chave_sugestao` (UNIQUE) = hash de (instância, tipo, task, sugerido, versão das regras, dia de Manaus): duas decisões sobre a mesma
-- sugestão no mesmo dia (dois cliques, duas abas) caem na restrição e a segunda é recusada (409). O texto do motivo é livre (até 500
-- caracteres, saneado) e NUNCA vai para o `access_log`.

CREATE TABLE decisao_distribuicao (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    instance       TEXT    NOT NULL,
    tipo           TEXT    NOT NULL CHECK (tipo IN ('DISTRIBUICAO', 'REBALANCEAMENTO')),
    issue_key      TEXT    NOT NULL,
    sugerido       TEXT,
    escolhido      TEXT,
    decisao        TEXT    NOT NULL CHECK (decisao IN ('APROVADA', 'TROCADA', 'REJEITADA')),
    motivo         TEXT,
    decidido_por   TEXT    NOT NULL,
    decidido_em    INTEGER NOT NULL,
    versao_regras  TEXT    NOT NULL,
    chave_sugestao TEXT    NOT NULL UNIQUE
);

CREATE INDEX idx_decisao_distribuicao_em ON decisao_distribuicao (instance, decidido_em);
