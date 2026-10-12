# Guia do dashboard Jira Data Center: plano de implementação

> **Para agentes de execução:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendada) ou superpowers:executing-plans para executar este plano tarefa a tarefa. Os passos usam checkboxes (`- [ ]`).

**Objetivo:** produzir o guia de implantação do dashboard de monitoramento de tarefas para Jira Data Center, em capítulos Markdown em português, com JQL pronto para copiar e colar e um verificador automático de consistência.

**Arquitetura:** núcleo nativo + módulos opcionais por app (Time in Status, ScriptRunner, eazyBI), conforme a spec. Cada capítulo é um arquivo em `docs/guia/`. Um script Python (`scripts/check_guia.py`) valida JQL, nomes de filtros e referências cruzadas, e é a "suíte de testes" do guia.

**Tecnologias:** Markdown, Python 3 (biblioteca padrão, `unittest`), JQL do Jira Data Center.

**Spec:** `docs/superpowers/specs/2026-10-12-dashboard-jira-design.md`

**Observação:** a pasta não é um repositório git. Onde um plano normal teria "commit", este plano roda o verificador. Não rodar `git init` sem pedido do usuário.

## Restrições globais

- Todo texto em português do Brasil. Comentários de código também em português.
- Data Center: usernames ou user keys. A palavra "accountId" nunca aparece dentro de blocos ```jql.
- Datas em dd/mm/aaaa nos textos e nomes. Nos blocos ```jql datas literais usam `aaaa-mm-dd`, nunca dd/mm/aaaa.
- Nome de filtro segue o padrão `EQP | <seção> | <métrica>`.
- Cada filtro tem os campos `**Nome:**`, `**Descrição:**`, `**Critério de data:**` (criação, vencimento, resolução, atualização, worklog ou "nenhum (retrato do momento)") e um bloco ```jql.
- A linha `**Nome:**` contém só o nome, sem crases, para o verificador casar as referências `filter = "..."`.
- Nada de gadget, app, função JQL ou recurso inventado. Tudo que depende de versão ou app leva a marca **[Confirmar na sua versão]** ou **[Exige app: X]**.
- Nada de preço inventado: custo de app é "licença paga no Marketplace; consultar o preço atual para o seu número de usuários".
- Marcadores proibidos em qualquer arquivo: `TBD`, `TODO`, `XXX`, `FIXME`.
- O guia usa placeholders de ambiente em maiúsculas entre colchetes, como `[CHAVE_DO_PROJETO]` e `grupo-da-equipe`. Eles são esperados e não são marcadores proibidos.

## Foco de revisão

1. Grupo `membersOf("grupo-da-equipe")` inexistente ou vazio: o filtro dá erro ou retorna nada. O guia manda conferir o grupo antes e dá a alternativa com lista de usernames.
2. Filtros referenciados por nome: dois filtros com o mesmo nome de donos diferentes causam ambiguidade. O guia recomenda um único dono e a alternativa de referenciar por ID (`filter = 12345`).
3. Pai sem vencimento no botão de sincronização: a regra para sem alterar nada e comenta no pai.
4. Subtarefa concluída, ou já alinhada: a regra não altera nem comenta.
5. Fronteira de data: vencimento hoje não é atrasado (`startOfDay()`), e o fuso do usuário versus o do servidor afeta `worklogDate`.

---

## Estrutura de arquivos

| Arquivo | Responsabilidade |
|---|---|
| `scripts/check_guia.py` | Verificador: arquivos, JQL, nomes de filtros, referências cruzadas |
| `scripts/tests/test_check_guia.py` | Testes do verificador |
| `docs/guia/README.md` | Índice, ordem de execução, como ler as marcas |
| `docs/guia/00-pre-requisitos.md` | Dados do ambiente, matriz nativo × app, checagem de versão |
| `docs/guia/01-filtros-salvos.md` | Fundação, segmentação por período, catálogo de filtros |
| `docs/guia/02-resumo-da-equipe.md` | Métrica → gadget, configuração dos gadgets |
| `docs/guia/03-negligenciados-e-horas.md` | Indicadores negligenciados, horas por pessoa, cards |
| `docs/guia/04-tempo-por-status.md` | Comparação e configuração das alternativas |
| `docs/guia/05-datas-e-botao-de-sincronizacao.md` | Divergência e regra do Automation |
| `docs/guia/06-layout-permissoes-e-validacao.md` | Layout, compartilhamento, checklist, desempenho, evolução |

---

### Task 1: Verificador do guia (TDD)

**Arquivos:**
- Criar: `scripts/check_guia.py`
- Teste: `scripts/tests/test_check_guia.py`

**Interfaces:**
- Produz: `verificar_texto(nome: str, texto: str) -> list[str]`, `verificar_referencias(textos: dict[str, str]) -> list[str]`, `main(argv) -> int`, constante `CAPITULOS: list[str]`.

- [ ] **Passo 1: Escrever os testes que falham**

Criar `scripts/tests/test_check_guia.py`:

```python
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from check_guia import verificar_referencias, verificar_texto

FILTRO_OK = """### EQP | Resumo | Atrasadas
**Nome:** EQP | Resumo | Atrasadas
**Descrição:** Tarefas vencidas e abertas.
**Critério de data:** vencimento
```jql
resolution = Unresolved AND duedate < startOfDay()
```
"""


class TestVerificarTexto(unittest.TestCase):
    def test_texto_valido_nao_tem_erros(self):
        self.assertEqual(verificar_texto("a.md", FILTRO_OK), [])

    def test_marcador_proibido(self):
        self.assertTrue(verificar_texto("a.md", "texto TODO aqui"))

    def test_parenteses_desbalanceados(self):
        texto = "```jql\nassignee in (a, b\n```\n"
        self.assertTrue(any("parênteses" in e for e in verificar_texto("a.md", texto)))

    def test_aspas_desbalanceadas(self):
        texto = '```jql\nassignee = "a\n```\n'
        self.assertTrue(any("aspas" in e for e in verificar_texto("a.md", texto)))

    def test_account_id_proibido_em_jql(self):
        texto = "```jql\nassignee = accountId\n```\n"
        self.assertTrue(any("accountId" in e for e in verificar_texto("a.md", texto)))

    def test_data_br_proibida_em_jql(self):
        texto = "```jql\ncreated >= 01/10/2026\n```\n"
        self.assertTrue(any("dd/mm/aaaa" in e for e in verificar_texto("a.md", texto)))

    def test_nome_fora_do_padrao(self):
        texto = FILTRO_OK.replace("**Nome:** EQP | Resumo | Atrasadas", "**Nome:** Atrasadas")
        self.assertTrue(any("padrão" in e for e in verificar_texto("a.md", texto)))

    def test_filtro_sem_criterio_de_data(self):
        texto = FILTRO_OK.replace("**Critério de data:** vencimento\n", "")
        self.assertTrue(any("critério" in e for e in verificar_texto("a.md", texto)))


class TestVerificarReferencias(unittest.TestCase):
    def test_referencia_existente(self):
        derivado = FILTRO_OK.replace("resolution", 'filter = "EQP | Resumo | Atrasadas" AND resolution')
        derivado = derivado.replace("Atrasadas\n**Descrição", "Outra\n**Descrição", 1)
        derivado = derivado.replace("**Nome:** EQP | Resumo | Atrasadas", "**Nome:** EQP | Resumo | Outra")
        self.assertEqual(verificar_referencias({"a.md": FILTRO_OK, "b.md": derivado}), [])

    def test_referencia_inexistente(self):
        texto = '```jql\nfilter = "EQP | Base | Fantasma"\n```\n'
        self.assertTrue(verificar_referencias({"a.md": texto}))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Passo 2: Rodar e ver falhar**

Executar: `python3 scripts/tests/test_check_guia.py`
Esperado: FALHA com `ModuleNotFoundError: No module named 'check_guia'`.

- [ ] **Passo 3: Implementar o verificador**

Criar `scripts/check_guia.py`:

```python
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
```

- [ ] **Passo 4: Rodar e ver passar**

Executar: `python3 scripts/tests/test_check_guia.py`
Esperado: `OK` (10 testes).

Executar: `python3 scripts/check_guia.py`
Esperado: `0 capítulo(s) verificado(s), 0 erro(s)` e código de saída 0.

---

### Task 2: Capítulo 00, pré-requisitos

**Arquivos:**
- Criar: `docs/guia/00-pre-requisitos.md`

**Interfaces:**
- Consome: restrições globais.
- Produz: a tabela "Matriz nativo × app" e a lista de dados de ambiente, citadas pelos capítulos seguintes.

- [ ] **Passo 1: Escrever o capítulo** com estas seções, em português:

1. **Dados do ambiente a coletar.** Tabela com colunas "Dado", "Onde encontrar", "Por que importa", com as linhas:
   - versão exata do Jira Data Center (Administração > Informações do sistema, ou rodapé);
   - apps instalados (Administração > Gerenciar apps): ScriptRunner, eazyBI, Time in Status, JMWE, Automation;
   - permissão para instalar apps (decisão interna);
   - chaves dos projetos;
   - usernames ou grupo da equipe;
   - lista de status do fluxo e quais são "concluído" e "em andamento";
   - tipos de tarefa em uso e se as subtarefas seguem o fluxo do pai;
   - campos personalizados relevantes;
   - Time Tracking ativo e estimativa preenchida;
   - volume aproximado de issues.
2. **Matriz nativo × app.** Tabela com colunas "Funcionalidade", "Nativo?", "O que exige", "Capítulo". Linhas: filtros e JQL de data (nativo); gadgets de resumo (nativo, **[Confirmar na sua versão]**); percentuais fora da pizza (exige eazyBI); cards numéricos reais (exige eazyBI); sem atualização, sem estimativa, sem vencimento, sem responsável (nativo); parada no mesmo status (parcialmente nativo, a validar); tempo por status (**[Exige app: Time in Status ou eazyBI]**, ou campos com Automation); horas por pessoa (Timesheet nativo **[Confirmar na sua versão]**; estimado × registrado por período exige eazyBI); divergência pai × subtarefa (Automation, ScriptRunner ou eazyBI); botão de sincronização (Automation com gatilho manual **[Confirmar na sua versão]**, ou ScriptRunner/JMWE); cards de horas separados por tipo (Timesheet por filtro, ou eazyBI).
3. **Checagem de versão em 5 minutos.** Lista numerada: (1) anotar a versão; (2) abrir um dashboard e Adicionar gadget, conferir se existem os gadgets citados no capítulo 02; (3) abrir uma tarefa, conferir se o menu Automação aparece; (4) abrir Administração > Gerenciar apps e anotar os apps; (5) conferir em Administração > Tarefas > Controle de tempo se o Time Tracking está ativo.
4. **Como ler as marcas** **[Confirmar na sua versão]** e **[Exige app: X]**.
5. **O que levar para a validação final**: uma amostra de 10 tarefas reais (5 tarefas e 5 subtarefas, incluindo uma sem vencimento, uma atrasada e uma com horas registradas).

- [ ] **Passo 2: Verificar**

Executar: `python3 scripts/check_guia.py`
Esperado: `1 capítulo(s) verificado(s), 0 erro(s)`.

---

### Task 3: Capítulo 01, filtros salvos

**Arquivos:**
- Criar: `docs/guia/01-filtros-salvos.md`

**Interfaces:**
- Consome: restrições globais.
- Produz: os filtros abaixo, referenciados pelos capítulos 02 a 05 com `filter = "<nome>"`. Os nomes exatos são parte da interface.

- [ ] **Passo 1: Escrever a parte de fundação** do capítulo:

1. **Como o filtro-base funciona**: filtro-base com `membersOf`, derivados encadeiam o base; mudança de membros em um único lugar.
2. **Dois modos do base**: com grupo (recomendado) e com lista de usernames, mostrando as duas linhas de JQL.
3. **Cuidados** (do foco de revisão): conferir o grupo antes; dono único dos filtros; alternativa por ID (`filter = 12345`); filtro-base sem `ORDER BY`.
4. **Segmentação por time, tipo, responsável e período.** Explicar com honestidade que dashboards nativos do Data Center não têm filtros interativos globais. Tabela de opções com colunas "Opção", "Prós", "Contras", "Custo":
   - filtros salvos separados por recorte: prós simples e nativo, contras muitos filtros e gadgets duplicados, custo zero;
   - JQL com funções de data (`startOfWeek()`, `startOfMonth()`, `-30d`): prós sempre atualiza, contras recortes fixos, custo zero;
   - Navegador de Tarefas para período livre: prós qualquer data, contras manual e não alimenta o dashboard, custo zero;
   - eazyBI com filtros de página: prós filtros interativos de período, time e tipo, contras dados importados em lotes e configuração própria, **[Exige app: eazyBI]** custo licença paga no Marketplace, consultar o preço atual;
   - outras soluções do Marketplace: só recomendar após confirmar a compatibilidade com Data Center na listagem do app, nenhuma nomeada neste guia.
5. **Formato de data**: textos em dd/mm/aaaa; JQL literal em `aaaa-mm-dd`. Exemplo: o dia 15/10/2026 vira `"2026-10-15"` no JQL.
6. **Como criar e compartilhar um filtro** (Filtros > Avançada > salvar como; compartilhar com o grupo da equipe).

- [ ] **Passo 2: Escrever o catálogo de filtros.** Cada linha da tabela abaixo vira uma entrada no formato:

```
### <nome>
**Nome:** <nome>
**Descrição:** <descrição>
**Critério de data:** <critério>
```jql
<JQL>
```
```

Dados (nome | descrição | critério de data | JQL):

| Nome | Descrição | Critério | JQL |
|---|---|---|---|
| EQP \| Base \| Equipe | Base de todos os filtros: issues dos projetos da equipe atribuídas a seus membros | nenhum (retrato do momento) | `project in ([CHAVE_DO_PROJETO]) AND assignee in membersOf("grupo-da-equipe")` |
| EQP \| Base \| Equipe sem responsável | Base alternativa para issues sem responsável nos projetos da equipe | nenhum (retrato do momento) | `project in ([CHAVE_DO_PROJETO]) AND assignee is EMPTY` |
| EQP \| Resumo \| Tarefas | Tarefas padrão (sem subtarefas) | nenhum (retrato do momento) | `filter = "EQP \| Base \| Equipe" AND issuetype in standardIssueTypes()` |
| EQP \| Resumo \| Subtarefas | Somente subtarefas | nenhum (retrato do momento) | `filter = "EQP \| Base \| Equipe" AND issuetype in subTaskIssueTypes()` |
| EQP \| Resumo \| Concluídas | Issues em categoria Concluído | nenhum (retrato do momento) | `filter = "EQP \| Base \| Equipe" AND statusCategory = Done` |
| EQP \| Resumo \| Em andamento | Issues em categoria Em andamento | nenhum (retrato do momento) | `filter = "EQP \| Base \| Equipe" AND statusCategory = "In Progress"` |
| EQP \| Resumo \| A fazer | Issues em categoria A fazer | nenhum (retrato do momento) | `filter = "EQP \| Base \| Equipe" AND statusCategory = "To Do"` |
| EQP \| Resumo \| Atrasadas | Abertas com vencimento anterior a hoje | vencimento | `filter = "EQP \| Base \| Equipe" AND resolution = Unresolved AND duedate < startOfDay()` |
| EQP \| Resumo \| Sem responsável | Abertas sem responsável | nenhum (retrato do momento) | `filter = "EQP \| Base \| Equipe sem responsável" AND resolution = Unresolved` |
| EQP \| Período \| Criadas no mês | Criadas desde o primeiro dia do mês atual | criação | `filter = "EQP \| Base \| Equipe" AND created >= startOfMonth()` |
| EQP \| Período \| Resolvidas no mês | Resolvidas desde o primeiro dia do mês atual | resolução | `filter = "EQP \| Base \| Equipe" AND resolved >= startOfMonth()` |
| EQP \| Período \| Vencendo na semana | Vencimento entre segunda e domingo da semana atual | vencimento | `filter = "EQP \| Base \| Equipe" AND duedate >= startOfWeek() AND duedate <= endOfWeek()` |
| EQP \| Período \| Criadas em 30 dias | Criadas nos últimos 30 dias | criação | `filter = "EQP \| Base \| Equipe" AND created >= -30d` |
| EQP \| Período \| Resolvidas em 30 dias | Resolvidas nos últimos 30 dias | resolução | `filter = "EQP \| Base \| Equipe" AND resolved >= -30d` |
| EQP \| Período \| Atualizadas em 30 dias | Atualizadas nos últimos 30 dias | atualização | `filter = "EQP \| Base \| Equipe" AND updated >= -30d` |
| EQP \| Período \| Criadas em 3 meses | Criadas no mês atual e nos 2 anteriores (o JQL não tem início de trimestre) | criação | `filter = "EQP \| Base \| Equipe" AND created >= startOfMonth(-2)` |

Dentro dos blocos ```jql os nomes de filtro usam `|` normal (a barra escapada `\|` existe só dentro da tabela deste plano).

- [ ] **Passo 3: Verificar**

Executar: `python3 scripts/check_guia.py`
Esperado: `2 capítulo(s) verificado(s), 0 erro(s)`. Os 16 filtros devem ter nome, critério e referências válidas.

---

### Task 4: Capítulo 02, resumo da equipe

**Arquivos:**
- Criar: `docs/guia/02-resumo-da-equipe.md`

**Interfaces:**
- Consome: filtros do capítulo 01 (por nome).
- Produz: a tabela métrica → gadget, citada pelo capítulo 06 no layout.

- [ ] **Passo 1: Escrever o capítulo** com:

1. **Tabela métrica → gadget → filtro → configuração** (marcando **[Confirmar na sua versão]** na coluna do gadget). Linhas:
   - total de tarefas: Resultados do Filtro ou Estatísticas da Tarefa, filtro `EQP | Resumo | Tarefas`;
   - total de subtarefas: idem, filtro `EQP | Resumo | Subtarefas`;
   - atrasadas: Resultados do Filtro, filtro `EQP | Resumo | Atrasadas`, colunas Chave, Resumo, Responsável, Vencimento;
   - sem responsável: Resultados do Filtro, filtro `EQP | Resumo | Sem responsável`;
   - distribuição por status: Gráfico de Pizza, filtro `EQP | Base | Equipe`, estatística Status;
   - distribuição por tipo: Gráfico de Pizza, estatística Tipo de tarefa;
   - distribuição por responsável: Gráfico de Pizza, estatística Responsável;
   - responsável × status: Estatísticas de Filtro Bidimensional, eixo X Status, eixo Y Responsável;
   - concluídas, em andamento e a fazer: Gráfico de Pizza sobre `EQP | Base | Equipe` por categoria de status, ou três gadgets Estatísticas da Tarefa com os filtros `EQP | Resumo | Concluídas`, `EQP | Resumo | Em andamento` e `EQP | Resumo | A fazer`;
   - tendência: Criadas vs Resolvidas, filtro `EQP | Base | Equipe`, período 30 dias, agrupamento diário ou semanal;
   - concentração: Mapa de Calor, estatística Responsável.
2. **Limites** em texto: percentuais só na pizza; percentual nos demais exige planilha ou eazyBI; não há card numérico nativo; quando precisar de card real, ver o módulo do capítulo 04.
3. **Como adicionar cada tipo de gadget** (passos genéricos: Dashboard > Adicionar gadget > escolher > selecionar filtro salvo > configurar > Salvar), com a nota de que o nome em português depende do idioma da instalação.
4. **Passo de validação do capítulo**: comparar cada total do gadget com o Navegador de Tarefas usando o mesmo filtro.

- [ ] **Passo 2: Verificar**

Executar: `python3 scripts/check_guia.py`
Esperado: `3 capítulo(s) verificado(s), 0 erro(s)`.

---

### Task 5: Capítulo 03, negligenciados e horas

**Arquivos:**
- Criar: `docs/guia/03-negligenciados-e-horas.md`

**Interfaces:**
- Consome: filtros `EQP | Base | Equipe` e `EQP | Base | Equipe sem responsável` do capítulo 01.
- Produz: os filtros abaixo (nomes exatos) e a regra dos cards de horas, citados pelos capítulos 04 e 06.

- [ ] **Passo 1: Escrever a seção de negligenciados**, com o catálogo no mesmo formato de entrada do capítulo 01. Dados (nome | descrição | critério | JQL):

| Nome | Descrição | Critério | JQL |
|---|---|---|---|
| EQP \| Negligenciados \| Sem atualização | Abertas sem atualização há 14 dias ou mais (ajuste o prazo conforme a equipe) | atualização | `filter = "EQP \| Base \| Equipe" AND statusCategory != Done AND updated <= -14d` |
| EQP \| Negligenciados \| Sem estimativa | Abertas sem estimativa original | nenhum (retrato do momento) | `filter = "EQP \| Base \| Equipe" AND statusCategory != Done AND originalEstimate is EMPTY` |
| EQP \| Negligenciados \| Sem vencimento | Abertas sem data de vencimento | nenhum (retrato do momento) | `filter = "EQP \| Base \| Equipe" AND statusCategory != Done AND duedate is EMPTY` |
| EQP \| Negligenciados \| Sem responsável | Abertas sem responsável | nenhum (retrato do momento) | `filter = "EQP \| Base \| Equipe sem responsável" AND statusCategory != Done` |
| EQP \| Negligenciados \| Parada no mesmo status | **A validar na amostra.** Abertas sem mudança de status nos últimos 14 dias | atualização | `filter = "EQP \| Base \| Equipe" AND statusCategory != Done AND NOT (status CHANGED AFTER -14d)` |

Na entrada do filtro "Parada no mesmo status", incluir o aviso: se a sua versão rejeitar a negação com `CHANGED`, use `EQP | Negligenciados | Sem atualização` como substituto seguro, e registre o resultado do teste. Explicar a diferença: "sem atualização" considera qualquer edição ou comentário, "parada no status" considera só mudança de status.

- [ ] **Passo 2: Escrever a seção de horas por pessoa**, com os filtros (nome | descrição | critério | JQL):

| Nome | Descrição | Critério | JQL |
|---|---|---|---|
| EQP \| Horas \| Com worklog no mês | Issues em que alguém da equipe registrou horas no mês atual (filtra tarefas, não soma horas) | worklog | `project in ([CHAVE_DO_PROJETO]) AND worklogAuthor in membersOf("grupo-da-equipe") AND worklogDate >= startOfMonth()` |
| EQP \| Horas \| Tarefas com horas | Tarefas padrão com tempo registrado | nenhum (retrato do momento) | `filter = "EQP \| Base \| Equipe" AND issuetype in standardIssueTypes() AND timespent > 0` |
| EQP \| Horas \| Subtarefas com horas | Subtarefas com tempo registrado | nenhum (retrato do momento) | `filter = "EQP \| Base \| Equipe" AND issuetype in subTaskIssueTypes() AND timespent > 0` |

Texto obrigatório da seção:
- `worklogAuthor` e `worklogDate` filtram tarefas; não somam horas. Marcar **[Confirmar na sua versão]** o uso de `membersOf` com `worklogAuthor`; se rejeitado, usar a lista de usernames.
- Comparação das alternativas, em tabela "Alternativa", "O que entrega", "Limite": JQL com `worklogAuthor`/`worklogDate`; Relatório de Time Tracking (estimado × registrado por projeto, versão ou sprint, não por período livre); gadget Timesheet (soma por pessoa e dia, **[Confirmar na sua versão]**); eazyBI (estimado × registrado por pessoa e período, **[Exige app: eazyBI]**).
- Recomendação: Timesheet para horas por pessoa no núcleo nativo; eazyBI para a comparação com o estimado.
- Configuração do Timesheet: filtro `EQP | Horas | Com worklog no mês`, período do mês, agrupamento por usuário; opção de somar horas das subtarefas conforme a versão; **testar com subtarefas** e anotar o resultado.

- [ ] **Passo 3: Escrever a seção dos cards de tempo dedicado**:
- Campos: `timespent` = tempo da própria issue; `aggregatetimespent` (Σ Time Spent) = issue mais subtarefas.
- Tabela "Card", "Campo", "Filtro", "Exibição":
  - Tempo em tarefas: Time Spent, filtro `EQP | Horas | Tarefas com horas`;
  - Tempo em subtarefas: Time Spent, filtro `EQP | Horas | Subtarefas com horas`;
  - Conjunto: soma dos dois cards acima; alternativa: Σ Time Spent só das tarefas padrão;
  - Estimado × realizado: **[Exige app: eazyBI]**.
- Regra de ouro, em destaque: nunca somar o Σ Time Spent do pai com o Time Spent das subtarefas.
- Limites nativos: Resultados do Filtro mostra a coluna mas não totaliza; o Timesheet respeita o filtro e o responsável da issue pode diferir de quem registrou as horas; para totais por tipo usar um Timesheet por filtro.
- Exemplo numérico com 3 casos: pai com 2h próprias e subtarefas de 3h e 1h → Time Spent do pai 2h, Σ do pai 6h, tarefas 2h, subtarefas 4h, conjunto 6h (e **não** 12h).

- [ ] **Passo 4: Verificar**

Executar: `python3 scripts/check_guia.py`
Esperado: `4 capítulo(s) verificado(s), 0 erro(s)`.

---

### Task 6: Capítulo 04, tempo por status

**Arquivos:**
- Criar: `docs/guia/04-tempo-por-status.md`

**Interfaces:**
- Consome: matriz do capítulo 00.
- Produz: a árvore de decisão e as configurações dos módulos, citadas pelo layout do capítulo 06.

- [ ] **Passo 1: Escrever o capítulo**:

1. **Por que não é nativo**: o Jira registra as transições no histórico mas não oferece relatório de tempo em cada status.
2. **Comparação**, tabela "Alternativa", "Como funciona", "Prós", "Contras", "Custo", "Marca":
   - app de Time in Status: calcula a partir do histórico, inclusive retroativo; prós menor esforço e média/total por status; contras licença e dashboards próprios; **[Exige app: Time in Status]**; custo licença paga, consultar preço atual;
   - eazyBI: mede tempo em status e permite média e total; prós também entrega cards, horas por pessoa e filtros de página; contras importação em lotes e curva de aprendizado; **[Exige app: eazyBI]**;
   - ScriptRunner: script calcula a partir do histórico (campo com script); prós sob medida; contras manutenção de código e custo de desempenho em instâncias grandes; **[Exige app: ScriptRunner]**;
   - campos personalizados alimentados por Automation: a cada transição, grava timestamp e acumula; prós sem app extra; contras sem histórico retroativo, manutenção de regras, só vale a partir da implantação.
3. **Árvore de decisão** em lista: (1) tem app de Time in Status? use-o, ver 4; (2) senão, tem eazyBI? use-o, ver 5; (3) senão, pode instalar um dos dois? recomendar Time in Status; (4) senão, campos com Automation, ver 6.
4. **Configuração: app de Time in Status** (passos genéricos e honestos, com **[Confirmar na documentação do app instalado]** para telas e nomes): instalar, definir o escopo (filtro `EQP | Base | Equipe`), escolher a medida (tempo em cada status, média e total), exibir no dashboard por gadget do app se existir.
5. **Configuração: eazyBI**: criar a conta de dados, importar os projetos, importar o campo de vencimento e as horas como medidas, relatório com Status nas linhas e as medidas de tempo em status nas colunas, filtros de página (período, equipe, tipo), incluir no dashboard pelo gadget do eazyBI; avisar a defasagem da importação e agendar fora do horário de pico.
6. **Configuração: campos com Automation** — passos completos:
   - criar o campo personalizado de data/hora "Entrada no status atual" e um campo numérico por status relevante ("Horas em <status>");
   - regra "Transição de issue" (gatilho); ação: calcular o tempo desde a entrada anterior com smart values de data e somar ao campo do status que acabou de sair; depois gravar o novo "Entrada no status atual" com `{{now}}`;
   - avisar que a aritmética de datas em smart values deve ser testada na versão (**[Confirmar na sua versão]**) e que issues antigas não terão histórico.
7. **Limites e manutenção**: cada alternativa, em um parágrafo.

- [ ] **Passo 2: Verificar**

Executar: `python3 scripts/check_guia.py`
Esperado: `5 capítulo(s) verificado(s), 0 erro(s)`.

---

### Task 7: Capítulo 05, divergência de datas e botão

**Arquivos:**
- Criar: `docs/guia/05-datas-e-botao-de-sincronizacao.md`

**Interfaces:**
- Consome: filtro `EQP | Base | Equipe` do capítulo 01.
- Produz: o filtro de divergência abaixo e a regra manual de sincronização, citados pelo layout do capítulo 06.

- [ ] **Passo 1: Escrever a detecção de divergência**:

1. Texto: o JQL nativo não compara o vencimento da subtarefa com o do pai.
2. **Camada 1 (núcleo, Automation)** — regra agendada de sinalização. Passos:
   - Nome: `Sinalizar vencimento divergente`.
   - Gatilho: agendado (por exemplo a cada hora, fora do horário de pico), com a busca JQL `project in ([CHAVE_DO_PROJETO]) AND issuetype in subTaskIssueTypes() AND statusCategory != Done` para cada issue encontrada.
   - Condição 1: o vencimento do pai não é vazio (comparação avançada: `{{issue.parent.duedate}}` não é igual a vazio).
   - Condição 2 (se/senão): comparação avançada com `{{issue.duedate}}` diferente de `{{issue.parent.duedate}}`.
   - Se divergir: ação Editar issue, adicionar o rótulo `venc-divergente`.
   - Senão: ação Editar issue, remover o rótulo `venc-divergente`.
   - Limites: o dado reflete a última execução; a edição por rótulo gera histórico e atualiza `updated` da subtarefa (afeta o filtro "sem atualização"); marcar **[Confirmar na sua versão]** a sintaxe e o limite de issues por execução do agendamento.
3. Filtro de divergência, em entrada padrão:

| Nome | Descrição | Critério | JQL |
|---|---|---|---|
| EQP \| Datas \| Subtarefas com vencimento divergente | Subtarefas abertas sinalizadas pela regra como divergentes do vencimento do pai | vencimento | `filter = "EQP \| Base \| Equipe" AND labels = venc-divergente AND statusCategory != Done` |

4. **Módulo ScriptRunner** **[Exige app: ScriptRunner]**: campo com script "Vencimento do pai" e "Diferença em dias" para exibição em tempo real; funções `subtasksOf()` e `parentsOf()`, com exemplos em blocos ```jql:
   `issueFunction in subtasksOf("project in ([CHAVE_DO_PROJETO]) AND duedate is not EMPTY")` e `issueFunction in parentsOf("labels = venc-divergente")`.
   Avisar: essas funções listam subtarefas ou pais a partir de uma busca; **não** há garantia de que comparem datas entre pai e filho, então testar na instância antes de contar com isso. Limite: licença, manutenção de scripts, impacto de desempenho.
5. **Módulo eazyBI** **[Exige app: eazyBI]**: tabela com pai, subtarefa, responsável, status e as duas datas e a diferença em dias; limite: importação em lotes.
6. **Exibição nativa**: gadget Resultados do Filtro sobre o filtro de divergência, com colunas Chave, Pai, Responsável, Status e Vencimento. Limite: o vencimento do pai não aparece ao lado.

- [ ] **Passo 2: Escrever o botão de sincronização**:

1. **Tabela das 4 opções** (colunas "Opção", "Viável?", "Prós", "Contras", "Quando usar"): (a) Automation com gatilho manual, recomendada **[Confirmar na sua versão]** se o Automation vem incluído e se o gatilho manual existe; (b) webhook de entrada acionado por link, com a ressalva de GET versus POST e do segredo na URL; (c) ScriptRunner ou JMWE; (d) gadget personalizado, não recomendado.
2. **Regra completa da opção (a)**, numerada:
   1. Nome: `Sincronizar vencimento das subtarefas com o do pai`.
   2. Gatilho: **Manual**. Quem pode executar: o grupo `[grupo-que-pode-sincronizar]`. Rótulo exibido na tarefa: "Sincronizar vencimento das subtarefas".
   3. Condição: `{{issue.duedate}}` não vazio, em tarefas padrão. Se vazio, ação Adicionar comentário na tarefa: "Sincronização cancelada: a tarefa não tem data de vencimento." e a regra encerra.
   4. Ramificação: **Para subtarefas** (branch).
   5. Condição dentro do ramo: `statusCategory` da subtarefa diferente de `Done`.
   6. Condição dentro do ramo: comparação avançada, `{{issue.duedate}}` diferente de `{{triggerIssue.duedate}}`. Se igual, nada é feito (sem comentário).
   7. Ação, **nesta ordem**: primeiro Adicionar comentário na subtarefa com o texto "Vencimento alterado de {{issue.duedate}} para {{triggerIssue.duedate}} por {{initiator.displayName}} (sincronização com a tarefa {{triggerIssue.key}})." — o comentário vem antes da edição para registrar a data antiga; depois Editar issue: Data de vencimento = `{{triggerIssue.duedate}}`.
   8. Opcional após o ramo: comentário na tarefa com o resumo da sincronização.
3. **Smart values usados**, em tabela: `{{triggerIssue.duedate}}`, `{{issue.duedate}}`, `{{initiator.displayName}}`, `{{triggerIssue.key}}`, `{{issue.parent.duedate}}`. Marcar **[Confirmar na sua versão]** o formato de data nos comentários e, se necessário, formatar com os sufixos de data suportados (`.jiraDate` ou `.shortDate`).
4. **Permissões**: o ator da regra precisa de Procurar Projetos, Editar Tarefas, Agendar Tarefas (alterar o vencimento) e Adicionar Comentários no projeto das subtarefas; a regra roda como o ator configurado (conta de serviço recomendada); quem aciona precisa estar no grupo permitido; quem administra a regra precisa de permissão de administração do projeto ou do Jira.
5. **Riscos e proteções**, em tabela "Risco", "Proteção na regra": sobrescrever datas legítimas → botão manual restrito a um grupo, comentário com a data antiga, ignorar subtarefas concluídas; pai sem data → a regra encerra e comenta; subtarefa já alinhada → sem alteração; muitas subtarefas → **[Confirmar na sua versão]** limite de issues por ramo e por execução; execução acidental → restrição por grupo e registro no histórico da regra.
6. **Roteiro de teste em homologação**: passo a passo com tabela "Caso", "Preparação", "Resultado esperado":
   1. pai com vencimento 20/10/2026 e 3 subtarefas abertas com datas diferentes → as 3 ficam em 20/10/2026, cada uma com comentário;
   2. subtarefa concluída com data diferente → não é alterada;
   3. subtarefa já com 20/10/2026 → não é alterada, sem comentário;
   4. pai sem vencimento → nenhuma subtarefa alterada, comentário no pai;
   5. subtarefa sem vencimento → recebe a data do pai;
   6. usuário fora do grupo → o botão não aparece ou a execução é negada;
   7. repetir a execução → nenhuma alteração nova.
   Após os testes, conferir o histórico das tarefas e só então copiar a regra para produção.

- [ ] **Passo 3: Verificar**

Executar: `python3 scripts/check_guia.py`
Esperado: `6 capítulo(s) verificado(s), 0 erro(s)`.

---

### Task 8: Capítulo 06, layout, permissões e validação

**Arquivos:**
- Criar: `docs/guia/06-layout-permissoes-e-validacao.md`

**Interfaces:**
- Consome: filtros e gadgets dos capítulos 01 a 05 (por nome).
- Produz: layout final, checklist e rotinas.

- [ ] **Passo 1: Escrever o capítulo**:

1. **Layout em 3 colunas**, tabela "Posição", "Gadget", "Filtro", "Capítulo". Ordem de cima para baixo:
   - faixa de totais: totais de tarefas, subtarefas, atrasadas, sem responsável, e os cards de horas;
   - visão da equipe: pizza de status, pizza de tipo, responsável × status, Criadas vs Resolvidas;
   - negligenciados: sem atualização, sem estimativa, sem vencimento, sem responsável;
   - datas e horas: subtarefas com vencimento divergente, Timesheet, módulo de tempo por status (se instalado).
   Recomendar no máximo 12 a 15 gadgets e explicar o motivo (cada gadget executa um JQL ao abrir).
2. **Onde fica o botão**: na própria tarefa; o dashboard mostra a lista de divergentes e o usuário abre a tarefa para acionar.
3. **Compartilhamento e permissões**: filtros e dashboard têm compartilhamento independente; compartilhar com o mesmo grupo; quem vê o dashboard sem acesso a um filtro vê o gadget vazio ou com erro; dono do dashboard e dos filtros deve ser conta de serviço ou de administrador, e o administrador transfere a propriedade se o dono sair; permissões da conta da automação (ver capítulo 05).
4. **Checklist de validação dos números**, com caixas de seleção `- [ ]`:
   - cada contagem de gadget bate com o Navegador de Tarefas para o mesmo filtro;
   - tipos somam o total, e status somam o total;
   - amostra de 10 tarefas (do capítulo 00) conferida à mão: horas registradas, estimativa e Σ Time Spent do pai;
   - card combinado sem contagem em duplicidade (caso do exemplo numérico do capítulo 03);
   - fronteiras de data: tarefa com vencimento hoje não conta como atrasada; conferir fuso do usuário versus servidor em `worklogDate`;
   - usuário sem acesso aos filtros vê o resultado esperado;
   - botão testado em homologação, conforme o roteiro do capítulo 05;
   - filtro "parada no mesmo status" e `membersOf` com `worklogAuthor` testados, com o resultado anotado.
5. **Desempenho em instâncias grandes**: evitar encadeamento profundo de filtros; evitar `status CHANGED` e funções de app pesadas em filtros usados por muitos gadgets; agendar a regra de divergência e a importação do eazyBI fora do horário de pico; delimitar por projeto; limitar colunas e linhas de Resultados do Filtro.
6. **Manutenção**: tabela "Ponto", "Quando muda", "O que fazer" — membros do grupo; dono dos filtros e do dashboard; mudança de fluxo de trabalho (revisar filtros por categoria); conta da automação; prazo de 14 dias do filtro de inatividade.
7. **Melhorias futuras**: alertas de tarefas atrasadas por e-mail via Automation; assinatura de filtro por e-mail (envia a lista do filtro, **não** o dashboard inteiro); relatório periódico em PDF do dashboard só com app que tenha compatibilidade com Data Center confirmada na listagem do Marketplace.

- [ ] **Passo 2: Verificar**

Executar: `python3 scripts/check_guia.py`
Esperado: `7 capítulo(s) verificado(s), 0 erro(s)`.

---

### Task 9: README e verificação final

**Arquivos:**
- Criar: `docs/guia/README.md`

**Interfaces:**
- Consome: todos os capítulos.

- [ ] **Passo 1: Escrever o README** com: objetivo do guia; a ordem de execução numerada com link para cada capítulo (00 a 06); quais capítulos são núcleo nativo (00 a 03, 05 camada 1 e 06) e quais são módulos opcionais (04, 05 camada ScriptRunner/eazyBI, partes eazyBI); como ler as marcas; o que o guia **não** faz (não acessa o seu Jira; todos os testes são feitos por você, com a amostra real); instruções para rodar `python3 scripts/check_guia.py --completo` após qualquer edição.

- [ ] **Passo 2: Verificação completa**

Executar: `python3 scripts/tests/test_check_guia.py`
Esperado: `OK`.

Executar: `python3 scripts/check_guia.py --completo`
Esperado: `8 capítulo(s) verificado(s), 0 erro(s)`.

- [ ] **Passo 3: Conferência contra a spec.** Abrir `docs/superpowers/specs/2026-10-12-dashboard-jira-design.md` e confirmar, seção por seção (1 a 10), que há um capítulo que a cobre. Em particular: opções de segmentação com prós, contras e custo (cap. 01); gadgets e limites (cap. 02); indicadores, horas por pessoa e cards (cap. 03); comparação do tempo por status e recomendação (cap. 04); divergência, botão, regra, permissões, riscos e roteiro de teste (cap. 05); layout, compartilhamento, checklist, desempenho, manutenção e melhorias (cap. 06). Corrigir qualquer lacuna no capítulo correspondente e rodar o verificador de novo.
