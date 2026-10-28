# Runbook: ligar a sincronização com o Jira

Procedimento para o **usuário** ligar, acompanhar e desligar a sincronização periódica do Painel de Squads. Vale para o ClineSR (Windows 11, PowerShell) e para macOS (zsh). Onde os comandos diferem, há um bloco para cada sistema.

> **Aviso.** A flag `JIRA_LIVE_ENABLED` é a **única trava** de acesso ao Jira (regra 1 do `CLAUDE.md`, 25/10). Com `false`, ninguém acessa o Jira: nem o app, nem o agendador, nem IA, pessoa, MCP, `curl` ou script. Com `true`, o app sincroniza sozinho e as ferramentas ficam liberadas. **Só o usuário muda a flag; nenhuma IA a liga.** O primeiro ciclo real segue o [checklist da primeira conexão](checklist-primeira-conexao.md) como procedimento cuidadoso recomendado.

Este documento não contém host, token nem username: eles ficam só no ambiente do usuário.

**Testes automáticos.** Os testes automáticos do projeto usam só fixtures e `MockEngine`, mesmo com a flag ligada: nenhum deles acessa o Jira real nem um provedor de login real. A prova técnica são os testes `SomenteAFabricaCriaClientesJira` (só a fábrica cria cliente do Jira) e `FlagDesligadaZeroRequisicoesTest` (com a flag desligada, zero requisição). Ligar a flag só muda o comportamento do painel em uso; a suíte continua offline.

**Pacote de produção.** O painel roda como pacote (`installDist` + build do frontend), sem contêiner e com SQLite em arquivo; a montagem, a verificação e as pastas estão em [`docs/implantacao.md`](../implantacao.md). Em produção o painel só sobe com o login SSO/OIDC configurado e **com o Jira desligado**: a flag é ligada depois, à mão, pelo usuário.

## Pré-requisitos

- Passos 1 a 5 do [checklist](checklist-primeira-conexao.md) concluídos e registrados:
  - o `timeZone` lido em `/myself` está anotado e vira `SYNC_FUSO_SERVIDOR` (se o servidor não estiver em America/Manaus);
  - o `customId` de cada campo está preenchido no `jira_campos.yaml` real;
  - o `status_map.yaml` real tem os status reais do TG-Basic.
- JDK 21 e Node 22 com pnpm instalados (o painel roda direto na máquina, com banco SQLite em arquivo).
- Configs reais criadas a partir dos modelos (próxima seção) e **nunca versionadas**.

## Configs reais necessárias

Todas ficam em `app/config/` (ou em `PAINEL_CONFIG_DIR`). Crie cada uma copiando o modelo e ajustando; o `.gitignore` já ignora o que tem dado real. **Nunca faça commit delas.**

| Arquivo real | Modelo | Observação |
|---|---|---|
| `.env` (em `app/`) | `.env.example` | URL e token de cada instância, flags e `SYNC_*`. O `.env` é ignorado pelo git. |
| `papeis.yaml` | `papeis.exemplo.yaml` | O `id` de cada pessoa é o username do Jira (igual ao `assignee`). |
| `calendario.yaml` | `calendario.exemplo.yaml` | Feriados e dias não úteis reais; obrigatório em produção; sem dado pessoal, mas fica fora do git como os demais reais. |
| `status_map.yaml` | `status_map.exemplo.yaml` | Nomes de status reais; obrigatório em produção. |
| `jira_campos.yaml` | `jira_campos.exemplo.yaml` | `customId` real de cada campo (passo 2 do checklist). Sem ele o app usa o modelo, só com vocabulário de exemplo. |
| `vinculos.yaml` | `vinculos.exemplo.yaml` | Tipo do link que liga a Review à task (`Blocks`) e os tipos de issue de Review e task (passo 4 do checklist). Obrigatório em produção; sem dado pessoal. |
| Fichas (`cards/*.card.yaml`) | as fichas versionadas em `app/config/cards/` | Ajuste só em cópia local se precisar de JQL próprio; a ficha `sincronizacao` define o universo sincronizado. |

macOS (zsh), a partir da raiz do repositório:

```sh
cp app/.env.example app/.env
cp app/config/papeis.exemplo.yaml app/config/papeis.yaml
cp app/config/calendario.exemplo.yaml app/config/calendario.yaml
cp app/config/status_map.exemplo.yaml app/config/status_map.yaml
cp app/config/jira_campos.exemplo.yaml app/config/jira_campos.yaml
cp app/config/vinculos.exemplo.yaml app/config/vinculos.yaml
```

Windows 11 (PowerShell), a partir da raiz do repositório:

```powershell
Copy-Item app\.env.example app\.env
Copy-Item app\config\papeis.exemplo.yaml app\config\papeis.yaml
Copy-Item app\config\calendario.exemplo.yaml app\config\calendario.yaml
Copy-Item app\config\status_map.exemplo.yaml app\config\status_map.yaml
Copy-Item app\config\jira_campos.exemplo.yaml app\config\jira_campos.yaml
Copy-Item app\config\vinculos.exemplo.yaml app\config\vinculos.yaml
```

Confira com `git status` que nenhum desses arquivos aparece como novo. Se aparecer, pare: o `.gitignore` está incompleto.

**Como os arquivos chegam ao painel.** O painel lê `app/config/` direto do disco (ou `PAINEL_CONFIG_DIR`) e as variáveis de `app/.env` na subida; variáveis já definidas no terminal têm precedência sobre o arquivo. Basta criar o arquivo e reiniciar o painel (`sh iniciar.sh` ou `.\iniciar.ps1`). Nada com dado pessoal vai para o repositório.

**Conferência dos arquivos carregados.** Depois de subir, o log da subida avisa, em modo desenvolvimento, de cada arquivo real ausente que foi trocado pelo modelo (`usando o modelo ... .exemplo.yaml`). Sem esse aviso para papéis, mapa de status e dicionário de campos, os reais foram carregados (o log nunca mostra o conteúdo). Em produção a ausência de um obrigatório derruba a subida. O log aparece no próprio terminal em que o painel foi iniciado: procure por `usando o modelo` (macOS: `| grep "usando o modelo"`; PowerShell: `| Select-String "usando o modelo"`). Saída vazia = nenhum modelo em uso.

## Variáveis

A sincronização liga **somente** pela flag `JIRA_LIVE_ENABLED` (valor verdadeiro). As variáveis `SYNC_*` só ajustam limites e horários; vazio significa o padrão. Valor fora da faixa faz o app falhar na subida, citando só o nome da variável.

| Variável | Padrão | Faixa | Efeito |
|---|---|---|---|
| `JIRA_LIVE_ENABLED` | `false` | `true`/`false` | Única chave de liga e desliga. |
| `SYNC_INSTANCIAS` | `samsung` | ids separados por vírgula | Instâncias sincronizadas. **Hoje só `samsung` funciona:** a ficha de sincronização (`sincronizacao.card.yaml`) é só da Samsung, então `sidia` falha a cada ciclo com `ERRO_INTERNO` (sem requisição) até existir ficha própria (Plano 14b). Cada instância precisa de URL e token completos. |
| `SYNC_INTERVALO_MIN` | `10` | 5 a 1440 | Minutos entre sincronizações incrementais. |
| `SYNC_HORA_RECONCILIACAO` | `03:00` | `HH:mm` | Reconciliação diária, no fuso America/Manaus. |
| `SYNC_HORA_RETENCAO` | `04:00` | `HH:mm` | Limpeza diária de dados locais (não toca o Jira). |
| `SYNC_MAX_ITENS` | `50000` | 1 a 200000 | Teto de issues por execução. |
| `SYNC_MAX_REQUISICOES` | `2000` | 1 a 20000 | Teto de requisições ao Jira por execução. |
| `SYNC_SOBREPOSICAO_MIN` | `5` | 1 a 60 | Sobreposição do cursor entre incrementais. |
| `SYNC_FUSO_SERVIDOR` | `America/Manaus` | fuso IANA | Fuso do usuário da credencial no Jira (o `timeZone` do `/myself`). |
| `SYNC_TEMPO_MAX_MIN` | `60` | 1 a 240 | Tempo máximo de uma execução. |
| `RETENTION_MONTHS` | `24` | 6 a 600 | Meses de dados locais mantidos (ver Retenção). |
| `ACCESS_LOG_RETENTION_MONTHS` | `24` | 12 a 600 | Meses da auditoria de acessos (`access_log`), independente de `RETENTION_MONTHS`. |

Todas as variáveis acima, as de conexão (`JIRA_SAMSUNG_URL`, `JIRA_SAMSUNG_TOKEN`, `JIRA_SIDIA_URL`, `JIRA_SIDIA_USER`, `JIRA_SIDIA_TOKEN`), `DATABASE_PATH` e `DASHBOARD_DEMO_DATA` são lidas de `app/.env` (vazio = padrão do código). O banco é o arquivo `data/painel.db`.

## Etapa 1: primeiro ciclo, pequeno

Objetivo: ver o app falar com o Jira com o menor volume possível. Todos os comandos rodam na pasta `app/` (a partir da raiz do repositório).

1. No `.env`, defina `SYNC_MAX_ITENS=50` e `SYNC_MAX_REQUISICOES=5`, mais URL e token da instância. **Ainda com `JIRA_LIVE_ENABLED=false`.**
2. **Conferência antes de ligar a flag.** Confirme que o `.env` tem os tetos e que as credenciais da instância estão preenchidas, sem imprimir segredo (mostra só o nome das variáveis, nunca o valor).

   macOS (zsh):

   ```sh
   grep -E "^(JIRA_LIVE_ENABLED|DASHBOARD_|SYNC_|RETENTION_MONTHS)" app/.env
   grep -E "^JIRA_[A-Z]+_(URL|USER|TOKEN)=" app/.env | awk -F= '{print $1, ($2 == "" ? "VAZIA" : "preenchida")}'
   ```

   Windows 11 (PowerShell):

   ```powershell
   Select-String -Path app\.env -Pattern "^(JIRA_LIVE_ENABLED|DASHBOARD_|SYNC_|RETENTION_MONTHS)"
   Select-String -Path app\.env -Pattern "^JIRA_[A-Z]+_(URL|USER|TOKEN)=" | ForEach-Object {
     $p = $_.Line.Trim() -split "=", 2
     "$($p[0]): " + $(if ($p[1] -eq "") { "VAZIA" } else { "preenchida" })
   }
   ```

   Esperado: `JIRA_LIVE_ENABLED=false`, `SYNC_MAX_ITENS=50`, `SYNC_MAX_REQUISICOES=5` e as credenciais da instância como `preenchida` (a da outra instância `VAZIA`).
3. **O usuário, à mão**, liga a flag `JIRA_LIVE_ENABLED` (muda o valor de `false` para o verdadeiro) no `.env`. Os hooks barram a IA de editar o arquivo de ambiente e as settings: se uma IA estiver conduzindo este runbook, ela para aqui e pede ao usuário (o passo 2 também é do usuário, porque lê as variáveis de conexão do Jira).
4. Suba (ou reinicie) o painel. O agendador faz a carga inicial sozinho, dentro desses tetos.

   macOS (zsh), na pasta `app`:

   ```sh
   sh iniciar.sh --sem-build-do-front
   ```

   Windows 11 (PowerShell), na pasta `app`:

   ```powershell
   .\iniciar.ps1 -SemBuildDoFront
   ```

5. Espere um ciclo e confira `/api/sync/status` e o card (próxima seção). O primeiro ciclo termina quando o app esgota o teto de 50 issues ou de 5 requisições; um código `TETO_ITENS` ou `ORCAMENTO_REQUISICOES` aqui é esperado, não é falha do Jira.

Para acompanhar o log, olhe o terminal em que o painel está rodando (Ctrl+C para parar).

## Etapa 2: limites padrão

1. Se a Etapa 1 estiver limpa, apague `SYNC_MAX_ITENS` e `SYNC_MAX_REQUISICOES` do `.env` (ou deixe vazios) e reinicie o painel.
2. O app faz a carga completa e depois incrementais a cada `SYNC_INTERVALO_MIN`.
3. Reconfira o status. Carga grande pode levar mais de um ciclo; o cursor só avança até o que foi gravado.

## Etapa 3: reconciliação

A reconciliação roda uma vez por dia (`SYNC_HORA_RECONCILIACAO`, padrão 03:00 de Manaus). Ela compara a lista leve do Jira com a base local: renomeia issues movidas, marca como removidas as que sumiram e rebusca as divergentes ou ausentes (isso também recupera issues que a paginação pulou numa carga longa). Depois da primeira, confira em `/api/sync/status` que `ultimaReconciliacao` foi preenchida e que a execução do tipo reconciliação terminou com status OK.

**Issue apagada no Jira:** antes de marcar uma issue como removida, o app a confirma por chave (`key in (...)`, lotes de 100). O Jira Data Center pode responder HTTP 400 a esse JQL quando uma chave citada não existe mais; nesse caso o app refaz a confirmação **chave por chave** (400 ou 404 numa chave sozinha = ausente, pode remover). Esse caminho gasta uma requisição por chave do lote do orçamento (`SYNC_MAX_REQUISICOES`) e ainda **não foi validado neste servidor**: na primeira reconciliação, confira que não houve `HTTP_400` e que `removidas` é coerente. Qualquer outro erro (401, 403, 429, 5xx, rede) segue as regras normais e nada é removido.

**Guarda de 50%:** se a remoção passaria de 50% da base local, ou o Jira vier vazio com base local, a reconciliação aborta com `RECONCILIACAO_SUSPEITA` e **não remove, não renomeia e não rebusca nada**. Isso protege contra um filtro errado ou uma resposta vazia. Não force: investigue a ficha `sincronizacao` e o escopo da credencial.

## Como ler o status

`GET /api/sync/status` (só o gerente; cada chamada é auditada). Em modo desenvolvimento (`DASHBOARD_DEV_MODE=true`) abra `/api/sync/status` no painel local (porta 8080, só localhost); a mesma informação aparece na tela **Sincronização** do painel (rota `/sincronizacao`).

- `modo`: `DESLIGADO_PELA_FLAG` ou `PERIODICO`, derivado só da flag.
- `liveEnabled`: o valor da flag.
- Por instância: `gate` (`LIBERADA` ou o motivo da negação), `cursor` (até onde o incremental leu), `ultimaReconciliacao`, `suspensa` (código da suspensão ou nulo) e as últimas `execucoes`.
- Por execução: `tipo`, `status`, `inicio`, `fim`, `issues`, `eventos`, `requisicoes`, `incompletas`, `usuariosNaoResolvidos`, `removidas` e `codigoErro`.
- `retencao`: a última limpeza diária.

A resposta traz só códigos, contagens e instantes: nunca URL, token, JQL, mensagem do Jira nem username.

## Códigos de erro

| Código | Significado | Suspende? |
|---|---|---|
| `HTTP_401`, `HTTP_403` | Credencial recusada ou sem permissão. | Sim |
| `HTTP_429` | Jira limitou a taxa. | Sim |
| `HTTP_5XX` | Erro do servidor Jira. | Só após 3 falhas seguidas |
| `REDE` | Sem resposta do Jira (rede, DNS, timeout de conexão). | Só após 3 falhas seguidas |
| `HTTP_nnn` | Outro status HTTP inesperado. | Só após 3 falhas seguidas |
| `RESPOSTA_INVALIDA` | O Jira respondeu 2xx com corpo que o app não entende. | Só após 3 falhas seguidas |
| `DADO_MALFORMADO` | Issue com data ou usuário malformado. Registrada com este código. | **Sim, após 3 falhas seguidas** (o app para até reiniciar) |
| `RECONCILIACAO_SUSPEITA` | A guarda de 50% da reconciliação disparou. Nada foi alterado. | Não suspende sozinha, mas é motivo para parar |
| `ORCAMENTO_REQUISICOES` | Bateu em `SYNC_MAX_REQUISICOES`. Aparece como **Incompleta (limite)** na tela; não conta como falha seguida. | Não, nunca |
| `TETO_ITENS` | Bateu em `SYNC_MAX_ITENS`. Aparece como **Incompleta (limite)**; não conta como falha seguida. | Não, nunca |
| `TEMPO_ESGOTADO` | Passou de `SYNC_TEMPO_MAX_MIN`. Aparece como **Incompleta (limite)**; não conta como falha seguida. | Não, nunca |
| `CONFLITO_DE_UNICIDADE` | Violação de unicidade no banco. | Só após 3 falhas seguidas |
| `INSTANCIA_SEM_CREDENCIAL` | Instância listada em `SYNC_INSTANCIAS` sem URL ou token completos. | Não |
| `INTERROMPIDA`, `CANCELADA` | Execução interrompida (outra réplica, parada do app). | Não |
| `ERRO_INTERNO` | Falha do próprio app. Veja o log. | Só após 3 falhas seguidas |

**Suspensão.** Depois de `HTTP_401`, `HTTP_403` ou `HTTP_429`, ou de 3 falhas seguidas no incremental (incluindo `DADO_MALFORMADO`), o app **para de sincronizar aquela instância sozinho** e o campo `suspensa` mostra o motivo. Ele só retoma quando o app **reinicia** (o reinício limpa a suspensão). Reinicie só depois de entender e corrigir a causa: insistir com credencial recusada pode bloquear a conta.

`incompletas` conta issues cuja leitura veio cortada (por exemplo, changelog truncado); `usuariosNaoResolvidos` conta pessoas que o app não conseguiu ligar a um username.

## Quando parar

Desligue a flag e reinicie (seção abaixo) diante de qualquer um destes sinais:

- `HTTP_401`, `HTTP_403`, `HTTP_429` ou `HTTP_5XX`;
- `RECONCILIACAO_SUSPEITA`;
- `usuariosNaoResolvidos > 0` sem explicação;
- `incompletas > 0`.

Registre o código e o instante, e só volte a ligar depois de decidir a correção. Isso vale mais ainda no primeiro ciclo (Etapa 1).

## Como desligar

1. No `.env`, ponha `JIRA_LIVE_ENABLED=false`.
2. Reinicie o painel (o `.env` é relido na subida).

Nenhuma outra variável liga ou desliga a sincronização. Com `false`, o gate nega qualquer acesso e o status mostra `DESLIGADO_PELA_FLAG`. Os dados já gravados ficam no banco local. Para apagá-los: pare o painel e apague o arquivo `data/painel.db`.

## Retenção

Todo dia, em `SYNC_HORA_RETENCAO`, o app apaga **só dados locais** antigos; nunca altera o Jira. O limite é `RETENTION_MONTHS` (padrão 24):

- `issue`: as resolvidas **e** sem alteração desde o limite, e também as removidas no Jira (nunca resolvidas) cuja remoção e última alteração passaram do limite (eventos, worklogs e vínculos saem junto). Issue aberta ativa ou recente nunca é apagada.
- `sync_run` (execuções) e o cache de usuários: por data.
- `access_log` (auditoria de acessos): limite próprio, `ACCESS_LOG_RETENTION_MONTHS` (mínimo 12), para a auditoria não encurtar junto com a retenção geral.

`RETENTION_MONTHS` tem piso de 6 meses (valor menor impede a subida). Reduzi-lo apaga mais no próximo ciclo, mas não toca o `access_log`.

## Dados de demonstração

Para ver o painel e a tela Sincronização sem tocar no Jira, com a flag `false`, use os dados de demonstração (100% fictícios; seção "Ver o painel hoje com dados fictícios" de `app/README.md`).

macOS ou Linux (a partir da raiz do repositório):

```sh
cd app
DASHBOARD_DEMO_DATA=true sh iniciar.sh
```

Windows (PowerShell, a partir da raiz do repositório):

```powershell
cd app
$env:DASHBOARD_DEMO_DATA = "true"
.\iniciar.ps1
```

Abra <http://localhost:8080>.

**Pré-requisito para ligar a flag do Jira: banco sem dados de demonstração.** O painel recusa subir com a flag `JIRA_LIVE_ENABLED` ligada quando o banco ainda tem issues de demonstração (`DASHBOARD_DEMO_DATA` também precisa estar `false`, ou a subida cai). Nada é apagado automaticamente: antes de ligar a flag, pare o painel, apague o arquivo `data/painel.db` e suba de novo **sem** `DASHBOARD_DEMO_DATA`. Do mesmo jeito, um banco com issues reais recusa a carga de demonstração.

## Depois do primeiro ciclo

1. Registre o resultado na [matriz de compatibilidade](matriz-compatibilidade.md), trocando **não validado** por **confirmado em aaaa-mm-dd**.
2. Não copie para o repositório dados pessoais, tokens nem respostas brutas do Jira.
3. Se for encerrar a sessão, desligue a flag e retire o token do ambiente.
