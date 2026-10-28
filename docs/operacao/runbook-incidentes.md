# Runbook de incidentes

O que fazer quando algo dá errado, na ordem **conter, investigar, corrigir, comunicar, aprender**. Todo playbook tem o passo a passo em **PowerShell** e **sh**. O nome do serviço nos exemplos é `painel-squads` (o real é do Plano 13). Para o dia a dia e os alertas, veja o [runbook diário](runbook-diario.md); para cópias e restauração, [backup e restauração](backup-e-restauracao.md).

**Regra que não muda:** a flag `JIRA_LIVE_ENABLED` é a única trava do acesso ao Jira e **só o usuário** a muda, à mão, no arquivo de variáveis. Nenhum playbook manda uma IA mexer na flag, ler o arquivo de variáveis, gerar ou ver token, nem acessar o Jira. Quando um passo fala em "desligar o Jira", é o usuário quem o faz.

## Severidades

| Nível | Quando | Quem decide |
|---|---|---|
| **S1** | Dados pessoais expostos, ou o painel fora do ar em horário de uso | o gerente do painel, com o **DPO** em todo incidente que envolva dado pessoal |
| **S2** | Números errados, ou sincronização parada há mais de 1 dia | o gerente |
| **S3** | O resto (aviso de atenção, degradação sem efeito para quem usa) | quem opera, registrando no fim |

O DPO é avisado em **todo** incidente com dado pessoal, qualquer que seja a severidade.

## Registro

Abra um registro por incidente (fora do painel, num lugar com acesso restrito). Modelo, **sem copiar dado pessoal** (nada de nome, username, e-mail ou conteúdo de linha do banco):

```text
Incidente:      <NOME do playbook>            Severidade: <S1|S2|S3>
Quando:         <início> até <fim> (hora de Manaus)
O que:          <o que se viu, em uma frase>
Quem foi avisado: <gerente, DPO, usuário do Jira...> em <hora>
Ações:          <hora> <o que foi feito>
Perdas:         <o que se perdeu e o período, se houver>
Aprendizado:    <o que muda para não repetir>
```

## Playbooks

### INCIDENTE PAINEL_FORA_DO_AR

O painel não responde, ou `GET /api/health/pronto` devolve 503 com um dos códigos `BANCO_INDISPONIVEL`, `MIGRACAO_PENDENTE`, `DISCO_BAIXO` ou `TRAVA_PERDIDA`.

**Conter:** confirme o código e o estado do serviço. Se for horário de uso, é S1: avise o gerente.

PowerShell:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health/pronto
Get-Service painel-squads
```

sh:

```sh
curl -sS -w '\n%{http_code}\n' http://127.0.0.1:8080/api/health/pronto
systemctl status painel-squads
```

**Investigar:** leia as últimas linhas do log (`Get-Content "$env:PAINEL_LOG_DIR\painel.log" -Tail 100` / `tail -n 100 "$PAINEL_LOG_DIR/painel.log"`); para uma requisição específica, use o `X-Request-Id` (campo `requisicao`). Por código: `BANCO_INDISPONIVEL` (o arquivo do banco sumiu, está sem permissão ou ilegível: **não apague nada**, vá ao incidente `BANCO_PERDIDO_OU_CORROMPIDO` se o arquivo não voltar), `MIGRACAO_PENDENTE` (falta aplicar uma migração: a subida a aplica; confira se o pacote é compatível com o banco), `DISCO_BAIXO` (falta espaço), `TRAVA_PERDIDA` (a trava do banco não é mais do painel).

**Corrigir:**

- **Reiniciar o serviço** resolve `TRAVA_PERDIDA` e `MIGRACAO_PENDENTE` na maioria dos casos:
  - PowerShell: `Restart-Service painel-squads`
  - sh: `sudo systemctl restart painel-squads`
- **Disco cheio** (`DISCO_BAIXO`): libere espaço em logs antigos (`painel.log.*`) e em cópias de backup excedentes que a infraestrutura já guardou em outro lugar. Nunca apague `painel.db`, `painel.db-wal` nem `painel.db-shm`.
- **Trava `painel.db.lock` órfã.** O arquivo `painel.db.lock` pode ficar depois de uma queda e isso é normal: a trava de verdade é do sistema operacional e some com o processo. Se a subida for recusada com "já existe outro painel", confira **primeiro** se existe mesmo outro processo; pare o serviço duplicado. Só com **nenhum** processo do painel vivo e a recusa persistindo, apague o arquivo `painel.db.lock` e suba de novo. Nunca apague `painel.db`, `painel.db-wal` nem `painel.db-shm` como remédio.

  PowerShell:

  ```powershell
  Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Select-Object ProcessId, CommandLine
  Stop-Service painel-squads
  # só se nenhum processo do painel continuar vivo:
  Remove-Item D:\painel\data\painel.db.lock
  Start-Service painel-squads
  ```

  sh:

  ```sh
  pgrep -af painel
  sudo systemctl stop painel-squads
  # só se nenhum processo do painel continuar vivo:
  rm /srv/painel/data/painel.db.lock
  sudo systemctl start painel-squads
  ```

**Comunicar:** avise quem usa o painel e o gerente (S1 se foi em horário de uso). Sem dado pessoal exposto, o DPO não precisa ser acionado.

**Aprender:** registre a causa e o tempo fora do ar; se foi disco, ajuste `PAINEL_DISCO_MIN_MB` ou a retenção; se foi queda, confira os alertas que deveriam ter avisado antes.

### INCIDENTE BANCO_PERDIDO_OU_CORROMPIDO

O arquivo do banco sumiu, não abre, ou o alerta `BANCO_SUSPEITO` indica que a cópia do banco vivo não passou na conferência.

**Conter:** pare o serviço. **Não apague nada.** Mova `painel.db`, `painel.db-wal` e `painel.db-shm` (os três, juntos) para uma pasta de quarentena fora do repositório: eles ficam como evidência e, se for o caso, para uma recuperação manual.

PowerShell:

```powershell
Stop-Service painel-squads
New-Item -ItemType Directory -Force D:\painel-quarentena | Out-Null
Move-Item D:\painel\data\painel.db, D:\painel\data\painel.db-wal, D:\painel\data\painel.db-shm D:\painel-quarentena -ErrorAction SilentlyContinue
```

sh:

```sh
sudo systemctl stop painel-squads
mkdir -p /var/quarentena-painel
mv /srv/painel/data/painel.db /srv/painel/data/painel.db-wal /srv/painel/data/painel.db-shm /var/quarentena-painel/ 2>/dev/null
```

**Investigar:** escolha a cópia íntegra mais recente. Rode `verificar-restauracao` nas cópias mais recentes, da mais nova para a mais velha, até uma dar `RESTAURACAO OK`.

PowerShell:

```powershell
Get-ChildItem D:\painel-copias\painel-*.db | Sort-Object Name -Descending | Select-Object -First 5 Name
.\app\scripts\painel-cli.ps1 verificar-restauracao D:\painel-copias\painel-2026-10-30-020000.db
```

sh:

```sh
ls -1 /var/backups/painel/painel-*.db | sort -r | head -5
sh app/scripts/painel-cli.sh verificar-restauracao /var/backups/painel/painel-2026-10-30-020000.db
```

**Corrigir:** copie a cópia escolhida para o caminho de `DATABASE_PATH` com o nome `painel.db`, **sem** `painel.db-wal` e **sem** `painel.db-shm` (restaurar por cima de arquivos `-wal`/`-shm` antigos corrompe o resultado). Ajuste a permissão do arquivo para a conta do serviço, suba o serviço, confira a prontidão e **rode `backup-agora`** para criar um ponto novo (o `backup_run` voltou ao estado da cópia).

PowerShell:

```powershell
Copy-Item D:\painel-copias\painel-2026-10-30-020000.db D:\painel\data\painel.db
icacls D:\painel\data\painel.db /inheritance:r /grant:r "CONTA_DO_SERVICO:F" "Administrators:F"
Start-Service painel-squads
Invoke-RestMethod http://127.0.0.1:8080/api/health/pronto
.\app\scripts\painel-cli.ps1 backup-agora
```

sh:

```sh
sudo install -m 600 -o painel -g painel /var/backups/painel/painel-2026-10-30-020000.db /srv/painel/data/painel.db
sudo systemctl start painel-squads
curl -fsS http://127.0.0.1:8080/api/health/pronto
sh app/scripts/painel-cli.sh backup-agora
```

Se o pacote em uso é **mais novo** que a cópia, a subida aplica as migrações que faltam; se for **mais antigo**, troque o pacote antes de subir (`MIGRACAO_PENDENTE` na verificação indica uma cópia de esquema mais antigo).

**Comunicar:** avise o gerente. O que se perde entre o backup e a falha: as decisões e o registro de acesso feitos depois da cópia; registre o período no incidente. Com a flag do Jira ligada **pelo usuário**, a sincronização repõe o que vem do Jira. Se o registro de acesso perdido cobre consultas a dado pessoal, avise também o DPO.

**Aprender:** por que o banco falhou (disco, queda, antivírus, desligamento brusco)? A cópia mais recente estava a quantas horas? Ajuste `BACKUP_HORA`/retenção se for preciso.

### INCIDENTE SUSPEITA_DE_VAZAMENTO

Há indício de que dado pessoal saiu: acesso estranho no registro de acesso, serviço exposto sem login, arquivo copiado por quem não devia.

**Conter:** tire a porta do painel do proxy/firewall **ou** pare o serviço. Troque o `SESSION_SECRET` **sem** colocar o antigo em `SESSION_SECRET_ANTERIOR` (derruba todas as sessões; o usuário edita o arquivo de variáveis, ver [rotação de segredos](rotacao-de-segredos.md)). Preserve as evidências: não apague o registro de acesso nem os logs, e guarde uma cópia do banco na quarentena.

PowerShell:

```powershell
Stop-Service painel-squads
.\app\scripts\painel-cli.ps1 backup-agora
New-Item -ItemType Directory -Force D:\painel-quarentena | Out-Null
Copy-Item D:\painel-copias\painel-*.db D:\painel-quarentena
Copy-Item "$env:PAINEL_LOG_DIR\painel.log*" D:\painel-quarentena
```

sh:

```sh
sudo systemctl stop painel-squads
sh app/scripts/painel-cli.sh backup-agora
mkdir -p /var/quarentena-painel
cp /var/backups/painel/painel-*.db /var/quarentena-painel/
cp "$PAINEL_LOG_DIR"/painel.log* /var/quarentena-painel/
```

**Investigar:** pela tela Registro de acesso (gerente): quem consultou o quê e quando; pelos logs, a requisição pelo `X-Request-Id`. Confira `papeis.yaml` e as vigências.

**Corrigir:** **o usuário** renova o token (PAT) do Jira, no Jira, e troca o segredo da sessão. Corrija a causa (configuração, regra de rede, papel) e só então volte o serviço.

**Comunicar:** avise o **DPO imediatamente**. A comunicação à ANPD e aos titulares segue a regulamentação vigente, **a confirmar com o DPO** (a regra e o prazo são dele; este runbook não os fixa).

**Aprender:** registre linha do tempo, o que foi exposto e para quem, e o que muda (regra de rede, papel, alerta).

### INCIDENTE ACESSO_INDEVIDO

Alguém viu o que não devia (um papel largo demais, uma vigência vencida que não foi encerrada).

**Conter:** corrija o papel ou a vigência em `papeis.yaml` (encerrando a vigência com `ate`, sem apagar a linha), valide e reinicie.

PowerShell:

```powershell
.\app\scripts\painel-cli.ps1 validar-config
Restart-Service painel-squads
```

sh:

```sh
sh app/scripts/painel-cli.sh validar-config
sudo systemctl restart painel-squads
```

**Investigar:** na tela Registro de acesso (gerente), veja o que essa pessoa consultou e quando; confira `papeis.yaml` e as datas de vigência.

**Corrigir:** ajuste os papéis e as vigências; confira com a própria regra de visão (o usuário vê só o próprio, o líder o próprio e o squad, coordenador e gerente todos).

**Comunicar:** avise o gerente e o **DPO**, com o período e o tipo de dado visto (sem copiar o dado). O DPO decide se há comunicação a fazer.

**Aprender:** por que a vigência não foi encerrada na saída da pessoa? Inclua a conferência na rotina de entrada e saída.

### INCIDENTE TOKEN_DO_JIRA_EXPOSTO

O token (PAT) do Jira apareceu onde não devia (log, repositório, mensagem, cópia de backup).

**Conter:** **o usuário** desliga a flag do Jira (valor falso, no arquivo de variáveis) e **revoga o token no próprio Jira**. Nenhuma IA acessa o Jira para isso. Reinicie o serviço.

PowerShell:

```powershell
Restart-Service painel-squads
Invoke-RestMethod http://127.0.0.1:8080/api/health/pronto
```

sh:

```sh
sudo systemctl restart painel-squads
curl -fsS http://127.0.0.1:8080/api/health/pronto
```

**Investigar:** onde o token apareceu? Faça a varredura do histórico do repositório e das cópias de backup e dos logs (o painel mascara tokens, mas um erro de configuração pode ter gravado em outro lugar). A varredura de segredos do repositório roda com `sh scripts/ci/varrer-dados-pessoais.sh --arvore`.

**Corrigir:** **o usuário** cria o token novo no Jira, atualiza a variável da instância e a data `SYNC_PAT_VALIDADE_<INSTANCIA>`, reinicia e só então religa a flag (ver [rotação de segredos](rotacao-de-segredos.md)).

**Comunicar:** avise o gerente; se o token deu acesso a dado pessoal e houve uso indevido, avise o **DPO**.

**Aprender:** como o token chegou ali? Ajuste a máscara de log ou o procedimento.

### INCIDENTE BACKUP_VAZADO_OU_PERDIDO

A pasta de backup foi exposta, ou o disco com as cópias foi perdido. As cópias têm dados pessoais.

**Conter:** trate como `SUSPEITA_DE_VAZAMENTO`. Revise as permissões da pasta de backup agora.

PowerShell:

```powershell
icacls D:\painel-copias
icacls D:\painel-copias /inheritance:r /grant:r "CONTA_DO_SERVICO:(OI)(CI)F" "Administrators:(OI)(CI)F"
```

sh:

```sh
ls -ld /var/backups/painel
sudo chmod 700 /var/backups/painel
```

**Investigar:** liste o período coberto pelas cópias (os nomes `painel-aaaa-MM-dd-HHmmss.db` dão as datas) e o que há nelas (banco inteiro: registro de acesso, decisões, dados das issues).

**Corrigir:** troque o que for segredo e esteve nas cópias (o segredo da sessão, e o token do Jira pelo usuário); reponha cópias novas num volume cifrado e protegido; rode `backup-agora`.

**Comunicar:** avise o gerente e o **DPO**, informando o período coberto.

**Aprender:** a cópia para fora do servidor e a cifragem do volume estão em "Depois da flag / com o usuário" de [backup e restauração](backup-e-restauracao.md); revise-as.

### INCIDENTE NUMEROS_ERRADOS

Um card mostra números que não batem com o que o time vê no Jira.

**Conter:** avise quem usa que os números do card estão sob revisão (S2).

**Investigar:** compare com o Jira **só com a flag ligada pelo usuário**; sem ela, não há comparação possível e a investigação usa o que o painel já tem. Confira `status_map.yaml` (status novo sem mapa), a ficha do card, os avisos de qualidade dos dados e a última sincronização (tela Sincronização).

PowerShell:

```powershell
.\app\scripts\painel-cli.ps1 validar-config
```

sh:

```sh
sh app/scripts/painel-cli.sh validar-config
```

**Corrigir:** ajuste o arquivo de configuração que causa a diferença, valide e reinicie. Se a causa é um defeito de cálculo, registre a diferença esperada.

**Comunicar:** avise o gerente e quem usa o card quando os números voltarem a bater.

**Aprender:** registre a diferença e a causa; a validação completa com dados reais é do Plano 14.

### INCIDENTE MODO_DEV_EM_PRODUCAO

O alerta `MODO_DEV_COM_JIRA_LIGADO` apareceu, ou a faixa vermelha do modo desenvolvimento está no ar em produção: o painel está **sem login**.

**Conter:** desligue o modo desenvolvimento e reinicie **imediatamente** (o usuário edita o arquivo de variáveis: remova a linha `DASHBOARD_DEV_MODE` ou dê a ela o valor falso). Se não for possível agora, pare o serviço.

PowerShell:

```powershell
Stop-Service painel-squads
# o usuário corrige DASHBOARD_DEV_MODE no arquivo de variáveis
.\app\scripts\painel-cli.ps1 validar-config
Start-Service painel-squads
```

sh:

```sh
sudo systemctl stop painel-squads
# o usuário corrige DASHBOARD_DEV_MODE no arquivo de variáveis
sh app/scripts/painel-cli.sh validar-config
sudo systemctl start painel-squads
```

**Investigar:** desde quando o modo desenvolvimento estava ligado (log de subida) e se o serviço esteve acessível pela rede nesse período.

**Corrigir:** confirme que `GET /api/meta` não mostra `devMode` verdadeiro e que o painel exige login.

**Comunicar:** trate como possível exposição: siga `SUSPEITA_DE_VAZAMENTO` e avise o **DPO**.

**Aprender:** como o modo desenvolvimento foi ligado em produção (arquivo copiado do modelo? pacote sem o perfil de produção?) e que conferência evita repetir.
