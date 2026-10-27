# Plano 0: preparar o PC da empresa (rede segura, sem Docker)

> **Para o executor (ClineSR):** leia antes `docs/guia-execucao-outra-ia.md`, `docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`, `docs/superpowers/fluxo-de-entrega.md` e a spec `docs/superpowers/specs/2026-10-27-spec-fase-00-pc-da-empresa.md`. Este plano é **operacional**: quase não gera código. Cada tarefa tem um comando, o resultado esperado e o que fazer se falhar. Quando o resultado esperado não vier, **não improvise**: siga a coluna "Se falhar" ou pergunte ao usuário (pergunta com opções, recomendada primeiro).

**Goal:** clone compilando, testando e subindo o painel no PC da empresa, com ferramentas de rede (proxy, certificado da empresa, espelhos) configuradas **fora do repositório**.

**Restrições globais:** não ligar a flag do Jira, não ler nem editar `app/.env`, não acessar o Jira, não desativar verificação de certificado (nem `-k`, nem `NODE_TLS_REJECT_UNAUTHORIZED=0`, nem `strict-ssl=false`), não commitar endereços/credenciais da empresa. Comandos em PowerShell (5.1 ou 7; no 5.1 não existe `&&`).

**Branch:** `feat/pc-da-empresa` (fluxo de entrega, seção 1). Como o plano quase não altera arquivos, o PR traz só o arquivo de progresso, o `HANDOFF.md` e eventuais correções de documentação.

---

### Tarefa 1: Inventário das ferramentas

Rode e anote as versões no progresso (nada disso vai para o git além de "ok/versão"):
```powershell
java -version 2>&1 | Select-Object -First 1
node -v
pnpm -v
git --version
gh --version
gh auth status
$PSVersionTable.PSVersion.ToString()
```
Esperado: Java 21.x, Node v22.x, pnpm 9.x, git 2.4x, `gh` instalado e `Logged in`.
Se falhar:
- Sem JDK 21: instalar Temurin/OpenJDK 21 (zip, sem instalador, em pasta do usuário, e `JAVA_HOME` apontando para ela). Precisando de privilégio ou aprovação da TI: **pergunte**.
- Sem Node 22: instalar o zip do Node 22 LTS em pasta do usuário e acrescentar ao `PATH` do usuário.
- Sem pnpm: `npm install -g pnpm@9` (ou `corepack enable; corepack prepare pnpm@9 --activate`).
- `gh` sem login: **peça ao usuário** `gh auth login` (é interativo e envolve o token dele; a IA não o executa). Sem `gh`, o fluxo de entrega para na PR; registre isso no progresso.
Critério: versões anotadas; pendências listadas.

### Tarefa 2: Confiança no certificado da empresa (inspeção TLS) e proxy

Só faça esta tarefa se a Tarefa 3 ou 4 falhar com erro de certificado (`PKIX path building failed`, `unable to find valid certification path`, `UNABLE_TO_GET_ISSUER_CERT_LOCALLY`, `SSL certificate problem`) ou de proxy (`407`, `connect timed out`). Se tudo passar, escreva "não necessária" no progresso e vá à Tarefa 5.

1. **JVM/Gradle** — fazer a JVM usar o repositório de certificados do Windows (onde a TI já instala a CA da empresa):
   - Variável de ambiente do usuário (vale para o Gradle Wrapper, que baixa a distribuição antes do daemon):
     ```powershell
     [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS', '-Djavax.net.ssl.trustStoreType=Windows-ROOT', 'User')
     ```
     Abra um **novo** terminal depois disso.
   - Arquivo do usuário `%USERPROFILE%\.gradle\gradle.properties` (crie se não existir; **fora do repositório**):
     ```
     systemProp.javax.net.ssl.trustStoreType=Windows-ROOT
     ```
   - Proxy (somente se a rede exigir; valores fornecidos pelo usuário/TI, nunca inventados): no mesmo arquivo, `systemProp.http.proxyHost`, `systemProp.http.proxyPort`, `systemProp.https.proxyHost`, `systemProp.https.proxyPort`, `systemProp.http.nonProxyHosts=localhost|127.0.0.1`. Usuário/senha do proxy (`systemProp.https.proxyUser`/`proxyPassword`) **só nesse arquivo do usuário**.
2. **Git** — usar o repositório de certificados do Windows: `git config --global http.sslBackend schannel`. Proxy: `git config --global http.proxy <url fornecida pelo usuário>`.
3. **Node/pnpm** — exigir da TI/usuário o arquivo `.pem` da CA da empresa (a IA **não** exporta certificados por conta própria; **pergunte** onde está). Então: `[Environment]::SetEnvironmentVariable('NODE_EXTRA_CA_CERTS', '<caminho do .pem>', 'User')` e novo terminal. Proxy/espelho do pnpm: `pnpm config set https-proxy <url>` e `pnpm config set registry <url do espelho>` (valores do usuário).
4. **Espelho Maven** (só se o acesso direto a `repo.maven.apache.org`, `plugins.gradle.org` e `services.gradle.org` for bloqueado): pergunte ao usuário o endereço do espelho e crie **no perfil dele** `%USERPROFILE%\.gradle\init.d\espelho.gradle.kts` redirecionando `repositories`; nunca altere `build.gradle.kts` do projeto para isso.

Critério: `./gradlew.bat --version` e `pnpm view react version` funcionam sem erro de certificado. Nenhum arquivo dessas etapas está dentro do repositório (`git status` limpo).

### Tarefa 3: Backend compila e os testes passam

```powershell
cd app\backend
.\gradlew.bat --version
.\gradlew.bat test --rerun-tasks
```
Esperado: `BUILD SUCCESSFUL`; relatório em `app\backend\build\reports\tests\test\index.html` com **0 falhas** (na entrega deste plano eram 745+ testes). Anote o total.
Se falhar:
- Erro de certificado/proxy → Tarefa 2.
- `Unsupported class file major version` ou erro de toolchain → o `java` do PATH não é o 21: ajuste `JAVA_HOME`.
- Teste de documentação falhando por texto de Docker/Postgres → é defeito do repositório: corrija o documento citado (mínimo), não o teste.
- Falha de teste de banco com "database is locked" → feche qualquer painel/processo Java usando `app\data\painel.db` e rode de novo.
- Outras falhas: **não** ajuste teste para passar. Registre o nome e a mensagem, pergunte ao usuário.

### Tarefa 4: Frontend instala, testa e gera o build

```powershell
cd app\frontend
pnpm install --frozen-lockfile
pnpm vitest run
if ($LASTEXITCODE -eq 0) { pnpm build }
```
Esperado: testes sem falhas (48+) e `app\frontend\dist\index.html` criado. Falha de rede/certificado → Tarefa 2. `ERR_PNPM_OUTDATED_LOCKFILE` → não rode `pnpm install` sem `--frozen-lockfile`; pergunte.

### Tarefa 5: Política de execução do PowerShell e subida com dados fictícios

1. Se `.ps1` for bloqueado ("execução de scripts foi desabilitada"): rode apenas no escopo do processo, sem mexer na política da máquina:
   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\app\iniciar.ps1 -SemBuildDoFront
   ```
   (a política de grupo da empresa pode impedir até isso: **pergunte**; alternativa: `cd app\backend; .\gradlew.bat run`, que não usa `.ps1`).
2. Para os dados fictícios: `$env:DASHBOARD_DEMO_DATA = 'true'` **só na sessão** (nunca no `app/.env`) e depois `.\app\iniciar.ps1 -SemBuildDoFront`.
3. Em **outro** terminal, na pasta `app` (para não citar o nome do repositório no comando):
   ```powershell
   Invoke-RestMethod http://127.0.0.1:8080/api/health
   Invoke-RestMethod http://127.0.0.1:8080/api/meta
   ```
   Esperado: `ok = True`; `meta` com `devMode = True`, `liveEnabled = False` e o banner de dados fictícios. Abra `http://127.0.0.1:8080` no navegador e confira que a interface carrega.
4. Pare o painel (Ctrl+C), apague `app\data\painel.db*` (dados fictícios) e remova a variável da sessão: `Remove-Item Env:DASHBOARD_DEMO_DATA`.
Se a porta 8080 estiver ocupada: `Get-NetTCPConnection -LocalPort 8080` mostra o dono; pergunte antes de encerrar processos que não sejam seus.
Critério: health ok e tela carregando. Em `/api/meta`, `liveEnabled` tem que ser `False`.

### Tarefa 6: Hooks de proteção do Cline

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .clinerules\hooks\test-PreToolUse.ps1
```
Esperado: todos os casos passam. Confirme no Cline que **Enable Hooks** está ligado e que o hook usado é `PreToolUse.ps1` (`docs/ferramentas-claude-para-o-cline/hooks.md`). Se algum caso falhar, **pare** e pergunte: sem o hook funcionando, esta IA não prossegue para planos que toquem em segurança.

### Tarefa 7: Configuração local do painel (ação do USUÁRIO)

Esta tarefa é do **usuário**; a IA só entrega o passo a passo e **espera** (pergunta com opções: "já fiz", "preciso de ajuda com algum arquivo", "pular por enquanto"). A IA **não lê nem edita** `app/.env` nem os YAML reais.
```powershell
Copy-Item app\.env.example app\.env
Copy-Item app\config\papeis.exemplo.yaml        app\config\papeis.yaml
Copy-Item app\config\calendario.exemplo.yaml     app\config\calendario.yaml
Copy-Item app\config\status_map.exemplo.yaml     app\config\status_map.yaml
Copy-Item app\config\jira_campos.exemplo.yaml    app\config\jira_campos.yaml
Copy-Item app\config\vinculos.exemplo.yaml       app\config\vinculos.yaml
```
O usuário edita `papeis.yaml` (o `id` de cada pessoa é o **username do Jira**, em minúsculas, e o papel define a visão; deve haver ao menos um GERENTE vigente) e confere feriados. `app/.env` fica com a flag do Jira **desligada**; URL e token do Jira entram no `.env` só no Plano 14 (checklist da primeira conexão). Critério: `git status` não lista nenhum desses arquivos (estão no `.gitignore`).

### Tarefa 8: Fechamento (progresso, HANDOFF e entrega)

1. No arquivo de progresso: versões das ferramentas (Tarefa 1), contagem de testes (Tarefas 3 e 4), quais ajustes de rede foram necessários **sem** endereço, credencial ou caminho de certificado da empresa (só "JVM usa repositório do Windows", "NODE_EXTRA_CA_CERTS definido", "espelho configurado"), resultado das Tarefas 5 e 6 e se o `gh` está autenticado.
2. `HANDOFF.md`: estado "PC da empresa preparado; próximo: Plano 9 (CI e endurecimento), depois Plano 14a".
3. Siga `docs/superpowers/fluxo-de-entrega.md` (seções 3 a 6) para o PR e o merge. Se o `gh` não estiver autenticado, pare na PR (seção 6, penúltimo item).

## Foco da revisão (revisor independente)

- Nenhum endereço, credencial, caminho de certificado ou nome de usuário da empresa no diff nem nas mensagens de commit.
- Nada de verificação de certificado desativada em documento, script ou configuração.
- A flag do Jira continua desligada; nenhum comando do plano acessa o Jira.
- O progresso registra números reais (não "deve passar").
