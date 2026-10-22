"""CLI: check (valida conexão e campos), sync, seed-demo, serve."""
from __future__ import annotations

import argparse
import json
import sys

from .config import settings


def init_wizard(args) -> int:
    from getpass import getpass
    from pathlib import Path
    from .config import ROOT
    flavor = args.flavor or (input("Jira Cloud (cloud) ou Data Center (dc)? [cloud]: ").strip() or "cloud")
    url = args.url or input("URL base (ex.: https://empresa.atlassian.net ou https://jira.empresa.com.br): ").strip()
    email = args.email if args.email is not None else (input("E-mail da conta de serviço (só Cloud): ").strip() if flavor == "cloud" else "")
    token = args.token or getpass("API token (Cloud) ou Personal Access Token (DC) — não aparece na tela: ").strip()
    env = ROOT / ".env"
    lines = [f"JIRADASH_MODE=jira", f"JIRA_FLAVOR={flavor}", f"JIRA_BASE_URL={url}", f"JIRA_EMAIL={email}", f"JIRA_TOKEN={token}",
             "JIRADASH_SYNC_MIN=10", "JIRADASH_ALLOW_WRITE=0"]
    env.write_text("\n".join(lines) + "\n", encoding="utf-8")
    try:
        env.chmod(0o600)
    except OSError:
        pass
    print(f"Gravado {env} (não versione este arquivo).")
    import os
    os.environ.update({"JIRADASH_MODE": "jira", "JIRA_FLAVOR": flavor, "JIRA_BASE_URL": url, "JIRA_EMAIL": email, "JIRA_TOKEN": token})
    from .jira.client import JiraClient
    try:
        me = JiraClient(flavor, url, token, email).myself()
        print("Conexão OK. Conectado como:", me.get("displayName"))
    except Exception as e:  # noqa: BLE001
        print("FALHA na conexão:", e)
        return 1
    print("Próximo passo: python -m jiradash discover   (gera o rascunho de config/equipes.yaml)")
    return 0


def run_discover(args) -> int:
    from .config import ROOT
    from .jira.discover import discover, draft_yaml
    from .jobs import make_client
    keys = [k.strip() for k in args.projects.split(",")] if args.projects else None
    info = discover(make_client(), keys)
    out = ROOT / "config" / "equipes.gerado.yaml"
    out.write_text(draft_yaml(info), encoding="utf-8")
    print(f"{len(info['projects'])} projetos, {len(info['status_map'])} status, {len(info['users'])} usuários.")
    print("Tipos de issue:", ", ".join(info["issue_types"]))
    print(f"Rascunho em {out}. Revise e salve como config/equipes.yaml.")
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(prog="jiradash")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("check", help="testa credenciais, descobre campos e valida a configuração de equipes")
    sp = sub.add_parser("sync", help="sincroniza do Jira")
    sp.add_argument("--full", action="store_true")
    sub.add_parser("serve", help="sobe a API e o front")
    ip = sub.add_parser("init", help="assistente: grava o .env com servidor e credenciais e testa a conexão")
    ip.add_argument("--flavor", choices=["cloud", "dc"])
    ip.add_argument("--url")
    ip.add_argument("--email")
    ip.add_argument("--token")
    dp = sub.add_parser("discover", help="lista projetos, status, tipos e usuários do Jira e gera config/equipes.gerado.yaml")
    dp.add_argument("--projects", help="chaves separadas por vírgula (padrão: todos)")
    args = ap.parse_args(argv)

    if args.cmd == "init":
        return init_wizard(args)
    if args.cmd == "discover":
        return run_discover(args)
    if args.cmd == "serve":
        import uvicorn
        uvicorn.run("jiradash.main:app", host="0.0.0.0", port=8000)
        return 0

    from .db import Base, SessionLocal, engine
    Base.metadata.create_all(engine)
    from . import teams
    from .jobs import make_client, run_sync

    with SessionLocal() as db:
        r = teams.bootstrap(db, online_holidays=True)
        print("equipes carregadas:", r)
        if args.cmd == "check":
            c = make_client()
            me = c.myself()
            print("conectado como:", me.get("displayName"), "| variante:", settings.jira_flavor)
            print("campos descobertos:", json.dumps(c.discover_field_ids()))
            from .config import load_teams
            cfg = load_teams()
            jql = cfg.get("jql_scope", "project is not EMPTY")
            first = next(iter(c.search(jql + " ORDER BY updated DESC", ["status", "assignee", "project"])), None)
            if not first:
                print("AVISO: o jql_scope não retornou issues")
                return 1
            st = first["fields"]["status"]["name"]
            print("amostra:", first["key"], "| status:", st, "| mapeado como:", cfg.get("status_map", {}).get(st, "NÃO MAPEADO"))
            unmapped = []
            for i in c.search(jql + " ORDER BY updated DESC", ["status"]):
                n = i["fields"]["status"]["name"]
                if n not in cfg.get("status_map", {}) and n not in unmapped:
                    unmapped.append(n)
                if len(unmapped) > 20:
                    break
            if unmapped:
                print("ATENÇÃO: status sem mapeamento em status_map:", unmapped)
            return 0
        if args.cmd == "sync":
            print(json.dumps(run_sync(db, args.full), ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
