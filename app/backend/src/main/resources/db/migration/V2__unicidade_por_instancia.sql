-- A chave da issue só é única dentro da instância do Jira, e o id do histórico só dentro da issue.
-- Os nomes abaixo são os gerados pelo PostgreSQL para as restrições UNIQUE inline da V1.

ALTER TABLE issue DROP CONSTRAINT issue_key_key;
ALTER TABLE issue ADD CONSTRAINT issue_instance_key_key UNIQUE (instance, key);

ALTER TABLE change_event DROP CONSTRAINT change_event_changelog_id_key;
ALTER TABLE change_event ADD CONSTRAINT change_event_issue_changelog_key UNIQUE (issue_id, changelog_id);
