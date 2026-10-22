"""Configuração por variáveis de ambiente e arquivo YAML de equipes."""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
CONFIG_DIR = Path(os.getenv("JIRADASH_CONFIG_DIR", ROOT / "config"))


@dataclass
class Settings:
    # "demo" gera dados fictícios; "jira" sincroniza do servidor configurado
    mode: str = os.getenv("JIRADASH_MODE", "demo")
    database_url: str = os.getenv("DATABASE_URL", "")
    jira_flavor: str = os.getenv("JIRA_FLAVOR", "cloud")  # cloud | dc
    jira_base_url: str = os.getenv("JIRA_BASE_URL", "")
    jira_email: str = os.getenv("JIRA_EMAIL", "")  # só Cloud
    jira_token: str = os.getenv("JIRA_TOKEN", "")  # API token (Cloud) ou PAT (DC)
    sync_interval_min: int = int(os.getenv("JIRADASH_SYNC_MIN", "10"))
    # Escrita no Jira desligada por padrão: o motor só sugere (modo sombra)
    allow_jira_write: bool = os.getenv("JIRADASH_ALLOW_WRITE", "0") == "1"
    api_key: str = os.getenv("JIRADASH_API_KEY", "")  # vazio = sem autenticação (só dev)
    tz: str = os.getenv("JIRADASH_TZ", "America/Sao_Paulo")

    def db_url(self) -> str:
        if self.database_url:
            return self.database_url
        name = "demo.db" if self.mode == "demo" else "jiradash.db"
        data = ROOT / "data"
        data.mkdir(exist_ok=True)
        return f"sqlite:///{data / name}"


settings = Settings()


def load_teams() -> dict:
    """Lê config/equipes.yaml (equipes, pessoas, mapeamento de status)."""
    name = "equipes.demo.yaml" if settings.mode == "demo" else "equipes.yaml"
    path = CONFIG_DIR / name
    if not path.exists():
        raise FileNotFoundError(
            f"{path} não encontrado. Copie config/equipes.exemplo.yaml para config/equipes.yaml."
        )
    with open(path, encoding="utf-8") as f:
        return yaml.safe_load(f) or {}
