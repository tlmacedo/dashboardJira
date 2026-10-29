# Primeiros passos do ClineSR: depois de clonar e antes da primeira subida

Data: 29/10/2026. Para: ClineSR (Cline com Gauss 3.7), Windows 11, PowerShell, PC da empresa. Pedido do usuário, literal: "indique para o ClineSR perguntas que deve fazer a mim e ele fazer os primeiros ajustes logo depois de clonar e antes de tirar o projeto pela primeira vez" (entendido como: antes de subir o painel pela primeira vez).

Este documento é uma ordem de serviço. Ele **não substitui** a Fase 0 (`docs/superpowers/plans/2026-10-27-plano-00-pc-da-empresa.md`): reorganiza o que ela e o `HANDOFF.md` já mandam, acrescenta o que falta e lista as perguntas que você faz ao usuário. Onde o repositório não responde, está escrito **não confirmado** e onde procurar. Se um passo falhar de um jeito que a coluna "Se falhar" não cobre: pare e pergunte (seção 3).

## 1. Regras que você não pode quebrar agora

1. **A flag `JIRA_LIVE_ENABLED` é a única trava do Jira e só o usuário a muda** (`CLAUDE.md`, regra 1). Hoje ela está desligada. Você não a liga, não pede a outra ferramenta que a ligue, não a escreve em documento com valor verdadeiro (o hook barra). Escreva "ligada" ou "valor verdadeiro".
2. **Com a flag desligada, nenhum acesso a servidor Jira, por nenhum caminho**: nem pelo painel, nem por `curl`, `Invoke-RestMethod`, navegador, script, MCP da Atlassian. Os passos desta página marcados **[TOCA O JIRA]** só existem depois de o usuário ligar a flag e avisar você. Os demais são offline.
3. **Nunca leia, edite nem commite** `app/.env`, `papeis.yaml`, `equipes.yaml`, `ausencias.yaml` nem `Filtros_Status_tool.txt` (`.clinerules/00-regras-do-projeto.md`, regra 2; o hook barra comandos que citem esses nomes). Quem cria e edita esses arquivos é o **usuário**. Você só entrega o comando e espera. Para saber se a flag está desligada, você não lê o `.env`: consulta `GET /api/meta` (campo `liveEnabled`) com o painel no ar.
4. **O repositório é PÚBLICO.** Nada de nome ou username real, e-mail real, host ou URL do Jira ou da empresa, caminho de certificado, token ou endereço de proxy em arquivo versionado, mensagem de commit, comentário de PR ou arquivo de progresso. Use `.invalid` e nomes fictícios (`ana.silva`). A resposta do usuário a uma pergunta da seção 3 que seja dado real fica **só** no `app/.env` ou nos YAML reais dele, nunca no repositório: registre apenas "respondido em aaaa-mm-dd" e a escolha da opção.
5. **Proibido neste projeto: contêiner, servidor de banco e WSL.** No terminal do Windows não use `bash`, `wsl` nem `sh` pelo PATH (o `bash.exe` abre o WSL e o comando é abortado). Onde este documento precisa do `sh` do Git for Windows, o caminho é `& "$env:ProgramFiles\Git\bin\sh.exe"` (`docs/operacao/ci.md`, seção 2).
6. **Nunca desative a verificação de certificado** (nem `-k`, nem `NODE_TLS_REJECT_UNAUTHORIZED=0`, nem `strict-ssl=false`) e nunca contorne um hook ou teste reescrevendo o comando.
7. **Toda dúvida é pergunta com opções**, a recomendada primeiro e marcada "(Recomendada)" (`CLAUDE.md`, regra 4). Formato na seção 3.
8. **Nada de push na `main`, nada de `--admin`, nada de `git add -A`.** Branch por alteração, a partir da `main` atualizada; `scripts/ci/varrer-dados-pessoais.sh` antes de todo push; fluxo em `docs/superpowers/fluxo-de-entrega.md`.

## 2. Checklist de ajustes iniciais (ordem executável)

Legenda: **[VOCÊ]** o ClineSR executa. **[USUÁRIO]** só o usuário executa (você entrega o comando e espera a resposta). **[TOCA O JIRA]** só depois de o usuário ligar a flag e avisar. Registre o resultado de cada passo no arquivo de progresso `docs/superpowers/progresso/2026-10-27-plano-00-pc-da-empresa.md`, que **você cria** numa branch própria (é versionado, então sem dado real; primeira linha: `# Progresso — plano: docs/superpowers/plans/2026-10-27-plano-00-pc-da-empresa.md`), só com versões e "ok/falhou", sem caminho, endereço ou credencial da empresa.

### Passo 1 [VOCÊ]: clone atualizado e limpo

```powershell
git fetch origin
git checkout main
git pull --ff-only origin main
git status -sb
git log --oneline -1
```

Esperado: `main` igual a `origin/main`, árvore limpa. Anote o hash curto no progresso. Se falhar: erro de certificado ou proxy no `git` vai para o Passo 3; conflito ou arquivo modificado que você não fez: pare e pergunte (não use `reset --hard`).

Depois, leia na ordem do `HANDOFF.md` ("COMECE AQUI", item 2) o que ainda não leu. Este documento vem **depois** dessa leitura.

### Passo 2 [VOCÊ]: ferramentas instaladas (Fase 0, Tarefa 1)

```powershell
java -version 2>&1
node -v
pnpm -v
git --version
gh --version
gh auth status
$PSVersionTable.PSVersion.ToString()
```

Esperado: Java 21.x, Node v22.x, pnpm 9.x ou 10.x (referência: 9.12.3, a do CI com lockfile v9), `gh` com `Logged in`. Se falhar: sem JDK 21, Node 22 ou pnpm, instale em pasta do usuário (zip, sem instalador) conforme a Tarefa 1 da Fase 0 (`npm install -g` sem administrador pode exigir `npm config set prefix` para uma pasta do usuário e essa pasta no PATH), e se precisar de privilégio ou aprovação da TI, pergunte (Pergunta A5). `gh` sem login: peça ao usuário `gh auth login` (interativo, envolve o token dele); sem `gh`, o fluxo de entrega para na PR.

### Passo 3 [VOCÊ]: proxy e certificado da empresa (só se algo falhar)

Rode este passo **somente** se o Passo 1, 6 ou 7 falhar com `PKIX path building failed`, `unable to find valid certification path`, `UNABLE_TO_GET_ISSUER_CERT_LOCALLY`, `SSL certificate problem`, `407` ou `connect timed out`. Receita completa na Tarefa 2 da Fase 0: JVM com `-Djavax.net.ssl.trustStoreType=Windows-ROOT` (variável de usuário `JAVA_TOOL_OPTIONS` e `%USERPROFILE%\.gradle\gradle.properties`), `git config --global http.sslBackend schannel`, `NODE_EXTRA_CA_CERTS` para o pnpm, espelho Maven em `%USERPROFILE%\.gradle\init.d\`. Tudo **fora do repositório**. Esperado: `.\gradlew.bat --version` (em `app\backend`) e `pnpm view react version` sem erro de certificado, e `git status` limpo. Se falhar: o arquivo `.pem` da CA, o endereço do proxy e o do espelho só o usuário ou a TI fornece. Pergunte (A2); não exporte certificado por conta própria.

### Passo 4 [VOCÊ]: hooks de proteção (Fase 0, Tarefa 6; feito **antes** de qualquer subida)

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .clinerules\hooks\test-PreToolUse.ps1
```

Esperado: todos os casos passam. Confirme no Cline que **Enable Hooks** está ligado e que o hook é `PreToolUse.ps1` (`docs/ferramentas-claude-para-o-cline/hooks.md`). Se falhar: **pare**. Sem o hook funcionando você não prossegue para nada que toque em segurança. Relate o caso que falhou e pergunte. Observação: a Fase 0 faz este teste depois da subida; aqui ele vem antes de propósito.

### Passo 5 [USUÁRIO]: criar `app/.env` a partir do modelo, com a flag desligada

Você não cria nem abre este arquivo (o hook barra). Peça ao usuário, em PowerShell, a partir da raiz do clone:

```powershell
Copy-Item app\.env.example app\.env
```

O modelo já traz a linha da flag com valor **desligado** e o modo desenvolvimento só comentado (o `iniciar.ps1` e o `gradlew run` assumem modo desenvolvimento quando a variável falta). O usuário **não deve** alterar a linha da flag. Esperado: `git status` não lista `app\.env` (está no `.gitignore`). Como você confere a flag sem ler o arquivo: no Passo 9, `liveEnabled = False` em `/api/meta`; e se a flag estivesse ligada, a subida com dados fictícios falha de propósito. Se o usuário quiser conferir à mão, o comando dele é `Select-String -Path app\.env -Pattern '^JIRA_LIVE_ENABLED'`. Pergunta A1 antes deste passo.

### Passo 6 [VOCÊ]: suíte do backend, sem cache (Fase 0, Tarefa 3)

`gradlew test --rerun-tasks` e o primeiro `gradlew run` (download de dependências) podem passar do tempo limite de comando do Cline: rode em segundo plano (`Start-Process`, saída para arquivo fora do repositório) ou aumente o timeout, e acompanhe até o fim.

```powershell
Push-Location app\backend
.\gradlew.bat --version
.\gradlew.bat test --rerun-tasks
Pop-Location
```

Esperado: `BUILD SUCCESSFUL` e **0 falhas** em `app\backend\build\reports\tests\test\index.html`. Anote o total real (o `HANDOFF.md` não fixa contagem). Não rode junto com a suíte do frontend: a máquina fica sob carga. O Gradle usa `-Xmx3g` (`app/backend/gradle.properties`); não reduza. Se falhar: certificado ou proxy volta ao Passo 3; `Unsupported class file major version` significa que o `java` do PATH não é o 21 (ajuste `JAVA_HOME`); "database is locked" significa processo Java usando `app\data\painel.db` (feche e rode de novo); estouro de memória (`Not enough memory to run compilation`) significa que `gradle.properties` foi alterado. Teste vermelho de outro tipo: **não ajuste o teste**. Registre nome e mensagem e pergunte. Atenção: `testCarga` (desempenho) **não** é parte deste passo (seção 3, grupo "podem esperar").

### Passo 7 [VOCÊ]: suíte e build do frontend (Fase 0, Tarefa 4)

```powershell
Push-Location app\frontend
pnpm install --frozen-lockfile
pnpm vitest run
if ($LASTEXITCODE -eq 0) { pnpm build }
Pop-Location
```

Esperado: testes sem falha e `app\frontend\dist\index.html` criado. Se falhar: rede ou certificado, Passo 3; `ERR_PNPM_OUTDATED_LOCKFILE`, não rode `pnpm install` sem `--frozen-lockfile`: pergunte. O CI também roda `pnpm lint` e `node scripts/verificar-csp.mjs` (`docs/operacao/ci.md`, seção 2); rode-os se quiser a mesma cobertura do CI.

### Passo 8 [VOCÊ]: testes da varredura e varredura de dados pessoais

```powershell
git fetch origin main
& "$env:ProgramFiles\Git\bin\sh.exe" scripts/ci/test-varredura.sh
& "$env:ProgramFiles\Git\bin\sh.exe" scripts/ci/varrer-dados-pessoais.sh --base origin/main
```

Esperado: a segunda termina com `VARREDURA OK`. Na `main` limpa não há nada novo a varrer; o valor do passo é provar que a ferramenta roda neste PC antes de você precisar dela num push. Se falhar: `sh.exe` ausente ou bloqueado pela TI/pelo hook: **não confirmado** que funciona neste PC (procure em `docs/operacao/ci.md`, seção 2, e `docs/ferramentas-claude-para-o-cline/README.md`); pergunte. Achado `ACHADO ...` na varredura: pare, não publique, relate o arquivo e a categoria (a varredura nunca imprime o texto).

### Passo 9 [VOCÊ]: primeira subida, em modo demonstração, sem Jira (Fase 0, Tarefa 5)

O painel é um processo que não termina e você roda um comando por vez: suba-o em segundo plano, espere o health e encerre-o por programa. Os dados fictícios valem só na sessão (nunca grave `DASHBOARD_DEMO_DATA` no `.env`). A partir da raiz do clone:

```powershell
$env:DASHBOARD_DEMO_DATA = 'true'
$proc = Start-Process -FilePath powershell.exe -PassThru -WindowStyle Hidden -WorkingDirectory app `
  -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File','iniciar.ps1','-SemBuildDoFront'
$ok = $false
1..60 | ForEach-Object { Start-Sleep 5; try { if ((Invoke-RestMethod http://127.0.0.1:8080/api/health -Proxy $null -TimeoutSec 3).ok) { $ok = $true; break } } catch { } }
$ok
Invoke-RestMethod http://127.0.0.1:8080/api/meta -Proxy $null
```

`-Proxy $null` evita o proxy da empresa para `127.0.0.1`, mas **no PowerShell 5.1 é não confirmado** que o parâmetro aceite `$null`. Se der erro, use `[System.Net.WebRequest]::DefaultWebProxy = $null` antes das chamadas, defina `$env:NO_PROXY = '127.0.0.1,localhost'`, ou `curl.exe --noproxy "*" http://127.0.0.1:8080/api/health`.

Esperado: `$ok` = `True`; `/api/meta` com `devMode = True`, `liveEnabled = False`, `zone = America/Manaus`, `banner = MODO DESENVOLVIMENTO: dados fictícios de demonstração` e `aguardandoCargaDoJira = False`. Se o navegador estiver disponível, abra `http://127.0.0.1:8080` e confira a **faixa vermelha** de modo desenvolvimento e que a interface carrega (senão, registre "só a API foi verificada"). Nada nesta subida acessa o Jira.

`iniciar.sh` e `iniciar.ps1` **não têm registro de execução no Windows** (`HANDOFF.md` e `docs/superpowers/relatorio-final-antes-da-flag.md`, seção (d), só afirmam isso dos `.ps1` do pacote e da implantação; para o `iniciar.ps1` o resultado é **não confirmado**). Trate esta como a primeira execução dele e registre o resultado. Se falhar:
- "execução de scripts foi desabilitada": o `-ExecutionPolicy Bypass` acima vale só no processo; se a política de grupo impedir até isso, pergunte (A3). Alternativa sem `.ps1`: `Push-Location app\backend; .\gradlew.bat run` (rode a partir de `app\backend`, que já usa `app\` como pasta de trabalho).
- Porta 8080 ocupada: `Get-NetTCPConnection -LocalPort 8080` mostra o dono; não encerre processo que não seja seu: pergunte (A4).
- `liveEnabled = True` ou a subida recusar por causa da demonstração com a flag ligada: **pare**. Alguém ligou a flag no `app\.env`. Não a desligue por conta própria; avise o usuário (a flag é dele).
- Qualquer código de falha de configuração (a mensagem cita só o nome da variável ou do arquivo): relate o código ao usuário.

Encerre sempre toda a árvore de processos (`taskkill /T` mata o PowerShell filho e seus descendentes; o filtro por `br.sidia.painel.ApplicationKt` evita matar outro Java; **não encerre o que não é seu**), apague os dados fictícios e limpe a sessão:

```powershell
Stop-Process -Id $proc.Id -Force
taskkill /PID $($proc.Id) /T /F
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match 'br\.sidia\.painel\.ApplicationKt' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
Remove-Item app\data\painel.db* -ErrorAction SilentlyContinue
Remove-Item Env:DASHBOARD_DEMO_DATA
```

Esperado: a consulta da porta vem vazia. Se não vier, pergunte antes de encerrar o que não é seu. Apagar `app\data\painel.db*` aqui é seguro **porque só contém a demonstração**; depois que existir qualquer tentativa de sincronização real, nunca apague o banco sem backup e sem perguntar (`docs/jira/checklist-primeira-conexao.md`, seção "O que acontece na subida quando a flag é ligada").

### Passo 10 [VOCÊ]: pasta de dados, porta e permissões

Conferências sem tocar em dados reais:
- O banco é o arquivo SQLite `app\data\painel.db` (criado na primeira subida; `DATABASE_PATH` muda). A pasta `app\data\` e `*.db` estão no `.gitignore`. Um só painel por arquivo (trava `painel.db.lock` ao lado).
- Espaço livre: abaixo de `PAINEL_DISCO_MIN_MB` (padrão 500) a prontidão (`/api/health/pronto`) responde 503. Confira o disco da pasta escolhida (Pergunta B5): `Get-PSDrive -PSProvider FileSystem | Select-Object Name,@{n='LivreMB';e={[int]($_.Free/1MB)}}`.
- Escuta: `PAINEL_HOST` padrão `127.0.0.1` e `DASHBOARD_PORT` padrão 8080. Em estação de trabalho **deixe em `127.0.0.1`**; outra interface expõe o painel à rede e exige firewall (`docs/implantacao.md`, seção 7). O modo desenvolvimento não tem login.
- Backup: `BACKUP_DIR` fica **fora do clone, fora da pasta do pacote e fora da pasta do banco**, com permissão só da conta do usuário e administradores (`docs/operacao/backup-e-restauracao.md`, seção 2, tem os `icacls`). É obrigatório em produção; para a primeira conexão a pergunta é B9.

Se a pasta de dados ou a de backup não aceitar escrita: relate; não mude permissão de pasta que não seja sua sem perguntar.

### Passo 11 [USUÁRIO]: YAML reais (só depois de decidir onde ficam; Pergunta B5)

Para a **primeira subida em demonstração não precisa de nenhum YAML real**: em modo desenvolvimento o painel usa os modelos `*.exemplo.yaml` e avisa no log (`usando o modelo ...`). Os reais são necessários para a primeira conexão. Quem copia e edita é o usuário; você só entrega os comandos (do `docs/jira/runbook-sincronizacao.md`, seção "Configs reais necessárias"):

```powershell
Copy-Item app\config\papeis.exemplo.yaml        app\config\papeis.yaml
Copy-Item app\config\calendario.exemplo.yaml     app\config\calendario.yaml
Copy-Item app\config\status_map.exemplo.yaml     app\config\status_map.yaml
Copy-Item app\config\jira_campos.exemplo.yaml    app\config\jira_campos.yaml
Copy-Item app\config\vinculos.exemplo.yaml       app\config\vinculos.yaml
Copy-Item app\config\equipes.exemplo.yaml        app\config\equipes.yaml
Copy-Item app\config\ausencias.exemplo.yaml      app\config\ausencias.yaml
```

Pontos que você explica ao usuário:
- O `id` de cada pessoa em `papeis.yaml` é o **username do Jira**, em minúsculas, `[a-z0-9._-]`, 1 a 64 caracteres, igual ao `assignee`; ids reservados: `dev`, `system`, `admin`, `root`, `anonymous`. Em produção é obrigatório ao menos um GERENTE com papel vigente hoje. `equipes.yaml` e `ausencias.yaml` só aceitam ids que existam em `papeis.yaml`.
- **O `.gitignore` cobre só estes sete nomes** em `app/config/`: `papeis`, `equipes`, `ausencias`, `calendario`, `status_map`, `jira_campos`, `vinculos`. Os demais (`labels_atraso`, `auditoria`, `metas`, `risco`, `previsao`, `planejamento`, `esforco`, `checklist_review`, `integridade`, `distribuicao` e outros) **não** estão ignorados: se o usuário os copiar com o nome real, o `git status` mostra arquivo novo. Eles não têm dado pessoal, mas **não os commite** sem perguntar (repositório público) e use sempre `git add` por caminho.
- `PAINEL_CONFIG_DIR` fora de `app/config`: em modo desenvolvimento o painel procura o real **e** o modelo `.exemplo` na mesma pasta (`AppContext.kt`, função `resolver`); uma pasta nova sem os modelos faz a subida falhar com `ARQUIVO_AUSENTE`, e a subpasta `cards/` (fichas) também precisa estar nela (**não confirmado** se o caminho das fichas segue `PAINEL_CONFIG_DIR`: procure em `app/backend/src/main/kotlin/br/sidia/painel/AppContext.kt`). Por isso a opção recomendada na Pergunta B5 é manter `app/config` por enquanto.
- Validar sem subir o painel e sem Jira: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File app\scripts\painel-cli.ps1 validar-config` (precisa do pacote: antes, `Push-Location app\backend; .\gradlew.bat installDist; Pop-Location`). Ele imprime `OK <arquivo>` / `ERRO <código> <arquivo>:<linha>` e nunca o valor. Códigos e correções: `docs/operacao/configuracao.md`, seção 3. O `painel-cli.ps1` **nunca foi executado** (D14 do roteiro): é a primeira execução; falhas viram correção com teste, em PR próprio.

Esperado: `git status` não lista os sete arquivos. Se aparecer algum: o `.gitignore` está incompleto; pare e avise.

### Passo 12 [USUÁRIO]: tetos da primeira carga, ainda com a flag desligada

No mesmo `app\.env`, **antes** de ligar a flag, o usuário define (os padrões do código são 50000 itens e 2000 requisições; sem estes tetos a primeira carga sai sem limite):

```
SYNC_MAX_ITENS=50
SYNC_MAX_REQUISICOES=5
```

Conferência pelo usuário, sem imprimir segredo (`docs/jira/runbook-sincronizacao.md`, Etapa 1, passo 2): `Select-String -Path app\.env -Pattern "^(JIRA_LIVE_ENABLED|DASHBOARD_|SYNC_|RETENTION_MONTHS)"` e a variante que mostra só "preenchida/VAZIA" para `JIRA_*_(URL|USER|TOKEN)`. Esperado: flag desligada, os dois tetos presentes, credenciais da instância escolhida como `preenchida`, as da outra como `VAZIA`. Você pede a **confirmação** (Pergunta B4); não lê o arquivo. Os tetos só sobem depois da primeira carga conferida (Etapa 2 do runbook), e isso é depois da flag.

### Passo 13 [VOCÊ, opcional, não bloqueia a primeira subida]: scripts `.ps1` do pacote (D14)

`montar-pacote.ps1`, `verificar-pacote.ps1` e `deploy\windows\*.ps1` nunca foram executados (só testes estáticos). Antes da implantação real, e não antes da primeira subida, rode a partir de `app\`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\montar-pacote.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verificar-pacote.ps1
```

Esperado: a montagem termina e a verificação não acusa YAML real, `.env`, banco ou contêiner no pacote. Ambas usam a porta 8080 e pastas temporárias, não ligam a flag e não acessam o Jira. Se falhar: corrija com teste em PR próprio (`docs/superpowers/plans/2026-10-28-roteiro-antes-e-depois-da-flag.md`, D14); não improvise no PC.

### Passo 14 [TOCA O JIRA]: diagnóstico de conexão e checklist da primeira conexão (Fase 0 estendida)

**Só depois de o usuário ligar a flag e avisar.** Antes disso, nada daqui roda.
- `docs/jira/diagnostico-conexao.ps1` é rodado **pelo usuário** (ele usa a credencial dele): grava `diagnostico_jira_AAAAMMDD_HHMMSS.txt` na pasta atual, com e-mails, nomes e títulos de tasks. O arquivo está no `.gitignore`: **nunca o commite, o cole em mensagem ou o compartilhe**. O script recusa rodar se a flag não estiver ligada em `app\.env`. As variáveis (`JIRA_BASE_URL`, `JIRA_EMAIL`, `JIRA_TOKEN`) são do ambiente do usuário; o token nunca vai como parâmetro.
- Depois, o Plano 14a, Tarefa 1 (`docs/superpowers/plans/2026-10-25-plano-14-primeira-conexao-validacao-e-calibracao.md`) e `docs/jira/checklist-primeira-conexao.md`: uma chamada por vez, registrar, **parar no primeiro 401, 403, 429, 5xx ou CAPTCHA**. Primeiro ciclo de sincronização: `docs/jira/runbook-sincronizacao.md`, Etapa 1. As apostas que provavelmente falham estão em `docs/superpowers/plans/2026-10-28-roteiro-antes-e-depois-da-flag.md`, seção 4 (R1 a R22).

## 3. Perguntas ao usuário

### Como perguntar

Use `ask_followup_question` com `options` (`.clinerules/workflows/perguntar-ao-usuario.md`). Cada pergunta tem de 2 a 4 opções, a recomendada **primeiro** e marcada "(Recomendada)", e cada opção diz o porquê e a consequência. Uma pergunta por vez, na ordem dos grupos. Agrupe sua mensagem assim: situação em uma frase, a pergunta, as opções. Nunca pergunte em prosa nem em lista solta. Nunca peça ao usuário que cole token, URL interna, username ou nome na conversa: a resposta real fica no ambiente dele.

Onde registrar a resposta (sem dado real): decisões de produto (confirmações 1 a 10) na tabela "Confirmações do usuário" do `HANDOFF.md`, com "confirmo" ou "reverter" e a data; demais respostas no arquivo de progresso da Fase 0 (`docs/superpowers/progresso/2026-10-27-plano-00-pc-da-empresa.md`), como `Ruling:` (decisão, motivo, como reverter), conforme `docs/guia-execucao-outra-ia.md`, seção 4.

### Grupo A: bloqueiam a primeira subida

**A1. Quem cria o `app\.env`? (antes do Passo 5)**
Por que importa: o hook impede você de criar ou ler o arquivo, e é ele que guarda a flag e, depois, as credenciais.
- "Preciso do comando" (Recomendada): você entrega `Copy-Item app\.env.example app\.env`, o usuário o roda sem alterar a flag e responde de novo. É o caminho mais seguro, pois você não vê o arquivo.
- "Já criei o `app\.env` a partir do modelo e não mexi na flag". Segue ao Passo 6.
- "Pular por enquanto": o painel sobe sem `.env` em modo desenvolvimento com a flag desligada por padrão (linha ausente = desligada); os passos 6 a 9 seguem; o 12 e tudo que vier depois ficam bloqueados.
Registro: progresso da Fase 0, "app\.env criado: sim/não".

**A2. Proxy, certificado e espelho (só se o Passo 3 foi necessário)**
Por que importa: Gradle, pnpm e git baixam dependências e a rede da empresa pode inspecionar TLS.
- "Tenho o `.pem` da CA, o proxy e o espelho; vou colocá-los nas variáveis de usuário" (Recomendada): o usuário informa os valores **no próprio PC** (variáveis de usuário e `%USERPROFILE%\.gradle\`), você só diz quais variáveis e testa.
- "A TI precisa fornecer": você para neste ponto e lista exatamente o que falta (arquivo `.pem`, host e porta do proxy, endereço do espelho Maven e do registro npm).
- "Rede liberada para os registros públicos, sem proxy": você tenta de novo sem configuração.
Consequência: sem isso nenhuma suíte roda. Registro: progresso, "ajustes de rede: JVM usa repositório do Windows / NODE_EXTRA_CA_CERTS definido / espelho configurado" (sem endereços).

**A3. Política de execução do PowerShell (só se o `.ps1` for bloqueado)**
- "Rodar com `-ExecutionPolicy Bypass` só no processo" (Recomendada): nada muda na máquina; é o comando já usado nos passos.
- "Subir com `gradlew.bat run`, sem `.ps1`": funciona sempre, mas não exercita o `iniciar.ps1`.
- "A política de grupo bloqueia até isso": você para e o usuário fala com a TI. Nunca altere a política da máquina.

**A4. Porta 8080 ocupada ou outro painel rodando**
- "Usar outra porta (`DASHBOARD_PORT`)" (Recomendada): não mexe em processo de ninguém; o usuário a define no `app\.env`, os comandos de health trocam a porta e o painel continua em `127.0.0.1`.
- "Encerro o processo dono da porta": o usuário diz qual é; você só encerra o que ele confirmar.
Consequência: uma segunda subida no mesmo banco também é recusada pela trava `painel.db.lock`.

**A5. Instalar ferramentas e entrar no GitHub**
- "Instale em pasta do meu usuário, sem instalador" (Recomendada): JDK 21 e Node 22 em zip, pnpm por `npm install -g pnpm@9.12.3` (sem administrador, pode precisar de `npm config set prefix` numa pasta do usuário).
- "A TI instala": você lista versões e para.
- "Já tenho, vou ajustar `JAVA_HOME`/`PATH`".
`gh auth login` é sempre do usuário.

### Grupo B: bloqueiam ligar a flag (pergunte agora, mas só vale antes do pedido da flag)

**B1. Instância, autenticação e projeto do Jira**
Por que importa: define `JIRA_SAMSUNG_URL`/`JIRA_SAMSUNG_TOKEN` (Bearer, token pessoal do Data Center) ou `JIRA_SIDIA_URL`/`_USER`/`_TOKEN` (Basic), e a ficha `app/config/cards/sincronizacao.card.yaml` (hoje `project: ["TG-Basic"]` e seis tipos de issue).
- "Instância Samsung (Data Center), token pessoal (Bearer), projeto TG-Basic, tipos como na ficha" (Recomendada): é o que o código suporta hoje (`SYNC_INSTANCIAS` padrão `samsung`).
- "Samsung com projeto ou tipos diferentes": o usuário diz as diferenças; a mudança é na ficha, em PR, com teste do JQL (JQL só pelo `JqlBuilder`).
- "Sidia (Basic)": **não funciona hoje** (a ficha de sincronização é só da Samsung; a Sidia pede ficha própria, Plano 14b, risco R9). Sem decisão, não ligue nada.
Registro: progresso, "instância: samsung; autenticação: Bearer; projeto/tipos: confirmado" (sem URL). A URL e o token ficam só no `app\.env` do usuário.

**B2. Validade do token (PAT)**
- "Informo a data de vencimento em `SYNC_PAT_VALIDADE_SAMSUNG` (aaaa-mm-dd)" (Recomendada): o painel alerta 14 dias antes e depois do vencimento (só com a flag ligada); o painel nunca consulta o Jira para saber.
- "Sem validade definida": nenhum alerta de vencimento; a sincronização passa a falhar sem aviso quando o token vencer.

**B3. Usernames e papéis (gerente, coordenadores, Squad Leaders, squads)**
Por que importa: `papeis.yaml` decide quem vê o quê; sem GERENTE vigente a produção não sobe; um `id` com caixa ou grafia diferente do `assignee` do Jira faz a pessoa não ver as próprias tasks (R5/R17).
- "Eu preencho `papeis.yaml`, `equipes.yaml` e `ausencias.yaml` sozinho; você só roda `validar-config` e me conta os códigos" (Recomendada): você nunca vê nome nem username.
- "Primeiro com os modelos fictícios, preencho depois": a flag só pode ser ligada depois do preenchimento (o checklist da primeira conexão confere cada `id`).
Registro: progresso, "papeis.yaml preenchido: sim/não; validar-config: OK/erros (códigos)".

**B4. Tetos da primeira carga (Passo 12)**
- "Defini `SYNC_MAX_ITENS=50` e `SYNC_MAX_REQUISICOES=5`" (Recomendada): primeiro ciclo pequeno; erro `TETO_ITENS` ou `ORCAMENTO_REQUISICOES` é esperado.
- "Quero outros valores": ele diz quais (faixas: 1 a 200000 e 1 a 20000); mantenha pequenos.
- "Sem tetos": a primeira carga usa 50000 e 2000; contraria `HANDOFF.md`; só com decisão expressa do usuário e registro de `Ruling:`.

**B5. Onde ficam os YAML reais e o banco**
- "Em `app\config` e `app\data`, dentro do clone, por enquanto" (Recomendada): é o caminho testado; os sete nomes sensíveis e o banco estão ignorados pelo git; a pasta de produção (`PAINEL_CONFIG_DIR`, `DATABASE_PATH` absolutos) é decidida na implantação (Plano 13). Consequência: faça backup manual do `painel.db` antes da primeira subida com a flag ligada (`docs/superpowers/relatorio-final-antes-da-flag.md`, confirmação 10).
- "Fora do clone, em pastas absolutas": melhor isolamento de dado pessoal; exige que a pasta de configuração tenha os modelos `*.exemplo.yaml` e `cards/` (ver Passo 11) e o `.env` com `PAINEL_CONFIG_DIR`/`DATABASE_PATH`.
- "Repositório privado de configuração" (`docs/operacao/configuracao.md`, seção 2): recomendado para produção, **ainda não existe**; o usuário o cria com acesso restrito.

**B6. Porta, host e proxy**
- "`127.0.0.1:8080`, só esta máquina" (Recomendada): padrão; sem exposição de rede.
- "Outra porta" (`DASHBOARD_PORT`): se 8080 está em uso.
- "Acesso de outras máquinas": exige firewall, `PAINEL_HOST` com um IP interno, `DASHBOARD_PROXY_CONFIAVEL` e, fora do modo desenvolvimento, login. É Plano 13; não atenda agora.

**B7. Modo desenvolvimento (sem login) ou SSO/OIDC na primeira conexão**
Por que importa: com a flag ligada, o modo desenvolvimento mostra dados reais **sem login**, com o banner "MODO DESENVOLVIMENTO: dados reais do Jira sem login" e um aviso no log. O OIDC só foi testado com emissor falso.
- "Primeira conexão em modo desenvolvimento, só em `127.0.0.1`, neste PC, sem acesso remoto de outras pessoas" (Recomendada): é o que o Plano 14a pressupõe. Consequência: quem usa este Windows (inclusive por acesso remoto) enxerga tudo. Peça ao usuário que confirme que a escuta é só loopback (`PAINEL_HOST` padrão) e que ninguém mais acessa este PC; `validar-config` acusa `ESCUTA_FORA_DO_LOOPBACK` se a escuta sair do loopback.
- "Esperar o login SSO/OIDC (Plano 13)": mais seguro, adia a primeira conexão até haver provedor, claim e proxy.
Registro: progresso, `Ruling:`.

**B8. Fuso e feriados**
Por que importa: o painel usa America/Manaus e dias úteis por padrão; `SYNC_FUSO_SERVIDOR` deve ser o `timeZone` do usuário da credencial no Jira, que só se descobre com a flag ligada (Passo 1 do checklist). O `calendario.yaml` modelo traz feriados nacionais, de Amazonas e de Manaus e móveis de 2026 a 2030, sem pontos facultativos.
- "Manter America/Manaus e o calendário do modelo; revisar feriados municipais e pontos facultativos (Quarta-feira de Cinzas, 24 e 31/12) comigo" (Recomendada): o usuário edita `calendario.yaml`; confirme o fuso real no Passo 1 do checklist.
- "O Jira roda em outro fuso": o usuário diz qual; `SYNC_FUSO_SERVIDOR` recebe esse fuso e o checklist (R4/R21) confere o deslocamento perto da meia-noite.
- "Dias corridos por padrão": é uma mudança de comportamento de produto; não a faça sem ordem expressa.

**B9. Backup e canal de alertas**
- "`BACKUP_DIR` numa pasta fora do clone e do banco, com permissão só minha e dos administradores; alertas só em arquivo (`PAINEL_ALERTAS_ARQUIVO`)" (Recomendada): atende à regra do backup e ao canal atual (arquivo, log, rota e faixa; sem webhook).
- "Sem backup por enquanto": só aceitável com o banco ainda de demonstração; antes de dado real, não.
- "Canal externo (e-mail, chat) para alertas": é decisão de infraestrutura (Plano 12, D10); fica no grupo C.

**B10. Dado real antes do parecer do DPO**
Por que importa: `docs/implantacao.md`, seção 12, diz que base legal, retenção e comunicação à equipe precisam da validação do DPO **antes** de dados reais entrarem e que, até lá, só fixtures e demonstração; por outro lado, o código só fecha Auditoria e Integridade (trava `DASHBOARD_DPO_ACEITE`, fechada por padrão), o resto do painel abre com a flag. **Não confirmado** se o DPO exige o parecer antes de o painel guardar qualquer cópia local de dado do Jira (procure em `docs/lgpd/inventario-de-dados.md` e `docs/lgpd/relatorio-para-o-dpo.md`).
- "Obter pelo menos a ciência do DPO antes de ligar a flag; o parecer completo antes de abrir Auditoria e Integridade" (Recomendada): mais prudente; adia a flag até haver esse registro.
- "Ligar a flag antes do parecer, com Auditoria e Integridade fechadas pela trava": **contraria `docs/implantacao.md`, seção 12**; só com decisão expressa do usuário registrada como `Ruling:` (com data e o risco aceito).
- "Aguardar o parecer completo antes de qualquer dado real".
Registro: `HANDOFF.md`, confirmação 5 (a data do aceite e a referência do parecer; **nunca o nome do DPO**).

**B11. Quem é o DPO e há parecer**
- "Tenho DPO designado e já enviei `docs/lgpd/relatorio-para-o-dpo.md` (sete perguntas)" (Recomendada): o usuário informa só a referência do parecer, se existir (para `DASHBOARD_DPO_ACEITE_REF`, 1 a 80 caracteres `A-Za-z0-9._/-`).
- "Ainda não tenho DPO ou não enviei": o usuário designa e envia; você prepara apenas o texto, sem enviar nada. O aviso à equipe (`docs/lgpd/comunicacao-a-equipe.md`) é enviado pelo usuário.
- "Não se aplica à minha empresa": isso muda a análise jurídica; pare e peça orientação ao usuário.
Você nunca liga `DASHBOARD_DPO_ACEITE`.

**B12. Confirmação das decisões tomadas sozinho (tabela do `HANDOFF.md`, "Confirmações do usuário")**
Pergunte **cada uma** como pergunta separada, com as opções "Confirmo" (Recomendada), "Reverter" e "Decidir depois". Ao ouvir "reverter", **não** mude código: registre a resposta e abra o assunto como plano novo (migração nunca é editada). Estas interferem na primeira conexão (roteiro D23, "antes da recarga completa"):

| # | Tema | Por que pergunta já | Se "reverter" |
|---|---|---|---|
| 1 | Migração V7 e Plano 11, T3: guarda estimativas e data de criação do worklog, **zera o cursor** (a primeira carga é recarga completa de 24 meses) e **remove a tabela `person`** | Muda dado guardado; a V7 já vale em qualquer banco novo, inclusive o da demonstração (descartável, então reverter agora é o mais barato) | Migração nova (V8) que recria `person` e desfaz as colunas; os sinais `ESTIMATIVA_REDUZIDA`, `WORKLOG_RETROATIVO` e `ESTIMATIVA_AUMENTADA` ficam sem dado |
| 2 | Qualidade dos dados aberta à coordenação (o plano dizia só o gerente) | Muda quem vê o quê | `ALTA` por `['GERENTE']` em `nav.ts`, papel da rota e dos cartões `QD-nn` |
| 3 | Gaveta da task só pelo responsável atual (ex-dono recebe 404) | Muda quem vê o quê | `permitePosse` na rota da tarefa |
| 7 | "Quem consultou quem" fora do `access_log` | Muda o que a auditoria guarda | Remover o ramo `sensivel` em `filtrosAuditados` |
| 8 | Squad Leader sem task sem responsável | Muda quem vê o quê | `Escopo.veSemResponsavel` incluir o líder |
| 9 | Distribuição e Rebalanceamento só em modo sombra | Garante que nada é escrito no Jira | Plano 16, só por ordem expressa |
| 10 | Expurgo automático dos dados fictícios e recusas de banco na subida com a flag ligada | Muda dado guardado e pode parar a subida | Procedimento manual (parar o painel, backup e apagar `app/data/painel.db`) |

Texto, motivo e reversão completos: `docs/superpowers/relatorio-final-antes-da-flag.md`, seção (c). Registro: tabela do `HANDOFF.md` (coluna Resposta e Data), em PR de documentação. A confirmação 5 (aceite do DPO fechado até o parecer) é coberta por B10 e B11; a 4 (`OIDC_DOMINIOS_PERMITIDOS` obrigatória), pelo grupo C. A 6 (remoção do app antigo) é tratada fora deste documento; **não a pergunte agora**.

### Grupo C: podem esperar (não bloqueiam a primeira subida nem a flag)

Pergunte só quando chegar a fase correspondente; registre no progresso do plano.
- **C1. OIDC real (Plano 13, D22):** provedor (`OIDC_ISSUER`, `OIDC_CLIENT_ID`, `OIDC_REDIRECT_URI`), claim com o username, estratégia (`claim_direto` padrão, `parte_local_do_email` ou `conta_windows`), `OIDC_DOMINIOS_PERMITIDOS` (**obrigatória** nas duas últimas; confirmação 4 do `HANDOFF.md`; sem ela a produção não sobe com `DOMINIO_DO_USERNAME_AUSENTE`), `OIDC_HOSTS_PERMITIDOS` se o provedor estiver na intranet. Recomendada: `claim_direto` se o provedor entrega o username do Jira na claim; a claim precisa ser imutável e controlada pelo administrador do provedor.
- **C2. Implantação (D11):** máquina e gerenciador do serviço, proxy reverso com TLS (`DASHBOARD_ORIGEM_PUBLICA` https, `DASHBOARD_PROXY_CONFIAVEL`), cofre de segredos, entrega do pacote, conta do serviço sem privilégio, firewall.
- **C3. Retenção por categoria (pergunta 3 do DPO):** padrão 24 meses; `RETENTION_*_MONTHS`.
- **C4. Canal externo de alertas, cópia de backup para fora do servidor e cifragem do volume (D10).**
- **C5. Segunda instância (Sidia) e cards dela (D5, R9).**
- **C6. `testCarga` no PC da empresa (D21):** `Push-Location app\backend; .\gradlew.bat testCarga; Pop-Location`; o orçamento do p95 é 4 s e na VM de referência ficou em 4,1 a 4,3 s. Decide `PAINEL_CACHE_SEG`, `DATABASE_POOL_SIZE` e a memória do serviço. Rode sozinho (carga alta) e só com autorização.
- **C7. Capturas de tela, ativos de marca, treinamento, aceite do gestor, calibração (D20, D12, D7, D6).**

## 4. Critério de pronto: o que você devolve ao usuário antes de ele ligar a flag

Entregue uma mensagem (e o mesmo no arquivo de progresso) com **todos** os itens abaixo, cada um "feito/passou", "falhou/pendente" ou "não confirmado", com o número do passo. Se algum item de 1 a 9 estiver pendente, diga isso claramente e **não** peça a flag.

1. `main` atualizada, hash curto, árvore limpa (Passo 1).
2. Versões: Java 21.x, Node 22.x, pnpm, `gh` autenticado (Passo 2); quais ajustes de rede foram necessários, sem endereços (Passo 3).
3. Teste dos hooks passou e **Enable Hooks** está ligado (Passo 4).
4. Suíte do backend sem cache: `BUILD SUCCESSFUL`, total de testes e 0 falhas (Passo 6).
5. Suíte do frontend: total e 0 falhas, build gerado (Passo 7).
6. Varredura: `VARREDURA OK` e os testes da varredura passando neste PC (Passo 8).
7. Primeira subida em modo demonstração: `/api/health` ok, `/api/meta` com `devMode = True` e **`liveEnabled = False`**, faixa vermelha vista (ou "só a API verificada"), painel encerrado, porta livre, banco de demonstração apagado (Passo 9). Resultado do `iniciar.ps1` registrado (primeira execução no Windows).
8. Pasta de dados, disco, porta e backup definidos (Passo 10; B5, B6, B9).
9. `app\.env` criado pelo usuário, com a flag desligada, os dois tetos (`SYNC_MAX_ITENS=50`, `SYNC_MAX_REQUISICOES=5`) e as credenciais da instância preenchidas, **confirmados pelo usuário** (Passos 5 e 12; A1, B4); YAML reais criados por ele e `validar-config` sem erro (Passo 11; B3); `git status` sem nenhum arquivo real à vista.
10. Respostas obtidas e registradas (sem dado real): B1 a B12, com a data; as pendentes do grupo C listadas.
11. Confirmações 1 a 10 do `HANDOFF.md`: quais foram respondidas e quais ficam como "pendente" (o código vale como está).
12. Pendências conhecidas que **não** bloqueiam: `.ps1` do pacote ainda não executados (Passo 13), `testCarga` não rodado, OIDC e implantação por fazer, DPO (B10/B11) com o status informado.
13. Declaração final: "Nenhuma requisição ao Jira foi feita; a flag está desligada" e a evidência (`liveEnabled = False` do Passo 9).

Só então peça ao usuário, com uma pergunta de opções, para ligar a flag à mão em `app\.env` e reiniciar o painel; ao receber o aviso, siga para o Passo 14 e o Plano 14a, Tarefa 1.

## 5. Fontes

`CLAUDE.md` (regras 1 a 8), `HANDOFF.md`, `.clinerules/00-regras-do-projeto.md`, `docs/guia-execucao-outra-ia.md`, `docs/superpowers/plans/2026-10-27-plano-00-pc-da-empresa.md`, `docs/superpowers/relatorio-final-antes-da-flag.md` (seções (c), (d), (f)), `docs/superpowers/plans/2026-10-28-roteiro-antes-e-depois-da-flag.md` (D1 a D23, R1 a R22), `docs/jira/checklist-primeira-conexao.md`, `docs/jira/runbook-sincronizacao.md`, `docs/jira/diagnostico-conexao.ps1`, `app/.env.example`, `app/README.md`, `docs/implantacao.md`, `docs/operacao/` (`configuracao.md`, `backup-e-restauracao.md`, `ci.md`), `docs/lgpd/`, `app/config/*.exemplo.yaml`, `app/iniciar.ps1`.
