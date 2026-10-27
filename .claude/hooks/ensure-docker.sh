#!/bin/sh
# Obsoleto: o projeto não usa mais servidor de banco nem contêiner (o banco é um arquivo SQLite).
# Mantido como no-op só para que gatilhos antigos não falhem; pode ser removido junto com a referência em settings.json.
echo "Nada a iniciar: o painel usa SQLite em arquivo (sem Docker)."
exit 0
