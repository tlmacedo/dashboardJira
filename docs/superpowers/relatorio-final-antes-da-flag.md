# Relatório final: tudo que vem antes da mudança da flag do Jira

Data: 29/10/2026. Base: `origin/main` em `1ad9529` (PR #48 mesclado), mais este fechamento (linha A16 do [roteiro](plans/2026-10-28-roteiro-antes-e-depois-da-flag.md) e Tarefa 10 do Plano 8). Escopo: o que o Claude Code implementou sem acessar o Jira, entre 28 e 29/10. O relatório da noite anterior (PRs #25 a #30: CI, hooks, changelog, referência do Jira, Plano 17 escrito, roteiro) está em [relatorio-2026-10-28.md](relatorio-2026-10-28.md).

A flag de acesso ao Jira continua desligada em todo o repositório e nenhum teste, script ou documento a liga. Nenhum acesso ao Jira foi feito em todo o projeto. Tudo foi validado contra fixtures, `MockEngine`, dados de demonstração fictícios e SQLite real em arquivo temporário.

Este relatório só afirma o que está nos arquivos de progresso (`docs/superpowers/progresso/`), no código e no CI. Onde o arquivo de progresso não registra achado de revisão, a seção (b) não inventa um.

## (a) O que foi feito

Vinte PRs, na ordem em que entraram na `main` (a tabela segue a ordem de merge, que difere da numeração). Foram mesclados pelo [fluxo de entrega](fluxo-de-entrega.md), seção 5, como o roteiro manda. Os arquivos de progresso registram em cada plano a revisão que houve; a revisão em Opus aparece depois da tabela.

| Ordem | PR | Linha do roteiro | Plano e tarefas | Entrega |
|---|---|---|---|---|
| 1 | #31 | A1 | 17a, T1 a T7 | Catálogo de cartões (`catalogoPadrao()`), drill genérico `GET /api/cartoes/{id}/tasks`, teste de contrato (número do cartão = total do drill), componentes `Cartao` e `GavetaDeTasks`, Tempo em status e Sincronização migrados, tela Mapa de cartões |
| 2 | #32 | A2 | 4, T4 a T9 | Sinais de labels de atraso, Task × Review, "no prazo" e "no prazo ajustado", leitura no SQLite, API de auditoria, telas Auditoria e Divergência |
| 3 | #33 | A3 | 5a, T1 a T9 | Abertas, Negligenciadas (coluna "último movimento", I-06), Fechadas, Metas e prazos; migração V2 |
| 4 | #34 | A4 | 5b, T10 a T14 | Negativas, Riscos, Previsão (Monte Carlo com taxa de chegada), Épicos; `SerieSemanal` |
| 5 | #35 | A5 | 6a, T1 a T9 | Equipes, ausências, planejamento em YAML, capacidade, Agenda, Carga, Capacidade, Calendário, Plano, Vencimentos, Gargalos, Tendência |
| 6 | #36 | A11 | 17b, T8 a T13 | Fluxo da semana, Envelhecimento por etapa, Bloqueios entre tasks, Controle do ciclo, aba Cruzamentos (rodou antes do 14b por ordem do usuário em 28/10) |
| 7 | #38 | A15 (parte) | 15, T1 a T4 | Componente de marca, fonte com alternativa de sistema, tokens finais, moldura do tema Sidia, tema sem piscada |
| 8 | #37 | A13 | 12, T1 a T12 | Logs JSON sem segredos, prontidão, métricas em porta interna, 14 alertas, backup diário com restauração testada, rotação do `SESSION_SECRET`, runbooks |
| 9 | #39 | A6 | 6b, T10 a T16 | Worklog por instância (V4), Posse, Esforço, Cobertura, Simulador, Competências, Revisões, Carga por tipo/OS; `SomenteLeituraTest` |
| 10 | #41 | A12 (parte) | 8, T2, T6, T7, T8 | SSO/OIDC genérico testado com emissor falso, cache de leitura e orçamento de tempo na escala da spec, pacote de produção, checklist da 1ª conexão e matriz |
| 11 | #40 | A7 | 7a, T1 a T8 | Checklist × Review e Integridade (12 sinais, só o gerente, sempre auditada) |
| 12 | #42 | A12 (parte) | 8, endurecimento | Máscara de log, login OIDC, cache e pacote, a partir da revisão do #41 |
| 13 | #43 | A8 | 7b, T9 a T18 | Distribuição e Rebalanceamento em modo sombra (V5), Decisões, Qualidade dos dados, Registro de acesso, Configuração em leitura, Central de atenção, Meu squad, Meu painel |
| 14 | #44 | A9 | 10, T1 a T12 | Campos do dicionário (V6), filtros por campo, gaveta universal da task, busca por chave, tabela virtual, estados de tela, paleta de gráficos, I-26 e I-27 |
| 15 | #45 | A10 | 11, T1 a T12 | Trava do DPO, auditoria completa (estimativa reduzida e worklog retroativo), retenção por categoria, Meus dados, documentos para o DPO (V7) |
| 16 | #46 | A12 (parte) | 8, T3 sem o passo 5 | Documento de equivalência por card com o app atual; `legacy/` intacto |
| 17 | #47 | A15 (parte) | 15, T5 a T8 | `DESIGN.md`, guia do painel em pt-BR e inglês, perguntas frequentes, material de treinamento por papel |
| 18 | #49 | A12 (parte) | 8, T1, T4, T5 | Inventário de rotas com papel e registro de acesso, acessibilidade WCAG AA por teste, revisão visual por inspeção de CSS |
| 19 | #50 | fora do roteiro | expurgo | Expurgo automático dos dados fictícios ao ligar a flag, estado "aguardando a primeira carga do Jira" |
| 20 | #48 | A14 | 13, T2 a T6, T9, T10 | Estratégia de username do OIDC, proxy confiável, guardas de produção, versão em `/api/meta`, pacote com SHA-256, segredos, atualizar e reverter (ensaiados no Linux; os `.ps1` não foram executados) |

Revisão em Opus registrada nos arquivos de progresso: PRs #43, #45, #48 e #49 (a regra 8 do `CLAUDE.md` limita o Opus à revisão final de código sensível a segurança e a quer dita ao usuário antes; aqui o usuário não estava presente, e isto é o aviso).

Este PR fecha a linha A16: relatório, atualização do roteiro, do índice dos planos, do `HANDOFF.md`, do `PRODUCT.md`, do `app/README.md` e da tabela de caminhos do `CLAUDE.md` (as regras 1 a 8 e as decisões de produto não foram tocadas), e os testes de documento que amarram esses textos ao código (inclusive `claudeMdSemCaminhoInexistente`, que fecha a Tarefa 10 do Plano 8; os testes `guiaCitaCadaItemDoMenu` e `guiaCitaCadaFicha` já existiam em `GuiasDocTest`).

## (b) Problemas encontrados e corrigidos pelas revisões

Achados dos revisores (Important, salvo indicação) que mudaram o código ou os testes antes do merge. A severidade só aparece onde o arquivo de progresso a registra. Detalhe, prova e mutação de cada um: arquivo de progresso do plano.

| PR | Achado | Correção |
|---|---|---|
| #32 | O `access_log.route` do histórico de uma task gravava a chave da issue; o histórico avaliava autorização também onde não há regra | Grava o padrão `/api/auditoria/historico/{key}`; `autorizado = null` onde não há regra |
| #33 | Status fora do mapa numa candidata resolvida antes do período deixava Fechadas e Metas com universo parcial, em silêncio | `Fluxo.exigirStatusMapeados` comum às duas telas; aviso bloqueante `STATUS_SEM_MAPEAMENTO` |
| #34 | O id de épico do drill era derivado da chave (SHA-256 truncado) e ia ao `access_log`: recuperável por enumeração | Id posicional (`epico:<n>:...`); chave de issue nem derivados vão ao registro |
| #35 | Mensagens de erro de carga de `equipes.yaml`, `ausencias.yaml` e `planejamento.yaml` citavam id, data ou número lido; datas extremas (`+999999999-12-31`) passavam em `dataOuNulo` | Erros citam só arquivo e item `#N`; `dataOuNulo` usa a leitura comum (1970 a 2200), 400 sem eco |
| #36 | Cruzamentos destacava "faixa mais alta" com pessoa nas linhas ou colunas: ranking visual de pessoas | Sem destaque nem legenda com pessoa em qualquer eixo |
| #37 | Exceção em fase inicial vazava o caminho cru no log; método HTTP e rota com cardinalidade aberta nas métricas; sessão aceitava `fim` além da duração máxima | Log só com modelo da rota e classe; método em lista fixa mais OUTRO; sessão rejeita fim fora da duração |
| #40 | (I1) A tabela de evidências mostrava o autor na coluna "Responsável na época", culpando a pessoa errada; (I2) um fechamento rápido gerava dois sinais e mudava o nível da pessoa; (I3) a `TravaDpo` abria sobre um banco já sincronizado; (I4) o `access_log` guardava o próprio achado (pessoa, sinal, total); IMP-1: caminho codificado (`%69ntegridade`) contornava o registro | Colunas distintas; um sinal por fechamento rápido; trava consulta `sync_run` e marca d'água; rotas sensíveis gravam só quem consultou; `normalizarCaminho` |
| #42 | Máscara de log levava cerca de 7 s em 20 mil caracteres; descoberta OIDC aceitava endereços internos; `ConsumoUnico` cheio recusava login | Corte em 8 KB com padrões limitados, `OIDC_HOSTS_PERMITIDOS`, sem IP literal nem `localhost`, descarte do mais antigo |
| #43 | (I-1) Meu painel mostrava só "no prazo", contra a regra das duas métricas lado a lado; (I-2) indício alto na Central sem os avisos "indício, não prova" e "validar base legal"; (I-3) o Squad Leader via e decidia task sem responsável; (I-4) valores de filtro vazavam do `access_log` para a tela | MP-06 com as duas; avisos fixos; só coordenação e gerente veem task sem dono; só nomes de filtro na tela |
| #44 | (I-1) O `access_log` gravava a chave de issue em `/api/tarefa/{key}` e em drills; (I-2) a gaveta da task seguia a posse histórica e mostrava task a ex-dono; barra invertida crua e ids de cartão falsificavam a rota registrada | Rótulo é sempre o modelo da rota casada (`modeloDaRota`) ou rótulo fixo; gaveta só pelo responsável atual |
| #45 | (I-1) A `TravaDpo` abriria sobre dado real depois da V7 (que zera a marca d'água); (I-2) o relatório para o DPO negava a pontuação que a Integridade entrega | Trava fecha com qualquer linha de `sync_state` de instância do Jira; relatório descreve pontos, taxa, múltiplo e nível, com teste |
| #46 | (I-1) Linhas "equivalente" no documento de equivalência sem prova numérica contra o legado (faltava `CompatLegadoTempoEmStatusTest`); (I-2) "equivalente" sem distinção de "só consistente" | Teste de compatibilidade criado; quarta situação "consistente"; 29 ids reclassificados |
| #47 | O guia dizia que o seletor "Papel (simulação)" mudava o escopo de dados; em modo dev ele só filtra o menu | Guias, FAQ e treinamento reescritos; escopo por papel só vale em produção com login |
| #48 | (I1) Risco de escalada de privilégio: com as estratégias `parte_local_do_email` e `conta_windows`, um convidado de outro domínio com a mesma parte local de um gerente recebia os papéis dele; (I2) escuta comparada por texto em vez de endereço; (I3) cabeçalhos `X-Forwarded-*` repetidos; (I4) `versao-anterior.txt` num lugar escrevível pelo serviço; (J1) TOCTOU: o `reverter` e o `atualizar` conferiam a cópia ou o zip e depois a liam de novo, e um link plantado levava um segredo para o banco | `OIDC_DOMINIOS_PERMITIDOS` obrigatória nessas duas estratégias; comparação pelo endereço; todas as linhas do cabeçalho; arquivo na raiz só de root; cópia lida uma vez para pasta temporária 0700 de root e restaurada só dali (`ensaio-troca.sh` imprime `TOCTOU_OK`) |
| #49 | (I1) O inventário de rotas dizia coberto o que não era (a Central marcava `ROTA_SENSIVEL` sem teste) | Teste ponta a ponta com a Central do gerente; mutação que apaga a marca derruba o teste |
| #50 | (I-1) O expurgo poderia apagar dado real de um projeto com chave `DEMO`; (I-2) tentativa que falhou tirava o aviso "aguardando a primeira carga"; (I-3) demonstração carregava sobre banco com tentativa real | Expurgo só age antes da primeira tentativa de sincronização (checada na mesma transação); `cargaConcluida` separada de `houveTentativa`; `BANCO_COM_SINCRONIZACAO_REAL` |

Outros achados corrigidos fora das revisões de PR: as falhas que só o CI encontrou e as 3 Important dos hooks (relatório da noite), e a suíte do frontend que falhava sob carga por tempo-limite de 1 s do Testing Library (corrigido no #49: `asyncUtilTimeout` de 5 s e `testTimeout` de 20 s, sem pular teste).

## (c) Decisões tomadas sozinho pela regra "(Recomendada)"

Como o usuário pediu para não ser consultado no lado "antes", cada ponto de parada sem efeito fora do repositório foi decidido pela opção recomendada e registrado como `Ruling:` no arquivo de progresso do plano (seção 0 do roteiro). Os efeitos fora do repositório (tag, serviço, firewall, proxy, certificado, remoção do `legacy/`, aceite do DPO, a flag) não foram decididos nem executados.

### Precisam de confirmação do usuário

Estas mudam comportamento, dado guardado ou quem vê o quê. Cada uma tem a reversão e o que acontece se nada for dito (o padrão do código fica como está).

| # | Decisão | Onde está registrada | Como reverter |
|---|---|---|---|
| 1 | **Plano 11, T3, opção A:** o painel guarda o valor bruto das estimativas (`timeoriginalestimate`, `timeestimate`) e a data de criação do worklog. A migração **V7** zera `sync_state.watermark` (força recarga completa quando a flag for ligada) e **remove a tabela `person`** (sem uso em `src/main`) | progresso do Plano 11, Ruling de T3 | A remoção de `person` e o zerar do cursor valem desde a primeira subida com a V7; a recarga só roda quando o usuário ligar a flag. Reverter: criar migração nova que recrie `person` e desfaça as colunas; os sinais `ESTIMATIVA_REDUZIDA`, `WORKLOG_RETROATIVO` e `ESTIMATIVA_AUMENTADA` passam a ficar sem dado. Nunca editar a V7 |
| 2 | **Qualidade dos dados aberta à coordenação** (o plano dizia só o gerente): a Central mostra "sem estimativa" à coordenação e a verificação `LABEL_SEM_CLASSIFICACAO` precisa de tela de destino | progresso do Plano 7, T12 (Minor adiado M-2) | Trocar `ALTA` por `['GERENTE']` em `nav.ts` e o papel da rota e dos cartões `QD-nn` |
| 3 | **Gaveta da task só pelo responsável atual** (`/api/tarefa/{key}`): ex-dono recebe 404. Efeito colateral: as gavetas de Posse, Esforço, Tendência e Carga por tipo abrem "não encontrada" para task que já passou adiante | progresso do Plano 10, Ruling da revisão do #44 (I-2) e "Segunda rodada" | Usar `permitePosse` na rota da tarefa (amplia quem vê; ler a regra de visão antes) |
| 4 | **`OIDC_DOMINIOS_PERMITIDOS` obrigatória** com `parte_local_do_email` e `conta_windows`; sem a lista a produção não sobe (`DOMINIO_DO_USERNAME_AUSENTE`). `claim_direto` (padrão) não usa | progresso do Plano 13, revisão do #48 (I1) | Não recomendado: remover a guarda reabre a escalada de privilégio entre domínios |
| 5 | **Aceite do DPO continua fechado:** Auditoria e Integridade respondem 403 `AGUARDANDO_ACEITE_DPO` fora do modo dev offline até o usuário definir `DASHBOARD_DPO_ACEITE` com a referência do parecer | progresso do Plano 11, Ruling de T1 | Só o usuário abre, depois do parecer. A mecânica é fail-closed e não tem atalho |
| 6 | **Remoção do `legacy/`:** o documento de equivalência (`docs/analise/equivalencia-por-card.md`, com a contagem por situação: equivalente, consistente, diferença esperada e não migrado) espera o "sim"; nada em `legacy/` foi tocado e não há tag `legacy-final` | progresso do Plano 8, "Depois da flag / com o usuário" | Sem "sim", a Tarefa 9 não roda. Antes de remover: congelar os valores dos `CompatLegado` de Capacidade, Distribuição e Checklist (hoje pulados sem a pasta) |
| 7 | **"Quem consultou quem" não fica no `access_log`** das rotas sensíveis: só quem consultou o módulo. Pergunta deixada para o Plano 11 e decidida pela opção mais restritiva | progresso do Plano 7 (I4) e do Plano 11 (T1, pendência 3) | Remover o ramo `sensivel` em `filtrosAuditados` |
| 8 | **Squad Leader não vê nem decide task sem responsável** (só coordenação e gerente), revertendo o ruling da T11 do 7b | progresso do Plano 7, revisão do #43 (I-3) | Voltar `Escopo.veSemResponsavel` para incluir o líder (reabre decisão duplicada por `chave_sugestao`) |
| 9 | **Distribuição e Rebalanceamento só em modo sombra:** a decisão fica no painel (`decisao_distribuicao`, V5, retida 24 meses) e nada vai ao Jira. Escrita no Jira é o Plano 16 | progresso do Plano 7, T11 | Plano 16, só por ordem expressa do usuário |

### Demais decisões (reversíveis; podem seguir sem resposta)

| Plano | Decisão | Como reverter |
|---|---|---|
| 4 | Gavetas pelo contrato do 17a (`/api/cartoes/{id}/tasks`) no lugar de `/api/drill/*`; papel checado dentro de `executarAuditado` para a negação ir ao `access_log`; "em lote (N)" (I-15) com `emLoteMinimo` padrão 5; auditoria lê só a instância `samsung`; link direto ao Jira só com a flag ligada e base https sem credencial | Rotas `/api/drill/*` sobre as mesmas funções; `requireRole`; remover a chave e o campo; parâmetro de instância; (o link sem a trava da flag contraria a regra 1: não reverter) |
| 5 | Fichas de Fechadas e Metas leem os mesmos 6 tipos da sincronização; modelo de `status_map` mapeia `Cancelled: done`; período padrão `de = ate - (N - 1)`; Negativas e Riscos seguem a visão pelo responsável atual; Previsão: data = segunda da k-ésima semana inteira depois da atual, vazão desde a primeira semana com atividade; Riscos com piso de 3 dias úteis (o legado usava 2) | Cada um tem uma linha de reversão no Ruling do progresso do Plano 5 |
| 6 | Restante do Jira igual a zero não vale como informação; `equipes.yaml` usa a chave `membros:` e `ausencias.yaml` a chave `ausencias:`; Posse, Competências, Revisões e Carga por tipo/OS abertas a todos os papéis (cada um vê o seu escopo); simulador recusa hipótese fora de 60 dias ou fração diferente de 0,5 e 1 | Voltar os itens a `LIDERANCA` em `nav.ts` e `SO_LIDERANCA` nas rotas; demais, uma linha cada |
| 7 | Produtividade da Distribuição multiplica a carga (gasto ÷ estimado), o inverso do legado; histerese mede a origem depois do movimento; `INDICIO_ALTO` em cartão próprio (VG-13, só gerente); Meu painel com as duas métricas de prazo lado a lado; a Integridade ficou fora da medição de carga | Trocar o termo em `Distribuidor.fatoresDe`; comparar com `origem.ocupacao`; os demais, no progresso do Plano 7 |
| 8 | Sem dependência nova para OIDC (validação RS256 própria); `PAINEL_HOST` padrão `127.0.0.1`; cache de leitura de 60 s ligado no `main` (`PAINEL_CACHE_SEG=0` desliga); sem migração de índices (o perfil mostrou que índice não resolve); `/auth/logout` pública; início da rotação do `SESSION_SECRET` persistido em `<banco>.rotacao` | Trocar `ValidadorDeIdToken` por `ktor-server-auth-jwt`; `PAINEL_HOST=0.0.0.0`; `PAINEL_CACHE_SEG=0`; tirar a rota de `rotasPublicas` |
| 9 | CI em `ubuntu-latest`, actions por tag maior e não por SHA; proteção da `main` só documentada; perfil de produção só nos scripts do pacote | Trocar `runs-on`; fixar SHA (o teste já aceita); a proteção é ação do usuário |
| 10 | VG-02 e VG-03 ficam na Central com variação relativa em %; filtros rápidos em `filtros_rapidos.yaml` opcional; todo 400 sem código vira `FILTRO_INVALIDO`; rótulo da rota no `access_log` sempre o modelo casado | Recalcular em pontos percentuais; os demais, no progresso do Plano 10 |
| 11 | Retenção por categoria: `RETENTION_*_MONTHS` (1 a 120) vence as variáveis antigas, com piso de 12 meses para auditoria; `/api/me` lista só os módulos bloqueados que o papel enxerga | Tirar o piso em `lerRetencao` |
| 12 | Canal de alertas só arquivo, log, rota e faixa (sem webhook); backup local em volume separado, cifragem e cópia para fora ficam com a infraestrutura; métricas em segundo servidor numa porta interna; nome do serviço nos runbooks `painel-squads` | Webhook é uma `SaidaDeAlertas` a mais; os demais dependem da implantação |
| 13 | Plugin próprio de proxy confiável (o módulo oficial não estava no cache offline); `claim_direto` continua o padrão da estratégia de username; a reversão MOVE o banco e o `-wal` em vez de apagar; `config/` no pacote (não `config-exemplo/`); nome do serviço como parâmetro | Trocar por `XForwardedHeaders`; `OIDC_USERNAME_ESTRATEGIA`; os demais, no progresso do Plano 13 |
| 15 | Ativos de marca pelo caminho alternativo (logo e fonte do sistema, azul `#1329A0` único); treinamento só em pt-BR | Seguir `docs/design/ativos-de-marca.md` quando o usuário entregar os ativos |
| 17 | 17b executado antes do 14b por ordem do usuário (calibração só com dado real, depois); controle do ciclo com `semOutliers=true` e limite de 30 dias corridos em constante, sem YAML novo | Criar `ciclo.yaml` e ler o limite no `AppContext` |
| expurgo | O expurgo só age antes da primeira tentativa de sincronização e a demonstração só carrega em banco que nunca teve tentativa | Mudar `houveTentativa` (enfraquece a proteção do dado real) |

## (d) O que não foi possível ou fica de fora, e por quê

- **Capturas de tela e navegador:** nenhuma tela foi aberta em navegador (o ambiente de execução não tem). Faltam as capturas a 1440 e 390 px nos três temas de todas as telas (Plano 8, T5 só fecha depois delas), as imagens do material de treinamento (o `manifesto.yaml` está vazio) e a conferência real da CSP e dos cabeçalhos. A cobertura vem de testes de CSS, de árvore de acessibilidade e de `testApplication`.
- **Leitor de tela:** sem NVDA nem VoiceOver; a conferência é pela árvore do Testing Library.
- **Os `.ps1` do pacote e da implantação nunca foram executados:** `montar-pacote.ps1`, `verificar-pacote.ps1`, `painel-cli.ps1`, `atualizar.ps1`, `reverter.ps1`, `gerar-session-secret.ps1`, `comum.ps1` e `ensaio-troca.ps1` só passaram por testes estáticos (ASCII, comandos, listas). O CI do Windows (`guarda-windows`) executa apenas o hook `PreToolUse.ps1`. As versões `.sh` foram executadas de ponta a ponta no Linux (`TROCA_OK`, `REVERSAO_OK`, `TOCTOU_OK`, pacote reprodutível). A primeira execução no Windows 11 é do ClineSR.
- **Teste dinâmico de restauração pulado no CI comum:** os testes que dependem de root e do usuário `nobody` (restauração com a identidade do serviço, modos da pasta temporária, recusa de uid 0) são pulados sem privilégio; as provas estáticas e o `ensaio-troca.sh` rodam em qualquer ambiente. Sugestão: um job com `sudo`.
- **`legacy/` não foi removido** e não existe a tag `legacy-final`: depende do "sim" do usuário ao documento de equivalência. Os `CompatLegado` de Capacidade, Distribuição e Checklist ainda leem a pasta e são pulados sem ela.
- **Tag, serviço, firewall, proxy, certificado:** Plano 13, T1, T7 (passo 5, tag e release), T8 (serviço no SO da empresa), T11 a T13, e o canal externo de alertas, a cópia de backup para fora do servidor e a cifragem do volume (Plano 12). Dependem da infraestrutura da empresa; nada disso foi tocado.
- **Provedor OIDC real:** o painel só foi validado contra emissor falso. `OIDC_ISSUER`, a claim com o username e a autenticação do endpoint do token dependem do provedor (Plano 13).
- **Aceite do DPO:** a mecânica existe e fica fechada. O parecer sobre as sete perguntas de `docs/lgpd/relatorio-para-o-dpo.md` e o envio de `docs/lgpd/comunicacao-a-equipe.md` são do usuário.
- **Ativos de marca:** não há logo limpo, manual da Sidia nem licença da fonte "Mark" (Plano 15, T1). Sessões de treinamento (T9) e dono da manutenção (T10 e T11) esperam o usuário.
- **GitGuardian:** os alertas "Generic Password" em `SettingsTest.kt` e `guard.sh` são falsos positivos (valores fictícios de teste); só o usuário os marca no painel dele.
- **`testCarga` no PC da empresa:** os números do Plano 8 vêm de uma VM de 4 vCPU; o p95 da leitura concorrente ficou em 4,1 a 4,3 s contra o orçamento de 4 s, com margem de ruído de 25% no teste. Precisa ser repetido no PC da empresa.
- **Fase 0, Plano 14a e 14b, Plano 16:** por natureza, dependem do PC da empresa, do Jira real ou de decisão expressa do usuário.
- **Calibração com dados reais:** esforço, foco, horas por tipo, pesos da distribuição, faixa mais alta do Cruzamentos, limiar de outlier e a heurística de Riscos esperam o 14b.

## (e) Números

Medidos em 29/10/2026 sobre `1ad9529`. Migrações, cartões e itens do menu são amarrados ao código por `RelatorioFinalDocTest` (o teste reprova se o código passar a ter MENOS que o dito aqui; acrescentar não exige mexer no relatório). As contagens de testes são fotografia da medição e não são amarradas.

- 7 migrações (V1 a V7): `V1__base`, `V2__campos_de_trabalho`, `V3__execucao_de_backup`, `V4__worklog_por_instancia`, `V5__decisao_distribuicao`, `V6__campos_do_dicionario`, `V7__dados_para_auditoria_completa`. A próxima livre é a V8.
- 175 cartões no catálogo (`catalogoPadrao()`) e 43 itens no menu (`nav.ts`).
- Frontend: 80 arquivos de teste e 718 testes, 0 falhas (`pnpm test`).
- Backend: 2.679 testes sobre `1ad9529` e 2.701 com os testes de documento deste fechamento (`./gradlew test --rerun-tasks`), 0 falhas, 1 ignorado (`ValidadorDeConfigTest.bancoEmPastaSemEscrita`, que se pula quando o processo roda como root, como nesta máquina).
- Dependência nova de runtime registrada nos arquivos de progresso: `micrometer-registry-prometheus` (Plano 12). O Plano 8 (OIDC e sessão) e o Plano 13 (proxy confiável) não acrescentaram dependência.

## (f) Lista de ações do usuário, em ordem

1. **Ativar a proteção da `main` no GitHub**, exigindo os quatro checks `backend`, `frontend`, `guarda` e `varredura`; `guarda-windows` entra como recomendado (leva cerca de 18 minutos). Passo a passo em `docs/operacao/ci.md`, seção 5. Só depois do primeiro CI o GitHub oferece os nomes.
2. **Segredo `VARREDURA_NOMES`** (os usernames reais do time, um por linha) e a opção "Require approval for all outside collaborators".
3. **Marcar os falsos positivos do GitGuardian** (`SettingsTest.kt` e `guard.sh`).
4. **Entregar os ativos de marca** (logo em SVG ou PNG transparente, manual de marca, fonte "Mark" com licença): o painel usa alternativas de sistema até lá.
5. **Obter o parecer do DPO** sobre as sete perguntas do relatório e, só depois, dar o aceite (`DASHBOARD_DPO_ACEITE` com a referência) mais o envio do aviso à equipe.
6. **Responder as confirmações da seção (c)** (as nove da primeira tabela), em especial a T3 do Plano 11 e a V7, a Qualidade aberta à coordenação, a gaveta da task e a remoção do `legacy/` (o "sim" ao documento de equivalência).
7. **Limpar os worktrees antigos locais** em `.claude/worktrees/` (`git worktree list` mostra os que sobraram das execuções; o git não os remove sozinho). Confirme antes que a branch de cada um já está mesclada.
8. **Fornecer os YAML reais** em `app/config/` ou em `PAINEL_CONFIG_DIR` (papéis com ao menos um gerente, equipes, ausências, status, vínculos, calendário e os demais obrigatórios em produção listados em `app/README.md`), a partir dos modelos `.exemplo`. Ficam fora do git.
9. **Fase 0 no PC da empresa** (`docs/superpowers/plans/2026-10-27-plano-00-pc-da-empresa.md`): JDK 21, Node 22 com pnpm, clone, suíte verde, PowerShell sem bash nem WSL.
10. **Credenciais do Jira** no `app/.env` do PC (token pessoal da conta de leitura, nunca no repositório).
11. **Definir os tetos pequenos da primeira carga, ainda com a flag desligada:** `SYNC_MAX_ITENS=50` e `SYNC_MAX_REQUISICOES=5` no `app/.env` (os padrões do código são 50000 e 2000; sem os tetos a primeira carga sai sem limite). Só sobem depois da primeira carga conferida (`docs/jira/runbook-sincronizacao.md`, Etapa 1).
12. **Por último, mudar a flag à mão** em `app/.env` (a linha da flag com valor verdadeiro), reiniciar o painel e avisar o ClineSR, que começa pelo Plano 14a, Tarefa 1. Só o usuário muda a flag.
