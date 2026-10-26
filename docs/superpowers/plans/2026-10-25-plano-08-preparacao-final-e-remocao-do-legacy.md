# Fase 8: Preparação final, acessibilidade, implantação e remoção do `legacy/` — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo (Goal):** Fechar a etapa offline. O painel fica pronto para implantar: todas as rotas cobertas por papel e `access_log`, SSO/OIDC atrás de uma interface testada com um emissor falso, WCAG AA verificado nos três temas, desempenho medido na escala da spec, contêiner de produção endurecido, e documentos da 1ª conexão atualizados. Depois da conferência de equivalência card a card, aprovada pelo usuário, o app Python em `legacy/` é removido.

**Arquitetura (Architecture):** Nenhum card novo. A fase adiciona:
- testes transversais: inventário de rotas, contraste dos tokens, teclado, carga sintética e varredura de referências;
- um `OidcAuthenticator` real que implementa a interface `Authenticator`, validado contra um emissor OIDC falso no próprio teste (descoberta, JWKS e token por `MockEngine`, chaves RSA geradas em memória);
- um perfil de produção no compose;
- o documento de equivalência por ID do relatório.

A remoção do `legacy/` é a última tarefa e tem uma porta de aprovação.

**Stack (Tech Stack):** Kotlin 2 + Ktor 3.3.1 (`ktor-server-auth`, `ktor-server-auth-jwt`, `ktor-server-sessions`), PostgreSQL + Testcontainers 2.0.3, Ktor `MockEngine`; React + TypeScript (Vite, Vitest, Testing Library); Docker Compose.

**Spec:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 3, 4.3, 4.5, 4.6 "Produção × desenvolvimento", "Volume e atualidade", "Ambiente", "Oráculo de números", 5.5, 8 item 8, 9, 10, 11). Inventário: `docs/analise/relatorio-cards-app-atual.md` (todas as IDs; seção 8). Visual: `docs/design/plano-visual-frontend.md` (seções 2, 3, 7 e 8).

## Estado herdado (confirmar na execução)

Antes da Tarefa 1, o executor confere na `main` que as Fases 4A, 4, 5, 6 e 7 foram mescladas e que estes nomes existem. Se algum nome mudou, atualiza só os nomes deste plano e registra no `progress.md`.

| Nome | Origem |
|---|---|
| `Authenticator`, `DevAuthenticator`, `OidcAuthenticator` (stub), `principalFor`, `installAccessControl`, `requireRole` | `security/` (Planos 1 e 3A) |
| `executarAuditado`, `AccessLog`, `rotaDeCard`, `CardDeLeitura` | Planos 3, 4 e 5 |
| `CompatLegado*Test` (Tempo em status, Trabalho, Capacidade, Checklist, Distribuição) e os documentos `docs/analise/diferencas-esperadas-*.md` | Planos 3 a 7 |
| `PreparacaoDocsTest`, `docs/jira/checklist-primeira-conexao.md`, `docs/jira/matriz-compatibilidade.md`, `docs/jira/runbook-sincronizacao.md` | Planos 2 e 4A |
| `tokens.css` (temas Claro, Escuro e Sidia), `Shell`, `DrillDrawer`, `nav.ts` | frontend (Planos 1, 3 e 5) |
| `app/Dockerfile`, `app/docker-compose.yml`, `app/.dockerignore`, `.claude/hooks/guard.sh` | Planos 1 e 4A |

## Restrições globais (Global Constraints)

- **A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`** (regra 1 do `CLAUDE.md`, 25/10). Com `false`, ninguém acessa o Jira por caminho nenhum; com `true`, o acesso fica liberado, e só o usuário muda a flag. Este plano não precisa de acesso ao Jira: testes e verificações rodam offline, qualquer que seja o valor da flag.
- **Nenhum teste automático acessa o Jira real nem um provedor OIDC real, mesmo com a flag `true`.** Os testes usam fixtures, `MockEngine` e hosts `.invalid` (`https://jira.exemplo.invalid`, `https://sso.exemplo.invalid`); o emissor OIDC dos testes é falso e roda no próprio teste. As travas existentes continuam verdes: `SomenteAFabricaCriaClientesJira`, `FlagDesligadaZeroRequisicoesTest` (Plano 4A) e `sh .claude/hooks/test-guard.sh`. Nenhuma variável nova começa com `JIRA_`.
- **Rede para registros de pacotes e de imagens é permitida** (decisão 4): baixar imagens base do Docker e dependências do Maven/Gradle e do npm, quando não estiverem em cache. **Nunca** para o Jira (vale a flag) nem para provedor OIDC real. Este plano não pede dependência npm nova.
- Os papéis vêm **sempre** do `RoleDirectory` na data de hoje, nunca de claims do token. Com `DASHBOARD_DEV_MODE=false`, a inicialização falha fechada se o OIDC não estiver configurado.
- WCAG AA: texto ≥ 4,5:1, texto grande e elementos gráficos ou bordas de componente ≥ 3:1, nos três temas. Cor nunca é o único sinal. Foco visível de 2 px. `Esc` fecha gaveta e janela. `prefers-reduced-motion` é respeitado.
- Escala da spec 4.6: dezenas de pessoas e dezenas de milhares de issues. O teste de carga usa dados sintéticos e fica fora da suíte padrão (`@Tag("carga")`).
- Configs reais (`equipes.yaml`, `ausencias.yaml`, `papeis.yaml`, `squads.yaml`) nunca entram no git nem na imagem.
- **PONTO DE PARADA OBRIGATÓRIO: remoção do `legacy/` só depois que o usuário aprovar o documento de equivalência** (decisão 1). O executor pergunta ao usuário, em pergunta com opções, ao fim da Tarefa 3; sem um "sim" explícito, a Tarefa 9 não é executada. Antes da remoção, o último commit que contém `legacy/` recebe a tag `legacy-final`.
- Commits em Conventional Commits pt-BR (`feat(seguranca): ...`, `test(a11y): ...`, `perf: ...`, `build(docker): ...`, `docs: ...`, `chore: remover legacy`). Comentários em pt-BR.

## Foco da revisão (Review Focus)

1. **Rota nova esquecida sem papel ou sem `access_log`.** Uma rota criada numa fase anterior fora do padrão quebra o teste que enumera todas as rotas → `todaRotaApiTemPapelERegistro` (T1).
2. **Token OIDC válido com claim `roles: [GERENTE]` de quem é USUARIO no diretório.** A claim é ignorada e a pessoa vê só o que é dela → `claimDePapelIgnorada` (T2).
3. **Produção sem OIDC configurado** ou com o autenticador de desenvolvimento: o serviço não sobe e cita a variável que falta → `producaoSemOidcNaoSobe` (T2).
4. **Texto secundário e símbolo `●` de atenção no tema Escuro.** São os pares mais perto do limite → `contrasteDosTokensNosTresTemas` (T4).
5. **Referência ao `legacy/` sobrando** em hook, Dockerfile, teste ou documento vivo depois da remoção → `nadaReferenciaLegacy` (T9).
6. **Remoção do `legacy/` sem o "sim" do usuário** ao documento de equivalência, ou documento de `docs/jira/` que ainda segue a regra antiga de acesso (substituída em 25/10) no lugar da flag → pré-condições da T9 registradas no `HANDOFF.md` e `cadaPassoDoChecklistExigeAFlag` (T8).

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/security/{Oidc.kt,OidcSettings.kt,Sessao.kt}
app/backend/src/main/resources/db/migration/V{n}__indices_de_leitura.sql      # só se a Tarefa 6 pedir
app/backend/src/test/kotlin/br/sidia/painel/{seguranca/InventarioDeRotasTest.kt, seguranca/OidcTest.kt,
  carga/GeradorSintetico.kt, carga/CargaTest.kt, docs/EquivalenciaDocTest.kt, docs/ReferenciasLegacyTest.kt, docs/GuiaDoPainelDocTest.kt}
app/frontend/src/a11y/{contraste.test.ts, teclado.test.tsx, rotulos.test.tsx}
app/docker-compose.yml (perfil prod), app/.env.example, app/scripts/verificar-imagem.sh
docs/analise/equivalencia-por-card.md, docs/implantacao.md, docs/manual/guia-do-painel.md
docs/jira/{checklist-primeira-conexao.md, matriz-compatibilidade.md}, CLAUDE.md, README.md, app/README.md, PRODUCT.md, HANDOFF.md
```

---

### Tarefa 1: Inventário de rotas, papel e registro de acesso

**Arquivos:** Criar `seguranca/InventarioDeRotasTest.kt`. Modificar `Application.kt` só se o teste achar uma rota fora do padrão.

**Interfaces:**
- Consome: o `module(settings, context, authenticator)`, `requireRole` e `executarAuditado`.
- Produz `val ROTAS_PUBLICAS: Set<String> = setOf("/api/health", "/api/meta")`, mais os estáticos da SPA. A Tarefa 2 acrescenta `/auth/login` e `/auth/callback` (públicas) e `/auth/logout` (com sessão). O teste percorre a árvore de rotas do Ktor (`application.plugin(Routing)` e os filhos recursivos) e monta a lista `(método, caminho)` de tudo que está sob `/api`.

- [ ] **Passo 1: Testes que falham:**
  - `todaRotaApiTemPapelERegistro`. Para cada rota fora de `ROTAS_PUBLICAS`:
    - sem principal → 401 ou 403;
    - USUARIO numa rota de liderança → 403;
    - cada chamada grava exatamente 1 linha em `access_log`, inclusive as negadas.
  - `inventarioConhecido`. A lista de rotas é igual a uma lista esperada versionada no teste, para que uma rota nova obrigue a atualizar o teste e os papéis.
  - `postSoOndeEsperado`. Os únicos `POST` são `/api/distribuicao/decisao` (Plano 7), além dos de autenticação da Tarefa 2.
  - `integridadeEAcessosSoGerente`.
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*InventarioDeRotasTest*'`. Resultado esperado: FALHA, ou PASSA se tudo já estiver conforme. Se passar de primeira, conferir a lista esperada contra os Planos 3 a 7.
- [ ] **Passo 3: Corrigir** as rotas apontadas, sem mudar respostas.
- [ ] **Passo 4: Rodar** de novo e esperar PASSA; rodar também a suíte completa.
- [ ] **Passo 5: Commit** `test(seguranca): inventário de rotas com papel e registro de acesso`.

---

### Tarefa 2: SSO/OIDC atrás da interface, sem provedor real

**Arquivos:** Criar `security/OidcSettings.kt`, `security/Oidc.kt` (substitui o stub `OidcAuthenticator`) e `security/Sessao.kt`. Modificar `config/Settings.kt`, `Application.kt`, `app/.env.example` (variáveis sem valor, comentadas em pt-BR), `app/build.gradle.kts` (`ktor-server-auth`, `ktor-server-auth-jwt`, `ktor-server-sessions`, se ainda não estiverem no build) e o `Shell` do frontend (botão "Sair" fora do modo desenvolvimento). Teste: `seguranca/OidcTest.kt` e `config/SettingsTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class OidcSettings(val issuer: String, val clientId: String, val clientSecret: String?, val redirectUri: String,
      val claimUsername: String = "preferred_username", val escopos: List<String> = listOf("openid", "profile"),
      val segredoDaSessao: ByteArray, val duracaoDaSessao: Duration = 8.hours)
  class OidcAuthenticator(cfg: OidcSettings, roles: RoleDirectory, clock: Clock, http: HttpClient) : Authenticator
  fun Route.rotasDeLogin(cfg: OidcSettings, auditoria: AccessLog)   // GET /auth/login, GET /auth/callback, POST /auth/logout
  ```
- Variáveis de ambiente:

  | Variável | Obrigatória | Regra |
  |---|---|---|
  | `OIDC_ISSUER` | sim | URL `https` |
  | `OIDC_CLIENT_ID` | sim | — |
  | `OIDC_CLIENT_SECRET` | não | — |
  | `OIDC_REDIRECT_URI` | sim | — |
  | `OIDC_CLAIM_USERNAME` | não | — |
  | `SESSION_SECRET` | sim | ≥ 32 bytes, em base64 |

- Comportamento:
  - Fluxo *authorization code* com PKCE, `state` e `nonce`.
  - O `id_token` é validado com o JWKS da descoberta (`/.well-known/openid-configuration`): assinatura RS256, `iss`, `aud`, `exp` e `nonce`.
  - O username é `normalizarUsername(claim)`. Os papéis saem de `principalFor(username, roles, hoje)`.
  - Cookie de sessão `painel_sessao` com `HttpOnly`, `Secure`, `SameSite=Lax`, assinado e cifrado.
  - Login e logout gravam `access_log` sem token.
  - `DASHBOARD_DEV_MODE=false` sem as variáveis obrigatórias → `IllegalStateException` na inicialização, citando só o nome da variável que falta.
  - O provedor concreto continua a definir com a infraestrutura da empresa (spec 9).

- [ ] **Passo 1: Testes que falham.** O emissor falso responde descoberta, JWKS e token por `MockEngine`, com chave RSA gerada no teste.
  - `tokenValidoViraPrincipalDoDiretorio`: `preferred_username` igual a `Ana.Silva` resulta em `ana.silva`, com os papéis do diretório.
  - `claimDePapelIgnorada`: um token com `roles: [GERENTE]` de quem é USUARIO no diretório resulta em `roles == {USUARIO}`.
  - `assinaturaInvalida401`, `expirado401`, `issuerErrado401`, `audienceErrada401`, `nonceOuStateErrado401`.
  - `usuarioSemPapelVigente403`.
  - `cookieComFlagsSeguras`.
  - `logoutLimpaSessao`.
  - `producaoSemOidcNaoSobe`.
  - `devModeContinuaSemLogin`.
  - `nenhumaRequisicaoParaHostReal`: o `MockEngine` registra só `sso.exemplo.invalid`.
  - `logNaoGuardaToken`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*OidcTest*' --tests '*SettingsTest*'`. Resultado esperado: FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** de novo e esperar PASSA; rodar também a suíte completa e `sh .claude/hooks/test-guard.sh`.
- [ ] **Passo 5: Commit** `feat(seguranca): SSO/OIDC genérico atrás da interface, testado com emissor falso`.

---

### Tarefa 3: Conferência de equivalência por card

**Arquivos:** Criar `docs/analise/equivalencia-por-card.md` e `docs/EquivalenciaDocTest.kt`. Modificar os `CompatLegado*Test` se faltar algum caso apontado pelo documento.

**Interfaces:**
- Consome todos os `CompatLegado*Test` e os documentos de diferenças esperadas das Fases 3 a 7.
- Produz uma tabela com uma linha por ID do relatório:
  - `ID`;
  - `Card novo` (rota);
  - `Situação`, que é uma de `equivalente`, `diferença esperada`, `reformulado (sem ranking)`, `removido` ou `novo`;
  - `Prova` (classe e método de teste);
  - `Documento` (link para as diferenças esperadas).
- Os cards novos da spec 6.2 entram como `novo`.

- [ ] **Passo 1: Testes que falham:**
  - `todoIdDoRelatorioTemLinha`: lê `relatorio-cards-app-atual.md`, extrai os IDs com a regex `[A-Z]{2}-\d{2}` e confere que cada um aparece no documento. QD, AU e SI entram pelo prefixo.
  - `todaProvaExiste`: cada `Classe.metodo` citado existe em `src/test`, conferido por reflexão sobre as classes compiladas.
  - `situacaoValida`.
  - `nenhumaLinhaSemProvaSalvoRemovido`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*EquivalenciaDocTest*'`. Resultado esperado: FALHA.
- [ ] **Passo 3: Escrever** o documento a partir dos documentos de diferenças.
- [ ] **Passo 4: Rodar** de novo e esperar PASSA. Rodar todos os `./gradlew test --tests '*CompatLegado*'` com o `legacy/` ainda presente, para conferir as fórmulas citadas.
- [ ] **Passo 5: PONTO DE PARADA OBRIGATÓRIO — levar ao usuário.** Apresentar o resumo (quantos equivalentes, diferenças, reformulados e removidos) e o link do documento como pergunta com opções, a recomendada primeiro: "Aprovar e remover o `legacy/` na Tarefa 9 (Recomendada)", "Aprovar com ajustes (indicar quais)" (o executor aplica os ajustes ao documento e pergunta de novo antes da Tarefa 9) e "Ainda não; manter o `legacy/`" (a Tarefa 9 fica de fora deste PR e a pendência vai ao `HANDOFF.md`). Registrar a resposta e a data no `HANDOFF.md` e no arquivo de progresso. Sem aprovação, seguir com as Tarefas 4 a 8 e 10 e pular a 9 (ver a Tarefa 9).
- [ ] **Passo 6: Commit** `docs: equivalência por card com o app atual`.

---

### Tarefa 4: Acessibilidade WCAG AA

**Arquivos:** Criar `app/frontend/src/a11y/contraste.test.ts`, `teclado.test.tsx` e `rotulos.test.tsx`. Modificar `theme/tokens.css` (só valores que reprovarem), o `Shell` (link "Pular para o conteúdo", `<main>`, `<nav aria-label>`), o `DrillDrawer` e o `CardPage`, se algum teste apontar.

**Interfaces:**
- Produz `function contraste(corA: string, corB: string): number`, com a fórmula de luminância relativa da WCAG 2.x, em `a11y/contraste.ts`.
- O teste lê os tokens de cada tema (`[data-theme=claro|escuro|sidia]`) direto do `tokens.css` e verifica estes pares:

  | Par | Mínimo |
  |---|---|
  | texto / fundo | 4,5 |
  | texto secundário / fundo | 4,5 |
  | texto / superfície | 4,5 |
  | ação primária / fundo | 4,5 |
  | ok, atenção e problema (como texto) / fundo | 4,5 |
  | símbolo `✓` `●` `▲` / superfície | 3,0 |
  | foco / fundo | 3,0 |
  | texto do menu / menu (tema Sidia) | 4,5 |

  Os valores de referência estão no plano visual, seção 3.
- Nenhuma dependência nova: os rótulos e papéis ARIA são conferidos com Testing Library, e não com axe.

- [ ] **Passo 1: Testes que falham:**
  - `contrasteDosTokensNosTresTemas`: a mensagem de falha cita tema, par e razão.
  - `laranjaEVerdeDaMarcaNuncaComoTexto`: `#F58634` e `#13DC14` não aparecem como `color` de texto sobre fundo claro.
  - `focoVisivelDoisPixels`: o CSS tem `:focus-visible` com `outline` de 2 px.
  - `reducedMotion`: há regra `@media (prefers-reduced-motion: reduce)`.
  - `pularParaOConteudo`: é o primeiro foco e leva ao `<main>`.
  - `tabPercorreMenuFiltrosKpisTabela`.
  - `escFechaGavetaEDevolveFoco`, em todas as telas com gaveta.
  - `kpisTemNomeAcessivel`: `getByRole('button', {name: /Vencidas.*18/})`.
  - `tabelasComCabecalho`: `th` com `scope`.
  - `estadoNuncaSoPorCor`: cada célula com classe de estado tem um símbolo e um texto.
  - `celular390MenuViraBotao`.
  - `paridadeDeChavesPtEn`: já existe desde o Plano 5; rodar.
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/a11y`. Resultado esperado: FALHA.
- [ ] **Passo 3: Corrigir** tokens e componentes apontados.
- [ ] **Passo 4: Rodar** de novo e esperar PASSA; rodar também a suíte inteira e `pnpm build`.
- [ ] **Passo 5: Commit** `test(a11y): contraste dos três temas, teclado e rótulos`.

---

### Tarefa 5: Revisão visual dos três temas e responsividade

**Arquivos:** Criar `docs/design/revisao-final.md` (achados e correções). Modificar CSS e telas só onde a captura apontar.

**Interfaces:** Consome todas as telas do menu.

- [ ] **Passo 1: Captura.** Subir backend e frontend em modo demo e fazer **uma** rodada de capturas com Chrome headless:
  - todas as telas do `nav.ts`;
  - larguras de 1440 e 390 px;
  - temas Claro, Escuro e Sidia;
  - pt-BR e inglês só na Central, no Meu painel e na Auditoria.
- [ ] **Passo 2: Conferir** cada captura contra o plano visual:
  - gramática única da seção 5 (cabeçalho, filtros em chips, KPIs, no máximo dois gráficos e uma tabela);
  - densa nas telas de trabalho e editorial nas de leitura;
  - faixa vermelha do modo desenvolvimento;
  - textos sem corte em inglês;
  - tabelas com rolagem dentro do cartão a 390 px.
- [ ] **Passo 3: Corrigir** tudo de uma vez e registrar no documento.
- [ ] **Passo 4: Fazer** no máximo mais uma rodada de confirmação; rodar a suíte do frontend e `pnpm build`.
- [ ] **Passo 5: Commit** `fix(ui): revisão visual final dos três temas e do celular`.

---

### Tarefa 6: Desempenho na escala da spec

**Arquivos:** Criar `carga/GeradorSintetico.kt` e `carga/CargaTest.kt`, com `@Tag("carga")`. Modificar `app/backend/build.gradle.kts`: a tarefa `testCarga` roda só a tag `carga`, e o `test` padrão a exclui. Criar `resources/db/migration/V{n}__indices_de_leitura.sql` **só se** um orçamento estourar. Criar `app/frontend/scripts/orcamento-bundle.mjs`.

**Interfaces:**
- Produz `object GeradorSintetico { fun popular(ds: DataSource, semente: Long, issues: Int = 30_000, eventosPorIssue: Int = 10, pessoas: Int = 60, worklogs: Int = 50_000) }`.
  - Os dados são fictícios e determinísticos, com usernames `dev.sint001` a `dev.sint060`.
  - O gerador também cria o `RoleDirectory` de teste com 6 squads.
- Orçamentos, medidos localmente com Testcontainers, em 5 execuções depois de 1 de aquecimento:

  | Alvo | Orçamento |
  |---|---|
  | cada `GET /api/cards/*` e `/api/drill/*` | p95 < 2 s |
  | `/api/central` | p95 < 3 s |
  | memória do processo | heap < 512 MB |
  | bundle principal do frontend (gzip) | < 300 KB |

- [ ] **Passo 1: Testes que falham:**
  - `cardsDentroDoOrcamento`: mede cada card como GERENTE e como SQUAD_LEADER.
  - `centralDentroDoOrcamento`.
  - `drillMesmoTamanhoNaCarga`: amostra 5 cards.
  - `orcamentoDoBundle`: o script lê `dist/` e falha acima do limite.
- [ ] **Passo 2: Rodar** `./gradlew testCarga` e `cd app/frontend && pnpm build && node scripts/orcamento-bundle.mjs`, e registrar os tempos.
- [ ] **Passo 3: Se algo estourar,** atacar nesta ordem e medir de novo:
  1. índices de leitura na migração, por exemplo `issue(instance, resolved)`, `issue(instance, assignee_ref)`, `issue(instance, removida_em)` e `change_event(issue_id, field, at)`;
  2. consultas em lote;
  3. *code splitting* por tela no frontend.

  Cache de resultados só se, depois dos três passos, algum orçamento ainda estourar (decisão 3). Nesse caso, a chave do cache inclui o escopo visível do principal (papel, pessoas visíveis), os filtros e a versão das regras, para que um papel nunca receba o número calculado para outro; registrar como `Ruling:` no arquivo de progresso.
- [ ] **Passo 4: Rodar** de novo e esperar PASSA; rodar também a suíte padrão e confirmar que a tag `carga` fica fora dela.
- [ ] **Passo 5: Commit** `perf: orçamento de tempo dos cards e do bundle na escala da spec`.

---

### Tarefa 7: Contêiner de produção e implantação

**Arquivos:** Modificar `app/docker-compose.yml`, `app/Dockerfile` (se preciso) e `app/.env.example`. Criar `app/scripts/verificar-imagem.sh` e `docs/implantacao.md`. Teste: `implantacao/ComposeTest.kt`, que é estático e lê o YAML do compose.

**Interfaces:**
- Produz o perfil `prod` do compose:
  - `DASHBOARD_DEV_MODE=false` e `JIRA_LIVE_ENABLED=false`;
  - variáveis `OIDC_*` e `SESSION_SECRET` sem valor;
  - `config/` real montado como volume `:ro`;
  - `read_only: true` com `tmpfs: /tmp`, `cap_drop: [ALL]` e `security_opt: [no-new-privileges:true]`;
  - porta só em `127.0.0.1`, com o proxy da empresa à frente;
  - `healthcheck` em `/api/health`.
- `verificar-imagem.sh` faz o build da imagem e roda dentro dela:
  - `ls /app/config`: não pode haver `equipes.yaml`, `ausencias.yaml`, `papeis.yaml` nem `squads.yaml`;
  - `id -u`: deve ser `10001`;
  - nenhum arquivo `.env`.
- `docs/implantacao.md` cobre:
  - variáveis e volumes;
  - como ligar o OIDC;
  - a ordem segura de ligar o Jira (link para o runbook da sincronização do Plano 4A e para o checklist da 1ª conexão);
  - backup e restauração do PostgreSQL (`pg_dump` e `pg_restore`, com teste de restauração);
  - retenção (`RETENTION_MONTHS`);
  - atualização e reversão de versão;
  - o que pedir à infraestrutura (provedor OIDC, proxy, TLS).

- [ ] **Passo 1: Testes que falham:**
  - `perfilProdEndurecido`: confere as chaves acima no YAML.
  - `prodSemDevMode`.
  - `semValoresDeSegredoNoCompose`: nenhuma variável `*_SECRET` ou `*_PASSWORD` com valor.
  - `portaSoLocalhost`.
  - `documentoDeImplantacaoCitaBackupERestauracao`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ComposeTest*'`. Resultado esperado: FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** de novo e esperar PASSA.
- [ ] **Passo 5: Rodar** `sh app/scripts/verificar-imagem.sh`. Se as imagens base não estiverem em cache, o *pull* do registro de imagens está permitido (decisão 4); nenhum acesso ao Jira.
- [ ] **Passo 6: Subir** `docker compose --profile prod up -d` com valores descartáveis e conferir que o serviço recusa subir sem `OIDC_ISSUER`. Depois rodar `docker compose down -v`.
- [ ] **Passo 7: Commit** `build(docker): perfil de produção endurecido e guia de implantação`.

---

### Tarefa 8: Documentos de preparação da 1ª conexão

**Arquivos:** Modificar `docs/jira/checklist-primeira-conexao.md`, `docs/jira/matriz-compatibilidade.md`, `docs/jira/runbook-sincronizacao.md` (Plano 4A), `app/config/jira_campos.exemplo.yaml` (finalidades) e `app/backend/src/test/kotlin/br/sidia/painel/jql/PreparacaoDocsTest.kt`.

**Regra de acesso nos documentos:** desde 25/10 a única trava é a flag `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`). Os documentos deixam a regra antiga de acesso (substituída em 25/10): cada passo do checklist exige a flag `true`, ligada **só pelo usuário**, e segue a ordem do checklist na primeira conexão. Se uma fase anterior (4A ou 4) já tiver reescrito os documentos e o teste, esta tarefa só confere; senão, faz a troca aqui.

**Interfaces:**
- Consome o dicionário, as fichas de todos os cards e os endpoints usados pelo `JiraClient`: `search`, `changelog`, `worklog`, `field` e `myself`.
- O checklist passa a listar, com "o que conferir" e "quando parar":
  - cada campo "a confirmar" (`Story Points`, `Epic Link`, `Flagged`, `cf[12416]`, `cf[11412]`, OS Version e os demais do TG-Basic);
  - o vocabulário de `priority`, `labelsCriticidade` e `prioridadePadrao`;
  - a `ordem` do fluxo e as resoluções negativas;
  - o formato do worklog;
  - o `from/to` do assignee no changelog;
  - o Reporter como PL (spec 11).

- [ ] **Passo 1: Testes que falham:**
  - `checklistCitaCadaCampoAConfirmar`: cada campo do dicionário com `finalidade` "a confirmar" aparece no checklist.
  - `matrizCitaCadaEndpoint`.
  - `cadaFichaUsaSoCamposDoDicionario`: carrega todas as `cards/*.card.yaml`.
  - `cadaPassoDoChecklistExigeAFlag`: substitui o teste `cadaPassoDoChecklistExigeAutorizacaoExpressa` (Plano 2) e a contagem mínima de "autorização expressa" do mesmo arquivo. Cada seção de passo do checklist cita `JIRA_LIVE_ENABLED`; o documento diz que só o usuário muda a flag; nenhum documento de `docs/jira/` contém "autorização expressa" nem "por pesquisa". Se ainda existir no mesmo arquivo algum teste que exija essa expressão (por exemplo um `runbookAvisaRegraDoAssistente` de versão antiga do Plano 4A), ele é removido ou trocado pela checagem da flag.
  - `semHostRealNemToken`.
  - `testesNaoAcessamOJiraReal`: os documentos dizem que testes automáticos usam só fixtures e `MockEngine`, mesmo com a flag `true` (a prova técnica continua nos testes `SomenteAFabricaCriaClientesJira` e `FlagDesligadaZeroRequisicoesTest`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*PreparacaoDocsTest*'`. Resultado esperado: FALHA.
- [ ] **Passo 3: Atualizar** os documentos.
- [ ] **Passo 4: Rodar** de novo e esperar PASSA.
- [ ] **Passo 5: Commit** `docs(jira): checklist da 1ª conexão e matriz de compatibilidade finais`.

---

### Tarefa 9: Remoção do `legacy/`

**PONTO DE PARADA OBRIGATÓRIO. Pré-condições (parar se faltar alguma):**
- o usuário aprovou, com um "sim" explícito registrado no `HANDOFF.md`, o documento de equivalência (Tarefa 3, Passo 5; decisão 1);
- todos os `CompatLegado*Test` estão verdes;
- a alteração local em `legacy/.env.example` foi resolvida com o usuário, também em pergunta com opções ("descartar (Recomendada)" ou "guardar o valor em outro lugar antes").

**Se o usuário ainda não aprovou:** não executar esta tarefa. Concluir as demais, abrir o PR sem a remoção e registrar no `HANDOFF.md` a pendência "remoção do `legacy/` aguardando aprovação do documento de equivalência", com data. A remoção vira um PR próprio depois do "sim".

**Arquivos:**
- Remover `legacy/`.
- Modificar `.gitignore` (linha `legacy/data/`), `CLAUDE.md` (tabela "Onde está cada coisa" e "Estrutura e comandos"), `README.md`, `app/README.md`, `HANDOFF.md`, `PRODUCT.md` ("Evidence on Hand"), `docs/analise/diferencas-esperadas-*.md`, `docs/guia-execucao-outra-ia.md`, e `.claude/hooks/guard.sh` e `test-guard.sh` se citarem `legacy`.
- Modificar os comentários dos `CompatLegado*Test`: a referência passa de `legacy/...` para "`legacy/...` na tag `legacy-final`".
- Criar `docs/ReferenciasLegacyTest.kt`.

**Interfaces:** os documentos vivos citam o código antigo como "tag `legacy-final`". Os planos e specs em `docs/superpowers/` são histórico e ficam como estão.

- [ ] **Passo 1: Criar a tag** `git tag legacy-final` no commit atual, que ainda contém `legacy/`. A tag sobe junto com o push do PR.
- [ ] **Passo 2: Teste que falha.** `nadaReferenciaLegacy` varre `app/`, `.claude/hooks/`, `CLAUDE.md`, `README.md`, `PRODUCT.md`, `HANDOFF.md` e `docs/`, exceto `docs/superpowers/` e o próprio documento de equivalência. Nenhum caminho `legacy/` pode aparecer sem a menção `legacy-final`. O teste confere também `nenhumCodigoLeLegacy` em `src/main` e `src/test`.
- [ ] **Passo 3: Remover** com `git rm -r legacy/` e atualizar as referências.
- [ ] **Passo 4: Rodar** as suítes completas do backend e do frontend, `pnpm build`, `sh .claude/hooks/test-guard.sh` e `docker compose build`. Resultado esperado: PASSA.
- [ ] **Passo 5: Commit** `chore: remover o app Python legado (preservado na tag legacy-final)`.

---

### Tarefa 10: Documentação final e guia do painel

**Arquivos:** Criar `docs/manual/guia-do-painel.md` e `docs/GuiaDoPainelDocTest.kt`. Modificar `CLAUDE.md`, `app/README.md`, `PRODUCT.md` (Evidence on Hand e Capabilities), `docs/superpowers/plans/2026-10-24-indice-dos-planos.md` (estado final das fases) e `HANDOFF.md`.

**Interfaces:**
- O guia tem uma seção por item do `nav.ts`, com:
  - a pergunta que a tela responde;
  - quem vê (papéis);
  - de onde vêm os números (ficha `cards/<id>.card.yaml`, fórmula e aceite);
  - os avisos possíveis;
  - como abrir o detalhamento.
- O guia é em pt-BR. A versão em inglês fica fora do escopo.

- [ ] **Passo 1: Testes que falham:**
  - `guiaCitaCadaItemDoMenu`: lê os ids de `nav.ts`.
  - `guiaCitaCadaFicha`.
  - `claudeMdSemCaminhoInexistente`: cada caminho em crase na tabela "Onde está cada coisa" existe.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*GuiaDoPainelDocTest*'`. Resultado esperado: FALHA.
- [ ] **Passo 3: Escrever** o guia e atualizar os documentos.
- [ ] **Passo 4: Rodar** de novo e esperar PASSA; rodar também as suítes completas.
- [ ] **Passo 5: Commit** `docs: guia do painel e documentação final da etapa offline`.

---

## Autoavaliação

**Cobertura da spec:**

| Requisito | Tarefas |
|---|---|
| Spec 10: nenhuma requisição ao Jira | T2, T8, `test-guard` |
| Spec 10: cada papel vê só o permitido em todas as rotas | T1 |
| Spec 10: três temas e AA nos pares de cor | T4, T5 |
| Spec 10: documentos de preparação | T8 |
| Spec 4.6: provedor OIDC genérico atrás de interface | T2 |
| Spec 4.6: volume | T6 |
| Spec 4.6: ambiente em contêiner atrás do proxy | T7 |
| Spec 4.6: oráculo de números | T3 |
| Spec 4.1: o app Python é substituído e o histórico fica no git | T9 |
| Spec 11: riscos levados ao checklist | T8 |
| Relatório, seção 8 item 2: oráculo antes de aposentar o Python | T3 antes de T9 |

**Consistência de tipos:** o `OidcAuthenticator` (T2) implementa o `Authenticator` existente e usa `principalFor`. `ROTAS_PUBLICAS` (T1) inclui as rotas `/auth/*` da T2. O `GeradorSintetico` (T6) grava pelas mesmas tabelas que o `PostgresIssueStore`.

**Pendências deliberadas:**
- o provedor OIDC concreto, o logo limpo e a fonte da Sidia;
- a calibração das heurísticas com dados reais;
- a validação com o DPO.

Todas ficam registradas no `HANDOFF.md`.

## Decisões (25/10, valor padrão recomendado, reversível)

O usuário delegou estas escolhas em 25/10; vale a opção que o plano recomendava. Cada uma pode ser revertida depois por decisão do usuário. A spec da fase é `docs/superpowers/specs/2026-10-25-spec-fase-08-preparacao-final-e-remocao-do-legacy.md`.

1. **Remover o `legacy/` depois que o usuário aprovar o documento de equivalência card a card.** O código antigo fica recuperável pela tag `legacy-final`, e o repositório deixa de ter duas implementações. **A aprovação do documento continua com o usuário: é ponto de parada obrigatório** (Tarefa 3, Passo 5; pré-condições da Tarefa 9). O executor pergunta e não remove nada sem o "sim". Alternativa descartada: manter o `legacy/` até a 1ª conexão real.
2. **Oráculo de equivalência: cenários pequenos por card, com a fórmula do app atual conferida no código** (padrão dos `CompatLegado*Test`; offline e rápido; Tarefa 3). Alternativa descartada: rodar o app Python em modo demonstração e comparar agregados.
3. **Desempenho: primeiro índices, depois consultas em lote e *code splitting*; cache só se ainda faltar**, com chave pelo escopo visível do principal e registrado como `Ruling:` (Tarefa 6, Passo 3). Evita números velhos e vazamento entre papéis. Alternativa descartada: cache de 60 s por card como no app atual.
4. **Rede para registros de pacotes e de imagens: permitida, nunca ao Jira.** O executor pode baixar imagens base do Docker e dependências do Maven/Gradle (OIDC/JWT/sessões do Ktor) e do npm quando não estiverem em cache, sem perguntar (Restrições globais; Tarefas 2 e 7). Acesso ao Jira continua regido só pela flag `JIRA_LIVE_ENABLED`, e nenhum teste automático acessa o Jira real ou um provedor OIDC real.
