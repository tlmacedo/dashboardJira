-- Worklog por instância (Plano 6, Tarefa 10). A V3 é do Plano 12 (execução de backup); a V4 é a próxima livre deste bloco.
-- O worklog guarda SÓ: id do Jira, autor (username normalizado), início e segundos. Nunca o comentário nem outro texto livre.
-- Linhas antigas (hoje não existe nenhuma) ficam sem `instance` e sem `jira_id`; o índice único aceita vários nulos.

ALTER TABLE worklog ADD COLUMN instance TEXT;
ALTER TABLE worklog ADD COLUMN jira_id TEXT;

CREATE UNIQUE INDEX uq_worklog_instance_jira_id ON worklog (instance, jira_id);
CREATE INDEX idx_worklog_author_started ON worklog (author_ref, started);
