# Configuração do painel (arquivos YAML)

Esta página diz o que cada arquivo de configuração guarda, quem o edita, como validar antes de reiniciar e como desfazer uma mudança. A configuração **só vale depois de reiniciar o serviço**: não há recarga a quente.

Regras que valem sempre: os arquivos reais com dados de pessoas nunca vão para este repositório (que é público) nem para o pacote; ficam na pasta indicada por `PAINEL_CONFIG_DIR`, fora do pacote, com leitura só para a conta do serviço. O repositório guarda apenas os modelos `*.exemplo.yaml`, 100% fictícios.

## 1. Os arquivos

| Arquivo (real) | O que guarda | Dado pessoal? | Quem edita | Com que frequência |
|---|---|---|---|---|
| `papeis.yaml` | Pessoas, papéis e squads com vigência (`id` = username do Jira) | sim | responsável nomeado pelo gerente | a cada entrada, saída ou troca de squad |
| `equipes.yaml` | Capacidade e competências por pessoa (Plano 6) | sim | idem | quando a equipe muda |
| `ausencias.yaml` | Férias e ausências, sem motivo (Plano 6) | sim | idem | semanal |
| `calendario.yaml` | Feriados fixos e móveis | não | idem | uma vez por ano (até 31/10 para o ano seguinte) |
| `status_map.yaml` | Status do Jira e categoria (todo, active, wait, blocked, done) | não | idem | quando o workflow do Jira muda |
| `jira_campos.yaml` | Dicionário de campos (nomes, tipos, `customId`) | não | idem | na primeira conexão e quando um campo muda |
| `vinculos.yaml`, `labels_atraso.yaml`, `auditoria.yaml`, `metas.yaml`, `risco.yaml`, `previsao.yaml` | Regras e limites dos cartões | não | idem | raramente (calibração) |
| `cards/*.card.yaml` | Fichas dos cartões (campos e filtros) | não | desenvolvimento | com a versão do painel |

Em desenvolvimento (`DASHBOARD_DEV_MODE=true`) o painel cai nos modelos `*.exemplo.yaml` quando o arquivo real falta. **Em produção isso é erro** (`EXEMPLO_EM_PRODUCAO` ou `ARQUIVO_AUSENTE`): a subida falha em vez de abrir com dados de exemplo.

## 2. Onde ficam os arquivos reais e como se edita (decisão recomendada, reversível)

Ponto de parada 3 da spec da fase: foi adotada a opção recomendada.

1. **Repositório git privado de configuração** (recomendado): quem edita abre uma mudança, outra pessoa revisa, a validação (`validar-config`) roda automaticamente nesse repositório e, ao aprovar, o arquivo é copiado para a pasta `PAINEL_CONFIG_DIR` do servidor. Dá histórico de quem mudou o quê e revisão antes de valer.
2. **Alternativa: pasta do servidor** editada por um responsável nomeado: sem histórico nem revisão. Se esta for a escolha, faça cópia do arquivo antes de cada edição (por exemplo, `papeis.yaml.2026-11-03`) e valide antes de reiniciar.

Esse repositório privado **não existe ainda**: o usuário o cria, com acesso restrito (tem nomes de pessoas). A cópia para o servidor é parte da implantação (Plano 13). Veja "Depois da flag / com o usuário" no fim.

## 3. Validar antes de reiniciar

O painel traz o subcomando `validar-config`. Ele lê os arquivos pelo mesmo caminho da subida, **sem** abrir o banco, **sem** tomar a trava, **sem** subir o servidor e **sem** falar com o Jira. A saída lista `OK <arquivo>` por arquivo válido e `ERRO <codigo> <arquivo>[:<linha>]` por problema, e termina com `VALIDACAO OK` (código de saída 0) ou `VALIDACAO FALHOU <n>` (código 1). A saída **nunca** mostra o valor errado, porque pode ser o nome de uma pessoa.

PowerShell (Windows, a partir da raiz do repositório ou do pacote):

```powershell
.\app\scripts\painel-cli.ps1 validar-config
.\app\scripts\painel-cli.ps1 validar-config --dir D:\painel\config
```

sh (Linux):

```sh
sh app/scripts/painel-cli.sh validar-config
sh app/scripts/painel-cli.sh validar-config --dir /srv/painel/config
```

Sem `--dir`, vale `PAINEL_CONFIG_DIR` (padrão `config`). O script localiza o pacote em `PAINEL_HOME` (padrão `app/backend/build/install/painel`).

### Códigos de problema

| Código | Significa | O que fazer |
|---|---|---|
| `SETTINGS_INVALIDO` | uma variável de ambiente tem valor inválido (o arquivo citado é o NOME da variável) | corrigir a variável no arquivo de variáveis |
| `ARQUIVO_AUSENTE` | falta o arquivo real (e nem o modelo existe) | criar o arquivo a partir do modelo |
| `EXEMPLO_EM_PRODUCAO` | em produção só existe o modelo `*.exemplo.yaml` | copiar o modelo para o nome real e editar |
| `YAML_INVALIDO` | o arquivo não é um YAML aceito (sintaxe, tipo, valor fora da regra) | abrir o arquivo na linha indicada |
| `CHAVE_DESCONHECIDA` | chave que o painel não conhece (os arquivos são estritos) | remover ou corrigir a chave |
| `PAPEIS_SEM_GERENTE_VIGENTE` | ninguém com papel GERENTE vigente hoje | incluir ou renovar um gerente |
| `ID_RESERVADO` | `id` de pessoa reservado (`dev`, `system`, `admin`, `root`, `anonymous`) | usar o username real do Jira |
| `PESSOA_DESCONHECIDA` | pessoa de `equipes.yaml` ou `ausencias.yaml` que não consta em `papeis.yaml` | incluir a pessoa em `papeis.yaml` ou corrigir o `id` |
| `SQUAD_SEM_LIDER` | squad de uma pessoa de `equipes.yaml` sem líder vigente | nomear um líder em `papeis.yaml` |
| `PERIODO_INVERTIDO` | ausência com `ate` antes de `de` | corrigir as datas |
| `CALENDARIO_SEM_ANO` | falta feriado móvel do ano atual ou do seguinte | acrescentar os feriados do ano seguinte |
| `STATUS_DA_FICHA_SEM_MAPA` | status usado como filtro fixo de uma ficha que não consta em `status_map.yaml` | acrescentar o status ao mapa |
| `CUSTOM_ID_INVALIDO` | `customId` fora do formato `customfield_NNNNN` | corrigir o id do campo |
| `BANCO_PASTA_SEM_ESCRITA` | (produção) a pasta de `DATABASE_PATH` não existe ou não aceita escrita | ajustar a permissão da pasta |
| `CONFIG_INVALIDA` | outra falha de carga sem arquivo identificado | rodar a subida em modo de desenvolvimento e ler o log |

As verificações de `BACKUP_*` estão em [backup-e-restauracao.md](backup-e-restauracao.md).

## 4. Fluxo de uma mudança

1. Edite o arquivo (no repositório de configuração, se for a opção 1).
2. Rode `validar-config` apontando para a pasta com a mudança. Só siga com `VALIDACAO OK`.
3. Copie o arquivo para `PAINEL_CONFIG_DIR` do servidor.
4. Reinicie o serviço (comando no [runbook diário](runbook-diario.md)) e confira `GET /api/health/pronto`.

Entrada e saída de pessoas: **encerre a vigência** (`ate`) em vez de apagar a linha, porque a auditoria usa a vigência na data.

## 5. Como desfazer

Volte a versão anterior do arquivo (o commit anterior do repositório de configuração, ou a cópia datada), rode `validar-config`, copie para o servidor e reinicie. Como a configuração não é recarregada a quente, nada muda até o reinício.

## 6. Variáveis do arquivo de variáveis

O modelo é `app/.env.example` (copie para `app/.env`, que o git ignora, ou aponte `PAINEL_ENV_FILE` para outro arquivo). Vazio ou ausente = padrão. Nenhuma variável abaixo liga o Jira por si só: quem liga é só `JIRA_LIVE_ENABLED`, e só o usuário a muda.

| Variável | Padrão | Para quê |
|---|---|---|
| `JIRA_LIVE_ENABLED` | falso | A **única** chave do acesso ao Jira (só o usuário muda, à mão, só neste arquivo) |
| `DASHBOARD_DEV_MODE` | verdadeiro em `iniciar.*`; falso no pacote | Modo desenvolvimento: sem login, com faixa vermelha. Nunca em produção |
| `DASHBOARD_DEMO_DATA` | falso | Dados fictícios de demonstração (só em desenvolvimento, Jira desligado) |
| `DASHBOARD_ORIGEM_PUBLICA` | vazio | Origem pela qual o navegador acessa o painel (proteção CSRF); `https` em produção |
| `DATABASE_PATH` | `data/painel.db` | Arquivo SQLite do painel |
| `DATABASE_POOL_SIZE` | 4 | Conexões do pool (1 a 100) |
| `PAINEL_CONFIG_DIR` | `config` | Pasta dos YAML de configuração (em produção, caminho absoluto: relativo recusa a subida) |
| `PAINEL_STATIC_DIR` | `frontend/dist` | Build do frontend servido em `/` (em produção, caminho absoluto) |
| `DASHBOARD_PORT` | 8080 | Porta em que o painel escuta (1 a 65535) |
| `DASHBOARD_PROXY_CONFIAVEL` | vazio | Faixas CIDR do proxy reverso da empresa; só elas podem informar `X-Forwarded-*`. Obrigatória em produção |
| `PAINEL_CACHE_SEG` | 60 | Segundos em que as telas reaproveitam a leitura bruta do banco (issues e histórico); `0` desliga. Fim de sincronização e de retenção invalidam na hora; o recorte por papel é feito depois, então o cache nunca mistura papéis |
| `PAINEL_HOST` | `127.0.0.1` | Interface em que o painel escuta (`DASHBOARD_PORT`). O padrão só atende a própria máquina; outra interface exige firewall e gera `AVISO ESCUTA_FORA_DO_LOOPBACK` no `validar-config`; em produção `0.0.0.0` e `::` são recusados na subida |
| `JIRA_SAMSUNG_URL`, `JIRA_SAMSUNG_TOKEN` | vazio | Instância `samsung` (Bearer); só ativa com as duas preenchidas |
| `JIRA_SIDIA_URL`, `JIRA_SIDIA_USER`, `JIRA_SIDIA_TOKEN` | vazio | Instância `sidia` (Basic); só ativa com as três preenchidas |
| `SYNC_INSTANCIAS`, `SYNC_INTERVALO_MIN`, `SYNC_HORA_RECONCILIACAO`, `SYNC_HORA_RETENCAO`, `SYNC_MAX_ITENS`, `SYNC_MAX_REQUISICOES`, `SYNC_SOBREPOSICAO_MIN`, `SYNC_FUSO_SERVIDOR`, `SYNC_TEMPO_MAX_MIN` | ver `app/README.md` | Intervalos e limites da sincronização (não ligam nada) |
| `RETENTION_MONTHS`, `ACCESS_LOG_RETENTION_MONTHS` | 24 | Meses de dados locais e de registro de acesso |
| `PAINEL_LOG_FORMATO`, `PAINEL_LOG_DIR` | `JSON`, vazio | Formato e pasta dos logs ([observabilidade](observabilidade.md)) |
| `PAINEL_DISCO_MIN_MB` | 500 | Espaço livre mínimo para o painel estar pronto |
| `PAINEL_METRICAS_PORTA`, `PAINEL_METRICAS_HOST` | 9464, `127.0.0.1` | Porta interna das métricas; `0` desliga. Host fora do loopback gera `AVISO METRICAS_HOST_NAO_LOOPBACK` no `validar-config` (não muda o código de saída) |
| `PAINEL_ALERTAS_ARQUIVO` | vazio | Arquivo de texto dos alertas |
| `SYNC_PAT_VALIDADE_SAMSUNG`, `SYNC_PAT_VALIDADE_SIDIA` | vazio | Validade do token, `aaaa-mm-dd`, informada por você |
| `SESSION_SECRET`, `SESSION_SECRET_ANTERIOR` | vazio | Assinatura do cookie de sessão ([rotação de segredos](rotacao-de-segredos.md)). **Obrigatório em produção** (em branco = a subida falha); o início da rotação fica gravado ao lado do banco (`<banco>.rotacao`) para que reiniciar não reabra a janela do segredo anterior |
| `OIDC_ISSUER`, `OIDC_CLIENT_ID`, `OIDC_REDIRECT_URI` | vazio | Login SSO/OIDC. **Obrigatórios em produção**: faltando um, o painel não sobe e a mensagem cita só o nome. `OIDC_ISSUER` e `OIDC_REDIRECT_URI` são `https`; a segunda termina em `/auth/callback` |
| `OIDC_CLIENT_SECRET` | vazio | Segredo do cliente, se o provedor exigir (opcional com PKCE); nunca vai para log nem para `toString` |
| `OIDC_CLAIM_USERNAME` | `preferred_username` | Claim do `id_token` com o username do Jira; o papel nunca vem do token, vem do `papeis.yaml` |
| `BACKUP_DIR`, `BACKUP_HORA`, `BACKUP_RETENCAO_COPIAS` | vazio, `02:00`, 14 | Backup do banco ([backup e restauração](backup-e-restauracao.md)) |

## Depois da flag / com o usuário

- Criar o repositório privado de configuração (acesso restrito) e definir quem revisa. O painel não depende dele para funcionar.
- Definir a cópia automática dessa pasta para `PAINEL_CONFIG_DIR` no servidor (Plano 13).
- Nomear o responsável pela edição semanal de `ausencias.yaml` e pela edição anual do calendário.
