# Hooks: o que o Claude Code fazia e como fica no Cline (Windows 11 e macOS/Linux)

O executor é o **ClineSR** (variante Samsung do Cline, com o modelo Gauss 3.7) num **PC com Windows 11**. Tudo o que está abaixo foi conferido no Cline público; se o ClineSR tem hooks como o Cline público é **não confirmado** (teste de fumaça no fim deste documento).

## Hooks do Claude Code neste projeto

Configurados em `.claude/settings.json` (versionado):

| Evento (Claude) | Quando | Script | O que faz |
|---|---|---|---|
| `SessionStart` (assíncrono) | Ao abrir a sessão | `.claude/hooks/ensure-docker.sh` | Se o Docker não responde, abre o Docker Desktop em segundo plano e espera até 120 s; com `POSTGRES_PASSWORD` definida, sobe o `postgres` do `app/docker-compose.yml` |
| `PreToolUse` em `Bash(*gradlew*)` e `Bash(docker *)` | Antes de testes e comandos Docker | `ensure-docker.sh` | O mesmo, antes de precisar do banco |
| `PreToolUse` em ferramentas MCP `*tlassian*` e `Bash` | Antes de cada comando | `.claude/hooks/guard.sh jira` | Bloqueia: ferramentas MCP do Atlassian; qualquer variável `JIRA_*` (exceto a própria flag); URL com `jira`/`atlassian`; ferramenta de rede (`curl`, `wget`, `ssh`...) junto da palavra Jira; `jiradash check`, `sync` ou `serve`; ligar `JIRA_LIVE_ENABLED` dentro do comando |
| `PreToolUse` em `Write`, `Edit`, `NotebookEdit`, `Bash` | Antes de escrever ou rodar | `.claude/hooks/guard.sh arquivos` | Bloqueia editar `.env` (exceto `.env.example`/`.env.sample`) e `config/{equipes,ausencias,papeis,squads}.yaml`; bloqueia comandos que escrevem, copiam, removem ou versionam esses arquivos; bloqueia `git add -f`/`--force` |

Testes: `sh .claude/hooks/test-guard.sh`. O Plano 4A, Tarefa 11, ainda vai tornar o `guard.sh` consciente da flag.

## O que o Cline oferece (verificado em 25/10/2026, código da branch `main` de github.com/cline/cline)

- **Extensão VS Code:** hooks por arquivo em `.clinerules/hooks/` (projeto) e `~/Documents/Cline/Hooks` (global; no Windows, `%USERPROFILE%\Documents\Cline\Hooks`). Precisa ligar **"Enable Hooks"** nas configurações do Cline (Settings → Features).
  - **Windows:** só `.clinerules/hooks/PreToolUse.ps1` é executado (o arquivo sem extensão é ignorado). A extensão chama `<PowerShell> -NoProfile -NonInteractive -ExecutionPolicy Bypass -File PreToolUse.ps1`, preferindo o PowerShell 7 (`pwsh`, nos caminhos de instalação e no `PATH`) e caindo para o Windows PowerShell 5.1 (`powershell.exe`). Fontes: `apps/vscode/src/core/hooks/utils.ts`, `HookProcess.ts` e `apps/vscode/src/utils/powershell.ts`.
  - **macOS/Linux:** arquivo com o nome exato do evento, sem extensão e executável.
  - Entrada: JSON no stdin (`taskId`, `preToolUse.toolName`, `preToolUse.parameters`, `workspaceRoots`...). Saída: JSON no stdout `{"cancel": bool, "contextModification": "...", "errorMessage": "..."}`; `cancel: true` impede a ferramenta. Tempo máximo padrão: **30 s** (`HookProcess.ts`); saída com código diferente de 0 é tratada como erro do hook.
  - O modelo de hook que a própria extensão gera para Windows (`templates.ts`) só lê o stdin e sempre libera; não serve de trava.
- **CLI/SDK do Cline:** hooks em `.cline/hooks/` (`PreToolUse.sh`...) com outro formato de entrada (`tool_call.name`, `tool_call.input`); plugins em TypeScript não valem para a extensão (https://docs.cline.bot/customization/plugins.md).
- **Documentação:** https://docs.cline.bot/customization/hooks.md só aponta para os plugins do SDK; o comportamento foi lido no código. Na versão publicada que o ClineSR usa: **não confirmado**.

## Porte feito

| Claude | Cline | Situação |
|---|---|---|
| `guard.sh jira` + `guard.sh arquivos` (macOS/Linux) | `.clinerules/hooks/PreToolUse` (bash, executável): adaptador que chama o `guard.sh` e acrescenta os extras do Cline | **Testado no macOS** (`sh .clinerules/hooks/test-PreToolUse.sh`, 94 casos, 0 falhas em 25/10/2026) |
| `guard.sh` (Windows) | `.clinerules/hooks/PreToolUse.ps1`: as regras do `guard.sh` **reescritas** em PowerShell, mais os extras do Cline, sem `jq`, `sh` nem `bash` | **Não executado no Windows.** Rodado só com PowerShell 7.5 no macOS (`test-PreToolUse.ps1`, 176 casos, 0 falhas em 25/10/2026). PowerShell 5.1 e Windows 11: **o ClineSR roda o teste primeiro** (abaixo) |
| `ensure-docker.sh` no início da sessão e antes de `gradlew`/`docker` | Passo do workflow `/continuar.md` e de `/executar-plano.md` | Checklist manual. No Windows o script `.sh` não abre o Docker Desktop: veja o comando PowerShell no `README.md` |

### O que os hooks do Cline bloqueiam (os dois, `PreToolUse` e `PreToolUse.ps1`)

**Com `JIRA_LIVE_ENABLED` ausente, `false` ou qualquer valor que não seja `true`:**
- Tudo o que o `guard.sh jira` bloqueia (tabela acima), também com variáveis no estilo Windows (`$env:JIRA_TOKEN`, `%JIRA_TOKEN%`, em minúsculas).
- Cmdlets e programas de rede do Windows junto de `jira`/`atlassian`: `Invoke-WebRequest`/`iwr`, `Invoke-RestMethod`/`irm`, `curl.exe`, `Start-BitsTransfer`, `bitsadmin`, `certutil`, `Test-NetConnection`, `Resolve-DnsName`, `nslookup`, `ping`, `Net.WebClient`, `HttpClient`, `Start-Process`.
- Ligar a flag por `setx`, `[Environment]::SetEnvironmentVariable`, `Set-Item env:` ou `$env:JIRA_LIVE_ENABLED="true"`.
- **Navegador (`browser_action`) e `web_fetch` para qualquer URL que contenha `jira` ou `atlassian`** (sem diferenciar maiúsculas), e ferramentas MCP com URL do Jira nos argumentos. Decisão do usuário em 25/10.
  - **Efeito aceito:** isso bloqueia também a documentação pública do Jira (por exemplo `docs.atlassian.com`, `developer.atlassian.com`) enquanto a flag não for `true`. Nesses períodos, a pesquisa na documentação pública é feita **pelo usuário**, colando trechos no chat, ou pela **busca genérica** (`web_search`, que continua liberada), abrindo só resultados cujo endereço não tenha `jira` nem `atlassian`.
  - **Falso positivo aceito:** se a pasta do projeto tiver "jira" no nome (como `dashboardJira`), abrir um arquivo local no navegador (`file:///.../dashboardJira/...`) e comandos de rede com o caminho completo também são bloqueados. **Clone o projeto numa pasta sem "jira" no nome**, por exemplo `C:\dev\painel-squads`.

**Com `JIRA_LIVE_ENABLED=true` (sem diferenciar maiúsculas, como o `Settings.kt`):** nada do bloco acima é bloqueado, exceto **exibir token ou senha do Jira** (`echo`, `printenv`, `Write-Output`, `Get-ChildItem env:`... de `JIRA_*TOKEN/PASS/SENHA/SECRET/PAT`, ou a variável sozinha no PowerShell), que continua barrado (Plano 4A, Decisão 10).

**Com qualquer valor da flag:**
- O modo `arquivos` do `guard.sh`: escrever, copiar, remover ou versionar `.env` e `config/{equipes,ausencias,papeis,squads}.yaml` (inclusive `Set-Content`, `Out-File`, `Copy-Item`, `Remove-Item`... e caminhos com barra invertida); `git add -f`.
- **Ler** `.env` (exceto `.env.example`/`.env.sample`) e as configs reais, por `read_file` ou por comando (`cat`, `type`, `Get-Content`, `Select-String`, `findstr`...): o conteúdo iria para o modelo. Os modelos `*.exemplo.yaml` continuam liberados. *Extra do Cline: o Claude não bloqueava leitura.*
- Listar todo o ambiente (`env`, `printenv`, `set` sozinhos, `Get-ChildItem env:`, `[Environment]::GetEnvironmentVariables()`): mostraria os tokens.

**Liberados:** leitura de arquivos comuns, `search_files`, `list_files`, `web_search`, navegador e `web_fetch` fora do Jira, outros servidores MCP.

**Flag:** lida do ambiente do processo do Cline (do VS Code), **não** do `app\.env` nem do comando. No Windows, só o usuário a define (por exemplo, variável de usuário nas Configurações do Windows) e depois reabre o VS Code.

**`.clineignore`:** o arquivo `.clineignore` na raiz impede o Cline de ler segredos e dados pessoais (`.env`, arquivos de `config/` com dados reais, chaves) e reforça o hook, sem substituí-lo.

**Falha fechada:** JSON ilegível, entrada vazia ou erro interno cancelam a ferramenta. No macOS/Linux, sem `jq` ou sem o `guard.sh`, também.

**Duas cópias das regras:** o `.sh` chama o `guard.sh`; o `.ps1` reescreve as mesmas regras. Ao mudar o `guard.sh` (Plano 4A, Tarefa 11, por exemplo), mude também o `PreToolUse.ps1` e o `test-PreToolUse.ps1` (que espelha caso a caso o `test-guard.sh`). Pendência da Tarefa 11: no `.sh`, remover o atalho `jira_livre` e a expressão de token duplicada e chamar sempre o `guard.sh jira`.

### Instalar no PC com Windows 11 (ClineSR)

1. Clonar o repositório numa pasta **sem "jira" no nome** (`git clone <url> C:\dev\painel-squads`), **num terminal comum, fora do Cline** (o hook não vale fora do Cline, e o endereço do repositório pode conter "Jira", o que o hook bloquearia). O `.gitattributes` garante LF nos `.sh` e CRLF nos `.ps1`.
2. **Primeiro, rodar o teste do hook nos dois PowerShell** (na raiz do projeto, num terminal PowerShell):
   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .clinerules\hooks\test-PreToolUse.ps1
   pwsh -NoProfile -ExecutionPolicy Bypass -File .clinerules\hooks\test-PreToolUse.ps1
   ```
   Os dois devem terminar com `falhas: 0` (o segundo só se o PowerShell 7 estiver instalado; o Cline o usa quando existe). **Se algum caso falhar, não ligue o hook nem siga com o trabalho:** relate ao usuário a saída completa como pergunta com opções (`/perguntar-ao-usuario.md`).
   Num PC corporativo, a política de execução definida por Group Policy (MachinePolicy/UserPolicy) vence o `-ExecutionPolicy Bypass`, e AppLocker/WDAC podem forçar o modo de linguagem restrito; nos dois casos o hook não roda. Se o teste nem começar, mande ao usuário a saída de `Get-ExecutionPolicy -List` e de `$ExecutionContext.SessionState.LanguageMode`.
3. No Cline/ClineSR: Settings → Features → ligar **Enable Hooks** (se o ClineSR não tiver essa opção, relate ao usuário).
4. Teste de fumaça, com a flag ausente ou `false`: pedir ao Cline para rodar `Invoke-WebRequest https://jira.exemplo.invalid` e para abrir `https://jira.exemplo.invalid` com o navegador; os dois devem ser bloqueados com a mensagem "BLOQUEADO". Pedir para ler `README.md` deve funcionar.
5. Se o teste de fumaça não bloquear, o hook não está ativo: siga só com as regras escritas e avise o usuário.

### Instalar no macOS/Linux

1. Clonar (o arquivo já vem executável; se não vier, `chmod +x .clinerules/hooks/PreToolUse`). Ter `jq` e `bash`.
2. Ligar **Enable Hooks**.
3. `sh .clinerules/hooks/test-PreToolUse.sh` e `sh .claude/hooks/test-guard.sh`: os dois com `falhas: 0`.
4. CLI do Cline: o script aceita o formato da CLI, mas ela o procura em `.cline/hooks/PreToolUse.sh`; criar o link só se o usuário confirmar que usa a CLI (não testado).

## Proibições que NÃO podem depender de hook

O hook pode estar desligado, não existir no ClineSR, ou ser contornado por acidente. Estas regras valem sempre, e quem as vigia é a própria IA:

> 1. Com `JIRA_LIVE_ENABLED` diferente de `true`, é proibido acessar qualquer servidor Jira por qualquer caminho: app, `curl`, `Invoke-WebRequest`, navegador, `web_fetch`, script, MCP da Atlassian. Nesse período, nem a documentação pública em endereços com `jira`/`atlassian`: peça um trecho ao usuário ou use a busca genérica. Só o usuário muda a flag; nunca a ligue, nem dentro de um comando.
> 2. Nunca edite, leia nem commite `.env`, `config/papeis.yaml`, `config/equipes.yaml`, `config/ausencias.yaml`, `config/squads.yaml`; nunca edite nem commite `Filtros_Status_tool.txt`. Use os modelos `.exemplo`/`.demo`.
> 3. Nunca use `git add -A`, `git add .` sem conferir, nem `git add -f`/`--force`.
> 4. Nunca exiba token, senha ou URL com credencial no terminal, em log, em erro ou em commit; nunca liste todas as variáveis de ambiente.
> 5. Nunca reescreva um comando bloqueado para ele passar. Pare e relate ao usuário.
> 6. Se o Docker estiver parado, inicie-o ou relate; nunca troque o PostgreSQL real por H2 ou mock.

Checklist manual antes de cada comando que toca rede ou arquivo sensível: "este comando ou URL fala com o Jira?", "a flag é `true`?", "toca `.env` ou config real?", "vai para o git?". Qualquer dúvida: pergunta ao usuário com opções (`/perguntar-ao-usuario.md`).
