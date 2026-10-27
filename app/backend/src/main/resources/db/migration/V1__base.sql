-- Esquema do Painel de Squads em SQLite (arquivo local; sem servidor).
-- Convenções: instantes em INTEGER (milissegundos desde 1970, UTC), prazo (duedate) em TEXT ISO aaaa-mm-dd,
-- listas e o JSON bruto em TEXT (lidos com json_each/json_extract), booleanos em INTEGER 0/1.
-- Este arquivo consolida o esquema final (antes eram as migrações V1 a V6 do PostgreSQL).

CREATE TABLE person (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    jira_ref TEXT    NOT NULL UNIQUE,
    name     TEXT    NOT NULL,
    active   INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE issue (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    key          TEXT    NOT NULL,
    instance     TEXT    NOT NULL,
    project      TEXT    NOT NULL,
    issue_type   TEXT,
    status       TEXT,
    resolution   TEXT,
    assignee_ref TEXT,
    reporter_ref TEXT,
    created      INTEGER,
    updated      INTEGER,
    resolved     INTEGER,
    duedate      TEXT,
    labels       TEXT    NOT NULL DEFAULT '[]',
    parent_key   TEXT,
    estimate_s   INTEGER,
    spent_s      INTEGER,
    raw          TEXT,
    -- Versão do SO (campo "OS Version" do Jira) em que a task deve ser executada; nula quando o campo não vem.
    os_version   TEXT,
    jira_id      INTEGER,
    removida_em  INTEGER,
    -- A chave da issue só é única dentro da instância do Jira.
    CONSTRAINT issue_instance_key_key UNIQUE (instance, key)
);

CREATE TABLE change_event (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    changelog_id TEXT    NOT NULL,
    issue_id     INTEGER NOT NULL REFERENCES issue (id) ON DELETE CASCADE,
    field        TEXT    NOT NULL,
    from_value   TEXT,
    to_value     TEXT,
    at           INTEGER NOT NULL,
    author_ref   TEXT,
    -- O id do histórico só é único dentro da issue.
    CONSTRAINT change_event_issue_changelog_key UNIQUE (issue_id, changelog_id)
);

CREATE TABLE worklog (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    issue_id   INTEGER NOT NULL REFERENCES issue (id) ON DELETE CASCADE,
    author_ref TEXT,
    started    INTEGER NOT NULL,
    seconds    INTEGER NOT NULL
);

-- Estado da sincronização por instância (e da retenção, em linha própria).
CREATE TABLE sync_state (
    instance            TEXT PRIMARY KEY,
    watermark           INTEGER,
    ultima_reconciliacao INTEGER,
    suspensa_codigo     TEXT,
    suspensa_em         INTEGER,
    -- Marcador de zeragem da contagem de falhas seguidas: só FALHAs com id maior que este valor contam.
    falhas_desde_run_id INTEGER
);

CREATE TABLE access_log (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT    NOT NULL,
    route   TEXT    NOT NULL,
    filters TEXT,
    at      INTEGER NOT NULL
);

-- Histórico de execuções da sincronização. Só guarda códigos e contagens; nunca texto livre de erro.
CREATE TABLE sync_run (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    instance                TEXT    NOT NULL,
    kind                    TEXT    NOT NULL CHECK (kind IN ('INCREMENTAL', 'RECONCILIACAO', 'RETENCAO')),
    started_at              INTEGER NOT NULL,
    finished_at             INTEGER,
    status                  TEXT    NOT NULL CHECK (status IN ('EM_ANDAMENTO', 'OK', 'FALHA', 'CANCELADA')),
    issues                  INTEGER NOT NULL DEFAULT 0,
    eventos                 INTEGER NOT NULL DEFAULT 0,
    requisicoes             INTEGER NOT NULL DEFAULT 0,
    incompletas             INTEGER NOT NULL DEFAULT 0,
    usuarios_nao_resolvidos INTEGER NOT NULL DEFAULT 0,
    removidas               INTEGER NOT NULL DEFAULT 0,
    codigo_erro             TEXT,
    http_status             INTEGER
);

-- Mapa de chaves de usuário (Data Center) para username.
CREATE TABLE jira_user_key (
    instance TEXT    NOT NULL,
    user_key TEXT    NOT NULL,
    username TEXT    NOT NULL,
    visto_em INTEGER NOT NULL,
    PRIMARY KEY (instance, user_key)
);

-- Vínculos entre issues do Jira (fields.issuelinks), por exemplo "Blocks / is blocked by".
-- Uma linha por link do Jira, vista por qualquer das duas pontas. Guarda SÓ chaves e o nome do tipo:
-- nada de resumo, status ou outro dado da outra ponta. Sem FK para issue: a outra ponta pode não estar
-- na base (fora da ficha de sincronização ou de outro projeto).
CREATE TABLE issue_link (
    instance    TEXT    NOT NULL,
    from_key    TEXT    NOT NULL,   -- ponta outward ("blocks"); em tipo simétrico, a menor chave natural
    to_key      TEXT    NOT NULL,   -- ponta inward ("is blocked by")
    link_type   TEXT    NOT NULL,   -- type.name do Jira (ex.: "Blocks")
    direction   TEXT    NOT NULL CHECK (direction IN ('OUTWARD', 'SIMETRICO')),
    visto_em    INTEGER NOT NULL,
    removida_em INTEGER,            -- o link sumiu no Jira (remoção lógica; a retenção apaga depois)
    PRIMARY KEY (instance, from_key, to_key, link_type),
    CHECK (from_key <> to_key)
);

CREATE INDEX idx_change_event_issue_at ON change_event (issue_id, at);
CREATE INDEX idx_issue_status_resolved ON issue (status, resolved);
CREATE INDEX idx_worklog_started ON worklog (started);
CREATE INDEX issue_os_version_idx ON issue (instance, lower(os_version));
CREATE UNIQUE INDEX uq_issue_instance_jira_id ON issue (instance, jira_id) WHERE jira_id IS NOT NULL;
CREATE INDEX idx_sync_run_instance_kind_started ON sync_run (instance, kind, started_at DESC);
CREATE INDEX issue_link_to ON issue_link (instance, to_key);
CREATE INDEX issue_link_tipo_ativo ON issue_link (instance, link_type) WHERE removida_em IS NULL;
