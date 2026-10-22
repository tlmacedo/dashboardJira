"""CLI: check (valida conexão e campos), sync, seed-demo, serve."""
from __future__ import annotations

import argparse
import json
import sys

from .config import settings


def main(argv=None):
    ap = argparse.ArgumentParser(prog="jiradash")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("check", help="testa credenciais, descobre campos e valida a configuração de equipes")
    sp = sub.add_parser("sync", help="sincroniza do Jira")
    sp.add_argument("--full", action="store_true")
    sub.add_parser("serve", help="sobe a API e o front")
    args = ap.parse_args(argv)

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
