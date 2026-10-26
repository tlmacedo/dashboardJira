# Spec da Fase 8: Preparação final, acessibilidade, implantação e remoção do `legacy/`

Data: 25/10/2026
Plano: [`docs/superpowers/plans/2026-10-25-plano-08-preparacao-final-e-remocao-do-legacy.md`](../plans/2026-10-25-plano-08-preparacao-final-e-remocao-do-legacy.md) (10 tarefas, um PR; a remoção do `legacy/` pode virar PR próprio)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 3, 4.3, 4.5, 4.6 "Produção × desenvolvimento", "Volume e atualidade", "Ambiente", "Oráculo de números", 5.5, 8 item 8, 9, 10, 11)
Depende de: Fases 4A, 4, 5, 6 e 7 mescladas na `main`

> **Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano. A spec mestra de 24/10 ainda traz a regra antiga de acesso ao Jira, com autorização por consulta. **Essa regra foi substituída em 25/10** pela regra da flag única descrita abaixo. Esta fase atualiza os documentos de `docs/jira/` e o teste que ainda exigiam a regra antiga.

## 1. Intenção e porquê

Esta fase fecha a etapa offline. Ao final, o painel está pronto para ser implantado e ligado ao Jira real sem surpresas:

- toda rota tem papel e registro de acesso;
- o login de produção (SSO/OIDC) existe, atrás de uma interface, e foi testado contra um emissor falso;
- o contraste WCAG AA foi provado nos três temas;
- o desempenho foi medido na escala da spec;
- o contêiner de produção está endurecido;
- os documentos da 1ª conexão estão finais.

Por fim, depois de conferir card a card que o painel novo cobre o app atual, o app Python em `legacy/` é removido. Ele continua recuperável pela tag `legacy-final`. O objetivo é que o repositório deixe de ter duas implementações e que nenhuma referência viva dependa do código antigo.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- **Inventário de rotas:** um inventário testado de todas as rotas `/api`, com papel e `access_log`. Uma rota nova obriga a atualizar o teste.
- **OIDC:** o `OidcAuthenticator` real faz *authorization code* com PKCE, `state` e `nonce`, valida o `id_token` pelo JWKS e usa cookie de sessão seguro. Os papéis vêm **sempre** do `RoleDirectory`.
- **Equivalência:** o documento `docs/analise/equivalencia-por-card.md` traz uma linha por ID do relatório e uma prova (teste) por linha.
- **Acessibilidade:** WCAG AA verificado nos tokens dos três temas, no teclado, nos rótulos e em `prefers-reduced-motion`.
- **Revisão visual:** uma rodada de revisão nos três temas, a 1440 e 390 px.
- **Orçamentos de desempenho:** testados com dados sintéticos, fora da suíte padrão.
- **Contêiner de produção:** perfil `prod` do compose endurecido, `verificar-imagem.sh` e `docs/implantacao.md`.
- **Documentos da 1ª conexão:** `docs/jira/` atualizados para a regra da flag única.
- **Remoção do `legacy/`:** feita com a tag `legacy-final`, **só depois da aprovação do usuário**.
- **Guia do painel:** `docs/manual/guia-do-painel.md`, em pt-BR.

### Não objetivos

- Cards novos ou mudança de regra de cálculo.
- Escolher o provedor OIDC concreto. Isso fica com a infraestrutura da empresa.
- Conectar ao Jira real. Ligar a flag é do usuário, em outro momento, seguindo o checklist e o runbook.
- Guia em inglês.
- Calibrar heurísticas com dados reais.

### Antiobjetivos (falha mesmo que "funcione")

- Remover o `legacy/` sem o "sim" explícito do usuário ao documento de equivalência.
- Aceitar papel vindo de claim do token, por exemplo `roles: [GERENTE]`.
- Produção subir sem OIDC configurado, ou com o autenticador de desenvolvimento.
- Rota `/api` sem papel ou sem `access_log`.
- Cache que devolve a um papel o número calculado para outro.
- Imagem com `equipes.yaml`, `ausencias.yaml`, `papeis.yaml`, `squads.yaml` ou `.env`, ou rodando como root.
- Teste automático acessando o Jira real ou um provedor OIDC real, **mesmo com a flag `true`**.
- Acessar o Jira com `JIRA_LIVE_ENABLED=false`.

## 3. Restrições

### 3.1 Regras transversais do projeto (valem sempre)

**Acesso ao Jira.** A única trava é a flag `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`, 25/10).
- Com `false`, o padrão, é proibido acessar o Jira por qualquer caminho, para qualquer IA ou pessoa.
- Com `true`, o acesso fica liberado, seguindo `docs/jira/checklist-primeira-conexao.md` e `docs/jira/runbook-sincronizacao.md`.
- **Só o usuário muda a flag.**
- **Nenhum teste automático acessa o Jira real**: só fixtures, `MockEngine` e hosts `.invalid`.

**Sem ranking de pessoas.** O inventário, a equivalência e o guia confirmam que nenhuma tela reintroduz ranking. Tabelas de pessoas ficam sempre em ordem alfabética.

**Visibilidade por papel:**
- USUARIO vê só o que é seu;
- SQUAD_LEADER vê o próprio squad;
- COORDENADOR e GERENTE veem todos.

O papel vem do `RoleDirectory` vigente hoje, nunca de claims. Com `DASHBOARD_DEV_MODE=true`, não há login e aparece a faixa vermelha. Com `false`, a aplicação falha fechada sem OIDC.

**Integridade:** só o gerente acessa, e toda consulta é auditada. O mesmo vale para o Registro de acesso. O inventário prova isso.

**Regras de cálculo.** "Concluída" = `DONE` + `resolution`. O atraso justificado aparece em duas métricas ("no prazo" e "no prazo ajustado"). O fuso é America/Manaus e a contagem padrão é em dias úteis. Esta fase só confere essas regras no documento de equivalência e no guia.

**Interface:**
- WCAG AA: texto ≥ 4,5:1; texto grande, gráficos e bordas ≥ 3:1, nos temas Claro, Escuro e Sidia;
- foco visível de 2 px;
- `Esc` fecha gaveta e devolve o foco;
- a cor nunca é o único sinal;
- pt-BR e inglês com as mesmas chaves.

**DPO:** validar a base legal antes do uso real (Integridade, auditoria, retenção). Isso fica registrado em `docs/implantacao.md` e no `HANDOFF.md`.

**Configs reais:** ficam fora do git e da imagem. O `config/` real é montado como volume `:ro`.

**Ambiente:** Docker ativo; banco real por Testcontainers; comentários e commits em pt-BR.

### 3.2 Restrições específicas desta fase

**Rede para registros de pacotes e imagens: permitida** (Docker, Maven/Gradle, npm), sem perguntar. **Nunca** para o Jira nem para provedor OIDC real.

**Emissor OIDC dos testes:** é falso, com host `https://sso.exemplo.invalid`. Usa `MockEngine` e chaves RSA geradas em memória.

**Perfil `prod` do compose:**
- `DASHBOARD_DEV_MODE=false` e `JIRA_LIVE_ENABLED=false`;
- `OIDC_*` e `SESSION_SECRET` sem valor;
- `read_only` com `tmpfs: /tmp`, `cap_drop: [ALL]` e `no-new-privileges`;
- porta só em `127.0.0.1`;
- `healthcheck` em `/api/health`;
- usuário 10001.

**Escala e orçamentos.** A escala vem da spec mestra 4.6: 30.000 issues, 60 pessoas e 50.000 worklogs sintéticos (`dev.sint001`…`dev.sint060`). Orçamentos:

| Alvo | Orçamento |
|---|---|
| `/api/cards/*` e `/api/drill/*` | p95 < 2 s |
| `/api/central` | p95 < 3 s |
| heap | < 512 MB |
| bundle gzip | < 300 KB |

O teste de carga leva `@Tag("carga")` e fica fora da suíte padrão.

**`legacy/`.** A remoção cria antes a tag `legacy-final`. Depois dela, os documentos vivos citam o código antigo como "tag `legacy-final`". Os planos e specs em `docs/superpowers/` são histórico e não mudam.

**Repositório PRIVADO.** O histórico tem usernames reais. Não torná-lo público sem limpar o histórico.

## 4. Decisões já tomadas

| # | Decisão | Origem |
|---|---|---|
| U1 | O app Python é substituído; o histórico fica no git | [usuário] spec mestra 4.1 |
| U2 | Login: SSO/OIDC genérico atrás de interface; filtro por papel em todas as rotas; o provedor real entra quando a infraestrutura definir | [usuário/design] spec mestra 4.3 e 4.6 |
| U3 | Escala: dezenas de pessoas e dezenas de milhares de issues; incremental a cada 10 min; reconciliação diária | [design aceito] spec mestra 4.6 |
| U4 | Implantação: um contêiner servindo API e SPA atrás do proxy da empresa | [design aceito] spec mestra 4.6 |
| U5 | Acesso ao Jira só pela flag `JIRA_LIVE_ENABLED`; só o usuário a muda; testes nunca acessam o Jira real | [usuário] `CLAUDE.md`, 25/10 |
| U6 | Retenção configurável, padrão 24 meses (`RETENTION_MONTHS`); prazo final é do DPO | [usuário] spec mestra 5.4 |
| D1 | **Remover o `legacy/` depois que o usuário aprovar o documento de equivalência.** Código antigo preservado na tag `legacy-final`. **A aprovação do documento é ponto de parada obrigatório: o executor pergunta ao usuário** e não remove nada sem o "sim" | [valor padrão 25/10, reversível; a aprovação em si é do usuário] |
| D2 | **Oráculo de equivalência:** cenários pequenos por card, com a fórmula do app atual conferida no código (`CompatLegado*Test`) | [valor padrão 25/10, reversível] |
| D3 | **Desempenho:** primeiro índices, depois consultas em lote e *code splitting*. Cache só se ainda faltar, com chave pelo escopo visível do principal, filtros e versão das regras, registrado como `Ruling:` | [valor padrão 25/10, reversível] |
| D4 | **Rede para registros de pacotes e imagens: permitida, nunca ao Jira.** Inclui imagens base do Docker e dependências Maven/Gradle (`ktor-server-auth`, `-auth-jwt`, `-sessions`) e npm | [valor padrão 25/10, reversível] |
| D5 | Os documentos de `docs/jira/` passam da regra antiga para a flag única. `PreparacaoDocsTest.cadaPassoDoChecklistExigeAutorizacaoExpressa` é substituído por `cadaPassoDoChecklistExigeAFlag` | [decorre de U5] |

## 5. O que fica para quem implementa

- **OIDC:** a biblioteca exata de JWT/JWKS, dentro do ecossistema Ktor, e o formato interno da sessão. Fixos: cookie `painel_sessao`, `HttpOnly`, `Secure`, `SameSite=Lax`, assinado e cifrado.
- **Desempenho:** quais índices criar, se um orçamento estourar. Exemplos no plano: `issue(instance, resolved)` e `change_event(issue_id, field, at)`.
- **Pequenos ajustes:**
  - texto do guia do painel e de `docs/implantacao.md`, desde que cubram os itens exigidos pelos testes;
  - correções de tokens e componentes que os testes de acessibilidade apontarem (só valores que reprovarem).
- **Escolhas em aberto e ClineSR:** quando o plano deixar uma escolha em aberto, registrar `Ruling:` em `docs/superpowers/progresso/2026-10-25-plano-08-preparacao-final-e-remocao-do-legacy.md`. Para o ClineSR, "USE superpowers:..." equivale à seção 4 do guia.

## 6. Critérios de aceitação verificáveis

1. **Rotas (T1).** Rodar `cd app/backend && ./gradlew test --tests '*InventarioDeRotasTest*'`. Os testes `todaRotaApiTemPapelERegistro`, `inventarioConhecido`, `postSoOndeEsperado` e `integridadeEAcessosSoGerente` passam. O único `POST` de dados é `/api/distribuicao/decisao`, além de `/auth/*`.
2. **OIDC (T2).** Rodar `./gradlew test --tests '*OidcTest*' --tests '*SettingsTest*'`. Devem passar:
   - `tokenValidoViraPrincipalDoDiretorio`;
   - `claimDePapelIgnorada`;
   - `assinaturaInvalida401`, `expirado401`, `issuerErrado401`, `audienceErrada401` e `nonceOuStateErrado401`;
   - `usuarioSemPapelVigente403`;
   - `cookieComFlagsSeguras`;
   - `producaoSemOidcNaoSobe` (cita só o nome da variável);
   - `nenhumaRequisicaoParaHostReal` (só `sso.exemplo.invalid`);
   - `logNaoGuardaToken`.

   Também devem passar `sh .claude/hooks/test-guard.sh` e a suíte completa.
3. **Equivalência (T3).** `EquivalenciaDocTest` passa: `todoIdDoRelatorioTemLinha`, `todaProvaExiste`, `situacaoValida` e `nenhumaLinhaSemProvaSalvoRemovido`. Todos os `./gradlew test --tests '*CompatLegado*'` ficam verdes com o `legacy/` presente. **A resposta do usuário fica registrada no `HANDOFF.md`, com data.**
4. **Acessibilidade (T4).** `cd app/frontend && pnpm vitest run src/a11y` passa, incluindo:
   - `contrasteDosTokensNosTresTemas` (a falha cita tema, par e razão);
   - `laranjaEVerdeDaMarcaNuncaComoTexto`;
   - `focoVisivelDoisPixels`;
   - `reducedMotion`;
   - `escFechaGavetaEDevolveFoco`;
   - `estadoNuncaSoPorCor`;
   - `paridadeDeChavesPtEn`.
5. **Visual (T5).** `docs/design/revisao-final.md` registra os achados e as correções de no máximo duas rodadas de captura.
6. **Desempenho (T6).** `./gradlew testCarga` e `pnpm build && node scripts/orcamento-bundle.mjs` passam nos orçamentos. A suíte `test` padrão não roda a tag `carga`.
7. **Contêiner (T7).**
   - `ComposeTest` passa: `perfilProdEndurecido`, `prodSemDevMode`, `semValoresDeSegredoNoCompose`, `portaSoLocalhost` e `documentoDeImplantacaoCitaBackupERestauracao`.
   - `sh app/scripts/verificar-imagem.sh` mostra: nenhuma config real, `id -u` = 10001 e nenhum `.env`.
   - `docker compose --profile prod up -d` recusa subir sem `OIDC_ISSUER`.
8. **Documentos do Jira (T8).** `PreparacaoDocsTest` passa: `checklistCitaCadaCampoAConfirmar`, `matrizCitaCadaEndpoint`, `cadaFichaUsaSoCamposDoDicionario`, `cadaPassoDoChecklistExigeAFlag`, `semHostRealNemToken` e `testesNaoAcessamOJiraReal`. Nenhum documento de `docs/jira/` mantém a regra antiga.
9. **Remoção (T9), só com aprovação.** Antes, existe a tag `legacy-final`. `ReferenciasLegacyTest` passa: `nadaReferenciaLegacy` e `nenhumCodigoLeLegacy`. Também passam as suítes completas, `pnpm build`, `sh .claude/hooks/test-guard.sh` e `docker compose build`.
10. **Guia (T10).** `GuiaDoPainelDocTest` passa: `guiaCitaCadaItemDoMenu`, `guiaCitaCadaFicha` e `claudeMdSemCaminhoInexistente`. O índice dos planos mostra o estado final.
11. **Fim.** Varrer dados pessoais antes do push. Confirmar que o repositório é PRIVADO. Abrir PR para a `main` e não mesclar sem o usuário mandar.

## 7. Riscos, pendências e pontos de parada

**PONTO DE PARADA OBRIGATÓRIO: aprovação do documento de equivalência (T3, Passo 5).** O executor faz uma pergunta com opções:

- "Aprovar e remover (Recomendada)";
- "Aprovar com ajustes";
- "Ainda não".

Sem "sim", a T9 não é executada: o PR sai sem a remoção, e o `HANDOFF.md` registra a pendência com data.

**Pendência do usuário:** o arquivo `legacy/.env.example`, modificado localmente, fica fora de commit. Antes da T9, perguntar ao usuário se descarta ou guarda o arquivo.

**Pendências externas:**
- provedor OIDC concreto, proxy e TLS (infraestrutura da empresa);
- logo limpo e fonte da Sidia;
- validação com o DPO;
- calibração das heurísticas com dados reais.

**Documentos de outras fases ainda na regra antiga.** O teste `PreparacaoDocsTest`, o `checklist-primeira-conexao.md` e o runbook do Plano 4A ainda podem citar a regra antiga, se as fases anteriores não os atualizaram. A T8 resolve isso.

**Sinais sem dono.** "Estimativa reduzida" e "worklog retroativo" (spec mestra 7.2) não têm dono em nenhuma fase. A equivalência os marca como pendência, não como `equivalente`.

**Nomes herdados.** Os nomes vindos das Fases 4A a 7 podem ter mudado. Conferir antes da T1 (seção 8).

## 8. Glossário do que esta fase consome

Legenda:
- **[main]**: existe hoje na `main`;
- **[Plano N]**: previsto pelo plano N. Confirmar na `main` antes da T1. Se o nome mudou, atualizar só o nome no plano e registrar no progresso.

| Nome | Arquivo | Origem |
|---|---|---|
| `Authenticator`, `DevAuthenticator`, `OidcAuthenticator` (stub), `principalFor`, `installAccessControl`, `requireRole`, `RoleDirectory`, `normalizarUsername` | `app/backend/src/main/kotlin/br/sidia/painel/security/` | [main] Planos 1 e 3A |
| `Settings` | `.../config/Settings.kt` | [main] Plano 3A; campo `sync` no Plano 4A |
| `module(settings, context, authenticator)` | `.../Application.kt` | [main] |
| `AccessLog` (`record`, `listar`) | `.../db/AccessLog.kt` | [main]; `listar` no Plano 7 |
| `JiraClientFactory`, `LiveGuard`, teste `SomenteAFabricaCriaClientesJira` | `.../jira/`, `src/test/.../jira/` | [main] |
| `PreparacaoDocsTest` (contém hoje `cadaPassoDoChecklistExigeAutorizacaoExpressa`, a substituir) | `app/backend/src/test/kotlin/br/sidia/painel/jql/PreparacaoDocsTest.kt` | [main] Plano 2 |
| `docs/jira/checklist-primeira-conexao.md`, `docs/jira/matriz-compatibilidade.md` | `docs/jira/` | [main] Plano 2 |
| `docs/jira/runbook-sincronizacao.md`, `FlagDesligadaZeroRequisicoesTest`, `SyncGate` | `docs/jira/`, `.../sync/` | [Plano 4A] |
| `executarAuditado` | `.../cards/RotaAuditada.kt` | [Plano 4] |
| `CardDeLeitura`, `rotaDeCard` | `.../cards/` | [Plano 5] |
| `CompatLegadoTest` (Tempo em status) | `src/test/.../domain/CompatLegadoTest.kt` | [main] Plano 3 |
| `CompatLegado*Test` (Trabalho, Capacidade, Checklist, Distribuição) e `docs/analise/diferencas-esperadas-*.md` | `src/test/...`, `docs/analise/` | [Planos 4 a 7] |
| `GeradorSintetico` grava pelas mesmas tabelas do `PostgresIssueStore` | `.../sync/IssueStore.kt` | [main] Plano 3 |
| `tokens.css` (Claro, Escuro, Sidia), `Shell`, `nav.ts` | `app/frontend/src/theme/`, `shell/`, `nav/` | [main] Plano 1 |
| `DrillDrawer`, `CardPage` | `app/frontend/src/shared/` | [Plano 5] |
| `app/Dockerfile`, `app/docker-compose.yml`, `app/.dockerignore`, `app/.env.example` | `app/` | [main] Plano 1; variáveis `SYNC_*` no Plano 4A |
| `.claude/hooks/guard.sh`, `test-guard.sh` | `.claude/hooks/` | [main]; o ClineSR não tem hooks, mas `test-guard.sh` roda como teste |
| `docs/analise/relatorio-cards-app-atual.md` (IDs `[A-Z]{2}-\d{2}`, QD, AU, SI) | `docs/analise/` | [main] |

## 9. Referência ao plano

Execute [`2026-10-25-plano-08-preparacao-final-e-remocao-do-legacy.md`](../plans/2026-10-25-plano-08-preparacao-final-e-remocao-do-legacy.md) tarefa a tarefa, com TDD, conforme `docs/guia-execucao-outra-ia.md` (seções 4 a 8).

A Tarefa 9 só roda depois do "sim" do usuário (seção 7). Sem ele, as demais tarefas seguem, e a remoção vira um PR próprio depois.
