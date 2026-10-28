# Backup e restauração do banco

O banco do painel é um único arquivo SQLite (`DATABASE_PATH`, padrão `data/painel.db`, com `-wal` e `-shm` ao lado enquanto o painel roda). Sem cópia testada, uma queda de disco ou um arquivo corrompido apaga o histórico de decisões e o registro de acesso. Esta página diz o que o painel já faz sozinho, como pedir uma cópia na hora e como restaurar.

## 1. O que o painel faz sozinho

- **Uma cópia por dia**, às 02:00 de Manaus (`BACKUP_HORA`), pelo próprio agendador do painel (não precisa de cron nem do Agendador de Tarefas do sistema). Só existe se `BACKUP_DIR` estiver definido.
- A cópia usa `VACUUM INTO` (leitura consistente com o painel rodando; não segura nenhuma gravação). Ela vai para um arquivo `.parcial`, é **conferida** (`integrity_check`, `foreign_key_check` e versão do esquema iguais às do banco vivo), recebe o SHA-256, vira `painel-aaaa-MM-dd-HHmmss.db` por renomeação atômica e é registrada na tabela `backup_run` (só o nome, o tamanho e o SHA-256; nunca o caminho). Cópia que não passa na conferência **nunca** vira `.db` nem `OK`, e nenhum `.parcial` fica na pasta.
- **Retenção:** ficam as `BACKUP_RETENCAO_COPIAS` cópias mais recentes (padrão 14). Só arquivos com o nome exato `painel-aaaa-MM-dd-HHmmss.db` são candidatos; qualquer outro arquivo da pasta é ignorado, e a cópia `OK` mais recente conhecida nunca é apagada.
- Depois de cada cópia `OK`, a **restauração dela é testada** em uma pasta temporária (ver a seção 4).
- Se algo falhar, os alertas `BACKUP_ATRASADO`, `BANCO_SUSPEITO` e `RESTAURACAO_NAO_TESTADA` avisam (veja o [runbook diário](runbook-diario.md)).

Variáveis (todas no arquivo de variáveis do serviço; nenhuma liga o Jira):

| Variável | Padrão | O que faz |
|---|---|---|
| `BACKUP_DIR` | vazio (backup desligado) | Pasta das cópias. **Obrigatória em produção.** Fora do repositório, fora da pasta do pacote e fora da pasta do banco |
| `BACKUP_HORA` | `02:00` | Hora do backup diário (HH:mm, fuso do painel) |
| `BACKUP_RETENCAO_COPIAS` | `14` | Quantas cópias manter (1 a 365) |

`painel validar-config` confere a pasta: `BACKUP_DIR_AUSENTE` (produção), `BACKUP_DIR_DENTRO_DO_REPOSITORIO`, `BACKUP_DIR_NA_PASTA_DO_BANCO` (perder o disco perderia banco e cópias juntos) e `BACKUP_DIR_SEM_ESCRITA`.

## 2. A cópia contém dados pessoais

As cópias têm tudo o que o banco tem, inclusive o registro de acesso. Trate a pasta como o banco:

- permissão só para a conta do serviço (e para quem administra);
- fora do repositório e fora da pasta do pacote, nunca versionada;
- o painel **não cifra** o arquivo: a cifragem em repouso é do volume (BitLocker no Windows, LUKS no Linux);
- uma cópia antiga continua tendo o dado até sair da retenção (relevante para pedidos de eliminação, ver o runbook diário).

Proteger a pasta:

PowerShell (Windows; troque `CONTA_DO_SERVICO` pela conta que roda o serviço):

```powershell
New-Item -ItemType Directory -Force D:\painel-copias | Out-Null
icacls D:\painel-copias /inheritance:r /grant:r "CONTA_DO_SERVICO:(OI)(CI)F" "Administrators:(OI)(CI)F"
icacls D:\painel-copias        # conferir: só as duas contas
```

sh (Linux; troque `painel` pela conta do serviço):

```sh
sudo install -d -m 700 -o painel -g painel /var/backups/painel
ls -ld /var/backups/painel     # conferir: drwx------
```

Onde o sistema suporta (Linux), o próprio painel grava cada cópia com permissão `600`. No Windows vale a permissão da pasta.

## 3. Uma cópia na hora

Funciona com o serviço rodando (ele só lê o banco), antes de atualizar a versão ou depois de restaurar.

PowerShell:

```powershell
.\app\scripts\painel-cli.ps1 backup-agora
# BACKUP OK painel-2026-10-30-143012.db 18874368      (código de saída 0)
# BACKUP FALHOU DISCO_INSUFICIENTE                    (código de saída 1)
```

sh:

```sh
sh app/scripts/painel-cli.sh backup-agora
```

Códigos de falha: `PASTA_SEM_ESCRITA` (a pasta não existe ou não aceita escrita), `DISCO_INSUFICIENTE` (menos de 1,2 vez o tamanho do banco livre), `COPIA_FALHOU`, `INTEGRIDADE_FALHOU` (a cópia não passou na conferência: **trate como banco suspeito**, vá ao incidente `BANCO_PERDIDO_OU_CORROMPIDO` no [runbook de incidentes](runbook-incidentes.md)), `ERRO_INTERNO`, `BANCO_NAO_ENCONTRADO`. Sem `BACKUP_DIR` o comando explica e sai com 2.

Cada tentativa fica em `backup_run`. A linha de um backup é gravada **depois** da cópia, então a própria cópia não a contém: ao restaurar, o registro volta ao estado daquela hora. Por isso, logo depois de restaurar, rode `backup-agora` de novo.

## 4. Como a restauração é testada

Depois de cada backup `OK` (e quando você pedir), o painel **restaura a cópia numa pasta temporária** e confere: o SHA-256 do arquivo contra o registrado (`COPIA_ALTERADA` se mudou depois), `integrity_check` (`INTEGRIDADE_FALHOU`), migrações (`MIGRACAO_PENDENTE`: cópia de uma versão antiga do esquema) e, tabela a tabela, a contagem de linhas e um SHA-256 do conteúdo contra a cópia (`CONTAGEM_DIVERGENTE`). O temporário é apagado em qualquer desfecho, e o banco vivo não é tocado. O resultado fica em `backup_run` como `RESTAURACAO OK|FALHA`. Se nenhuma restauração `OK` acontecer em 3 dias, o alerta `RESTAURACAO_NAO_TESTADA` avisa.

Conferir uma cópia à mão (a mais recente de `BACKUP_DIR`, ou a indicada):

PowerShell:

```powershell
.\app\scripts\painel-cli.ps1 verificar-restauracao
.\app\scripts\painel-cli.ps1 verificar-restauracao D:\painel-copias\painel-2026-10-30-020000.db
# RESTAURACAO OK 11 tabelas
# issue 500
# change_event 2500
# ...
# RESTAURACAO FALHOU INTEGRIDADE_FALHOU     (código de saída 1)
```

sh:

```sh
sh app/scripts/painel-cli.sh verificar-restauracao
sh app/scripts/painel-cli.sh verificar-restauracao /var/backups/painel/painel-2026-10-30-020000.db
```

A saída só tem nomes de tabela e números (e, ao lado, a contagem do banco vivo quando ela difere: só informativo, porque o painel continua gravando). O comando funciona **sem** o banco vivo, para a recuperação de um desastre.

## 5. Restaurar uma cópia

Só com o serviço **parado**. Nunca restaure por cima de um banco com arquivos `-wal` e `-shm` antigos: o resultado fica corrompido. Os três arquivos (`painel.db`, `painel.db-wal` e `painel.db-shm`) saem de lá **juntos**, para uma pasta de quarentena fora do repositório; **não se apaga nada**. O passo a passo completo, com a escolha da cópia, está no incidente `BANCO_PERDIDO_OU_CORROMPIDO` do [runbook de incidentes](runbook-incidentes.md). Resumo:

PowerShell:

```powershell
Stop-Service painel-squads
New-Item -ItemType Directory -Force D:\painel-quarentena | Out-Null
Move-Item D:\painel\data\painel.db, D:\painel\data\painel.db-wal, D:\painel\data\painel.db-shm D:\painel-quarentena -ErrorAction SilentlyContinue
.\app\scripts\painel-cli.ps1 verificar-restauracao D:\painel-copias\painel-2026-10-30-020000.db    # precisa dar RESTAURACAO OK
Copy-Item D:\painel-copias\painel-2026-10-30-020000.db D:\painel\data\painel.db                    # só o .db, sem -wal nem -shm
icacls D:\painel\data\painel.db /inheritance:r /grant:r "CONTA_DO_SERVICO:F" "Administrators:F"
Start-Service painel-squads
Invoke-RestMethod http://127.0.0.1:8080/api/health/pronto
.\app\scripts\painel-cli.ps1 backup-agora      # novo ponto de partida (o backup_run voltou ao estado da cópia)
```

sh:

```sh
sudo systemctl stop painel-squads
mkdir -p /var/quarentena-painel
mv /srv/painel/data/painel.db /srv/painel/data/painel.db-wal /srv/painel/data/painel.db-shm /var/quarentena-painel/ 2>/dev/null
sh app/scripts/painel-cli.sh verificar-restauracao /var/backups/painel/painel-2026-10-30-020000.db   # precisa dar RESTAURACAO OK
sudo install -m 600 -o painel -g painel /var/backups/painel/painel-2026-10-30-020000.db /srv/painel/data/painel.db
sudo systemctl start painel-squads
curl -fsS http://127.0.0.1:8080/api/health/pronto
sh app/scripts/painel-cli.sh backup-agora
```

O que se perde entre o backup e a falha: as decisões e o registro de acesso feitos depois da cópia (registre isso no incidente); com a flag do Jira ligada **pelo usuário**, a sincronização repõe o que vem do Jira. Se o pacote em uso é mais novo que a cópia, a subida aplica as migrações que faltam; se for mais antigo, troque o pacote antes.

## Depois da flag / com o usuário

Itens com efeito fora do repositório; o painel não decide nenhum deles (ponto de parada 2 da spec, opção 1 adotada como recomendada):

- **Volume separado do banco:** escolher e preparar o volume (outro disco ou partição) para `BACKUP_DIR`. Até lá, perder o disco do servidor significa perder banco e cópias juntos.
- **Cifragem em repouso do volume:** ativar BitLocker (Windows) ou LUKS (Linux) no volume do banco e no das cópias.
- **Cópia para fora do servidor:** pedir à infraestrutura que copie a pasta de backup todo dia para outro lugar (o painel não guarda credencial de armazenamento nem chave). Definir quem confere que a cópia chegou.
- **Pasta definitiva e permissões** no servidor real (Plano 13).
