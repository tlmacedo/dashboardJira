# Spec da Fase 0: preparar o PC da empresa (rede segura, sem Docker)

**Objetivo:** deixar o clone do projeto, no PC da empresa (Windows 11, rede com acesso ao Jira), compilando, testando e subindo o painel, antes de qualquer plano de código. É a **primeira coisa que o ClineSR executa**. Não há código novo, exceto, se o plano mandar, ajustes de documentação. Plano: `docs/superpowers/plans/2026-10-27-plano-00-pc-da-empresa.md`.

## Contexto e restrições

- A empresa bloqueia Docker e PostgreSQL (Fase 3B, executada). O ambiente é JDK 21 + Node 22 + pnpm; banco SQLite em arquivo.
- A rede da empresa tem proxy e, muito provavelmente, **inspeção TLS** (certificado raiz próprio). Sintomas: Gradle/pnpm/Git falham com `PKIX path building failed` / `UNABLE_TO_GET_ISSUER_CERT_LOCALLY`, ou ficam sem resposta. **Nunca desative a verificação de certificado**: ensine a JVM/Node/Git a confiar na CA da empresa (repositório de certificados do Windows).
- Repositórios de dependências (Maven Central, Gradle Plugin Portal, npm) podem exigir **espelho interno** da empresa. Endereços e credenciais do espelho são dados da empresa: ficam em arquivos do **usuário** (`%USERPROFILE%\.gradle\gradle.properties`, `.npmrc`/configuração do pnpm), **fora do repositório**, nunca commitados.
- A política de execução do PowerShell pode barrar `.ps1`: use escopo de processo, nunca altere a política da máquina inteira.
- O Jira só pode ser acessado quando o usuário mudar `JIRA_LIVE_ENABLED` para o valor verdadeiro em `app/.env` (única trava). Esta fase **não** liga a flag e **não** acessa o Jira.

## Decisões já tomadas

- Fuso America/Manaus; comandos em PowerShell (5.1 ou 7); porta 8080 só em `127.0.0.1`.
- Segredos (token do Jira, credenciais de espelho/proxy) só em arquivos do usuário fora do git ou no `app/.env`; a IA não lê nem edita `app/.env`.
- `gh` autenticado é desejável (PR e merge pelo fluxo de entrega); sem ele o fluxo para na PR (`docs/superpowers/fluxo-de-entrega.md`, seção 6).

## Critérios de aceitação

1. `java -version` = 21; `node -v` = 22.x; `pnpm -v` presente; `git` e (de preferência) `gh` autenticados.
2. `cd app\backend; .\gradlew.bat test` conclui com **0 falhas** (a contagem registrada no progresso).
3. `cd app\frontend; pnpm install --frozen-lockfile; pnpm vitest run; pnpm build` concluem com 0 falhas.
4. `.\app\iniciar.ps1` com dados fictícios sobe o painel e `GET http://127.0.0.1:8080/api/health` responde `{"ok":true}`.
5. O teste dos hooks do Cline (`.clinerules\hooks\test-PreToolUse.ps1`) passa; os hooks estão ligados no Cline.
6. Existe `app/.env` (copiado do modelo, **com a flag desligada**) e as configurações reais de pessoas/calendário foram criadas **pelo usuário** a partir dos modelos, fora do git.
7. Nada de configuração da empresa (hosts, credenciais, certificados) entrou no repositório (`git status` limpo).

## Pontos de parada (perguntar ao usuário)

- Falta JDK 21, Node ou pnpm e a instalação exige privilégio/aprovação da TI.
- Falha de certificado/proxy que exija dados que só a TI tem (endereço do espelho, arquivo da CA).
- Necessidade de qualquer acesso ao Jira (resposta padrão: não; o usuário liga a flag à mão).
