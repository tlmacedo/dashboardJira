# Runbook: ligar a sincronização com o Jira

Procedimento para o **usuário** ligar, acompanhar e desligar a sincronização periódica do Painel de Squads. Vale para o ClineSR (Windows 11, PowerShell) e para macOS (zsh). Onde os comandos diferem, há um bloco para cada sistema.

> **Aviso.** A flag `JIRA_LIVE_ENABLED` é a **única trava** de acesso ao Jira (regra 1 do `CLAUDE.md`, 25/10). Com `false`, ninguém acessa o Jira: nem o app, nem o agendador, nem IA, pessoa, MCP, `curl` ou script. Com `true`, o app sincroniza sozinho e as ferramentas ficam liberadas. **Só o usuário muda a flag; nenhuma IA a liga.** O primeiro ciclo real segue o [checklist da primeira conexão](checklist-primeira-conexao.md) como procedimento cuidadoso recomendado.

Este documento não contém host, token nem username: eles ficam só no ambiente do usuário.

## Pré-requisitos

- Passos 1 a 5 do [checklist](checklist-primeira-conexao.md) concluídos e registrados:
  - o `timeZone` lido em `/myself` está anotado e vira `SYNC_FUSO_SERVIDOR` (se o servidor não estiver em America/Manaus);
  - o `customId` de cada campo está preenchido no `jira_campos.yaml` real;
  - o `status_map.yaml` real tem os status reais do TG-Basic.
- Docker ativo (PostgreSQL do compose).
- Configs reais criadas a partir dos modelos (próxima seção) e **nunca versionadas**.

## Configs reais necessárias

Todas ficam em `app/config/` (ou em `PAINEL_CONFIG_DIR`). Crie cada uma copiando o modelo e ajustando; o `.gitignore` já ignora o que tem dado real. **Nunca faça commit delas.**

| Arquivo real | Modelo | Observação |
|---|---|---|
| `.env` (em `app/`) | `.env.example` | URL e token de cada instância, `POSTGRES_PASSWORD`, flags e `SYNC_*`. O `.env` é ignorado pelo git. |
| `papeis.yaml` | `papeis.exemplo.yaml` | O `id` de cada pessoa é o username do Jira (igual ao `assignee`). |
| `status_map.yaml` | `status_map.exemplo.yaml` | Nomes de status reais; obrigatório em produção. |
| `jira_campos.yaml` | `jira_campos.exemplo.yaml` | `customId` real de cada campo (passo 2 do checklist). Sem ele o app usa o modelo, só com vocabulário de exemplo. |
| Fichas (`cards/*.card.yaml`) | as fichas versionadas em `app/config/cards/` | Ajuste só em cópia local se precisar de JQL próprio; a ficha `sincronizacao` define o universo sincronizado. |

macOS (zsh), a partir da raiz do repositório:

```sh
cp app/.env.example app/.env
cp app/config/papeis.exemplo.yaml app/config/papeis.yaml
cp app/config/status_map.exemplo.yaml app/config/status_map.yaml
cp app/config/jira_campos.exemplo.yaml app/config/jira_campos.yaml
```

Windows 11 (PowerShell), a partir da raiz do repositório:

```powershell
Copy-Item app\.env.example app\.env
Copy-Item app\config\papeis.exemplo.yaml app\config\papeis.yaml
Copy-Item app\config\status_map.exemplo.yaml app\config\status_map.yaml
Copy-Item app\config\jira_campos.exemplo.yaml app\config\jira_campos.yaml
```

Confira com `git status` que nenhum desses arquivos aparece como novo. Se aparecer, pare: o `.gitignore` está incompleto.

**Como os arquivos chegam ao contêiner.** O `docker-compose.yml` monta a pasta `app/config/` do host em `/app/config`, somente leitura (`:ro`), por cima da config embutida na imagem (que só traz os modelos `.exemplo`). Portanto os arquivos reais acima, e as fichas em `cards/`, são lidos sem rebuild de imagem: basta criar o arquivo e reiniciar (`docker compose up -d`). Nada com dado pessoal é copiado para a imagem nem para o repositório. Um arquivo `vinculos` (vínculo Blocks), quando existir, segue o mesmo caminho.

**Conferência dos arquivos carregados.** Depois de subir, o log da subida avisa, em modo desenvolvimento, de cada arquivo real ausente que foi trocado pelo modelo (`usando o modelo ... .exemplo.yaml`). Sem esse aviso para papéis, mapa de status e dicionário de campos, os reais foram carregados (o log nunca mostra o conteúdo). Em produção a ausência de um obrigatório derruba a subida.

macOS (zsh), a partir da pasta `app`:

```sh
docker compose logs painel | grep "usando o modelo"
```

Windows 11 (PowerShell), a partir da pasta `app`:

```powershell
docker compose logs painel | Select-String "usando o modelo"
```

Saída vazia = nenhum modelo em uso.

## Variáveis

A sincronização liga **somente** por `JIRA_LIVE_ENABLED=true`. As variáveis `SYNC_*` só ajustam limites e horários; vazio significa o padrão. Valor fora da faixa faz o app falhar na subida, citando só o nome da variável.

| Variável | Padrão | Faixa | Efeito |
|---|---|---|---|
| `JIRA_LIVE_ENABLED` | `false` | `true`/`false` | Única chave de liga e desliga. |
| `SYNC_INSTANCIAS` | `samsung` | ids separados por vírgula | Instâncias sincronizadas (`samsung`, `sidia`). Cada uma precisa de URL e token completos. |
| `SYNC_INTERVALO_MIN` | `10` | 5 a 1440 | Minutos entre sincronizações incrementais. |
| `SYNC_HORA_RECONCILIACAO` | `03:00` | `HH:mm` | Reconciliação diária, no fuso America/Manaus. |
| `SYNC_HORA_RETENCAO` | `04:00` | `HH:mm` | Limpeza diária de dados locais (não toca o Jira). |
| `SYNC_MAX_ITENS` | `50000` | 1 a 200000 | Teto de issues por execução. |
| `SYNC_MAX_REQUISICOES` | `2000` | 1 a 20000 | Teto de requisições ao Jira por execução. |
| `SYNC_SOBREPOSICAO_MIN` | `5` | 1 a 60 | Sobreposição do cursor entre incrementais. |
| `SYNC_FUSO_SERVIDOR` | `America/Manaus` | fuso IANA | Fuso do usuário da credencial no Jira (o `timeZone` do `/myself`). |
| `SYNC_TEMPO_MAX_MIN` | `60` | 1 a 240 | Tempo máximo de uma execução. |
| `RETENTION_MONTHS` | `24` | inteiro positivo | Meses de dados locais mantidos (ver Retenção). |

O `docker-compose.yml` já repassa ao contêiner todas as variáveis acima, as de conexão (`JIRA_SAMSUNG_URL`, `JIRA_SAMSUNG_TOKEN`, `JIRA_SIDIA_URL`, `JIRA_SIDIA_USER`, `JIRA_SIDIA_TOKEN`) e `DASHBOARD_DEMO_DATA`, sempre no formato `NOME: ${NOME:-}` (sem valor no arquivo; vazio = padrão do código). Os valores vêm do `.env`.

## Etapa 1: primeiro ciclo, pequeno

Objetivo: ver o app falar com o Jira com o menor volume possível. Todos os comandos rodam na pasta `app/` (a partir da raiz do repositório).

1. No `.env`, defina `SYNC_MAX_ITENS=50` e `SYNC_MAX_REQUISICOES=5`, mais URL e token da instância. **Ainda com `JIRA_LIVE_ENABLED=false`.**
2. **Conferência antes de ligar a flag.** Veja o que o contêiner vai receber, sem imprimir segredo. Nunca rode `docker compose config` sem filtro: ele mostra a senha do banco e o token.

   macOS (zsh):

   ```sh
   cd app
   docker compose config | grep -E "JIRA_LIVE_ENABLED|DASHBOARD_|SYNC_|RETENTION_MONTHS"
   docker compose config | grep -E "JIRA_[A-Z]+_(URL|USER|TOKEN):" | awk '{print $1, ($2 == "\"\"" ? "VAZIA" : "preenchida")}'
   ```

   Windows 11 (PowerShell):

   ```powershell
   Set-Location app
   docker compose config | Select-String "JIRA_LIVE_ENABLED|DASHBOARD_|SYNC_|RETENTION_MONTHS"
   docker compose config | Select-String "JIRA_[A-Z]+_(URL|USER|TOKEN):" | ForEach-Object {
     $p = $_.Line.Trim() -split ":\s*", 2
     "$($p[0]): " + $(if ($p[1] -in '""', "''", "") { "VAZIA" } else { "preenchida" })
   }
   ```

   Esperado: `JIRA_LIVE_ENABLED: "false"`, `SYNC_MAX_ITENS: "50"`, `SYNC_MAX_REQUISICOES: "5"` e as credenciais da instância como `preenchida` (a da outra instância `VAZIA`). Se um teto aparecer vazio, a variável não chegou ao contêiner: pare e corrija o `.env`.
3. **O usuário** liga a flag: `JIRA_LIVE_ENABLED=true` no `.env`.
4. Suba (ou recrie) o app. O agendador faz a carga inicial sozinho, dentro desses tetos.

   macOS (zsh), na pasta `app`:

   ```sh
   docker compose up --build -d
   ```

   Windows 11 (PowerShell), na pasta `app`:

   ```powershell
   docker compose up --build -d
   ```

5. Espere um ciclo e confira `/api/sync/status` e o card (próxima seção). O primeiro ciclo termina quando o app esgota o teto de 50 issues ou de 5 requisições; um código `TETO_ITENS` ou `ORCAMENTO_REQUISICOES` aqui é esperado, não é falha do Jira.

Para acompanhar o log (macOS e PowerShell, na pasta `app`):

```sh
docker compose logs -f painel
```

## Etapa 2: limites padrão

1. Se a Etapa 1 estiver limpa, apague `SYNC_MAX_ITENS` e `SYNC_MAX_REQUISICOES` do `.env` (ou deixe vazios) e reinicie (`docker compose up -d`).
2. O app faz a carga completa e depois incrementais a cada `SYNC_INTERVALO_MIN`.
3. Reconfira o status. Carga grande pode levar mais de um ciclo; o cursor só avança até o que foi gravado.

## Etapa 3: reconciliação

A reconciliação roda uma vez por dia (`SYNC_HORA_RECONCILIACAO`, padrão 03:00 de Manaus). Ela compara a lista leve do Jira com a base local: renomeia issues movidas, marca como removidas as que sumiram e rebusca as divergentes ou ausentes (isso também recupera issues que a paginação pulou numa carga longa). Depois da primeira, confira em `/api/sync/status` que `ultimaReconciliacao` foi preenchida e que a execução do tipo reconciliação terminou com status OK.

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
| `ORCAMENTO_REQUISICOES` | Bateu em `SYNC_MAX_REQUISICOES`. | Não |
| `TETO_ITENS` | Bateu em `SYNC_MAX_ITENS`. | Não |
| `TEMPO_ESGOTADO` | Passou de `SYNC_TEMPO_MAX_MIN`. | Não |
| `CONFLITO_DE_UNICIDADE` | Violação de unicidade no banco. | Não |
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
2. Reinicie o app (`docker compose up -d` recarrega o ambiente).

Nenhuma outra variável liga ou desliga a sincronização. Com `false`, o gate nega qualquer acesso e o status mostra `DESLIGADO_PELA_FLAG`. Os dados já gravados ficam no banco local. Para apagá-los: `docker compose down -v`.

## Retenção

Todo dia, em `SYNC_HORA_RETENCAO`, o app apaga **só dados locais** antigos; nunca altera o Jira. O limite é `RETENTION_MONTHS` (padrão 24):

- `issue`: só as resolvidas **e** sem alteração desde o limite (eventos e worklogs saem junto). Issue aberta nunca é apagada.
- `access_log` (auditoria de acessos), `sync_run` (execuções) e o cache de usuários: por data.

Reduzir `RETENTION_MONTHS` apaga mais no próximo ciclo e inclui o `access_log`: não use valores muito baixos sem decidir antes o prazo de auditoria.

## Dados de demonstração

Para ver o painel e a tela Sincronização sem tocar no Jira, com a flag `false`, use os dados de demonstração (100% fictícios; seção "Ver o painel hoje em 3 comandos" de `app/README.md`).

macOS ou Linux (a partir da raiz do repositório):

```sh
cd app
POSTGRES_PASSWORD=<valor descartável> DASHBOARD_DEMO_DATA=true docker compose up --build -d
open http://localhost:8080        # Linux: xdg-open
```

Windows (PowerShell, a partir da raiz do repositório):

```powershell
cd app
$env:POSTGRES_PASSWORD = "<valor descartável>"; $env:DASHBOARD_DEMO_DATA = "true"
docker compose up --build -d
Start-Process http://localhost:8080
```

**Pré-requisito para ligar a flag do Jira: banco sem dados de demonstração.** O painel recusa subir com `JIRA_LIVE_ENABLED=true` quando o banco ainda tem issues de demonstração (`DASHBOARD_DEMO_DATA` também precisa estar `false`, ou a subida cai). Nada é apagado automaticamente: antes de ligar a flag, use um volume novo (`docker compose down -v`) e suba de novo **sem** `DASHBOARD_DEMO_DATA`. Do mesmo jeito, um banco com issues reais recusa a carga de demonstração.

## Depois do primeiro ciclo

1. Registre o resultado na [matriz de compatibilidade](matriz-compatibilidade.md), trocando **não validado** por **confirmado em aaaa-mm-dd**.
2. Não copie para o repositório dados pessoais, tokens nem respostas brutas do Jira.
3. Se for encerrar a sessão, desligue a flag e retire o token do ambiente.
