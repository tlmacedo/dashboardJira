# Rotação de segredos

Esta página diz como trocar cada segredo do painel sem surpresa. Regra que vale para todos: **quem edita o arquivo de variáveis do serviço é o usuário** (`app/.env` ou o arquivo indicado por `PAINEL_ENV_FILE`). Nenhuma IA abre, lê ou edita esse arquivo, gera token ou acessa o Jira para isso. Depois de qualquer troca, o serviço precisa ser **reiniciado** (não há recarga a quente).

Os segredos nunca aparecem em log, métrica, resposta nem mensagem de erro do painel (a máscara de log os conhece pelo valor, inclusive escapados e em base64). O painel não usa senha de banco: o banco é um arquivo SQLite protegido pela permissão do arquivo e da pasta, então não há senha de banco para rotacionar.

Cada procedimento tem: quando, como, como conferir e como desfazer. Os comandos têm as versões PowerShell e `sh`.

## 1. `SESSION_SECRET` (assina o cookie de sessão)

**Quando:** a cada ano, ou quando quem tinha acesso ao arquivo de variáveis deixa o time. Em suspeita de vazamento, use o procedimento "derrubar todas as sessões" abaixo.

**Como (sem derrubar quem está logado):** o painel aceita um segredo anterior só para **ler** cookies, enquanto emite os novos com o atual.

1. Gere o segredo novo (48 bytes em base64; o painel exige no mínimo 32 bytes):

   PowerShell:

   ```powershell
   $b = New-Object byte[] 48
   $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($b); $rng.Dispose()
   [Convert]::ToBase64String($b)
   ```

   sh:

   ```sh
   openssl rand -base64 48
   ```

2. No arquivo de variáveis: copie o valor **atual** de `SESSION_SECRET` para `SESSION_SECRET_ANTERIOR` e ponha o valor novo em `SESSION_SECRET`.
3. Reinicie o serviço:

   PowerShell: `Restart-Service painel-squads`  
   sh: `sudo systemctl restart painel-squads`

4. Depois da duração de uma sessão (**8 horas**), apague a linha `SESSION_SECRET_ANTERIOR` e reinicie de novo. Enquanto ela existir, o painel relê cada sessão antiga com o segredo anterior e a regrava com o atual, sem estender o prazo da sessão.

**Como conferir:** `validar-config` acusa `SETTINGS_INVALIDO` (citando só o nome da variável) se algum dos dois tiver menos de 32 bytes, não for base64 ou se o anterior for igual ao atual. Quem estava logado continua logado depois do reinício; abrir uma aba anônima e entrar também funciona.

PowerShell: `.\app\scripts\painel-cli.ps1 validar-config`  
sh: `sh app/scripts/painel-cli.sh validar-config`

**Como desfazer:** volte o valor antigo para `SESSION_SECRET` (e apague o `SESSION_SECRET_ANTERIOR`) e reinicie. Sessões emitidas com o segredo novo deixam de valer; quem estava logado entra de novo.

**Derrubar todas as sessões (incidente):** troque `SESSION_SECRET` **sem** colocar o antigo em `SESSION_SECRET_ANTERIOR` e reinicie. Todo cookie existente deixa de valer. Use isto em `SUSPEITA_DE_VAZAMENTO` (ver o [runbook de incidentes](runbook-incidentes.md)).

## 2. Token (PAT) do Jira

**Quando:** antes de vencer (o alerta `PAT_EXPIRANDO` avisa 14 dias antes, usando a data que **você** informou) ou na hora, se o token vazou.

**Como:** **feito pelo usuário, no Jira**: cria o token novo e revoga o antigo no próprio Jira. Depois, no arquivo de variáveis, atualiza o token da instância (`JIRA_<INSTANCIA>_TOKEN`) e a data de validade `SYNC_PAT_VALIDADE_<INSTANCIA>` (formato `aaaa-mm-dd`). Reinicie o serviço. Nenhuma IA gera, lê ou guarda o token, e o painel nunca consulta o Jira para descobrir a validade.

**Como conferir:** a tela Sincronização (gerente) ou `GET /api/sync/status` deve mostrar a instância liberada e sem suspensão (reiniciar tira a suspensão). Enquanto a flag do Jira estiver desligada, nada é acessado e o alerta de PAT não dispara.

**Como desfazer:** o token antigo foi revogado, então não há volta: crie outro token. Se a troca falhou (401), a sincronização se suspende e o runbook diário explica (`SYNC_SUSPENSA`).

## 3. `OIDC_CLIENT_SECRET` (login corporativo)

**Quando:** conforme a política da equipe do provedor de identidade, ou em suspeita de vazamento.

**Como:** a equipe do provedor emite o segredo novo (Plano 13); o usuário o grava no arquivo de variáveis e reinicia o serviço. O painel registra o valor na máscara de log.

**Como conferir:** entrar pelo login corporativo e abrir `GET /api/health/pronto`.

**Como desfazer:** peça à equipe do provedor o segredo anterior, se ainda estiver válido, ou emita outro.

## 4. Permissões do arquivo de variáveis

Só a conta do serviço lê esse arquivo:

PowerShell:

```powershell
icacls D:\painel\painel.env /inheritance:r /grant:r "CONTA_DO_SERVICO:R" "Administrators:F"
```

sh:

```sh
sudo chown painel:painel /srv/painel/painel.env && sudo chmod 600 /srv/painel/painel.env
```

O arquivo de variáveis nunca vai para o repositório (que é público) nem para o pacote.

## Depois da flag / com o usuário

- O login corporativo (OIDC) e o cookie de sessão são do Plano 8 e do Plano 13; a classe que assina e rotaciona o cookie já existe (`SessaoAssinada`) e precisa ser ligada ao login quando ele existir. Até lá a rotação do `SESSION_SECRET` está pronta no código, mas não há sessão de produção para conferir na prática.
- Definir quem guarda e quem troca cada segredo, e o calendário anual das trocas.
