# Spec da Fase 12: Operação em produção

Data: 25/10/2026 (reescrita em 27/10/2026 para SQLite embutido, sem Docker, sem PostgreSQL, sem Testcontainers)
Plano: [`docs/superpowers/plans/2026-10-25-plano-12-operacao-em-producao.md`](../plans/2026-10-25-plano-12-operacao-em-producao.md) (12 tarefas, um PR)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 4.6, 5.4, 11)
Auditoria: [`docs/superpowers/cobertura-do-projeto.md`](../cobertura-do-projeto.md) (lacunas A1, A2 e A7; tabela B.5)
Adendo de plataforma: [`docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`](../plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md)
Depende de: Plano 8 mesclado na `main` (e, com ele, os Planos 9, 10 e 11). Vem antes do Plano 13 (implantação real do pacote como serviço).

> **Precedência:** `CLAUDE.md` > `.clinerules/` > `docs/guia-execucao-outra-ia.md` > adendo de 27/10 > esta spec > o plano > spec mestra de 24/10. A spec mestra ainda traz a regra antiga de acesso ao Jira (substituída em 25/10 pela flag única) e fala de PostgreSQL e contêiner (substituídos em 27/10 pelo adendo).

## 1. Intenção e porquê

Ao fim do Plano 8, o painel sobe em produção como processo, mas ninguém fica sabendo quando ele para de funcionar:
- uma suspensão da sincronização por 401 só aparece se o gerente abrir a tela de status;
- o painel continua mostrando números velhos sem avisar;
- os logs são texto livre, sem garantia de que não carregam token;
- não existe verificação de que o arquivo do banco está acessível, migrado, com espaço em disco e com a trava de processo;
- um YAML de ausências com erro derruba a subida, e só se descobre na hora de reiniciar;
- o banco é um único arquivo: sem cópia agendada e testada, uma queda de disco ou um arquivo corrompido apaga o histórico de decisões e o registro de acesso;
- quem operar o painel depende de quem escreveu o código para saber o que fazer com cada aviso.

Esta fase dá ao painel uma operação que não depende do autor:
- logs que se podem guardar e pesquisar sem expor segredo;
- prontidão e métricas para a infraestrutura monitorar, se quiser;
- alertas para o que já se sabe que dá errado, com saída em texto (arquivo e log) que não depende de serviço externo;
- validação da configuração antes de reiniciar;
- backup diário do SQLite com restauração provada, agendado pelo próprio painel;
- rotação de segredos;
- runbooks com um passo a passo por alerta e por incidente, para Windows (PowerShell) e `sh`.

Tudo offline e testado com SQLite real em arquivo temporário. O que depende da infraestrutura da empresa (para onde copiar backups, se há coleta de métricas, onde ficam os YAML reais) vira ponto de parada com opções e fica pronto para o Plano 13.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- **Logs** em JSON, uma linha por evento, com máscara de segredos, id de requisição e uma linha por requisição com o modelo da rota; na saída do processo e, se configurado, em arquivo com rotação. O formato e a pasta vêm do arquivo de variáveis, aplicados por um configurador que reinicia o Logback no começo do `main`.
- **Prontidão** (`/api/health/pronto`) que confere arquivo do banco acessível e gravável (permissão), migrações aplicadas, espaço em disco e trava de processo, e responde em até 3 s sem depender do pool de conexões.
- **Métricas Prometheus** numa porta interna (`127.0.0.1:9464`), sem dados pessoais nos rótulos.
- **Alertas operacionais** (14 códigos), por rota do gerente, métrica, log, **arquivo de alertas em texto** e faixa na tela: sincronização suspensa ou atrasada, reconciliação suspeita ou atrasada, retenção, registro de acesso, PAT, calendário, disco baixo, banco suspeito, backup, restauração e modo dev com o Jira ligado.
- **Validador de configuração** (`painel validar-config`), que roda antes da subida, no CI e por script contra a pasta real, sem imprimir valores e sem abrir banco nem Jira.
- **Backup agendado do SQLite**: cópia consistente por `VACUUM INTO` para uma pasta configurável fora do repositório, conferência de integridade, retenção das N cópias mais recentes, registro em `backup_run`, agendado pelo `Agendador` do painel (sem cron externo obrigatório) e disparável por `backup-agora`.
- **Teste automatizado de restauração**: a cópia é restaurada em diretório temporário, conferida (integridade, migrações, contagens e somas por tabela) e apagada, na suíte e depois de cada backup em produção.
- **Rotação do `SESSION_SECRET`** sem derrubar as sessões, e procedimentos para PAT e segredo do OIDC.
- **Runbooks** de operação diária e de incidentes, com blocos PowerShell e `sh`: ver logs, reiniciar o serviço, restaurar backup, trocar o pacote, reverter, banco corrompido, disco cheio e trava `.lock` órfã.

### Não objetivos

- A implantação real: instalação do serviço no sistema operacional, proxy, TLS, cofre de segredos, coleta das métricas e cópia dos backups para fora do servidor (Plano 13).
- Validação dos números com dados reais e calibração (Plano 14).
- Recarga de configuração a quente.
- Enviar alertas por e-mail ou chat, salvo escolha do usuário no ponto de parada 1.
- Cifrar o arquivo de backup dentro do painel (a cifragem em repouso é do volume: BitLocker no Windows, LUKS no Linux).
- Descobrir a validade do PAT consultando o Jira.
- Qualquer Docker, contêiner, PostgreSQL, `pg_dump` ou Testcontainers (proibidos pelo adendo).

### Antiobjetivos (falha mesmo que "funcione")

- Log com token, senha, cookie, cabeçalho `Authorization`, query string, JQL, chave de issue, username, caminho de arquivo ou texto livre.
- Rótulo de métrica com pessoa, chave de issue, squad, URL ou caminho.
- `/metrics` respondendo na porta pública.
- Readiness que revela versão, host, caminho do banco ou detalhes, que trava quando o banco não responde ou que espera o pool de conexões.
- Validador que imprime o valor errado: pode ser o nome de uma pessoa.
- Backup registrado como `OK` sem `integrity_check`, `foreign_key_check`, versão do esquema e SHA-256 conferidos; arquivo `.parcial` deixado na pasta de backup.
- Retenção que apaga arquivo que não é cópia do painel, ou a única cópia `OK`.
- Verificação de restauração que escreve no banco vivo ou deixa o diretório temporário para trás.
- Backup com permissão aberta (onde o sistema suporta), dentro do repositório, da pasta do pacote ou da pasta do banco.
- Qualquer alerta, script, validador ou backup que faça requisição ao Jira.
- Runbook que manda apagar `painel.db`, `-wal` ou `-shm` como remédio de trava órfã, ou que restaura por cima de `-wal`/`-shm` antigos.
- Runbook que fixa um prazo de comunicação à ANPD sem confirmação do DPO.

## 3. Restrições

### 3.1 Regras transversais do projeto (valem sempre)

**Acesso ao Jira.** A única trava é a flag `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`). Só o usuário a muda, e ela vale somente no `app/.env`. O runbook repete isso, e nenhum procedimento manda uma IA mudar a flag ou acessar o Jira. Os testes desta fase usam `Settings` em memória e `MockEngine` contador, nunca o ambiente real.

**Segredos e dados pessoais.** Nunca em log, métrica, resposta, documento ou commit. Configs reais e backups fora do git e fora do pacote.

**Registro de acesso.** O `access_log` (tabela) continua sendo o único registro de quem consultou o quê. O log do servidor não identifica usuários.

**Fail-closed.** Configuração inválida derruba a subida; o validador antecipa isso.

**Ambiente.** JDK 21 e Node 22 com pnpm; SQLite em arquivo (`app/data/painel.db`, `DATABASE_PATH`); um painel por arquivo (`<banco>.lock`); testes com SQLite real em arquivo temporário (`BancoEmArquivo`, `testContextComBanco`); sem Docker, sem servidor de banco, sem H2, sem mock de banco; Windows 11 e/ou Linux; comentários e commits em pt-BR.

### 3.2 Restrições específicas desta fase

- **Dependências novas**, todas do registro Maven: `ktor-server-call-id`, `ktor-server-call-logging` e `ktor-server-metrics-micrometer` (versão do Ktor, 3.3.1), mais `micrometer-registry-prometheus`, em versão exata resolvida na execução e registrada. Nenhuma dependência npm nova. O backup usa o `sqlite-jdbc` que já está no build.
- **Variáveis novas:** `PAINEL_LOG_FORMATO`, `PAINEL_LOG_DIR`, `PAINEL_METRICAS_PORTA`, `PAINEL_METRICAS_HOST`, `PAINEL_ALERTAS_ARQUIVO`, `PAINEL_DISCO_MIN_MB`, `SYNC_PAT_VALIDADE_<INSTANCIA>`, `SESSION_SECRET_ANTERIOR`, `BACKUP_DIR`, `BACKUP_HORA` e `BACKUP_RETENCAO_COPIAS`. Nenhuma começa com `JIRA_` e nenhuma liga a sincronização.
- **Uma migração:** `V{n}__execucao_de_backup.sql` (tabela `backup_run` em SQLite), com `n` = próximo número livre no momento da execução (hoje só existe a `V1`, então a próxima seria `V2`, mas os planos anteriores podem ter criado outras; conferir `ls` na pasta de migrações).
- **Backup** só com o que o JDK e o `sqlite-jdbc` oferecem (`VACUUM INTO`, `PRAGMA integrity_check`); nenhum utilitário externo, nenhum script obrigatório de agendamento do sistema.
- **Subcomandos** (`validar-config`, `backup-agora`, `verificar-restauracao`) no mesmo binário, sem tomar a trava do banco e sem subir o servidor.

## 4. Decisões já tomadas

| # | Decisão | Origem |
|---|---|---|
| U1 | A flag `JIRA_LIVE_ENABLED` é a única trava; só o usuário a liga | [usuário, 25/10] |
| U2 | Suspensão da sincronização após 401/403/429 (ou 3 falhas seguidas) até reiniciar | [usuário, 25/10, Plano 4A] |
| U3 | Retenção padrão de 24 meses | [usuário, 25/10] |
| U4 | Sem Docker, sem PostgreSQL, sem Testcontainers; SQLite em arquivo; o painel roda como processo/serviço | [usuário, 26/10; adendo de 27/10] |
| D1 | **Logs em JSON por padrão** (`PAINEL_LOG_FORMATO=JSON`; `TEXTO` opcional), encoder próprio, máscara de segredos registrados e de padrões conhecidos; arquivo com rotação só com `PAINEL_LOG_DIR`; nenhum usuário no log; configurador que reaplica o Logback depois de ler o arquivo de variáveis | [valor padrão 27/10, reversível] |
| D2 | **Id de requisição:** `X-Request-Id` do proxy aceito só se casar `^[A-Za-z0-9-]{8,64}$`; senão UUID | [valor padrão 25/10, reversível] |
| D3 | **Readiness** em `/api/health/pronto` (pública, mínima, cache de 5 s, tempo máximo de 2 s, sonda em conexão própria): códigos `BANCO_INDISPONIVEL`, `MIGRACAO_PENDENTE`, `DISCO_BAIXO` (padrão 500 MB, `PAINEL_DISCO_MIN_MB`), `TRAVA_PERDIDA`; sem escrita de teste; `/api/health` continua liveness | [valor padrão 27/10, reversível] |
| D4 | **Métricas** em `PAINEL_METRICAS_PORTA` (9464), host `127.0.0.1` por padrão, `0` desliga; rótulos fechados, conforme a tabela do plano | [valor padrão 27/10, reversível] |
| D5 | **Alertas**: 14 códigos com as condições e gravidades da tabela do plano, avaliados a cada 5 min, publicados só na mudança de estado em rota, métrica, log e arquivo de texto | [valor padrão 27/10, reversível] |
| D6 | **PAT:** validade informada pelo operador em `SYNC_PAT_VALIDADE_<INSTANCIA>`; alerta a 14 dias, só com a flag ligada | [valor padrão 25/10, reversível] |
| D7 | **Backup** diário às 02:00 de Manaus, `VACUUM INTO` para `painel-aaaa-MM-dd-HHmmss.db` numa pasta fora do repositório e fora da pasta do banco, conferência (`integrity_check`, `foreign_key_check`, versão do esquema, SHA-256), renomeação atômica, retenção de 14 cópias (`BACKUP_RETENCAO_COPIAS`), registro em `backup_run`; `BACKUP_DIR` obrigatório em produção; agendado pelo `Agendador` | [valor padrão 27/10, reversível] |
| D8 | **Restauração** testada em diretório temporário depois de cada backup `OK` e por `verificar-restauracao`; alerta depois de 3 dias sem teste `OK` | [valor padrão 27/10, reversível] |
| D9 | **Configuração sem recarga a quente:** validar e reiniciar o serviço | [valor padrão 25/10, reversível] |
| D10 | **Rotação do `SESSION_SECRET`** com `SESSION_SECRET_ANTERIOR` aceito só na leitura, por uma duração de sessão (8 h); sem senha de banco para rotacionar | [valor padrão 27/10, reversível] |
| D11 | **Cifragem em repouso** dos backups é do volume (BitLocker/LUKS); o painel não cifra | [valor padrão 27/10, reversível] |

## 5. O que fica para quem implementa

- A versão exata do `micrometer-registry-prometheus`: a última 1.x estável, compatível com o Ktor 3.3.1.
- A técnica de dois conectores no Netty, desde que `/metrics` nunca responda na 8080.
- Se o `VACUUM INTO` aceita o caminho como parâmetro no `sqlite-jdbc` da versão fixada; se não aceitar, usar o literal com aspas dobradas e registrar `Ruling:`.
- A forma de proteger a pasta de backup no Windows (`icacls`) e no Linux (`chmod`), documentada no runbook.
- O conteúdo dos runbooks além dos títulos fixos que os testes leem, desde que cada comando citado exista e cada bloco tenha as versões PowerShell e `sh`.
- Os nomes das métricas JVM e Hikari (os dos binders padrão).
- O nome do serviço nos exemplos do runbook (`painel-squads`) até o Plano 13 definir o real.

## 6. Critérios de aceitação verificáveis

| Critério | Prova |
|---|---|
| Log em JSON, uma linha por evento, segredos mascarados (inclusive escapados), sem usuário nem query; formato e pasta aplicados mesmo vindo do arquivo de variáveis | `umaLinhaJsonValidaPorEvento`, `quebraDeLinhaNaoCriaEntrada`, `excecaoComTokenSaiMascarada`, `segredoRegistradoMascaradoTambemEscapado`, `semUsuarioNoLog`, `linhaPorRequisicaoComModeloDaRota`, `configuradorAplicaFormatoTexto`, `configuradorGravaArquivoComRotacao` (T1) |
| Readiness detecta banco inacessível em até 3 s, sem esperar o pool, sem detalhes; disco baixo e trava perdida têm código; liveness continua | `prontoDetectaBancoIndisponivelEmAte3s`, `prontoNaoEsperaOPoolEsgotado`, `discoBaixo503`, `travaPerdida503`, `respostaSemDetalhes`, `healthContinua200ComBancoIndisponivel` (T2) |
| Inventário de rotas atualizado com a readiness | `InventarioDeRotasTest` (T2) |
| Validador aponta cada problema por código, sem valor, sem banco nem Jira; roda no CI; a refatoração do `AppContext` não muda comportamento | `pessoaDesconhecidaNaAusencia`, `erroSemValorNoTexto`, `naoAbreBancoNemJira`, `AppContextTest`, `backendValidaConfigDeExemplo` (T3) |
| Métricas só na porta interna e sem dado pessoal | `metricasNaoRespondemNaPortaPublica`, `semDadoPessoalNosRotulos`, `rotuloDeRotaEhModelo` (T4) |
| Cada alerta dispara na condição certa, uma vez por mudança, com saída em arquivo de texto e sem requisição ao Jira | um teste por código, `suspensaoViraAlertaUmaVez`, `linhaDeTextoSemDadoPessoal`, `avaliacaoNaoFazRequisicao` (T5) |
| A tela do gerente mostra os alertas com símbolo e texto, pt e en | `criticoComTrianguloETexto`, `todoCodigoDeAlertaTraduzido` (T6) |
| Cópia consistente (mesmo com escrita em paralelo), conferida, com `600` em POSIX, registrada, com retenção por nome exato; cópia corrompida não vira `OK` e não deixa `.parcial` | `copiaLegivelEIgualAoOriginal`, `copiaConsistenteComEscritaEmParalelo`, `permissao600`, `copiaCorrompidaNaoViraOk`, `parcialNaoSobra`, `retencaoSoApagaNomesDeBackup`, `retencaoPreservaUltimoOk` (T7) |
| O backup roda pelo `Agendador` na hora configurada, sem `SyncKind` novo, e também por `backup-agora` com o serviço rodando | `backupDiarioNaHoraConfigurada`, `backupAgoraComServicoRodando` (T7) |
| A restauração, em arquivo temporário, reproduz o original; o painel sobe nela com os mesmos números; cópia alterada ou truncada é detectada; o temporário e o banco vivo não são afetados | `restauracaoIgualAoOriginal`, `painelSobeNoRestaurado`, `numerosIguaisNoCard`, `copiaAlteradaDetectada`, `temporarioApagadoMesmoNaFalha`, `bancoVivoIntactoEmEscrita` (T8) |
| Rotação do segredo da sessão sem derrubar quem está logado | `sessaoComSegredoAnteriorAindaValida`, `sessaoLidaComAnteriorEhRegravadaComAtual` (T9) |
| Cada alerta e cada incidente tem procedimento com blocos PowerShell e `sh`; cada variável está documentada; trava órfã nunca manda apagar o banco; restauração cita `-wal` e `-shm`; nenhum prazo da ANPD fixado; nada de Docker nos documentos | `cadaAlertaTemSecao`, `cadaVariavelDoEnvExampleEstaDocumentada`, `cadaSistemaTemOsDoisBlocos`, `semDockerNemPostgres`, `cadaIncidenteTemAsCincoEtapas`, `restauracaoCitaWalEShm`, `travaOrfaNuncaApagaOBanco`, `prazoDaAnpdNaoFixado` (T10, T11) |

## 7. Riscos, pendências e pontos de parada

**PONTO DE PARADA 1: canal externo dos alertas (T5, antes do Passo 3).** A avaliação, a rota, a métrica, o log, o arquivo de alertas em texto e a faixa na tela são feitos em qualquer caso. Pergunta ao usuário (pergunta com opções, a recomendada primeiro):
1. **Arquivo de alertas + log + faixa na tela, sem serviço externo (Recomendada).** Por quê: nada a pedir à infraestrutura e nada novo para manter; o arquivo (`PAINEL_ALERTAS_ARQUIVO`) pode ser lido por quem monitora o servidor. Consequência: o alerta só chega a quem abre o painel ou olha o arquivo; o runbook diário exige a conferência da faixa e do arquivo; o `ops/prometheus/alertas.yml` fica disponível como extra opcional para quem tiver Prometheus.
2. **Webhook direto do painel para um canal de chat corporativo.** Por quê: avisa sem esperar alguém abrir o painel. Consequência: subtarefa a mais na T5: `DASHBOARD_ALERTA_WEBHOOK_URL` como segredo (só no `app/.env`, na máscara de log); envio só na mudança de estado, com o código e a gravidade, sem nenhum dado pessoal; teste com `MockEngine`; o painel passa a fazer uma requisição de saída para um host da empresa (autorizar com a rede/TI antes).
3. **Só a faixa na tela e o log, sem o arquivo de alertas.** Por quê: o mínimo. Consequência: quem não abrir o painel não vê o alerta; o log precisa ser lido para achar a linha `ALERTA ... ATIVO`.

Sem resposta, seguir com a opção 1, que é reversível e não faz nenhuma requisição de saída.

**PONTO DE PARADA 2: cópia dos backups além da pasta local e cifragem (T7, antes do Passo 3).** O backup local agendado e conferido é feito em qualquer caso. Pergunta ao usuário:
1. **Pasta local em volume separado do banco, cifrada pelo sistema (BitLocker/LUKS), com cópia diária feita pela infraestrutura para outro lugar (Recomendada).** Por quê: separa responsabilidades; o painel não guarda credencial de armazenamento nem chave. Consequência: o Plano 13 pede à infraestrutura a cópia; até lá, perder o servidor significa perder os backups.
2. **O próprio painel também grava cada cópia numa segunda pasta (`BACKUP_DIR_ESPELHO`: outro disco ou compartilhamento de rede já montado).** Por quê: uma cópia em outro disco sem depender da infraestrutura. Consequência: subtarefa a mais na T7 (cópia e conferência do espelho, mais um código de erro); a pasta de rede precisa de permissão restrita e vira ponto de exposição de dados pessoais.
3. **Só a pasta local.** Por quê: o mais simples. Consequência: risco aceito e registrado: falha de disco ou perda do servidor apaga o banco e os backups juntos.

Sem resposta, seguir com a opção 1 e registrar no `HANDOFF.md` a pendência para a infraestrutura.

**PONTO DE PARADA 3: onde ficam os YAML reais e quem os edita (T3, só para o documento).** Pergunta ao usuário:
1. **Repositório git privado de configuração, com PR revisado, a validação (`validar-config`) no CI desse repositório e a cópia para a pasta `PAINEL_CONFIG_DIR` do servidor na implantação (Recomendada).** Por quê: histórico de quem mudou o quê, revisão antes de valer e validação automática. Consequência: criar o repositório (o usuário cria, com acesso restrito, porque tem nomes de pessoas); o Plano 13 define a cópia.
2. **Arquivos numa pasta do servidor, editados por um responsável nomeado, com `validar-config` antes de reiniciar.** Por quê: sem repositório novo. Consequência: sem histórico nem revisão; o runbook pede cópia de segurança do arquivo antes de cada edição.

Sem resposta, o documento descreve a opção 1 como recomendada e a 2 como alternativa, e a pendência vai ao `HANDOFF.md`.

**Impacto em outros planos:**
- **Plano 8 (já mesclado antes deste):** `rotasPublicas`, `ROTAS_PUBLICAS` e `inventarioConhecido` ganham `/api/health/pronto`; `/api/operacao/alertas` entra no inventário como rota do gerente. A porta de métricas fica fora do inventário (outro conector). `Sessao.kt` ganha a leitura com o segredo anterior. `docs/implantacao.md` ganha links para `docs/operacao/*`.
- **Código existente tocado, com testes de linha de base:** `AppContext.load` (extração de `carregarConfiguracao`, `AppContextTest`), `TravaDeProcesso` (`valida`), `Database` (`migracoesPendentes`), `Agendador` (`TarefaDeOperacao`, `AgendadorTest`), `Retention` (linha de `backup_run`, `RetentionTest`), `Application.main` (`args`, `ConfiguradorDeLog`, segundo conector).
- **Plano 9:** o job `backend` do CI (só JDK 21 e Node 22/pnpm) passa a validar as configurações de exemplo.
- **Plano 11:** o relatório para o DPO (seção 10) aponta para o runbook de incidentes; a eliminação a pedido do titular fica no runbook diário, com a nota de que as cópias antigas ainda contêm o dado até saírem da retenção.
- **Plano 13:** recebe as pendências de infraestrutura (instalação do serviço no Windows e no Linux, pasta de backup definitiva e permissões, cópia dos backups, coleta opcional das métricas, local dos YAML).

**Riscos:**
- *A máscara não pegar um formato novo de segredo.* Mitigação: segredos carregados pelo `Settings` são registrados por valor exato (também escapados), além dos padrões.
- *Variáveis de log ignoradas por chegarem depois do Logback.* Mitigação: `ConfiguradorDeLog.aplicar` no começo do `main`, com teste.
- *Dois conectores no Netty exporem `/metrics` por engano na 8080.* Mitigação: teste explícito e roteamento pela porta local.
- *O backup competir com a sincronização ou com a retenção por gravação.* Mitigação: `VACUUM INTO` só lê (WAL), roda na mesma fila do `Agendador` e nunca segura transação de escrita.
- *Restaurar por cima de um banco com `-wal`/`-shm` antigos corromper o resultado.* Mitigação: runbook manda parar o serviço e mover os três arquivos; teste de documento.
- *Falso alarme de prontidão durante uma gravação longa.* Mitigação: a prontidão não faz escrita de teste; confere permissão e leitura.
- *Perder o `backup_run` ao restaurar (a linha do backup é gravada depois da cópia).* Mitigação: o runbook manda rodar `backup-agora` logo após restaurar.
- *No Windows a permissão `600` não existe.* Mitigação: runbook com `icacls` para a conta do serviço e validação de que a pasta de backup está fora do repositório e do pacote.

**Migrações:** a desta fase é a próxima livre no momento da execução. A tabela do índice dos planos lista "V12 ou V13" como rótulo antigo; vale o número livre real (adendo, seção 7).

## 8. Glossário do que esta fase consome

| Nome | O que é | Origem |
|---|---|---|
| `AppContext.load` e os carregadores dos YAML | subida fail-closed; a parte que só lê arquivos é extraída para o validador | Planos 1 a 7 |
| `Database`, `TravaDeProcesso`, `BancoEmArquivo`, `testContextComBanco` | SQLite em arquivo, trava de processo e banco de teste | Fase 3B / adendo |
| `SyncStateStore`, `SyncMode`, `SyncKind`, `RunStatus`, `Agendador`, `montarAgendador` | estado e agendamento da sincronização | Plano 4A |
| `Retention.purge`, `RetencaoSettings` | retenção por categoria | Planos 4A e 11 |
| `AccessLog`, `AccessLogException` | registro de consultas | Plano 3 |
| `rotasPublicas`, `ROTAS_PUBLICAS`, `inventarioConhecido` | rotas públicas e inventário | Planos 1, 9 e 8 |
| `Sessao.kt`, `OidcSettings`, `SESSION_SECRET` | sessão de produção | Plano 8, T2 |
| `docs/implantacao.md`, pacote `installDist` | implantação como serviço | Planos 8 e 13 |
| `GeradorSintetico` | dados sintéticos para testes | Plano 8, T6 |
| `ci.yml`, `CiWorkflowTest` | CI sem Docker | Plano 9 |
| `CodigosDeMensagem`, `EstadoDaTela` | mensagens por código e estados de tela | Plano 10 |
| `EncoderJsonSeguro`, `MascaraDeLog`, `ConfiguradorDeLog`, `Prontidao`, `VerificacaoDeDisco`, `Metricas`, `AvaliadorDeAlertas`, `CodigoAlerta`, `SaidaDeAlertas`, `backup_run`, `ServicoDeBackup`, `VerificadorDeRestauracao`, `TarefaDeOperacao`, `ValidadorDeConfig` (novos) | entregas desta fase | esta fase |

## 9. Referência ao plano

[`docs/superpowers/plans/2026-10-25-plano-12-operacao-em-producao.md`](../plans/2026-10-25-plano-12-operacao-em-producao.md): 12 tarefas, em um PR a partir da `main` com o Plano 8 mesclado.
