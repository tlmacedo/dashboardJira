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

## Depois da flag / com o usuário

- Criar o repositório privado de configuração (acesso restrito) e definir quem revisa. O painel não depende dele para funcionar.
- Definir a cópia automática dessa pasta para `PAINEL_CONFIG_DIR` no servidor (Plano 13).
- Nomear o responsável pela edição semanal de `ausencias.yaml` e pela edição anual do calendário.
