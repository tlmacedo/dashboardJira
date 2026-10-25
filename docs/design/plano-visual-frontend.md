# Plano visual do frontend

Escopo: estrutura, hierarquia, temas, idioma e comportamento das telas do novo dashboard (React + TypeScript + Vite). Não contém código. Modo: **Operate** (o usuário executa tarefas; clareza e consistência vencem expressão).

Base: [PRODUCT.md](../../PRODUCT.md), [relatório de cards](../analise/relatorio-cards-app-atual.md) e decisões registradas na memória do projeto.

## 1. Decisões já tomadas

| Tema | Decisão |
|---|---|
| Navegação | Menu lateral agrupado + página inicial por papel |
| Temas | **Claro**, **Escuro** e **Sidia** (marca forte); escolha do usuário, lembrada no navegador |
| Idioma | pt-BR e inglês, com seletor; padrão pt-BR |
| Acessibilidade | WCAG AA em todos os temas; a cor nunca é o único sinal |
| Identidade | Segue a Sidia; ativos oficiais (logo, manual, fonte) ainda não foram fornecidos |
| Acesso | `DASHBOARD_DEV_MODE=true` sem login e faixa vermelha; em produção SSO e visão por papel |
| Densidade | Telas densas e escaneáveis para uso diário em desktop; mobile funcional, não prioritário |

## 2. Identidade Sidia (levantada do site público, sidia.com)

Fontes: variáveis de cor do tema do site e declaração de fonte. **Não é manual de marca**; precisa de confirmação com a Sidia.

| Item | Valor encontrado | Uso planejado |
|---|---|---|
| Azul real | `#0023A4` (e `#0124A4` em um bloco) | cor de marca, menu e cabeçalho do tema Sidia, ação primária |
| Azul elétrico | `#3333FF` | links e foco |
| Roxo | `#772FEB` | destaque secundário |
| Verde | `#77FF94` (outros: `#00DD00`, `#61CE70`) | destaque e estado "ok" sobre fundos escuros |
| Neutros | `#181818`, `#333333`, `#595959`, `#F7F7F7`, `#FFFFFF` | texto e superfícies |
| Fonte | **Mark** (aparece como `font-family` no CSS do site; fonte licenciada) | usar só se a Sidia fornecer o arquivo e a licença |

Fallback enquanto a Mark não vier: pilha de sistema (`system-ui`, `Segoe UI`, `Roboto`, `Helvetica Neue`), sem baixar fonte de terceiros. Não inventar logo: usar o nome "Painel de Squads" em texto até receber o logo.

Achados de contraste (calculados):

- `#7A7A7A` (texto padrão do site) em branco: **4,29:1, reprova AA**. Não usar para texto. Usar `#595959` (7,0:1).
- `#00DD00` em branco: **1,85:1, reprova**. Verde sobre claro deve ser `#0B7A3B` (5,44:1). O verde `#77FF94` só entra sobre fundo escuro ou azul.
- Branco sobre `#0023A4`: 11,8:1. `#77FF94` sobre `#0023A4`: 9,3:1. `#772FEB` sobre branco: 6,2:1.

### 2.1 Cores medidas no logo fornecido (imagem "Setembro/2025")

O usuário enviou uma imagem do logo (peça de redes sociais, 300×300, "sidia" em azul com dois pontos verdes e moldura colorida). Não há manual de marca. Cores amostradas por pixel:

| Elemento do logo | Cor medida | Uso planejado |
|---|---|---|
| Palavra "sidia" | `#1329A0` (azul real) | marca, ação primária, menu do tema Sidia (confere com `#0023A4` do site; adotar `#1329A0` como azul de marca por vir do logo) |
| Pontos do "i" | `#13DC14` / `#6CE270` (verde) | destaque sobre azul e fundos escuros |
| Moldura esquerda | `#3E4095` (índigo) | cor categórica |
| Moldura superior | `#00A85A` (verde) | estado "ok" sobre escuro, categórica |
| Moldura direita e inferior | `#F58634` (laranja) | destaque e atenção sobre escuro; categórica |

Contrastes calculados: azul `#1329A0` em branco 11,3:1; laranja `#F58634` em branco **2,5:1 (reprova)** e em fundo escuro 7,3:1; verde `#00A85A` em branco **3,1:1 (reprova para texto)** e em fundo escuro 5,9:1; `#13DC14` em branco **1,9:1 (reprova)**. Em fundo claro, laranja e verde só entram em versão escurecida (laranja `#B85A0B` 4,7:1; verde `#0B7A3B` 5,4:1) ou como preenchimento com rótulo, nunca como texto sobre branco.

Limitação: a imagem é uma peça de divulgação com moldura e data; não serve como logo oficial do menu. Falta o arquivo limpo (SVG ou PNG transparente).

## 3. Temas (tokens planejados)

Tokens semânticos (nomes, não valores finais). Cada tema define os mesmos tokens.

| Token | Claro | Escuro | Sidia |
|---|---|---|---|
| Fundo da página | `#F7F7F7` | `#101521` | `#F4F6FF` |
| Superfície (card) | `#FFFFFF` | `#171E2E` | `#FFFFFF` |
| Menu lateral | `#FFFFFF` com borda | `#0D121C` | `#0023A4` com texto branco |
| Texto principal | `#181818` | `#EEF1F8` | `#10163A` |
| Texto secundário | `#595959` | `#B4BCCF` | `#4A5073` |
| Ação primária | `#0023A4` | `#8FA8FF` | `#0023A4` |
| Destaque | `#772FEB` | `#B9A0FF` | `#772FEB` |
| OK (símbolo ✓) | `#0B7A3B` | `#77FF94` | `#0B7A3B` |
| Atenção (●) | `#9A5B00` | `#FFB74D` | `#9A5B00` |
| Problema (▲) | `#B3261E` | `#FF8A80` | `#B3261E` |

Os valores acima foram escolhidos e calculados para AA (≥ 4,5:1 texto, ≥ 3:1 gráfico e borda de componente). A verificação final em cada par fundo/texto fica para a implementação.

Regras:
- **Claro e Escuro:** neutros; o azul Sidia aparece só como cor de ação e foco. Leitura longa não cansa.
- **Sidia:** menu e cabeçalho azul real, roxo e verde como destaque. É onde a marca fica explícita.
- Alerta nunca depende só da cor: ▲ problema, ● atenção, ✓ ok, ■ neutro (como no app atual) mais rótulo.
- Tema inicial segue `prefers-color-scheme` (claro/escuro); "Sidia" só por escolha.
- Gráficos: paleta categórica própria de 6 cores, distinguível em daltonismo, com rótulos diretos e padrão/forma além de cor.

## 4. Mapa de navegação

Grupos do menu (cada item mostra só o que o papel pode ver):

| Grupo | Telas | Papéis |
|---|---|---|
| **Início** | Central de atenção (gerente/coordenador), Meu squad (Squad Leader), Meu painel (usuário) | todos, com início por papel |
| **Trabalho em curso** | Abertas, Épicos, Negligenciadas, Riscos, Previsão | todos (filtrado) |
| **Resultados** | Fechadas, Negativas, Tempo em status, Esforço por task | todos (filtrado) |
| **Prazos** | Metas e prazos, Vencimentos da semana, Checklist × Review | todos (filtrado) |
| **Pessoas e planejamento** | Carga por pessoa, Capacidade, Plano 4–8 semanas, Calendário do time, Cobertura por ausência, Simulador de ausência, Competências, Revisões pendentes, Posse da task, Carga por tipo/OS, Tendência de ocupação, Gargalos | Squad Leader, coordenador, gerente; usuário só o próprio |
| **Ação** | Distribuição, Rebalanceamento | Squad Leader, coordenador, gerente |
| **Auditoria** | Alterações de data/dono/label, Divergência Task × Review, Integridade | auditoria: coordenador e gerente; Integridade só gerente |
| **Governança** | Qualidade dos dados, Sincronização, Configuração (leitura) | gerente |

Início por papel:
- **Gerente/coordenador:** Central de atenção (indicadores que pedem ação, cada um é link).
- **Squad Leader:** visão do squad (carga, vencimentos da semana, ausências, riscos).
- **Usuário:** Meu painel (fila, prazos, ausências, esforço).

Seletor de tema e idioma no rodapé do menu. Faixa vermelha fixa em modo desenvolvimento.

## 5. Padrões de tela (uma só gramática)

1. **Cabeçalho da tela:** título, uma frase do que responde, filtros ativos em chips, botão de exportar CSV.
2. **Barra de filtros:** só os filtros que alteram os números da tela; estado na URL (links compartilháveis).
3. **Faixa de KPIs:** 3 a 6 cartões; cada um com valor, variação, estado (símbolo + texto) e "ver tasks" quando clicável.
4. **Corpo:** no máximo dois gráficos e uma tabela principal por tela; o restante em abas.
5. **Tabela:** ordenável, linhas clicáveis (gaveta de detalhe), números alinhados à direita, cabeçalho fixo, densidade compacta.
6. **Detalhamento:** todo número abre a lista de tasks que o compõe (janela, nova aba, CSV), com a coluna "Detalhe" explicando por que entrou.
7. **Gaveta da task:** status, datas, histórico de alterações (quem, quando, autorizado ou não), tempo por status, posse por pessoa, fatores de risco.
8. **Estados:** carregando (esqueleto), vazio (mensagem + o que ajustar), erro (causa + tentar de novo), sem permissão (explica o papel), dados incompletos (aviso com link para Qualidade dos dados).

Regras de conteúdo: datas dd/mm/aaaa, números no formato do idioma, "d" = dia, "h" = hora, fuso America/Manaus indicado no rodapé. Tabelas de pessoas em ordem alfabética, sem ranking. Textos em arquivos de tradução (pt-BR e en).

## 6. Wireframes das telas-chave

### 6.1 Central de atenção (início do gerente/coordenador)

```
┌ Menu ───────┬──────────────────────────────────────────────────────────┐
│ Início      │ Central de atenção            [Squad ▾][Período ▾][CSV]  │
│  ▸ Central  │ O que pede ação agora                                    │
│ Trabalho    ├──────────────────────────────────────────────────────────┤
│ Resultados  │ ┌Abertas──┐┌Vencidas─┐┌Risco alto┐┌No prazo┐┌Ajustado┐   │
│ Prazos      │ │  412    ││ ▲ 18    ││ ▲ 9      ││ 87,4%  ││ 91,2%  │   │
│ Pessoas...  │ └ver tasks┘└ver tasks┘└ver tasks─┘└vs meta ┘└vs meta ┘   │
│ Ação        ├──────────────────────────────────────────────────────────┤
│ Auditoria   │ Indicadores de atenção                                   │
│ Governança  │ ▲ Pessoas acima da capacidade      6   → Capacidade      │
│             │ ▲ Atrasos previstos                 11  → Plano           │
│ ─────────── │ ● Sem atualização                   23  → Negligenciadas  │
│ Tema ◐      │ ● Paradas no status                 14  → Negligenciadas  │
│ Idioma PT   │ ▲ Alterações sem autorização        4   → Auditoria       │
│ Manaus UTC-4│ ● Review com vencimento diferente   7,2%→ Divergências    │
│             ├────────────────────────┬─────────────────────────────────┤
│             │ Entregas por semana    │ Quando o backlog termina        │
│             │ ▇ ▆ ▇ ▅ ▇ ▆ ▇ ▇        │ P50 ▏  P85 ▏  P95 ▏             │
│             └────────────────────────┴─────────────────────────────────┘
```
Hierarquia: KPIs no topo, lista de indicadores (leitura em colunas, cada linha é link) no centro, gráficos por último.

### 6.2 Tempo em status (fatia vertical)

```
┌ Tempo em status                 [Squad▾][Pessoa▾][Tipo▾][De–Até][CSV] ┐
│ ┌Analisadas┐┌Eficiência de fluxo┐┌Paradas além do normal┐ [Úteis|Corr.]│
│ │   236    ││      31%          ││       ● 17           │             │
│ └──────────┘└───────────────────┘└──────────ver tasks───┘             │
│ Tempo por status (mediana / P85, dias úteis)                          │
│  Em andamento  ██████ 3,2 ─────┤ 6,1                                  │
│  Em revisão    ████████████ 5,4 ───────┤ 11,0   ● espera              │
│  Bloqueado     ██ 1,1 ──┤ 2,3          ▲ bloqueio                     │
│ ┌ Mediana por pessoa × status (dias) ─────────────────────────────┐   │
│ │            Andamento  Revisão  Bloqueado                        │   │
│ │ Ana        ✓ 2,1      ● 4,8    —                                │   │
│ │ Bruno      ✓ 2,8      ▲ 7,2    ● 3,0                            │   │
│ └─────────────────────────────────────────────────────────────────┘   │
│ Abertas paradas além do normal (tabela, 50 linhas, linha → gaveta)    │
└───────────────────────────────────────────────────────────────────────┘
```
Alternância dias úteis × corridos fica no cabeçalho e persiste na URL.

### 6.3 Calendário do time

```
┌ Calendário do time        [Squad▾]  ‹ Out 2026 ›  [Semana|Mês]  [CSV] ┐
│            seg 26  ter 27  qua 28  qui 29  sex 30  sáb  dom           │
│ Ana        ▓▓▓▓▓▓  ▓▓▓▓▓▓  ▓▓▓▓▓▓  ▓▓▓▓▓▓  ▓▓▓▓▓▓   (férias)          │
│ Bruno      ·       ·       ◆2      ·       ◆1                         │
│ Carla      ·       ·       ·       Feriado ·                          │
│ ───────────────────────────────────────────────────────────────────── │
│ Capacidade do squad  82%    80%     71%     55%     60%               │
│ Vencimentos          3      5       9 ▲     4       6                 │
│ Legenda: ▓ ausência  ◆n vencimentos da pessoa  ▲ dia crítico         │
│ Clique numa célula → drawer: tasks que vencem, quem cobre (competência│
│ + folga) e horas a replanejar                                         │
└───────────────────────────────────────────────────────────────────────┘
```
Ausências aparecem sem motivo detalhado. Vem de YAML versionado; a tela é só leitura.

### 6.4 Auditoria de alterações

```
┌ Auditoria · Alterações de data, dono e label     [Squad▾][Período][CSV]┐
│ ┌Alterações┐┌Sem autorização┐┌Alertas (autorizadas)┐┌Task×Review ≠ ┐  │
│ │  128     ││ ▲ 14          ││ ● 9                 ││ ● 7,2%       │  │
│ └──────────┘└────ver tasks──┘└──────ver tasks──────┘└───ver tasks──┘  │
│ Filtros: Sinal ▾  Quem alterou ▾  Autorizado ▾  Reporter ▾            │
│ ┌─────────────────────────────────────────────────────────────────┐   │
│ │ Sev  Sinal                     Task    Quem        Autorizado   │   │
│ │ ▲▲▲  Vencimento mudou          TG-123  dev.a       ✕ não        │   │
│ │ ▲▲   LATE_SENT c/ posse ≥ 2 d  TG-140  dev.b       —            │   │
│ │ ●    Label após fechada         TG-151  pl.c        ✓ sim → alerta│  │
│ └─────────────────────────────────────────────────────────────────┘   │
│ Clique na linha → gaveta: histórico campo a campo (de → para, quando, │
│ por quem), linha do tempo de posse por pessoa, link para o Jira       │
│ Aviso fixo: indício para revisão humana, não prova                    │
└───────────────────────────────────────────────────────────────────────┘
```
Acesso: coordenador e gerente. Toda abertura é registrada.

### 6.5 Meu painel (início do usuário)

```
┌ Meu painel                                        Olá, Ana  [Tema][PT]┐
│ ┌Minhas abertas┐┌Vencem em 7 d┐┌Ocupação 4 sem┐┌Ausências futuras┐   │
│ │     14       ││  ● 3        ││   92%  ●     ││  1 (10–14 nov)  │   │
│ └──────────────┘└───ver tasks─┘└──────────────┘└─────────────────┘   │
│ Minha fila (da mais urgente)                                         │
│  TG-210 Checklist … vence 28/10 ▲ risco alto   previsão 30/10 ▲       │
│  TG-215 Review …    vence 30/10 ✓              previsão 29/10         │
│ ┌ Esforço das fechadas no mês ──────────────────────────────────┐    │
│ │ Tipo 3,0 · Testes TEST-* 12 · Horas 41 h · Índice 57          │    │
│ └────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```
Só dados do próprio usuário. Squad Leader vê o mesmo layout para cada pessoa do squad, escolhendo no filtro.

## 7. Comportamento e responsividade

- Desktop (≥ 1280 px) é o alvo; tablet reorganiza para uma coluna; celular: menu vira botão, cartões empilham, tabelas rolam na horizontal dentro do cartão.
- Navegação por teclado completa: foco visível (azul elétrico, 2 px), ordem lógica, `Esc` fecha gaveta e janela.
- `prefers-reduced-motion` respeitado; movimento só para mudança de estado (gaveta, troca de aba).
- Tabelas grandes com paginação virtual; CSV respeita filtros e traz o cabeçalho `# filtros`.

## 8. Pendências para confirmar

1. Logo, manual de marca e fonte Mark da Sidia (hoje só há as cores públicas do site).
2. Confirmar os valores de cor dos temas contra o manual de marca.
3. Aprovar o mapa de telas (seção 4) e os cinco wireframes.
4. Definir a ordem de implementação das telas (sugestão: Central de atenção, Tempo em status, Auditoria, Calendário, Meu painel).

## 9. Próximo passo

Aprovado este plano, a implementação segue o fluxo do Impeccable: registrar `DESIGN.md` a partir do que for construído, construir as telas na ordem acordada e fazer a revisão final com captura em desktop e celular nos três temas.
