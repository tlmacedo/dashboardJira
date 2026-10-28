# Runbook de operação diária

Para quem opera o painel sem depender de quem escreveu o código. Todo passo vem em **PowerShell** (Windows, principal) e **sh** (Linux), lado a lado. O nome do serviço nos exemplos é `painel-squads`; o nome real e a instalação do serviço são do Plano 13 (ajuste os comandos ao nome definido lá). Os scripts `app/scripts/painel-cli.ps1` e `app/scripts/painel-cli.sh` rodam os subcomandos de operação e localizam o pacote em `PAINEL_HOME`.

**Regra que não muda:** o acesso ao Jira só existe quando a flag `JIRA_LIVE_ENABLED`, no arquivo de variáveis, está com valor verdadeiro, e **só o usuário** muda essa flag, à mão. Nenhum passo deste runbook manda uma IA ligar a flag, ler o arquivo de variáveis ou acessar o Jira. Veja [Ligar e desligar o Jira](#ligar-e-desligar-o-jira).

Documentos irmãos: [configuração](configuracao.md), [observabilidade](observabilidade.md), [backup e restauração](backup-e-restauracao.md), [rotação de segredos](rotacao-de-segredos.md) e o [runbook de incidentes](runbook-incidentes.md).

## Rotina

| Quando | O que conferir |
|---|---|
| **Diária** | A faixa de alertas no topo do painel (gerente) e a tela Sincronização; as últimas linhas do arquivo de alertas (`PAINEL_ALERTAS_ARQUIVO`); `GET /api/health/pronto` responde `{"pronto":true}` |
| **Semanal** | As cópias na pasta de backup (uma por dia, a última `OK`); linhas `WARN` nos logs; as ausências da semana atualizadas em `ausencias.yaml` |
| **Mensal** | `verificar-restauracao` manual de conferência; espaço livre em disco (banco e pasta de backup) |
| **Anual** | Feriados do ano seguinte no calendário **até 31/10** (o alerta começa em 1º/11); validade do token (PAT) do Jira e `SYNC_PAT_VALIDADE_<INSTANCIA>` |

## Comandos do dia a dia

### COMANDO ver o estado do serviço

PowerShell:

```powershell
Get-Service painel-squads
```

sh:

```sh
systemctl status painel-squads
```

### COMANDO ver os logs

Os logs saem em JSON, uma linha por evento (veja [observabilidade](observabilidade.md)). Com `PAINEL_LOG_DIR` há o arquivo `painel.log`; sem ele, os logs vão para a saída do serviço.

PowerShell:

```powershell
Get-Content "$env:PAINEL_LOG_DIR\painel.log" -Tail 100 -Wait
```

sh:

```sh
journalctl -u painel-squads -n 100 -f
# ou, com PAINEL_LOG_DIR:
tail -n 100 -f "$PAINEL_LOG_DIR/painel.log"
```

### COMANDO achar uma requisição pelo id

Toda resposta traz o cabeçalho `X-Request-Id`; a linha de log da requisição e as linhas de erro dela têm o mesmo valor no campo `requisicao`. O log nunca tem usuário, query string nem chave de issue.

PowerShell:

```powershell
Select-String -Path "$env:PAINEL_LOG_DIR\painel.log" -Pattern '"requisicao":"abc-12345678"'
```

sh:

```sh
grep '"requisicao":"abc-12345678"' "$PAINEL_LOG_DIR/painel.log"
```

### COMANDO reiniciar o serviço

Toda mudança de configuração só vale depois de reiniciar (não há recarga a quente). Reiniciar também retoma a sincronização suspensa.

PowerShell:

```powershell
Restart-Service painel-squads
```

sh:

```sh
sudo systemctl restart painel-squads
```

### COMANDO conferir a prontidão

`/api/health` só diz que o processo responde. `/api/health/pronto` confere banco, migrações, disco e trava; responde 200 `{"pronto":true}` ou 503 `{"pronto":false}` sem motivo; o motivo está no log (`Prontidão: não pronto (<codigo>)`) e no campo `naoPronto` de `/api/operacao/alertas` (gerente). Códigos: (`BANCO_INDISPONIVEL`, `MIGRACAO_PENDENTE`, `DISCO_BAIXO`, `TRAVA_PERDIDA`).

PowerShell:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health/pronto
```

sh:

```sh
curl -fsS http://127.0.0.1:8080/api/health/pronto
```

### COMANDO validar a configuração antes de reiniciar

PowerShell:

```powershell
.\app\scripts\painel-cli.ps1 validar-config
```

sh:

```sh
sh app/scripts/painel-cli.sh validar-config
```

Só reinicie com `VALIDACAO OK`. Os códigos de problema estão em [configuração](configuracao.md).

### COMANDO fazer um backup na hora

PowerShell:

```powershell
.\app\scripts\painel-cli.ps1 backup-agora
```

sh:

```sh
sh app/scripts/painel-cli.sh backup-agora
```

Funciona com o serviço rodando. Saída: `BACKUP OK <nome> <tamanho>` ou `BACKUP FALHOU <codigo>`.

### COMANDO verificar a restauração

Restaura a cópia mais recente numa pasta temporária e confere (não toca o banco vivo).

PowerShell:

```powershell
.\app\scripts\painel-cli.ps1 verificar-restauracao
```

sh:

```sh
sh app/scripts/painel-cli.sh verificar-restauracao
```

### COMANDO ler o arquivo de alertas

PowerShell:

```powershell
Get-Content $env:PAINEL_ALERTAS_ARQUIVO -Tail 50
```

sh:

```sh
tail -n 50 "$PAINEL_ALERTAS_ARQUIVO"
```

Cada linha é `<data e hora de Manaus> ATIVO|RESOLVIDO <GRAVIDADE> <CODIGO> [instância]`.

### COMANDO conferir o espaço em disco

PowerShell:

```powershell
Get-PSDrive -PSProvider FileSystem
```

sh:

```sh
df -h
```

## Alertas

A faixa no topo do painel (só para o gerente), a lista na tela Sincronização, `GET /api/operacao/alertas`, a métrica `painel_alerta_ativo`, uma linha `WARN` no log (`ALERTA <CODIGO> ATIVO|RESOLVIDO`) e o arquivo de alertas mostram os mesmos 14 alertas, uma vez por mudança de estado. Cada item abaixo diz o que significa, como confirmar, o que fazer e quando escalar. **Escalar** é avisar o gerente do painel; em qualquer alerta que envolva dado pessoal, o DPO também.

### ALERTA SYNC_SUSPENSA

**O que significa:** a sincronização de uma instância parou sozinha (401, 403, 429 ou três falhas seguidas) e fica parada até reiniciar. O painel passa a mostrar números velhos.
**Como confirmar:** tela Sincronização ou `GET /api/sync/status`: o código da suspensão (`HTTP_401`, `HTTP_403`, `HTTP_429`, `FALHAS_SEGUIDAS`).
**O que fazer:** `HTTP_401` ou `HTTP_403`: token inválido ou sem permissão; **o usuário** renova o token no Jira (ver [rotação de segredos](rotacao-de-segredos.md)). `HTTP_429`: o Jira pediu para esperar; reinicie fora do horário de pico. `FALHAS_SEGUIDAS`: veja o código das últimas execuções. Reiniciar o serviço retoma a sincronização.
**Quando escalar:** se a suspensão voltar depois do reinício, ou se durar mais de um dia útil.

### ALERTA SYNC_ATRASADA

**O que significa:** a flag do Jira está ligada e o último incremental `OK` foi há mais de 3 intervalos (`SYNC_INTERVALO_MIN`).
**Como confirmar:** tela Sincronização: a hora do último incremental `OK` e o cursor.
**O que fazer:** olhar as execuções recentes (código de erro). Se há `TETO_ITENS`, `ORCAMENTO_REQUISICOES` ou `TEMPO_ESGOTADO`, a execução parou no limite e continua na seguinte. Se nada executa, ver o serviço e reiniciar.
**Quando escalar:** atraso de mais de 2 horas em dia útil.

### ALERTA RECONCILIACAO_SUSPEITA

**O que significa:** a reconciliação diária abortou porque removeria uma fração grande da base (proteção contra apagar dados por engano); **nada foi removido**.
**Como confirmar:** tela Sincronização: a última reconciliação com o código `RECONCILIACAO_SUSPEITA`.
**O que fazer:** não force nada. Confira se o filtro da ficha de sincronização mudou ou se houve mudança grande no Jira. Veja o [runbook da sincronização](../jira/runbook-sincronizacao.md).
**Quando escalar:** sempre, ao gerente; é possível erro de configuração da ficha.

### ALERTA RECONCILIACAO_ATRASADA

**O que significa:** a flag do Jira está ligada e a última reconciliação `OK` foi há mais de 26 horas.
**Como confirmar:** tela Sincronização: a data da última reconciliação.
**O que fazer:** ver o código da última tentativa; se foi suspensa, tratar `SYNC_SUSPENSA`; senão reiniciar e acompanhar a próxima janela (`SYNC_HORA_RECONCILIACAO`).
**Quando escalar:** se passar de dois dias.

### ALERTA RETENCAO_FALHOU

**O que significa:** a limpeza diária por retenção falhou ou não roda há mais de 26 horas.
**Como confirmar:** tela Sincronização: o cartão da retenção.
**O que fazer:** olhar o espaço em disco e o log (`Retenção falhou`). Reiniciar. A retenção só apaga dados locais antigos.
**Quando escalar:** se falhar por mais de 3 dias (o banco só cresce).

### ALERTA ACCESS_LOG_FALHANDO

**O que significa:** o registro de acesso (auditoria de quem consultou o quê) falhou ao gravar na última hora. Sem registro, as rotas de dados respondem 503 (o painel não entrega dado sem auditar).
**Como confirmar:** log com `Falha ao gravar access_log`; `GET /api/health/pronto`.
**O que fazer:** conferir disco cheio e permissão do arquivo do banco; reiniciar.
**Quando escalar:** imediatamente ao gerente e ao DPO se durar mais de alguns minutos.

### ALERTA PAT_EXPIRANDO

**O que significa:** o token do Jira informado em `SYNC_PAT_VALIDADE_<INSTANCIA>` vence em 14 dias ou menos (só com a flag ligada).
**Como confirmar:** a data no arquivo de variáveis (o painel nunca consulta o Jira para saber).
**O que fazer:** **o usuário** cria o token novo no Jira e atualiza a variável e a data (ver [rotação de segredos](rotacao-de-segredos.md)).
**Quando escalar:** quando faltarem menos de 5 dias.

### ALERTA PAT_EXPIRADO

**O que significa:** a data informada já passou; a sincronização vai falhar com 401.
**Como confirmar:** a data em `SYNC_PAT_VALIDADE_<INSTANCIA>` e a tela Sincronização.
**O que fazer:** igual a `PAT_EXPIRANDO`, com urgência.
**Quando escalar:** na hora, ao usuário que renova o token.

### ALERTA CALENDARIO_SEM_PROXIMO_ANO

**O que significa:** a partir de 1º de novembro, o calendário não tem feriados móveis do ano seguinte; os dias úteis do ano seguinte ficam errados.
**Como confirmar:** `validar-config` acusa `CALENDARIO_SEM_ANO`.
**O que fazer:** ver [Feriados do ano seguinte](#feriados-do-ano-seguinte).
**Quando escalar:** se chegar a dezembro sem o calendário atualizado.

### ALERTA DISCO_BAIXO

**O que significa:** o espaço livre na pasta do banco está abaixo de `PAINEL_DISCO_MIN_MB`. O painel também deixa de estar pronto (`DISCO_BAIXO` em `/api/health/pronto`).
**Como confirmar:** "conferir o espaço em disco" (acima).
**O que fazer:** liberar espaço: logs antigos (`painel.log.*`), cópias de backup excedentes que a infraestrutura já guardou em outro lugar. **Nunca apague `painel.db`, `painel.db-wal` nem `painel.db-shm`.** Se a retenção do banco é a causa, avise o gerente: reduzir `RETENTION_MONTHS` é decisão dele.
**Quando escalar:** se o espaço não voltar ao normal no mesmo dia.

### ALERTA BANCO_SUSPEITO

**O que significa:** a última cópia do banco vivo **não passou na conferência** (`INTEGRIDADE_FALHOU`): o arquivo do banco pode estar corrompido.
**Como confirmar:** `backup-agora` de novo; o log do backup; a tela de alertas.
**O que fazer:** vá direto ao incidente `BANCO_PERDIDO_OU_CORROMPIDO` no [runbook de incidentes](runbook-incidentes.md). Não apague nada.
**Quando escalar:** na hora, ao gerente.

### ALERTA BACKUP_ATRASADO

**O que significa:** em produção, nenhum backup `OK` há mais de 26 horas (ou `BACKUP_DIR` não está definido).
**Como confirmar:** o arquivo mais recente da pasta de backup; o código da última falha no log (`Backup FALHOU: <codigo>`).
**O que fazer:** rodar "fazer um backup na hora"; conforme o código: `PASTA_SEM_ESCRITA` (permissão ou volume fora), `DISCO_INSUFICIENTE` (liberar espaço), `INTEGRIDADE_FALHOU` (ver `BANCO_SUSPEITO`). Sem `BACKUP_DIR`, defina-o (`validar-config` acusa `BACKUP_DIR_AUSENTE`).
**Quando escalar:** se passar de dois dias sem backup.

### ALERTA RESTAURACAO_NAO_TESTADA

**O que significa:** em produção, nenhuma restauração `OK` há mais de 3 dias (a verificação roda depois de cada backup). Crítico quando a última verificação falhou.
**Como confirmar:** "verificar a restauração" (acima).
**O que fazer:** rodar o comando; se der `COPIA_ALTERADA` ou `INTEGRIDADE_FALHOU`, a cópia não serve: faça um backup novo e verifique. Se o backup novo também falha, é `BANCO_SUSPEITO`.
**Quando escalar:** se a verificação falhar duas vezes seguidas com cópias diferentes.

### ALERTA MODO_DEV_COM_JIRA_LIGADO

**O que significa:** o painel está em modo desenvolvimento (**sem login**) com o Jira ligado: dados reais ficariam expostos sem autenticação.
**Como confirmar:** a faixa vermelha do modo desenvolvimento e `GET /api/meta` (`devMode: true`).
**O que fazer:** desligar o modo desenvolvimento e reiniciar na hora (veja o incidente `MODO_DEV_EM_PRODUCAO` no [runbook de incidentes](runbook-incidentes.md)).
**Quando escalar:** imediatamente: trate como possível exposição.

## Entrada e saída de pessoas

Edite `papeis.yaml` e `equipes.yaml` **encerrando a vigência** (`ate`) em vez de apagar a linha: a auditoria usa a vigência na data. Valide (`validar-config`) e reinicie. Pessoa nova: `id` = username do Jira, em minúsculas. Veja [configuração](configuracao.md).

## Ausências

Atualização semanal de `ausencias.yaml` (sem motivo: só quem, de quando até quando e a fração do dia). Valide e reinicie.

## Feriados do ano seguinte

Acrescente ao `calendario.yaml` os feriados móveis do ano seguinte **até 31/10** (o alerta começa em 1º/11). Os feriados municipais de Manaus precisam ser confirmados a cada ano. Valide e reinicie.

## Pedido do titular

- **Consulta:** a pessoa usa a tela Meus dados (Plano 11).
- **Eliminação:** pelo canal do DPO. Procedimento técnico: o DPO define o que pode ser apagado e o que fica por obrigação; o registro do pedido e da decisão fica **fora** do painel; o apagamento no banco é feito com o serviço parado e depois de um backup. **As cópias de backup antigas ainda contêm o dado até saírem da retenção** (`BACKUP_RETENCAO_COPIAS`): registre isso no pedido.

## Atualizar e reverter versão

Detalhes do pacote e do serviço em `docs/implantacao.md` (Planos 8 e 13). Passos:

1. Faça um backup na hora e confirme `BACKUP OK`.
2. Pare o serviço, troque o pacote, valide a configuração e suba.
3. Confira `GET /api/health/pronto`.

Reverter é voltar o pacote anterior. Se a versão nova trouxe uma migração incompatível com o pacote anterior, reverter é **restaurar o backup tirado no passo 1** (veja [backup e restauração](backup-e-restauracao.md)); nunca se edita uma migração.

## Ligar e desligar o Jira

Só o usuário muda a flag `JIRA_LIVE_ENABLED`, à mão, no arquivo de variáveis (valor falso ou ausente: nenhum acesso ao Jira por ninguém; valor verdadeiro: liberado). Depois de mudar, reinicie. O primeiro uso real segue `docs/jira/checklist-primeira-conexao.md` e o [runbook da sincronização](../jira/runbook-sincronizacao.md).
