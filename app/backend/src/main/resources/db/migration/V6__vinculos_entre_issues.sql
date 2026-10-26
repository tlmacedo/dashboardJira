-- Vínculos entre issues do Jira (fields.issuelinks), por exemplo "Blocks / is blocked by".
-- Uma linha por link do Jira, vista por qualquer das duas pontas. Guarda SÓ chaves e o nome do tipo:
-- nada de resumo, status ou outro dado da outra ponta. Sem FK para issue: a outra ponta pode não estar
-- na base (fora da ficha de sincronização ou de outro projeto).
CREATE TABLE issue_link (
    instance    text NOT NULL,
    from_key    text NOT NULL,              -- ponta outward ("blocks"); em tipo simétrico, a menor chave natural
    to_key      text NOT NULL,              -- ponta inward ("is blocked by")
    link_type   text NOT NULL,              -- type.name do Jira (ex.: "Blocks")
    direction   text NOT NULL CHECK (direction IN ('OUTWARD', 'SIMETRICO')),
    visto_em    timestamptz NOT NULL,
    removida_em timestamptz,                -- o link sumiu no Jira (remoção lógica; a retenção apaga depois)
    PRIMARY KEY (instance, from_key, to_key, link_type),
    CHECK (from_key <> to_key)
);

CREATE INDEX issue_link_to ON issue_link (instance, to_key);
CREATE INDEX issue_link_tipo_ativo ON issue_link (instance, link_type) WHERE removida_em IS NULL;
