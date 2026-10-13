#!/usr/bin/env python3
"""Tempo em status, passagens, tempo de ciclo e reaberturas a partir do changelog do Jira.

Lê as issues pelo REST do Jira Data Center (``/rest/api/2/search`` com
``expand=changelog``) e calcula tudo localmente, só com a biblioteca padrão.

O cálculo é nosso (não vem do Jira) e foi validado apenas com fixtures no
formato do REST; não foi validado contra um Jira real. Confira algumas issues
de histórico conhecido antes de confiar nos números.
"""

import argparse
import csv
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone

AJUDA_CSV = """\
Colunas do CSV (uma linha por issue):
  chave                    chave da issue (ex.: ABC-1)
  tempo_<status>_horas     horas acumuladas em cada status encontrado, com uma
                           casa decimal (colunas em ordem alfabética do status)
  passagens_<status>       quantas vezes a issue entrou no status (o status
                           inicial conta como 1 passagem)
  ciclo_horas              horas entre a primeira entrada em --status-inicio e a
                           primeira entrada em --status-fim depois dela; vazio
                           se não houver o par
  reaberturas              quantas vezes a issue saiu de --status-fim para outro
                           status

O último intervalo de cada issue termina em resolutiondate, se existir; senão,
no instante atual. O token (PAT) é lido da variável de ambiente indicada em
--token-env. O cálculo foi validado só com fixtures, não contra um Jira real.
"""


# Nome usado quando a primeira transição não informa o status de origem.
STATUS_DESCONHECIDO = "(desconhecido)"


def analisar_data(texto):
    """Converte datas do Jira (+0000, com ou sem milissegundos) em datetime com fuso."""
    for formato in ("%Y-%m-%dT%H:%M:%S.%f%z", "%Y-%m-%dT%H:%M:%S%z"):
        try:
            return datetime.strptime(texto, formato)
        except ValueError:
            continue
    raise ValueError(f"Data em formato desconhecido: {texto!r}")


def transicoes_de_status(issue):
    """Lista as transições de status em ordem cronológica."""
    resultado = []
    historico = (issue.get("changelog") or {}).get("histories") or []
    for h in historico:
        for item in h.get("items") or []:
            if item.get("field") != "status":
                continue
            resultado.append({
                "quando": analisar_data(h["created"]),
                "autor": (h.get("author") or {}).get("name", ""),
                "de": item.get("fromString") or STATUS_DESCONHECIDO,
                "para": item.get("toString"),
            })
    resultado.sort(key=lambda x: x["quando"])  # ordenação estável
    return resultado


def intervalos(issue, agora):
    """Intervalos (status, inicio, fim, autor_entrada) da criação até a resolução ou agora."""
    campos = issue["fields"]
    criada = analisar_data(campos["created"])
    fim_final = analisar_data(campos["resolutiondate"]) if campos.get("resolutiondate") else agora
    transicoes = transicoes_de_status(issue)

    if transicoes:
        status = transicoes[0]["de"]
    else:
        status = (campos.get("status") or {}).get("name")
    inicio, autor = criada, ""
    resultado = []
    for tr in transicoes:
        resultado.append({"status": status, "inicio": inicio, "fim": tr["quando"],
                          "autor_entrada": autor})
        status, inicio, autor = tr["para"], tr["quando"], tr["autor"]
    # Último intervalo: nunca negativo (resolutiondate pode ser anterior à última entrada).
    resultado.append({"status": status, "inicio": inicio, "fim": max(inicio, fim_final),
                      "autor_entrada": autor})
    return resultado


def tempo_por_status(issue, agora):
    total = {}
    for iv in intervalos(issue, agora):
        total[iv["status"]] = total.get(iv["status"], timedelta()) + (iv["fim"] - iv["inicio"])
    return total


def passagens_por_status(issue):
    """Conta entradas em cada status; o status inicial conta como 1."""
    contagem = {}
    for iv in intervalos(issue, datetime.now(timezone.utc)):
        contagem[iv["status"]] = contagem.get(iv["status"], 0) + 1
    return contagem


def tempo_de_ciclo(issue, status_inicio, status_fim):
    """Da primeira entrada em status_inicio até a primeira entrada em status_fim depois dela."""
    entradas = [(iv["status"], iv["inicio"]) for iv in intervalos(issue, datetime.now(timezone.utc))]
    inicio = next((q for s, q in entradas if s == status_inicio), None)
    if inicio is None:
        return None
    fim = next((q for s, q in entradas if s == status_fim and q > inicio), None)
    return None if fim is None else fim - inicio


def reaberturas(issue, de_status, para_status=None):
    """Conta transições de de_status para para_status (ou para qualquer outro, se None)."""
    return sum(1 for tr in transicoes_de_status(issue)
               if tr["de"] == de_status and (para_status is None or tr["para"] == para_status))


def _ler_pagina(req, abrir, dormir, max_tentativas):
    for tentativa in range(1, max_tentativas + 1):
        try:
            resposta = abrir(req, timeout=60)
            try:
                return json.loads(resposta.read().decode("utf-8"))
            finally:
                if hasattr(resposta, "close"):
                    resposta.close()
        except urllib.error.HTTPError as erro:
            erro.close()  # libera o corpo da resposta
            if erro.code != 429:
                raise
            if tentativa == max_tentativas:
                break
            try:
                espera = float(erro.headers.get("retry-after"))
            except (TypeError, ValueError):
                espera = 5
            dormir(int(espera) if espera == int(espera) else espera)
    raise RuntimeError(
        f"O Jira respondeu 429 (limite de requisições) em {max_tentativas} tentativas seguidas; "
        "tente novamente mais tarde.")


def buscar_issues(url, token, jql, abrir=urllib.request.urlopen, dormir=time.sleep,
                  tamanho_pagina=100, max_tentativas=5):
    """Gerador de issues do /rest/api/2/search, com paginação e espera em 429."""
    inicio = 0
    while True:
        consulta = urllib.parse.urlencode({
            "jql": jql, "expand": "changelog",
            "fields": "status,created,resolutiondate",
            "startAt": inicio, "maxResults": tamanho_pagina,
        })
        req = urllib.request.Request(
            f"{url.rstrip('/')}/rest/api/2/search?{consulta}",
            headers={"Authorization": f"Bearer {token}", "Accept": "application/json"})
        pagina = _ler_pagina(req, abrir, dormir, max_tentativas)
        issues = pagina.get("issues") or []
        yield from issues
        inicio = pagina.get("startAt", inicio) + len(issues)
        if not issues or inicio >= pagina.get("total", 0):
            return


def _horas(delta):
    return f"{delta.total_seconds() / 3600:.1f}"


def linhas_csv(issues, agora, status_inicio, status_fim):
    """Cabeçalho + uma linha por issue."""
    issues = list(issues)
    tempos = [tempo_por_status(i, agora) for i in issues]
    passagens = [passagens_por_status(i) for i in issues]
    status = sorted({s for d in tempos for s in d})
    cabecalho = (["chave"] + [f"tempo_{s}_horas" for s in status]
                 + [f"passagens_{s}" for s in status] + ["ciclo_horas", "reaberturas"])
    linhas = [cabecalho]
    for issue, tp, ps in zip(issues, tempos, passagens):
        ciclo = tempo_de_ciclo(issue, status_inicio, status_fim)
        linhas.append(
            [issue.get("key", "")]
            + [_horas(tp[s]) if s in tp else "0.0" for s in status]
            + [str(ps.get(s, 0)) for s in status]
            + ["" if ciclo is None else _horas(ciclo), str(reaberturas(issue, status_fim))])
    return linhas


def main(argv=None, abrir=urllib.request.urlopen, dormir=time.sleep):
    parser = argparse.ArgumentParser(
        description="Calcula tempo em status, passagens, tempo de ciclo e reaberturas "
                    "a partir do changelog do Jira (REST).",
        epilog=AJUDA_CSV, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", required=True, help="endereço base do Jira (ex.: https://jira.exemplo.com)")
    parser.add_argument("--jql", required=True, help="JQL que seleciona as issues")
    parser.add_argument("--token-env", required=True,
                        help="nome da variável de ambiente que contém o token de acesso pessoal (PAT)")
    parser.add_argument("--saida", help="arquivo CSV de saída (padrão: saída padrão)")
    parser.add_argument("--status-inicio", default="In Progress",
                        help="status em que o tempo de ciclo começa (padrão: In Progress)")
    parser.add_argument("--status-fim", default="Done",
                        help="status em que o tempo de ciclo termina (padrão: Done)")
    args = parser.parse_args(argv)

    token = os.environ.get(args.token_env)
    if not token:
        print(f"Variável de ambiente {args.token_env} não definida ou vazia.", file=sys.stderr)
        return 1

    agora = datetime.now(timezone.utc)
    # Mensagens curtas: sem URL, JQL nem token.
    try:
        issues = list(buscar_issues(args.url, token, args.jql, abrir=abrir, dormir=dormir))
    except urllib.error.HTTPError as erro:
        dica = " Verifique o PAT e as permissões." if erro.code in (401, 403) else ""
        print(f"O Jira respondeu com erro HTTP {erro.code}.{dica}", file=sys.stderr)
        return 1
    except urllib.error.URLError:
        print("Falha de conexão com o Jira; verifique o endereço e a rede.", file=sys.stderr)
        return 1
    except RuntimeError as erro:
        print(str(erro), file=sys.stderr)
        return 1
    linhas = linhas_csv(issues, agora, args.status_inicio, args.status_fim)
    if args.saida:
        with open(args.saida, "w", newline="", encoding="utf-8") as f:
            csv.writer(f).writerows(linhas)
    else:
        csv.writer(sys.stdout).writerows(linhas)
    return 0


if __name__ == "__main__":
    sys.exit(main())
