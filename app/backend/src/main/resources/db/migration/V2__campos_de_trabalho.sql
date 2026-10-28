-- Campos de trabalho das issues (Plano 5, Tarefa 1): prioridade, estimativa restante, pontos, épico, flag e componentes.
-- Todos nascem nulos (ou 0/[]) nas issues já sincronizadas: a próxima sincronização os preenche, e os cards avisam
-- "campos de trabalho indisponíveis" enquanto nenhuma issue tiver prioridade (nunca mostram "0 sem estimativa").
-- Convenções: booleano em INTEGER 0/1, lista em JSON de texto (lida com jsonParaTextos).

ALTER TABLE issue ADD COLUMN priority TEXT;
ALTER TABLE issue ADD COLUMN remaining_s INTEGER;
ALTER TABLE issue ADD COLUMN story_points REAL;
ALTER TABLE issue ADD COLUMN epic_key TEXT;
ALTER TABLE issue ADD COLUMN flagged INTEGER NOT NULL DEFAULT 0 CHECK (flagged IN (0, 1));
ALTER TABLE issue ADD COLUMN components TEXT NOT NULL DEFAULT '[]';
