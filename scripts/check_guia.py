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
    "07-historico-de-status.md",
]

PROIBIDOS = re.compile(r"\b(TBD|TODO|XXX|FIXME)\b")
BLOCO_JQL = re.compile(r"```jql\n(.*?)```", re.S)
NOME_FILTRO = re.compile(r"^\*\*Nome:\*\* (.+)$", re.M)
CRITERIO = re.compile(r"^\*\*Critério de data:\*\* .+$", re.M)
REFERENCIA = re.compile(r'filter\s*=\s*"([^"]+)"')
NOME_VALIDO = re.compile(r"^EQP \| [^|]+ \| [^|]+$")
CERCA_JQL = re.compile(r"^[ \t]*```[ \t]*jql", re.M)
ENTRADA_FILTRO = re.compile(r"^(?=\*\*Nome:\*\* )", re.M)


def normalizar(texto):
    """Converte fins de linha CRLF para LF."""
    return texto.replace("\r\n", "\n")


def verificar_texto(nome, texto):
    """Retorna a lista de erros de um único arquivo."""
    texto = normalizar(texto)
    erros = []
    if PROIBIDOS.search(texto):
        erros.append(f"{nome}: marcador proibido (TBD/TODO/XXX/FIXME)")
    blocos = BLOCO_JQL.findall(texto)
    if len(CERCA_JQL.findall(texto)) != len(blocos):
        erros.append(f"{nome}: cerca ```jql não fechada ou fora do formato")
    for i, bloco in enumerate(blocos, 1):
        if bloco.count("(") != bloco.count(")"):
            erros.append(f"{nome}: JQL #{i} com parênteses desbalanceados")
        if bloco.count('"') % 2:
            erros.append(f"{nome}: JQL #{i} com aspas desbalanceadas")
        if re.search("accountid", bloco, re.I):
            erros.append(f"{nome}: JQL #{i} usa accountId (não se aplica ao Data Center)")
        if re.search(r"\b\d{2}/\d{2}/\d{4}\b", bloco):
            erros.append(f"{nome}: JQL #{i} com data dd/mm/aaaa (use aaaa-mm-dd)")
    for n in NOME_FILTRO.findall(texto):
        if not NOME_VALIDO.match(n):
            erros.append(f"{nome}: nome de filtro fora do padrão 'EQP | ... | ...': {n}")
    # Cada entrada (do Nome até o próximo Nome ou fim do arquivo) deve ter exatamente um critério de data.
    for entrada in ENTRADA_FILTRO.split(texto)[1:]:
        nome_filtro = entrada.split("\n", 1)[0][len("**Nome:** "):]
        total = len(CRITERIO.findall(entrada))
        if total != 1:
            erros.append(
                f"{nome}: filtro '{nome_filtro}' deve ter exatamente um critério de data (encontrados {total})"
            )
    return erros


def verificar_referencias(textos):
    """Confere que todo filter = "X" aponta para um filtro definido no guia."""
    textos = {k: normalizar(v) for k, v in textos.items()}
    definidos = {n.strip() for t in textos.values() for n in NOME_FILTRO.findall(t)}
    erros = []
    for nome, texto in textos.items():
        for bloco in BLOCO_JQL.findall(texto):
            for ref in REFERENCIA.findall(bloco):
                ref = ref.strip()
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
