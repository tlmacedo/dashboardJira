-- Campos do dicionário por issue (Plano 10, Tarefa 1): Checklist Type, SKU Type, Model Name, Model Category,
-- TG Request Type e Carrier Code (os campos com `filtro: true` no jira_campos.yaml).
--
-- `campos_dicionario`: objeto JSON em texto (nome do campo -> valor). NULL = ainda não extraído; '{}' = extraído e vazio.
-- Os filtros leem com json_extract(campos_dicionario, '$."Model Name"') e, para listar valores, com json_each. Não há
-- índice: o SQLite não tem GIN; o orçamento de desempenho mede e, só se estourar, uma migração própria cria um índice de
-- expressão sobre json_extract.
--
-- `campos_dicionario_em`: instante (ms UTC) em que uma sincronização que JÁ pedia os campos gravou a linha. Linhas só
-- preenchidas do `raw` ficam com NULL: o raw antigo pode não ter os campos que a ficha passou a pedir, e a reconciliação
-- completa (com a flag do Jira ligada pelo usuário) as completa. O aviso CAMPOS_DO_DICIONARIO_INCOMPLETOS conta essas.

ALTER TABLE issue ADD COLUMN campos_dicionario TEXT;
ALTER TABLE issue ADD COLUMN campos_dicionario_em INTEGER;
