-- Dados para a auditoria completa (Plano 11, Tarefa 3, opção A: estimativas e criação do worklog).
--
-- 1) O worklog passa a guardar QUANDO o registro foi criado no Jira (milissegundos UTC, anulável: registro antigo fica sem a data).
--    Continua sem comentário e sem texto livre.
ALTER TABLE worklog ADD COLUMN criado_em INTEGER;

-- 2) O `change_event` passa a guardar o valor (segundos) das estimativas. Os eventos antigos não têm o valor: zera-se a marca d'água
--    para que a PRÓXIMA sincronização (só quando o usuário ligar a flag do Jira) refaça a carga com o changelog e preencha os valores.
--    Com a flag desligada nada é sincronizado; esta migração não faz requisição.
UPDATE sync_state SET watermark = NULL;

-- 3) Minimização: a tabela `person` (jira_ref e nome) não tem uso no código de produção; nome de pessoa sem finalidade sai do esquema.
DROP TABLE IF EXISTS person;
