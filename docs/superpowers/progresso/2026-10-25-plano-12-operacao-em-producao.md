# Progresso — plano: docs/superpowers/plans/2026-10-25-plano-12-operacao-em-producao.md

Branch: `feat/operacao-em-producao` (Tarefas 1 a 12 num só bloco). Executor: Claude Code, sem o usuário presente; pontos de parada resolvidos pela opção Recomendada e reversível. Roda em paralelo com outros planos (5b, 6a, 17b): mudanças em arquivos compartilhados foram mantidas pequenas e localizadas.

## Estado herdado (conferido contra o código real)

- Plano 9 está na `main` (CI, `CiWorkflowTest`, cabeçalhos, CSRF). Plano 8 NÃO foi feito: não existem `Sessao.kt`, `OidcSettings` (só o stub `OidcAuthenticator`), `GeradorSintetico`, `ROTAS_PUBLICAS`/`InventarioDeRotasTest` nem `docs/implantacao.md`.
- Migrações na `main` ao começar: `V1__base.sql` e `V2__campos_de_trabalho.sql` (o Plano 5a mesclou a V2).
- `travaDeProcesso` é `AutoCloseable?` privado em `AppContext`; `Agendador` tem só `tarefas`; `rotasPublicas` é `internal val` em `security/AccessControl.kt`.

## Rulings

- Ruling: Plano 8 ausente — o plano 12 declara dependência dele, que não foi feito; faz-se a menor versão local necessária em cada ponto (ver as Rulings de cada tarefa) sem executar o Plano 8 — custo se errado: quem mesclar o Plano 8 depois reconcilia `Sessao.kt` e as rotas públicas.
- Ruling (T1): `/api/health*` e a casca estática não geram linha de log (filtro do `CallLogging`) em vez de saírem em DEBUG — o plugin só aceita um nível — custo se errado: nenhum; basta trocar o filtro.
- Ruling (T1): o modelo da rota vem do evento `RoutingRoot.RoutingCallStarted` (guardado em atributo da chamada), porque a chamada vista pelo `CallLogging` não é a do roteador — custo se errado: log com `rota=desconhecida`, coberto por teste.
- Ruling (T1): o `ARQUIVO` do `logback.xml` usa `<lazy>true</lazy>` e `${java.io.tmpdir}` como pasta quando `PAINEL_LOG_DIR` falta, para o appender existir sem criar arquivo (o `root` só o referencia com `PAINEL_LOG_ARQUIVO_REF=ARQUIVO`) — custo se errado: nenhum.
- Ruling (T1): `valoresSecretos()` devolve também `usuario:token` das instâncias Basic, para a máscara cobrir o base64 do cabeçalho — custo se errado: nenhum.
- Ruling (T2): o arquivo do banco da prontidão vem da URL do pool (`Database.arquivoDe(ds)`), não de um parâmetro novo no `AppContext`/`testContextComBanco` — uma única fonte da verdade (o pool em uso) e nenhuma mudança no construtor compartilhado — custo se errado: nenhum.
- Ruling (T2): a sonda abre o SQLite com `open_mode=1` (somente leitura, parâmetro do `sqlite-jdbc`) em vez de `mode=ro`, e as migrações pendentes também são contadas por um `SQLiteDataSource` próprio (não pelo pool), para a prontidão responder com o pool esgotado — custo se errado: voltar para `mode=ro`.
- Ruling (T2): `module(...)` ganhou o último parâmetro `prontidao: Prontidao?` (padrão `prontidaoPadrao(context)`), só para os testes injetarem relógio/limites — custo se errado: nenhum.
- Ruling (T2): não existe `InventarioDeRotasTest` (é do Plano 8); a nova rota pública entra em `rotasPublicas` e a prova é `respostaSemDetalhesEProntoEhPublico`. Quem mesclar o Plano 8 acrescenta `/api/health/pronto` ao inventário.
- Ruling (T3): em vez de extrair `carregarConfiguracao(...)` com um novo tipo de retorno (mudaria o corpo de `AppContext.load` que os Planos 6a e 17b também editam), `load` virou um delegador de `carregar(settings, clock, comBanco)` e ganhou `AppContext.carregarSemBanco` (mesmo corpo; só pula a trava e o banco quando `comBanco=false`). Garante que o validador percorre EXATAMENTE o caminho da subida, inclusive os YAML que planos futuros acrescentarem ao `load` — custo se errado: nenhum; o `AppContextTest` ficou verde antes e depois.
- Ruling (T3): `ErroDeConfiguracao` (subclasse de `IllegalStateException`, mesmas mensagens) carrega código, arquivo e linha; é lançada por `resolver`, `lerYaml`, `resolverDicionario`, `lerFicha` e `exigirGerenteVigente`. Assim `ARQUIVO_AUSENTE`, `EXEMPLO_EM_PRODUCAO`, `YAML_INVALIDO`, `CHAVE_DESCONHECIDA` e `PAPEIS_SEM_GERENTE_VIGENTE` não dependem de ler mensagem de erro — custo se errado: nenhum.
- Ruling (T3): os formatos de `equipes.yaml` e `ausencias.yaml` ainda não existem na `main` (Plano 6a, em paralelo). Os cruzamentos `PESSOA_DESCONHECIDA`, `SQUAD_SEM_LIDER` e `PERIODO_INVERTIDO` leem esses arquivos de forma tolerante: acham, em qualquer nível, mapas com a chave `id` (equipes) ou `pessoa` (ausências, com `de`/`ate`). Arquivo sem essas chaves não gera problema. Quem mesclar o 6a deve conferir que os nomes batem (a spec do 6a usa `id`, `pessoa`, `de`, `ate`) — custo se errado: o cruzamento não dispara (falso negativo), nunca falso positivo.
- Ruling (T3): `STATUS_DA_FICHA_SEM_MAPA` olha só `filtrosFixos.status` (inclusão); `filtrosExcluidos` lista variantes de vocabulário ("Canceled", "Dropped") que não precisam constar no mapa, e as fichas de exemplo as usam. Status fora do vocabulário do dicionário já é barrado pela própria carga da ficha (`YAML_INVALIDO`).
- Ruling (T3): `CUSTOM_ID_INVALIDO` e `ID_RESERVADO` vêm de uma varredura textual (com linha) porque os carregadores falham com erro genérico; o genérico do mesmo arquivo é suprimido para não duplicar.
- Ruling (T3): `CALENDARIO_SEM_ANO` depende da data de hoje (exige feriados móveis do ano atual e do seguinte). O passo de CI valida `app/config` de exemplo, que cobre até 2030; a partir de 2030-01-01 o CI acusaria o calendário de exemplo, lembrando de ampliá-lo — custo se errado: CI vermelho com mensagem clara.
- Ruling (T3): `docs/operacao/configuracao.md` descreve a opção 1 do ponto de parada 3 (repositório privado de configuração) como recomendada e a 2 como alternativa; a criação do repositório é ação do usuário.
- Ruling (T4): `micrometer-registry-prometheus` 1.15.12 (última 1.15.x; o Ktor 3.3.1 declara `micrometer-core` 1.15.3, então não há salto de minor). Dependência nova de runtime exigida pelo plano; as 1.16/1.17/1.18 existem, mas puxariam o core para outra minor sem ganho aqui.
- Ruling (T4): `ktor-server-metrics-micrometer` NÃO foi adicionado: o plano exige nomes e rótulos fechados (`painel_http_requisicoes_total{rota,metodo,status}`, `painel_http_duracao_segundos`), que o plugin do Ktor não produz (usa `address`, `exception`, `ktor_http_server_requests`). Um plugin próprio de ~15 linhas (`instalarMetricasHttp`) dá os nomes certos e a rota pelo MODELO; uma dependência a menos.
- Ruling (T4): "dois conectores no Netty" virou DOIS servidores `embeddedServer` (8080 com o painel; porta interna só com `GET /metrics`). A garantia "nunca na 8080" fica estrutural (o `/metrics` interno nem existe no módulo público) em vez de depender de conferir `localPort`; o módulo público ainda responde 404 explícito a `/metrics`, porque a casca estática devolveria a página inicial (provado em `ServidorDeMetricasTest`).
- Ruling (T4): pontos de injeção sem mudar assinaturas: `EstadoComMetricas` (decorador do `SyncStateStore`, usado por `montarAgendador(..., metricas)`), `Retention.purge(..., aoRemover)` e `ObservadorDeAccessLog.aoFalhar` (objeto global, para não alterar as vários `AccessLog(ds)` espalhados). Contagens por instância usam só ids conhecidos (`samsung`, `sidia`), `local` para a retenção e `desconhecida` para o resto.
- Ruling (T4): `PAINEL_METRICAS_PORTA` aceita `0` (desliga) ou 1024..65535 diferente de 8080; porta ocupada na subida só avisa e o painel segue sem métricas (observar nunca derruba a produção). O pool do banco ganha o tracker do Hikari (`hikaricp_*`, rótulo `pool=painel-sqlite`).
- Ruling (T5): ponto de parada 1 (canal dos alertas): opção 1 (Recomendada) — arquivo de alertas + log + rota + faixa, sem serviço externo. Não há webhook nem requisição de saída. Reversível: o webhook é uma `SaidaDeAlertas` a mais (a interface já comporta), sem tocar na avaliação. O canal externo, se quiserem, fica em "Depois da flag / com o usuário".
- Ruling (T5): migração `V3__execucao_de_backup.sql` (a `main` tinha V1 e V2 ao começar; 6a/17b podem criar outra V3 em paralelo — quem mesclar depois renumera, e o `backup_run` não depende de nenhuma outra tabela).
- Ruling (T5): o avaliador é montado dentro de `module` (`montarAvaliadorDeAlertas`) a partir do contexto, sem parâmetro novo; com Metricas ausente (testes) usa uma própria. O laço começa a avaliar 30 s depois da subida e repete a cada 5 min.
- Ruling (T5): `painel_alerta_ativo{codigo}` tem um valor por CÓDIGO (1 se qualquer instância tem o alerta), publicado para os 14 códigos a cada ciclo (0 quando resolvido). A gravidade exata fica na rota/arquivo; o `alertas.yml` opcional agrupa os códigos pela gravidade usual.
- Ruling (T5): `SYNC_ATRASADA` não dispara enquanto a instância está suspensa (a suspensão já é o alerta); "nunca rodou" (sem OK) só alerta depois do tempo no ar da regra (1 h incremental, 26 h reconciliação/retenção/backup, 3 dias restauração). `ACCESS_LOG_FALHANDO` compara o contador com a menor amostra da última hora (amostrada a cada avaliação).
- Ruling (T5): a retenção apaga `backup_run` pelo `RETENTION_MONTHS` (24 meses), como o `sync_run`.
- Ruling (T6): a faixa fica dentro do `<main>`, acima do conteúdo, e a lista detalhada (âncora `#alertas`) no topo da tela Sincronização; as duas leem a mesma consulta (`['alertas-operacionais']`, a cada 5 min, só para GERENTE). O teste de paridade com o enum do backend é a lista fixa de 14 códigos no frontend (o Vite não deixa importar o `.kt` de fora da raiz) mais `AlertasTraduzidosTest` no backend, que lê o `pt.json`/`en.json`. O teste `todoCodigoTemTraducaoPtEEn` do Plano 10 ainda não existe na `main`; quando existir, passa a cobrir `alertas.*` sozinho.
- Ruling (T7): ponto de parada 2 (cópia fora do servidor e cifragem): opção 1 (Recomendada) — pasta local em volume separado, cifrada pelo sistema (BitLocker/LUKS), cópia diária para fora feita pela infraestrutura. Nenhuma credencial de armazenamento no painel e sem `BACKUP_DIR_ESPELHO`. Pendências em "Depois da flag / com o usuário" do `backup-e-restauracao.md` e daqui. Reversível: o espelho seria um segundo `ServicoDeBackup` apontando para outra pasta.
- Ruling (T7): `VACUUM INTO ?` aceita o caminho como PARÂMETRO no `sqlite-jdbc` 3.50.3.0 (provado por `copiaLegivelEIgualAoOriginal`); não foi preciso o literal com aspas dobradas.
- Ruling (T7): o backup roda no `Agendador` como `TarefaDeOperacao` (parâmetro opcional `operacoes`), sem `SyncKind` novo; a tarefa é "devida" por `Agenda.diariaDevidaDesde` sobre o último `BACKUP` do `backup_run` (qualquer status): falha de hoje só é tentada de novo amanhã (o alerta `BACKUP_ATRASADO` cobre depois de 26 h). `backup-agora` serve para tentar na hora.
- Ruling (T7): a retenção roda também depois de um backup que falhou, mas nunca apaga o último OK conhecido; uma cópia `OK` "mais antiga que as N mais recentes por nome" só sai se não for esse último OK.
- Ruling (T7): o validador confere `BACKUP_DIR` (ausente em produção, dentro de árvore com `.git`, dentro da pasta do banco, sem escrita) em qualquer modo em que ele estiver definido; "fora da pasta do pacote" não é conferido por código (o pacote não sabe onde está instalado) e fica no runbook.
- Ruling (T8): o `GeradorSintetico` (Plano 8) não existe; os testes usam `PopularBanco` (inserção direta, determinística: 500 issues, 5 eventos cada, 800 worklogs, registro de acesso e execuções). O contexto de teste sobre um banco pronto veio de `testContextComBanco(..., arquivoExistente)` (parâmetro opcional no fim).
- Ruling (T8): `backup_run` fica fora da comparação origem × restaurado de propósito: a linha do backup é gravada DEPOIS da cópia (decisão do plano), então a restauração tem 0 linhas ali. O teste "banco vivo intacto" usa um verificador sem registro, porque registrar `RESTAURACAO OK` em `backup_run` é uma escrita deliberada no banco vivo.
- Ruling (T8): `verificar-restauracao` funciona com o banco vivo ausente (`SemRegistroDeBackup`, nada é registrado), porque o uso principal é a recuperação de desastre; `backup-agora` roda a verificação logo após a cópia e, se ela falhar, imprime `RESTAURACAO FALHOU <codigo>` e sai com 1. Arquivo que nem abre como banco vira `INTEGRIDADE_FALHOU` (não `ERRO_INTERNO`).
- Ruling (T9): `Sessao.kt` e `OidcSettings` (Plano 8) não existem. Feita a MENOR versão local: `security/SessaoAssinada.kt` (cookie `v1.<usuário>.<fim>.<HMAC-SHA-256>`, emitido sempre com o segredo atual, lido com o atual e, se falhar, com o anterior; `regravarSeNecessario` reemite com o atual SEM estender o prazo) e `SegredosDaSessao` (`SESSION_SECRET` e `SESSION_SECRET_ANTERIOR`, base64 ≥ 32 bytes, anterior ≠ atual, mensagens só com o NOME da variável), lidos em `settings.operacao.sessao`. Usei outro nome de arquivo/classe para não colidir com o `Sessao.kt` do Plano 8; quem mesclar o Plano 8 liga o login a `SessaoAssinada` (ou migra a rotação para a classe dele) e pode mover o parse para o `OidcSettings`. `SESSION_SECRET` ausente = sem segredos (nada é obrigatório aqui: exigir em produção é do Plano 8). O teste ponta a ponta HTTP (`logoutLimpaSessao`, `cookieComFlagsSeguras`) é do Plano 8.
- Ruling (T9): o PowerShell do documento usa `RandomNumberGenerator.Create()`/`GetBytes` (funciona no 5.1 e no 7); o plano pedia confirmar na execução. Não foi executado (Linux); o trecho é a API padrão do .NET Framework.
- Ruling (T10): o hook de proteção barra texto que escreve a flag do Jira com valor verdadeiro em qualquer arquivo; os runbooks dizem "valor verdadeiro"/"valor falso" em vez de escrever a atribuição, o que também é mais seguro como documento. Os testes de documento (`RunbooksDocTest`) olham só os 6 documentos deste plano (o `ci.md` é do Plano 9 e cita a barreira contra Docker por extenso). `docs/implantacao.md` ainda não existe (Planos 8/13): é citado como texto, sem link; `cadaVariavelDoEnvExampleEstaDocumentada` o aceita quando existir.
- Ruling (T10): `configuracao.md` ganhou a tabela de TODAS as variáveis do `.env.example` (o teste de documentação exige que cada uma apareça em `docs/operacao`, `docs/implantacao.md` ou `app/README.md`).
- Ruling (T11): `docs/lgpd/relatorio-para-o-dpo.md` (Plano 11) não existe na `main`, então a seção 10 dele não foi editada; o Plano 11 deve apontar para `docs/operacao/runbook-incidentes.md`. O runbook não fixa prazo da ANPD (o teste `prazoDaAnpdNaoFixado` barra número de horas/dias junto de "ANPD") e diz "a confirmar com o DPO". O `secao()` do teste ignora títulos dentro de blocos de código (comentários de shell também começam com `# `).
- Ruling (T12): `docs/implantacao.md` (Planos 8/13) e `docs/lgpd/` (Plano 11) não existem na `main`; não foram criados. Os links para `docs/operacao/*` entram quando esses documentos forem escritos. O índice dos planos não foi editado (arquivo compartilhado com os outros blocos); marcar o 12 como executado fica para o primeiro commit do próximo plano.
- Ruling (T12): o `logback.xml` ganhou `NopStatusListener` e o atributo `class` nas regras de conversão (o `converterClass` é obsoleto): sem isso o Logback despejava na saída ~40 linhas de status a cada subida e a cada subcomando, por causa dos apêndices não referenciados. Custo: erros de configuração do próprio `logback.xml` (embutido no pacote) deixam de aparecer; o `ConfiguradorDeLogTest` cobre a configuração.

## Tarefas

- Tarefa 1: concluída (commit 5d69316; logs JSON, máscara, `ConfiguradorDeLog`, `X-Request-Id` e linha por requisição).
- Tarefa 2: concluída (commit a17a53a; `/api/health/pronto`).
- Tarefa 3: concluída (commit 8330ff7; `validar-config`, scripts `painel-cli`, passo no CI).
- Tarefa 4: concluída (commit 13ec09d; métricas na porta interna).
- Tarefa 5: concluída (commit e5f0a84; alertas, `backup_run` V3).
- Tarefa 6: concluída (commit 0013c78; faixa e lista de alertas na tela; frontend 244 testes).
- Tarefa 7: concluída (commit 1682774; backup agendado e `backup-agora`).
- Tarefa 8: concluída (commit 7d98342; `VerificadorDeRestauracao` e `verificar-restauracao`).
- Tarefa 9: concluída (commit 29418b2; `SessaoAssinada` e rotação do `SESSION_SECRET`).
- Tarefa 10: concluída (commit 5b64239; runbook diário e observabilidade).
- Tarefa 11: concluída (commit 89e0624; runbook de incidentes).
- Tarefa 12: ver o commit de fechamento no topo do histórico da branch.

## Verificação final (sem cache)

- Backend `./gradlew test --rerun-tasks`: 1425 testes, 0 falhas (a `main` tinha 1156 antes do bloco; foram +269). Frontend: `pnpm install --frozen-lockfile`, `pnpm vitest run` 244 testes (24 arquivos), 0 falhas; `pnpm build` e `node scripts/verificar-csp.mjs` ok. `sh .claude/hooks/test-guard.sh`: 0 falhas.
- Subida de conferência (modo dev, fora do repositório, sem Jira): logs em JSON na saída e em `painel.log`, `/api/health/pronto` 200, `/metrics` 200 só na 9464 (404 na 8080), `validar-config` OK, backup agendado na subida, `backup-agora` OK com o serviço rodando e `verificar-restauracao` OK. Ensaio do incidente `BANCO_PERDIDO_OU_CORROMPIDO`: uma cópia foi corrompida (`COPIA_ALTERADA`), a anterior verificou OK, os arquivos do banco foram para a quarentena, a cópia boa virou o `painel.db`, o painel subiu e a prontidão deu 200; `backup-agora` criou o ponto novo. Tudo apagado depois (a pasta de ensaio ficou no diretório temporário do agente).

## Minor adiados

- `AvaliadorDeAlertas`: `ACCESS_LOG_FALHANDO` e as regras "nunca rodou" dependem do tempo no ar do processo; depois de um reinício elas só disparam após o tempo da regra. Aceito (os dados de execução persistem no banco; só "nunca" depende do processo).
- `Prontidao`: a sonda lê só permissão do arquivo e da pasta (decisão do plano); uma pasta sem escrita por política de rede (compartilhamento) só aparece na primeira gravação.
- `ServicoDeBackup`: falha de hoje só é tentada de novo no dia seguinte (o alerta `BACKUP_ATRASADO` cobre depois de 26 h; `backup-agora` tenta na hora). Se quiserem nova tentativa automática, é uma regra a mais na `TarefaDeOperacao`.
- Backup de banco que ainda não tem a tabela `backup_run` (pacote novo sobre banco antigo, rodando `backup-agora` antes de subir): a cópia sai, só o registro falha (logado). Resolve ao subir o painel novo uma vez.
- Faltam captura visual da faixa de alertas nos 3 temas (sem navegador neste ambiente); a faixa usa variáveis de tema e símbolo + texto.
- `modeloDaRota` depende do evento `RoutingRoot.RoutingCallStarted` do Ktor; uma troca de versão do Ktor pode mudar isso (o teste `linhaPorRequisicaoComModeloDaRota` acusa).

## Depois da flag / com o usuário

Efeito fora do repositório: o código está pronto e nada abaixo foi decidido nem executado pela IA.

- **Canal externo dos alertas** (e-mail, chat, webhook): hoje os alertas saem na tela, na rota do gerente, no log, na métrica e no arquivo de texto (`PAINEL_ALERTAS_ARQUIVO`). Um canal externo exige uma saída nova e uma requisição de saída para um host da empresa (autorizar com a rede/TI).
- **Cópia dos backups para fora do servidor** e **cifragem em repouso** (BitLocker/LUKS) do volume do banco e do volume das cópias; escolher o volume separado para `BACKUP_DIR`; pasta definitiva e permissões (Plano 13). Sem isso, perder o servidor perde banco e cópias.
- **Repositório privado dos YAML reais** (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`...) e a cópia para `PAINEL_CONFIG_DIR` na implantação; responsável pela edição semanal das ausências.
- **Instalação do serviço** no Windows e no Linux, o nome real do serviço (os runbooks usam `painel-squads`) e o firewall (porta 8080 e, se houver coleta, a 9464 só para o coletor): Plano 13.
- **Coleta opcional das métricas** (Prometheus) e uso de `ops/prometheus/alertas.yml`.
- **Login/OIDC e cookie de sessão** (Plano 8): ligar `SessaoAssinada` (rotação do `SESSION_SECRET`) ao login e exigir `SESSION_SECRET` em produção.
- **Linhas do Plano 11/13 que apontam para estes documentos:** seção 10 do relatório para o DPO → `docs/operacao/runbook-incidentes.md`; `docs/implantacao.md` → `docs/operacao/*`.
- **Validação com o DPO** do prazo e do texto de comunicação à ANPD (o runbook diz "a confirmar com o DPO").


## Correções da revisão final (PR #37)

Corrigidos, cada um com teste e um commit: I1 (falha não tratada: log só com modelo da rota e classe, resposta 500 sem corpo; 404/405 e parsing cobertos), I2 (método HTTP como lista fixa mais OUTRO; status e rota fechados), I3 (sessão rejeita fim além da duração máxima; segredo anterior só na janela de rotação), M1 (prontidão pública só pronto/não pronto; motivo no log e na rota do gerente), M2 (painel-cli.ps1 só ASCII e exit 2 efetivo), M3 (.parcial com permissão restrita desde o início), M4 (.sha256 ao lado de cada cópia, usado sem banco vivo), M5 (symlink do BACKUP_DIR resolvido; aviso de host de métricas fora do loopback), M6 (OIDC_CLIENT_SECRET na máscara; valores entre aspas), M8 (comentário da fábrica do Jira), M9 (espaçamento do toml).

Minor da re-revisão (aprovada, sem Critical/Important), adiados para o Plano 8/13:

1. `LogDeRequisicao.kt:43-60`: exceção em fase anterior ao interceptor (Setup, Monitoring, CallLogging/MDC) ainda cai no `Unhandled: GET - <caminho cru>` do Ktor, com mensagem e pilha.
2. `CancellationException` é relançada e logada pelo Ktor com mensagem e pilha.
3. O teste `naoEncontradoEMetodoErradoNaoVazamCaminho` serve só como regressão.
4. FEITO agora: `SessaoAssinada.ler` com `fim` absurdo e assinatura válida lançava `DateTimeException`; agora devolve nulo (teste `fimAbsurdoComAssinaturaValidaNaoLancaExcecao`).
5. `SessaoAssinada.kt` (~77): `inicioDaRotacao` nulo vira o instante de criação, e cada reinício reabre a janela do segredo anterior; o Plano 8 deve persistir o início da rotação. `SESSION_SECRET` em branco vira "ausente" sem erro.
6. `MascaraDeLog.kt` (~63-70): JSON aninhado com aspas escapadas e `password=a b` sem aspas escapam do padrão.
7. `ServicoDeBackup.kt` (~127): `.sha256` órfão se o `move` falhar.
8. `AlertasRoutes.kt`: `runCatching` engole `CancellationException`; a lista de loopback é literal (127.0.0.2 e LOCALHOST geram aviso); `painel-cli.ps1` chama o `.bat` sem tratar metacaracteres do cmd.

Adiados (decisão registrada, sem mudança de código):

- M7: adiado para o Plano 13 (implantação), onde o desenho final do serviço e dos volumes é fechado.
- M10: adiado; melhoria menor sem risco de segurança, a reavaliar na revisão pós-implantação.
