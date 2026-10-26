# Spec da Fase 12: Operação em produção

Data: 25/10/2026
Plano: [`docs/superpowers/plans/2026-10-25-plano-12-operacao-em-producao.md`](../plans/2026-10-25-plano-12-operacao-em-producao.md) (12 tarefas, um PR)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 4.6, 5.4, 11)
Auditoria: [`docs/superpowers/cobertura-do-projeto.md`](../cobertura-do-projeto.md) (lacunas A1, A2 e A7; tabela B.5)
Depende de: Plano 8 mesclado na `main` (e, com ele, os Planos 9, 10 e 11). Vem antes do Plano 13 (implantação real).

> **Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano > spec mestra de 24/10. A spec mestra ainda traz a regra antiga de acesso ao Jira, substituída em 25/10 pela flag única.

## 1. Intenção e porquê

Ao fim do Plano 8, o painel sobe em produção, mas ninguém fica sabendo quando ele para de funcionar:
- uma suspensão da sincronização por 401 só aparece se o gerente abrir a tela de status;
- o painel continua mostrando números velhos sem avisar;
- os logs são texto livre, sem garantia de que não carregam token;
- não existe verificação de que o banco responde;
- um YAML de ausências com erro derruba a subida, e só se descobre na hora de reiniciar;
- o backup existe só como comando num documento, sem agendamento e sem teste de que restaura;
- quem operar o painel depende de quem escreveu o código para saber o que fazer com cada aviso.

Esta fase dá ao painel uma operação que não depende do autor:
- logs que se podem guardar e pesquisar sem expor segredo;
- prontidão e métricas para a infraestrutura monitorar;
- alertas para o que já se sabe que dá errado;
- validação da configuração antes de reiniciar;
- backup diário com restauração provada;
- rotação de segredos;
- runbooks com um passo a passo por alerta e por incidente.

Tudo offline e testado. O que depende da infraestrutura da empresa (onde coletar métricas, para onde mandar alertas, para onde copiar backups, onde ficam os YAML reais) vira ponto de parada com opções e fica pronto para o Plano 13.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- **Logs** em JSON, uma linha por evento, com máscara de segredos, id de requisição e uma linha por requisição com o modelo da rota.
- **Prontidão** (`/api/health/pronto`) que confere banco e migrações e responde em até 3 s. A imagem e o compose passam a usá-la.
- **Métricas Prometheus** numa porta interna, sem dados pessoais nos rótulos.
- **Alertas operacionais**, por rota do gerente, métrica, log e faixa na tela, com regras prontas para o Alertmanager: sincronização suspensa ou atrasada, reconciliação suspeita ou atrasada, retenção, registro de acesso, PAT, calendário, backup, restauração e modo dev com o Jira ligado.
- **Validador de configuração** (`painel validar-config`), que roda antes da subida, no CI e por script contra a pasta real, sem imprimir valores.
- **Backup agendado** do PostgreSQL, com conferência, retenção e registro.
- **Teste automatizado de restauração** na suíte e script mensal em produção.
- **Rotação do `SESSION_SECRET`** sem derrubar as sessões, e procedimentos para PAT, senha do banco e segredo do OIDC.
- **Runbooks** de operação diária e de incidentes.

### Não objetivos

- A implantação real: destino (VM ou k8s), proxy, TLS, cofre de segredos, coleta das métricas e carga das regras (Plano 13).
- Validação dos números com dados reais e calibração (Plano 14).
- Recarga de configuração a quente.
- Enviar alertas por e-mail ou chat, salvo escolha do usuário no ponto de parada 1.
- Descobrir a validade do PAT consultando o Jira.

### Antiobjetivos (falha mesmo que "funcione")

- Log com token, senha, cookie, cabeçalho `Authorization`, query string, JQL, chave de issue, username ou texto livre.
- Rótulo de métrica com pessoa, chave de issue, squad ou URL.
- `/metrics` respondendo na porta pública.
- Readiness que revela versão, host ou nome do banco, ou que trava quando o banco não responde.
- Validador que imprime o valor errado: pode ser o nome de uma pessoa.
- Backup registrado como `OK` sem conferir que o arquivo é legível.
- Backup com permissão aberta, dentro da imagem ou do repositório.
- Qualquer alerta, script ou validador que faça requisição ao Jira.
- Runbook que fixa um prazo de comunicação à ANPD sem confirmação do DPO.

## 3. Restrições

### 3.1 Regras transversais do projeto (valem sempre)

**Acesso ao Jira.** A única trava é a flag `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`, 25/10). Só o usuário a liga. O runbook repete isso, e nenhum procedimento manda uma IA mudar a flag ou acessar o Jira.

**Segredos e dados pessoais.** Nunca em log, métrica, resposta, documento ou commit. Configs reais e backups fora do git e da imagem.

**Registro de acesso.** O `access_log` (tabela) continua sendo o único registro de quem consultou o quê. O log do servidor não identifica usuários.

**Fail-closed.** Configuração inválida derruba a subida; o validador antecipa isso.

**Ambiente.** Docker ativo; banco real por Testcontainers; comentários e commits em pt-BR.

### 3.2 Restrições específicas desta fase

- **Dependências novas**, todas do registro Maven: `ktor-server-call-id`, `ktor-server-call-logging` e `ktor-server-metrics-micrometer` (versão do Ktor, 3.3.1), mais `micrometer-registry-prometheus`, em versão exata resolvida na execução e registrada. Nenhuma dependência npm nova.
- **Variáveis novas:** `PAINEL_LOG_FORMATO`, `PAINEL_METRICAS_PORTA`, `PAINEL_METRICAS_HOST`, `SYNC_PAT_VALIDADE_<INSTANCIA>`, `SESSION_SECRET_ANTERIOR`, `BACKUP_HORA`, `BACKUP_RETENCAO_DIAS`, `BACKUP_DIR` e `BACKUP_UMA_VEZ`. Nenhuma começa com `JIRA_` e nenhuma liga a sincronização.
- **Uma migração:** `V{n}__execucao_de_backup.sql` (esperado V12, ou V13 se o Plano 8 tiver criado a de índices).
- **Backup** só com ferramentas da imagem `postgres:16`, com o mesmo digest do compose; script POSIX.

## 4. Decisões já tomadas

| # | Decisão | Origem |
|---|---|---|
| U1 | A flag `JIRA_LIVE_ENABLED` é a única trava; só o usuário a liga | [usuário, 25/10] |
| U2 | Suspensão da sincronização após 401/403/429 (ou 3 falhas seguidas) até reiniciar | [usuário, 25/10, Plano 4A] |
| U3 | Retenção padrão de 24 meses | [usuário, 25/10] |
| D1 | **Logs em JSON por padrão** (`PAINEL_LOG_FORMATO=JSON`; `TEXTO` opcional para desenvolvimento), encoder próprio, máscara de segredos registrados e de padrões conhecidos; nenhum usuário no log | [valor padrão 25/10, reversível] |
| D2 | **Id de requisição:** `X-Request-Id` do proxy aceito só se casar `^[A-Za-z0-9-]{8,64}$`; senão UUID | [valor padrão 25/10, reversível] |
| D3 | **Readiness** em `/api/health/pronto` (pública, mínima, cache de 5 s, tempo máximo de 2 s); `/api/health` continua liveness | [valor padrão 25/10, reversível] |
| D4 | **Métricas** em `PAINEL_METRICAS_PORTA` (9464), host `127.0.0.1` por padrão; rótulos fechados, conforme a tabela do plano | [valor padrão 25/10, reversível] |
| D5 | **Alertas**: 12 códigos com as condições e gravidades da tabela do plano, avaliados a cada 5 min, publicados na mudança de estado | [valor padrão 25/10, reversível] |
| D6 | **PAT:** validade informada pelo operador em `SYNC_PAT_VALIDADE_<INSTANCIA>`; alerta a 14 dias | [valor padrão 25/10, reversível] |
| D7 | **Backup** diário às 02:00 de Manaus, `pg_dump -Fc`, conferência por `pg_restore --list`, permissão `600`, retenção de 14 dias, registro em `backup_run` | [valor padrão 25/10, reversível] |
| D8 | **Restauração** testada na suíte e por script mensal; alerta depois de 35 dias sem teste | [valor padrão 25/10, reversível] |
| D9 | **Configuração sem recarga a quente:** validar e reiniciar | [valor padrão 25/10, reversível] |
| D10 | **Rotação do `SESSION_SECRET`** com `SESSION_SECRET_ANTERIOR` aceito só na leitura, por uma duração de sessão (8 h) | [valor padrão 25/10, reversível] |

## 5. O que fica para quem implementa

- A versão exata do `micrometer-registry-prometheus`: a última 1.x estável, compatível com o Ktor 3.3.1.
- A técnica de dois conectores no Netty, desde que `/metrics` nunca responda na 8080.
- O conteúdo dos runbooks além dos títulos fixos que os testes leem, desde que cada comando citado exista.
- Os nomes das métricas JVM e Hikari (os dos binders padrão).
- A forma de calcular "até a próxima 02:00 de Manaus" no `backup-loop.sh`.

## 6. Critérios de aceitação verificáveis

| Critério | Prova |
|---|---|
| Log em JSON, uma linha por evento, segredos mascarados (inclusive escapados), sem usuário nem query | `umaLinhaJsonValidaPorEvento`, `quebraDeLinhaNaoCriaEntrada`, `excecaoComTokenSaiMascarada`, `segredoRegistradoMascaradoTambemEscapado`, `semUsuarioNoLog`, `linhaPorRequisicaoComModeloDaRota` (T1) |
| Readiness detecta banco fora em até 3 s, sem detalhes; liveness continua | `prontoDetectaBancoForaEmAte3s`, `respostaSemDetalhes`, `healthContinua200ComBancoFora` (T2) |
| Inventário de rotas atualizado com a readiness | `InventarioDeRotasTest` (T2) |
| Validador aponta cada problema por código, sem valor, sem banco nem Jira; roda no CI | `pessoaDesconhecidaNaAusencia`, `erroSemValorNoTexto`, `naoAbreBancoNemJira`, `backendValidaConfigDeExemplo` (T3) |
| Métricas só na porta interna e sem dado pessoal | `metricasNaoRespondemNaPortaPublica`, `semDadoPessoalNosRotulos`, `rotuloDeRotaEhModelo` (T4) |
| Cada alerta dispara na condição certa, uma vez por mudança, sem requisição ao Jira | um teste por código, `suspensaoViraAlertaUmaVez`, `avaliacaoNaoFazRequisicao` (T5) |
| A tela do gerente mostra os alertas com símbolo e texto, pt e en | `criticoComTrianguloETexto`, `todoCodigoDeAlertaTraduzido` (T6) |
| Backup legível, `600`, conferido, registrado, com retenção; arquivo truncado não vira `OK` | `backupGeraArquivoLegivelERegistraOk`, `permissao600`, `backupTruncadoNaoViraOk`, `retencaoApagaAntigos` (T7) |
| Serviço de backup endurecido, sem portas, com o mesmo digest | `backupNoPerfilProdSemPortas`, `backupEndurecido` (T7) |
| A restauração reproduz o original e o painel sobe nela com os mesmos números | `restauracaoIgualAoOriginal`, `painelSobeNoRestaurado`, `numerosIguaisNoCard` (T8) |
| Rotação do segredo da sessão sem derrubar quem está logado | `sessaoComSegredoAnteriorAindaValida`, `sessaoLidaComAnteriorEhRegravadaComAtual` (T9) |
| Cada alerta e cada incidente tem procedimento; cada variável está documentada; nenhum prazo da ANPD fixado | `cadaAlertaTemSecao`, `cadaVariavelDoEnvExampleEstaDocumentada`, `cadaIncidenteTemAsCincoEtapas`, `prazoDaAnpdNaoFixado` (T10, T11) |

## 7. Riscos, pendências e pontos de parada

**PONTO DE PARADA 1 — canal externo dos alertas (T5, antes do Passo 3).** A avaliação, a rota, a métrica, o log e a faixa na tela são feitos em qualquer caso. Pergunta ao usuário:
1. **Prometheus e Alertmanager da infraestrutura, com o arquivo `ops/prometheus/alertas.yml` (Recomendada).** Por quê: é o padrão de mercado, não põe segredo de destino no painel e a infraestrutura escolhe o canal (e-mail, chat, plantão). Consequência: o Plano 13 pede à infraestrutura que colete a porta 9464 e carregue o arquivo; até lá, os alertas aparecem só na tela do gerente e no log.
2. **Sem monitoramento externo: só a tela do gerente e o log.** Por quê: nada a pedir à infraestrutura. Consequência: um alerta só é visto quando o gerente abre o painel; o runbook diário passa a exigir a conferência diária da faixa.
3. **Webhook direto do painel para um canal de chat (por exemplo, Teams).** Por quê: avisa sem Prometheus. Consequência: subtarefa a mais na T5: `DASHBOARD_ALERTA_WEBHOOK_URL` como segredo (no cofre, nunca em arquivo); envio só na mudança de estado, com o código e a gravidade, sem nenhum dado pessoal; teste com `MockEngine`; o painel passa a fazer uma requisição de saída para um host da empresa.

Sem resposta, seguir com a opção 1, que é reversível e não muda o código do painel.

**PONTO DE PARADA 2 — destino dos backups fora do servidor e cifragem (T7, antes do Passo 3).** O backup local agendado é feito em qualquer caso. Pergunta ao usuário:
1. **Volume local, com cópia diária feita pela infraestrutura para um armazenamento externo cifrado (Recomendada).** Por quê: separa responsabilidades; o painel não guarda credencial de armazenamento. Consequência: o Plano 13 pede à infraestrutura a cópia e a cifragem em repouso; até lá, perder o servidor significa perder os backups.
2. **O próprio contêiner cifra o dump com uma chave pública (`age`) antes de gravar.** Por quê: o backup sai do contêiner já cifrado. Consequência: imagem de backup própria (com `age`), variável `BACKUP_CHAVE_PUBLICA`, chave privada guardada fora do servidor, e a restauração passa a exigir a chave privada (um passo a mais no runbook e no teste).
3. **Só o volume local.** Por quê: o mais simples. Consequência: risco aceito e registrado: falha de disco ou perda do servidor apaga o banco e os backups juntos.

Sem resposta, seguir com a opção 1 e registrar no `HANDOFF.md` a pendência para a infraestrutura.

**PONTO DE PARADA 3 — onde ficam os YAML reais e quem os edita (T3, só para o documento).** Pergunta ao usuário:
1. **Repositório git privado de configuração, com PR revisado, o `validar-config.sh` no CI desse repositório e a cópia para o volume `:ro` do servidor na implantação (Recomendada).** Por quê: histórico de quem mudou o quê, revisão antes de valer e a validação automática. Consequência: criar o repositório (o usuário cria, com acesso restrito, porque tem nomes de pessoas); o Plano 13 define a cópia.
2. **Arquivos no servidor, editados por um responsável nomeado, com `validar-config.sh` antes de reiniciar.** Por quê: sem repositório novo. Consequência: sem histórico nem revisão; o runbook pede cópia de segurança do arquivo antes de cada edição.
3. **ConfigMap/Secret do Kubernetes.** Por quê: só se o destino for k8s (decisão do Plano 13). Consequência: o documento fica provisório até o Plano 13 decidir o destino.

Sem resposta, o documento descreve a opção 1 como recomendada e a 2 como alternativa, e a pendência vai ao `HANDOFF.md`.

**Impacto em outros planos:**
- **Plano 8, T1 (já mesclado):** `ROTAS_PUBLICAS`, `inventarioConhecido` e `rotasPublicas` ganham `/api/health/pronto`; `/api/operacao/alertas` entra no inventário como rota do gerente. A porta de métricas fica fora do inventário (outro conector).
- **Plano 8, T2 e T7:** `Sessao.kt` ganha a leitura com o segredo anterior; o perfil `prod` do compose ganha o serviço `backup`, o volume `backups` e a porta interna de métricas; `ComposeTest` ganha casos.
- **Plano 9:** o job `backend` do CI passa a validar as configurações de exemplo.
- **Plano 11:** o relatório para o DPO (seção 10) aponta para o runbook de incidentes; a eliminação a pedido do titular fica no runbook diário.
- **Plano 13:** recebe as pendências de infraestrutura (coleta, regras, cópia de backup, local dos YAML).

**Riscos:**
- *A máscara não pegar um formato novo de segredo.* Mitigação: segredos carregados pelo `Settings` são registrados por valor exato (também escapados), além dos padrões.
- *Dois conectores no Netty exporem `/metrics` por engano na 8080.* Mitigação: teste explícito e roteamento pela porta local.
- *O `backup-loop.sh` derivar de horário* (contêiner reiniciado). Mitigação: o alerta `BACKUP_ATRASADO` a 26 h pega a falha; o runbook manda rodar `BACKUP_UMA_VEZ=1` na hora.
- *A restauração em produção competir por recursos.* Mitigação: o script usa um contêiner temporário sem volume persistente e roda fora do horário de uso (o runbook indica quando).

**Migrações:** a desta fase é a próxima livre depois do Plano 8 (V12 se o 8 não criou a de índices; V13 se criou). Sequência esperada até o Plano 12: V4, V5 e V6 (4A), V7 (5a), V8 (6b), V9 (7b), V10 (10), V11 (11, se a opção do ponto de parada a criou), V12 (8, condicional), V12 ou V13 (12). O Plano 16 (futuro) usa a próxima livre depois disso. Como todos os planos usam "próximo número livre", não há conflito em execução sequencial.

## 8. Glossário do que esta fase consome

| Nome | O que é | Origem |
|---|---|---|
| `AppContext.load` e os carregadores dos YAML | subida fail-closed | Planos 1 a 7 |
| `SyncStateStore`, `SyncMode`, `SyncKind`, `RunStatus`, `Agendador` | estado e agendamento da sincronização | Plano 4A |
| `Retention.purge`, `RetencaoSettings` | retenção por categoria | Planos 4A e 11 |
| `AccessLog`, `AccessLogException` | registro de consultas | Plano 3 |
| `rotasPublicas`, `ROTAS_PUBLICAS`, `inventarioConhecido` | rotas públicas e inventário | Planos 1, 9 e 8 |
| `Sessao.kt`, `OidcSettings`, `SESSION_SECRET` | sessão de produção | Plano 8, T2 |
| Perfil `prod`, `ComposeTest`, `verificar-imagem.sh`, `docs/implantacao.md` | contêiner de produção | Plano 8, T7 |
| `PostgresDeTeste`, `GeradorSintetico` | banco de teste e dados sintéticos | Planos 9 e 8 |
| `ci.yml`, `CiWorkflowTest` | CI | Plano 9 |
| `CodigosDeMensagem`, `EstadoDaTela` | mensagens por código e estados de tela | Plano 10 |
| `EncoderJsonSeguro`, `MascaraDeLog`, `Prontidao`, `Metricas`, `AvaliadorDeAlertas`, `CodigoAlerta`, `backup_run`, `ValidadorDeConfig` (novos) | entregas desta fase | esta fase |

## 9. Referência ao plano

[`docs/superpowers/plans/2026-10-25-plano-12-operacao-em-producao.md`](../plans/2026-10-25-plano-12-operacao-em-producao.md): 12 tarefas, em um PR a partir da `main` com o Plano 8 mesclado.
