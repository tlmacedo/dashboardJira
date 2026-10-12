#!/usr/bin/env python3
"""Verifica a consistência dos capítulos do guia do dashboard Jira.

Uso:
    python3 scripts/check_guia.py              # verifica os capítulos que existem
    python3 scripts/check_guia.py --completo   # exige todos os capítulos
"""
import re
import sys
from pathlib import Path

GUIA = Path(__file__).resolve().parent.parent / "docs" / "guia"

CAPITULOS = [
    "README.md",
    "00-pre-requisitos.md",
    "01-filtros-salvos.md",
    "02-resumo-da-equipe.md",
    "03-negligenciados-e-horas.md",
    "04-tempo-por-status.md",
    "05-datas-e-botao-de-sincronizacao.md",
    "06-layout-permissoes-e-validacao.md",
]

PROIBIDOS = re.compile(r"\b(TBD|TODO|XXX|FIXME)\b")
BLOCO_JQL = re.compile(r"```jql\n(.*?)```", re.S)
NOME_FILTRO = re.compile(r"^\*\*Nome:\*\* (.+)$", re.M)
CRITERIO = re.compile(r"^\*\*Critério de data:\*\* .+$", re.M)
REFERENCIA = re.compile(r'filter\s*=\s*"([^"]+)"')


def verificar_texto(nome, texto):
    """Retorna a lista de erros de um único arquivo."""
    erros = []
    if PROIBIDOS.search(texto):
        erros.append(f"{nome}: marcador proibido (TBD/TODO/XXX/FIXME)")
    for i, bloco in enumerate(BLOCO_JQL.findall(texto), 1):
        if bloco.count("(") != bloco.count(")"):
            erros.append(f"{nome}: JQL #{i} com parênteses desbalanceados")
        if bloco.count('"') % 2:
            erros.append(f"{nome}: JQL #{i} com aspas desbalanceadas")
        if re.search("accountid", bloco, re.I):
            erros.append(f"{nome}: JQL #{i} usa accountId (não se aplica ao Data Center)")
        if re.search(r"\b\d{2}/\d{2}/\d{4}\b", bloco):
            erros.append(f"{nome}: JQL #{i} com data dd/mm/aaaa (use aaaa-mm-dd)")
    nomes = NOME_FILTRO.findall(texto)
    for n in nomes:
        if not n.startswith("EQP | "):
            erros.append(f"{nome}: nome de filtro fora do padrão 'EQP | ...': {n}")
    if len(nomes) != len(CRITERIO.findall(texto)):
        erros.append(f"{nome}: número de nomes de filtro difere do número de critérios de data")
    return erros


def verificar_referencias(textos):
    """Confere que todo filter = "X" aponta para um filtro definido no guia."""
    definidos = {n.strip() for t in textos.values() for n in NOME_FILTRO.findall(t)}
    erros = []
    for nome, texto in textos.items():
        for bloco in BLOCO_JQL.findall(texto):
            for ref in REFERENCIA.findall(bloco):
                if ref not in definidos:
                    erros.append(f"{nome}: referência a filtro não definido: {ref}")
    return erros


def main(argv):
    completo = "--completo" in argv
    textos = {}
    erros = []
    for capitulo in CAPITULOS:
        caminho = GUIA / capitulo
        if caminho.exists():
            textos[capitulo] = caminho.read_text(encoding="utf-8")
        elif completo:
            erros.append(f"{capitulo}: arquivo ausente")
    for capitulo, texto in textos.items():
        erros += verificar_texto(capitulo, texto)
    erros += verificar_referencias(textos)
    for erro in erros:
        print(erro)
    print(f"{len(textos)} capítulo(s) verificado(s), {len(erros)} erro(s)")
    return 1 if erros else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
