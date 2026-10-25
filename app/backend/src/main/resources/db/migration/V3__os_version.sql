-- Versão do SO (campo "OS Version" do Jira) em que a task deve ser executada; nula quando o campo não vem.
-- Não é dado pessoal. A comparação do filtro é sem diferenciar maiúsculas, por isso o índice é sobre lower().
ALTER TABLE issue ADD COLUMN os_version text;

CREATE INDEX issue_os_version_idx ON issue (instance, lower(os_version));
