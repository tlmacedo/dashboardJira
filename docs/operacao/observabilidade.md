# Observabilidade: logs, prontidão, métricas e alertas

O painel diz sozinho como está, sem depender de ferramenta externa. Esta página descreve o que ele escreve e onde ler. Nenhuma saída leva token, senha, cookie, cabeçalho `Authorization`, query string, chave de issue, username, caminho de arquivo ou texto livre. O único registro de **quem consultou o quê** é a tabela `access_log` (tela Registro de acesso).

## 1. Logs

**Formato.** Uma linha JSON por evento, em UTF-8. Padrão `JSON`; `PAINEL_LOG_FORMATO=TEXTO` dá um formato legível para uso local (também com máscara de segredos).

| Campo | Conteúdo |
|---|---|
| `ts` | instante em UTC (ISO-8601) |
| `nivel` | `INFO`, `WARN`, `ERROR` |
| `logger` | classe que escreveu |
| `msg` | a mensagem, com segredos trocados por `***` |
| `requisicao` | id da requisição (só nos eventos de uma requisição) |
| `exc` | quando há exceção: `classe`, `msg` (mascarada) e até 20 quadros da pilha |

Quebras de linha e aspas dentro da mensagem viram escapes JSON: um texto malicioso não consegue forjar uma segunda entrada.

**Uma linha por requisição da API**, no logger `br.sidia.painel.requisicao`: `metodo=GET rota=/api/tarefa/{key} status=200 duracaoMs=12`. A `rota` é o **modelo** da rota (nunca o caminho real, que poderia ter a chave da issue); sem query string, usuário, cabeçalhos nem corpo. `/api/health*` e os arquivos do frontend não geram linha.

**Id da requisição.** Aceita o `X-Request-Id` enviado pelo proxy só se casar `^[A-Za-z0-9-]{8,64}$`; senão o painel gera um UUID. O id volta no cabeçalho da resposta e vai no campo `requisicao` de todo evento da requisição.

**Onde ficam.** Sempre na saída do processo/serviço. Com `PAINEL_LOG_DIR`, também no arquivo `painel.log` dessa pasta, com rotação (20 MB por arquivo, 14 dias, teto total de 500 MB). A pasta e o formato vêm do arquivo de variáveis, que o painel só lê depois de iniciar; por isso o `ConfiguradorDeLog` reaplica a configuração no começo da subida (as primeiras linhas saem no formato padrão). Se a pasta de log não puder ser criada, o painel avisa na saída padrão e continua só com ela.

**Filtrar por requisição** (também no [runbook diário](runbook-diario.md)):

PowerShell:

```powershell
Select-String -Path "$env:PAINEL_LOG_DIR\painel.log" -Pattern '"requisicao":"abc-12345678"'
```

sh:

```sh
grep '"requisicao":"abc-12345678"' "$PAINEL_LOG_DIR/painel.log"
```

## 2. Prontidão e vivacidade

- `GET /api/health`: **vivacidade**, o processo responde (200 `{"ok":true}`).
- `GET /api/health/pronto`: **prontidão**, pública e mínima. 200 `{"pronto":true}` ou 503 `{"pronto":false}`, **sem o motivo** (o endpoint é público). O motivo é um destes códigos, que vão para o log (`Prontidão: não pronto (<codigo>)`) e para o campo `naoPronto` de `GET /api/operacao/alertas` (só gerente): `BANCO_INDISPONIVEL` (arquivo do banco ausente, sem permissão ou ilegível), `MIGRACAO_PENDENTE`, `DISCO_BAIXO` (abaixo de `PAINEL_DISCO_MIN_MB`) e `TRAVA_PERDIDA` (a trava de processo do banco não é mais do painel). Nunca revela versão, host nem caminho. Responde em até 3 s mesmo com o banco sumido ou o pool de conexões esgotado (a sonda usa conexão própria) e guarda o resultado por 5 s.

## 3. Métricas (porta interna)

Texto Prometheus (formato 0.0.4) em `GET /metrics` na **porta interna** `PAINEL_METRICAS_PORTA` (padrão `9464`, em `PAINEL_METRICAS_HOST`, padrão `127.0.0.1`; `0` desliga). **Nunca** na porta pública `8080` (lá `/metrics` responde 404). Um coletor em outro host só enxerga a porta se o operador mudar o host **e** liberar a porta no firewall só para o coletor; isso é decisão da implantação (Plano 13).

| Métrica | Tipo | Rótulos |
|---|---|---|
| `painel_http_requisicoes_total`, `painel_http_duracao_segundos` | contador, histograma | `rota` (modelo), `metodo`, `status` |
| `painel_sync_execucoes_total` | contador | `instancia`, `tipo`, `status` |
| `painel_sync_ultima_ok_timestamp_segundos` | gauge | `instancia`, `tipo` |
| `painel_sync_suspensa` | gauge (0/1) | `instancia` |
| `painel_sync_cursor_idade_segundos` | gauge | `instancia` |
| `painel_access_log_falhas_total` | contador | |
| `painel_retencao_removidas_total` | contador | `categoria` (nome da tabela) |
| `painel_alerta_ativo` | gauge (0/1) | `codigo` |
| `painel_backup_execucoes_total` | contador | `status` |
| `painel_backup_ultimo_ok_timestamp_segundos`, `painel_backup_tamanho_bytes` | gauge | |
| `painel_restauracao_execucoes_total` | contador | `status` |
| `painel_disco_livre_bytes` | gauge | `volume` (`banco` ou `backup`) |
| `jvm_*`, `process_*`, `system_*`, `hikaricp_*` | os binders padrão do Micrometer | os do binder |

Os rótulos são de listas fechadas: instâncias conhecidas, modelo de rota, volume `banco`/`backup`, código de alerta. Nunca há pessoa, chave de issue, squad, JQL, URL nem caminho.

## 4. Alertas

Catorze alertas operacionais (lista e procedimento de cada um no [runbook diário](runbook-diario.md)) são avaliados a cada 5 minutos dentro do painel, só com o estado que ele já tem, **sem nenhuma requisição** (nem ao Jira). Cada mudança de estado, e só ela, aparece em cinco lugares:

1. a rota `GET /api/operacao/alertas` (só gerente, toda chamada fica no registro de acesso) e a faixa no topo do painel;
2. a métrica `painel_alerta_ativo{codigo}` (1 ativo, 0 resolvido);
3. uma linha `WARN` no log: `ALERTA <CODIGO> ATIVO` ou `ALERTA <CODIGO> RESOLVIDO`;
4. o **arquivo de alertas** (`PAINEL_ALERTAS_ARQUIVO`, texto, sem dado pessoal): `2026-10-30T08:00:00-04:00 ATIVO CRITICO SYNC_SUSPENSA samsung`; passando de 5 MB vira `<arquivo>.1`;
5. nada mais: o painel **não envia** e-mail, chat nem webhook. Quem monitora o servidor pode ler o arquivo de alertas ou o log.

## 5. Regras opcionais do Prometheus

Para quem já tiver Prometheus/Alertmanager existe `ops/prometheus/alertas.yml` (grupo `painel`: `PainelAlertaCritico`, `PainelAlertaAtencao` e `PainelForaDoAr`; a sonda `PainelNaoPronto` vem comentada). O painel não lê esse arquivo nem depende dele.

## Depois da flag / com o usuário

- Decidir se haverá coleta das métricas (Prometheus) e, se sim, em que host e com que regra de firewall (Plano 13).
- Decidir se algum canal externo de alertas (chat, e-mail) é desejado: exigiria uma saída nova (`SaidaDeAlertas`) e uma requisição de saída para um host da empresa, a autorizar com a rede/TI.
- Definir quem lê o arquivo de alertas e com que frequência.
