# Hooks: o que o Claude Code fazia e como fica no Cline (Windows 11 e macOS/Linux)

O executor é o **ClineSR** (variante Samsung do Cline, com o modelo Gauss 3.7) num **PC com Windows 11**. Tudo o que está abaixo foi conferido no Cline público; se o ClineSR tem hooks como o Cline público é **não confirmado** (teste de fumaça no fim deste documento).

## Hooks do Claude Code neste projeto

Configurados em `.claude/settings.json` (versionado):

| Evento (Claude) | Quando | Script | O que faz |
|---|---|---|---|
| `SessionStart` (assíncrono) | Ao abrir a sessão | `.claude/hooks/ensure-docker.sh` | Se o Docker não responde, abre o Docker Desktop em segundo plano e espera até 120 s; com `POSTGRES_PASSWORD` definida, sobe o `postgres` do `app/docker-compose.yml` |
| `PreToolUse` em `Bash(*gradlew*)` e `Bash(docker *)` | Antes de testes e comandos Docker | `ensure-docker.sh` | O mesmo, antes de precisar do banco |
| `PreToolUse` em ferramentas MCP `*tlassian*` e `Bash` | Antes de cada comando | `.claude/hooks/guard.sh jira` | Bloqueia: ferramentas MCP do Atlassian; qualquer variável `JIRA_*` (exceto a própria flag); URL com `jira`/`atlassian`; ferramenta de rede (`curl`, `wget`, `ssh`...) junto da palavra Jira; `jiradash check`, `sync` ou `serve`; ligar `JIRA_LIVE_ENABLED` dentro do comando |
| `PreToolUse` em `Write`, `Edit`, `NotebookEdit`, `Bash` | Antes de escrever ou rodar | `.claude/hooks/guard.sh arquivos` | Bloqueia editar `.env` (exceto `.env.example`/`.env.sample`) e `config/{equipes,ausencias,papeis,squads}.yaml`; bloqueia comandos que escrevem, copiam, removem ou versionam esses arquivos; bloqueia `git add -f`/`--force`. **Só o usuário altera** `.claude/settings*.json`, `*compose*override*` e **todo** `*compose*.y*ml` (inclusive `app/docker-compose.yml`): escrita pela IA bloqueada, leitura liberada. Caminho normalizado antes de casar; comando que cita esses arquivos é **negado por padrão** (só leitura conhecida passa); gravar texto que liga a flag em **qualquer** arquivo é bloqueado (detalhes abaixo) |

Testes: `sh .claude/hooks/test-guard.sh`. O `guard.sh jira` lê `JIRA_LIVE_ENABLED` do ambiente do Claude Code: com a flag `true` (espaços nas pontas ignorados, sem diferenciar maiúsculas) as regras de acesso ao Jira deixam de bloquear e só continua barrado exibir token ou senha no terminal; ausente, vazia, `false` ou inválida mantém tudo barrado, inclusive ligar a flag dentro do comando. O modo `arquivos` não depende da flag. Como o usuário liga a flag do hook: `HANDOFF.md` ("Dica do hook").

### Arquivos que só o usuário altera e conteúdo que liga a flag (onda final do Plano 4A, 26/10/2026)

Vale nos três hooks (`guard.sh`, `PreToolUse` e `PreToolUse.ps1`), com **qualquer** valor da flag:

- **Arquivos que ligam a flag:** `.claude/settings*.json` (o Claude Code lê `env` deles), `*compose*override*` e todo `*compose*.y*ml` (o `environment:` do compose repassa a flag ao painel), e os `env_file` conhecidos (`.env`, `app/.env`, já protegidos). A IA não escreve neles; o usuário edita à mão. **Isso inclui o `app/docker-compose.yml` comum**: mudança nele é pedida ao usuário como pergunta com opções.
- **Normalização antes de casar** (Write/Edit/NotebookEdit e as ferramentas de escrita do Cline): minúsculas; `\` vira `/`; barras duplas colapsadas; `.` e `..` resolvidos; espaços nas pontas e pontos/espaços do fim de cada segmento removidos; prefixo de dispositivo e letra de unidade retirados; **qualquer `:` depois da unidade bloqueia**; **nome curto 8.3** no nome do arquivo, na pasta `.claude` (`CLAUDE~1`) ou em qualquer segmento dentro dela bloqueia. No `guard.sh`, caminho relativo é resolvido contra o `cwd` do payload.
- **Conteúdo:** o texto gravado (`content`, `new_string`, `edits[].new_string`, `new_source` no Claude; `content`, `diff`, `replace`, `new_str` e o texto do `apply_patch` no Cline) não pode conter `JIRA_LIVE_ENABLED` seguido de `=`/`:` (com `]`, aspas, espaços ou vírgula opcionais; cobre `os.environ['...'] = ...` e `env("...", "...")`) e valor `1` ou `true`, sem diferenciar maiúsculas. Vale para **qualquer** arquivo. Em comando, a mesma expressão é barrada quando há escrita (`>`, `>>`, `tee`, `sed -i`, `cp`, interpretadores...). Liberados: o valor `false` e o padrão do compose (`${JIRA_LIVE_ENABLED:-false}`).
  - **Falso positivo aceito:** a documentação não pode citar a flag ligada no formato de atribuição; escreva "a flag em true" ou "`JIRA_LIVE_ENABLED` com valor true". Os testes montam o valor por variável (`T=true`).
- **Comandos: negação por padrão.** Se o comando (normalizado: minúsculas, `/`, sem `//` nem `/./`) cita `settings*.json` (em qualquer pasta), `.claude` junto de `settings` (cobre `cd .claude`/`pushd .claude` seguido de escrita em `settings*`), `*compose*.y*ml`, `*compose*override*` ou um nome curto 8.3 deles, ele só passa se **todo** trecho (separado por `;`, `&&`, `||`, `|`, `&`, `(`, `)`, `` ` `` e quebra de linha; chaves não separam, para não partir scripts de `awk`, e `{ cp ...; }` começa por `{` e é negado) começar por leitura conhecida e não houver redirecionamento de escrita (`>`, `>>`, `&>`, `1>`; `2>&1` e `>/dev/null` são inofensivos):
  - leitura: `cat ls grep egrep fgrep rg head tail diff wc stat file more jq cmp readlink realpath basename dirname nl shasum sha256sum md5 md5sum`, além de `cd pushd popd echo printf true test` (só escrevem com redirecionamento) e, para o Cline no Windows, `type Get-Content gc Select-String sls findstr`;
  - com ressalva: `less` (sem `-o`/`--log-file`), `sed` (sem `-i`/`-I`/`--in-place` e sem os comandos `w`/`e` no script), `awk`/`gawk` (sem `system`, `-i` nem `inplace`), `git` só `diff log show status blame ls-files grep rev-parse cat-file shortlog` (sem `--output`), `docker`/`docker-compose` (sem `cp`, `-o`, `--output`; libera `docker compose -f app/docker-compose.yml up`);
  - todo o resto é bloqueado, por exemplo `python`/`python3`/`perl`/`node`/`ruby`/`php`/`pwsh`/`powershell`, `git checkout/restore/apply/stash/add/commit`, `patch`, `rsync`, `curl -o`, `wget -O`, `dd`, `install`, `ln`, `cp`, `mv`, `rm`, `touch`, `chmod`, `truncate`, `tee`, `sort -o`, `yq -i`, `xargs`, `sh -c`/`bash -c` e `Set-Content`/`Copy-Item`.
  - **Falsos positivos aceitos:** `settings*.json` de outras pastas (`.vscode/settings.json`), `git add`/`git commit -m` que citam o compose, e `>` dentro de aspas num comando que cita esses arquivos.
- **Limites que continuam (trava contra acidente, não contra má-fé):** nomes montados por variável ou por glob (`cp x .cl*/set*`, `F=.cla; cp x ${F}ude/...`), valor da flag calculado em tempo de execução, um interpretador que monta o caminho sozinho sem citá-lo, e o comando `w` do `sed` escrito de formas não reconhecidas. O matcher do `.claude/settings.json` cobre `Write|Edit|NotebookEdit|Bash`; uma ferramenta `MultiEdit` (se existir na versão do Claude Code) só passa pelo hook se o usuário a incluir no matcher.

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
| `guard.sh jira` + `guard.sh arquivos` (macOS/Linux) | `.clinerules/hooks/PreToolUse` (bash, executável): adaptador que chama o `guard.sh` e acrescenta os extras do Cline | **Testado no macOS** (`sh .clinerules/hooks/test-PreToolUse.sh`, 447 casos, 0 falhas em 26/10/2026) |
| `guard.sh` (Windows) | `.clinerules/hooks/PreToolUse.ps1`: as regras do `guard.sh` **reescritas** em PowerShell, mais os extras do Cline, sem `jq`, `sh` nem `bash` | **Não executado no Windows.** Rodado só com PowerShell 7.5 no macOS (`test-PreToolUse.ps1`, 562 casos, 0 falhas em 26/10/2026). PowerShell 5.1 e Windows 11: **o ClineSR roda o teste primeiro** (abaixo) |
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
- **Configs reais protegidas pelo nome, em qualquer pasta** (não só `config/`): `equipes`, `ausencias`, `papeis` e `squads` com `.yaml` ou `.yml`, e `Filtros_Status_tool.txt*` (este agora também para `read_file` e escrita). Um relatório como `reports/Filtros JQL para cards Jira.md` não é afetado.
- Listar todo o ambiente (`env`, `printenv`, `set` sozinhos, `Get-ChildItem env:`, `[Environment]::GetEnvironmentVariables()`): mostraria os tokens.

**Busca e listagem (com qualquer valor da flag; desde 26/10).** O resultado de `search_files` traz linhas dos arquivos, então uma busca sem filtro exporia `.env` e as configs reais.
- **Busca de conteúdo** (`search_files` e equivalentes da CLI: `grep`, `grep_search`, `codebase_search`) só é liberada quando **todas** as condições valem:
  1. o `path` não é protegido (normalização abaixo);
  2. o `regex` não cita `.env`/`.env.*` (exceto `.env.example`/`.env.sample`), os YAML protegidos nem `filtros_status_tool` (só a checagem de nome: `TODO:` e outras regex com `:` continuam válidas);
  3. **há `file_pattern` seguro**: um nome exato não protegido **com extensão segura** (por exemplo `.env.example`, `build.gradle.kts`, `README.md`) ou da lista curta de arquivos conhecidos sem extensão (`Dockerfile`, `Makefile`, `gradlew`, `gradlew.bat`, `README`, `LICENSE`, `.gitignore`, `.gitattributes`, `.editorconfig`), ou um glob cujo nome termina numa extensão segura — `.kt .kts .java .gradle .ts .tsx .js .jsx .mjs .cjs .json .css .scss .html .md .sql .py .sh .ps1 .xml .svg .toml .exemplo.yaml .demo.yaml .example .sample` — com no máximo um grupo `{a,b}` em que todas as alternativas são seguras (`*.{kt,tsx,md}`); grupo vazio ou com alternativa vazia (`*{.kt,}`, `{,.kt}`, `*{.kt,,.md}`, `*{}`), chave sem par e grupos aninhados ou repetidos bloqueiam, porque a alternativa vazia expande para `*`. Nome exato fora disso (`config`, `app/config`, `secrets`) bloqueia, porque pode ser uma pasta e o ripgrep buscaria tudo dentro dela. Negação (`!`), `:`, `*`, `*.yaml`, `*.txt`, `.env*` e grupos com extensão insegura bloqueiam;
  4. ou, sem `file_pattern`, o `path` é um único arquivo com extensão segura (`docs/README.md`).
  Sem `file_pattern` em pasta (inclusive a raiz ou `app/config`): **bloqueado**.
- **Listagem** (`list_files`, `list_code_definition_names`, `glob`, `list_dir`): mostra só nomes (e definições de código-fonte), então fica liberada, exceto se o `path`/padrão for protegido (`list_files app/.env`).
- **Como autorizar uma busca bloqueada:** refazer a busca com `file_pattern` restrito a extensões seguras é a **forma permitida e documentada aqui**, não um contorno da regra 5 abaixo; a mensagem do hook diz isso. Qualquer outra busca (por exemplo, dentro de arquivos `.yaml` ou `.txt` em geral) vai para o usuário como pergunta: só ele autoriza, rodando a busca fora do Cline e colando o trecho relevante.

**Normalização de caminhos (ler, escrever, buscar e listar; macOS e Windows não diferenciam maiúsculas).** Antes de casar o nome, nesta ordem: minúsculas; `\` vira `/`; remove o prefixo de dispositivo (`\\?\`, `\\?\UNC\`, `\\.\`, `\??\`); remove a letra da unidade (`C:`); **qualquer `:` restante bloqueia** (fluxo alternativo do NTFS `.env::$DATA`, `README.md:oculto`); resolve `..` e ignora `.` e segmentos vazios; tira espaços das pontas e **pontos e espaços do fim de cada segmento** (`.Env.`, `.env `, `EQUIPES.yaml ` = o original no Windows); **nome de arquivo (último segmento) em forma curta 8.3** (`~` seguido de dígito, como `ENV~1`) bloqueia; pastas como `C:\Users\THIAGO~1\...` continuam liberadas. Assim `.ENV`, `Papeis.YAML`, `..\.env`, `\\?\C:\x\.env` e caminhos com barras mistas (Git Bash) são barrados. No `.ps1` a normalização usa só operações de texto (o `[IO.Path]` do pwsh no macOS/Linux não trata `\` como separador). Em comandos, a expressão dos protegidos também casa `.env::...`, `.env.` e nomes curtos com prefixo de nome protegido (`ENV~1`, `PAPEIS~1.YAM`), sem pegar `HEAD~1`; no `.sh`, a escrita por comando passou a ser checada também sem diferenciar maiúsculas (o `guard.sh` diferencia).

**Liberados:** leitura de arquivos comuns, buscas com escopo seguro (acima), listagens fora de caminhos protegidos, `web_search`, navegador e `web_fetch` fora do Jira, outros servidores MCP.

**Flag:** lida do ambiente do processo do Cline (do VS Code), **não** do `app\.env` nem do comando. No Windows, só o usuário a define (por exemplo, variável de usuário nas Configurações do Windows) e depois reabre o VS Code.

**`.clineignore`:** o arquivo `.clineignore` na raiz impede o Cline de ler segredos e dados pessoais (`.env`, arquivos de `config/` com dados reais, chaves) e reforça o hook, sem substituí-lo.

**Falha fechada:** JSON ilegível, entrada vazia, JSON que não é objeto, entrada sem nome de ferramenta ou erro interno cancelam a ferramenta. No macOS/Linux, sem `jq` ou sem o `guard.sh`, também. No `.sh`: `set -o pipefail`, cada leitura com `jq` checada (`|| bloquear`), as decisões de bloqueio usam `grep` com here-string (sem pipe: com `printf | grep -q` e `pipefail`, um comando grande gerava SIGPIPE e virava liberação), erro do `grep` (código 2) conta como protegido e uma `trap` de saída imprime `cancel: true` com código 0 se o script terminar sem responder (por exemplo, variável não definida com `set -u`). No `.ps1`: `try/catch` em volta de tudo, com JSON fixo como último recurso.

**Duas cópias das regras:** o `.sh` chama sempre o `guard.sh jira` e o `guard.sh arquivos` (a expressão de token do Cline é só um reforço mais estrito); o `.ps1` reescreve as mesmas regras. Ao mudar o `guard.sh`, mude também o `PreToolUse.ps1` e o `test-PreToolUse.ps1` (que espelha caso a caso o `test-guard.sh`). **Verificado em 26/10/2026** contra o `guard.sh` consciente da flag (Plano 4A, Tarefa 11): mesma leitura da flag (espaços só nas pontas, sem diferenciar maiúsculas, só `true` libera; `tr ue`, `true;`, `yes`, `1`, vazia e ausente travam), mesmo bloqueio de exibir segredo com a flag `true` e ligar a flag dentro do comando continua barrado com a flag diferente de `true`.

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
> 2. Nunca edite, leia nem commite `.env`, `config/papeis.yaml`, `config/equipes.yaml`, `config/ausencias.yaml`, `config/squads.yaml` (nem por busca: `search_files` só com `file_pattern` seguro); nunca edite nem commite `Filtros_Status_tool.txt` (o hook também barra lê-lo). Use os modelos `.exemplo`/`.demo`.
> 3. Nunca use `git add -A`, `git add .` sem conferir, nem `git add -f`/`--force`.
> 4. Nunca exiba token, senha ou URL com credencial no terminal, em log, em erro ou em commit; nunca liste todas as variáveis de ambiente.
> 5. Nunca reescreva um comando bloqueado para ele passar. Pare e relate ao usuário.
> 6. Se o Docker estiver parado, inicie-o ou relate; nunca troque o PostgreSQL real por H2 ou mock.

Checklist manual antes de cada comando que toca rede ou arquivo sensível: "este comando ou URL fala com o Jira?", "a flag é `true`?", "toca `.env` ou config real?", "vai para o git?". Qualquer dúvida: pergunta ao usuário com opções (`/perguntar-ao-usuario.md`).
