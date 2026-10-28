-- Plano 12: registro das cópias do banco (BACKUP) e dos testes de restauração dessas cópias (RESTAURACAO).
-- Guarda só o NOME do arquivo (painel-aaaa-MM-dd-HHmmss.db), nunca o caminho. Instantes em milissegundos UTC.
-- A linha de um backup é gravada DEPOIS da cópia, então a própria cópia não a contém.
CREATE TABLE backup_run (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    tipo          TEXT    NOT NULL CHECK (tipo IN ('BACKUP', 'RESTAURACAO')),
    started_at    INTEGER NOT NULL,
    finished_at   INTEGER,
    status        TEXT    NOT NULL CHECK (status IN ('OK', 'FALHA')),
    arquivo       TEXT,
    tamanho_bytes INTEGER,
    sha256        TEXT,
    codigo_erro   TEXT
);

CREATE INDEX idx_backup_run_tipo_started ON backup_run (tipo, started_at DESC);
