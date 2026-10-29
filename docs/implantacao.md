# Guia de implantação do Painel de Squads (pacote de produção, sem contêiner)

Este guia leva o painel do repositório até uma máquina de produção **sem contêiner, sem servidor de banco e sem WSL** (a empresa bloqueia os três). O painel é um processo só: a API (Ktor) e as telas (build do frontend) saem do mesmo pacote, e o banco é um arquivo SQLite. Quem faz a implantação real (serviço do sistema, proxy, TLS, cofre) segue também os documentos de operação e o Plano 13; este guia entrega o pacote e como verificá-lo.

> Regra que vale para tudo aqui: **o acesso ao Jira só liga quando o usuário liga a chave `JIRA_LIVE_ENABLED` à mão, no arquivo de variáveis**. Nenhum passo deste guia, nenhum script e nenhuma pessoa de TI ou IA a liga por conta própria.

## 1. O que é o pacote

Resultado de `installDist` (backend) mais o build do frontend, montado por `montar-pacote` em `app/pacote/painel/`:

```
bin/            painel (sh) e painel.bat (Windows): sobem o painel
lib/            os jars
frontend/dist/  as telas
config/         SÓ os modelos *.exemplo.yaml (e *.demo.yaml) e as fichas de cards/
.env.example    modelo das variáveis; nunca um arquivo real
implantacao/    scripts de atualização, reversão e segredos (windows/, linux/) e o modelo do arquivo de variáveis (modelos/)
docs/           este guia e os documentos de implantação
VERSAO.txt      versão, commit curto e data da montagem
LEIA-ME.txt     como rodar
```

O pacote **nunca** leva configuração real (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `calendario.yaml`...), arquivo de variáveis real, arquivo de banco, trava, chave ou certificado, nem a pasta `legacy/`. O `verificar-pacote` confere isso a cada montagem.

## 2. Requisitos

| Onde | O que |
|---|---|
| Máquina de montagem | JDK 21, Node 22 e pnpm; acesso aos registros de pacotes (ou ao espelho interno da empresa; veja `PAINEL_PNPM_ARGS` e `PAINEL_GRADLE_ARGS` abaixo) |
| Máquina de produção | JDK 21 (`JAVA_HOME` ou no `PATH`). Nada mais: sem Node, sem banco, sem contêiner |

## 3. Montar e verificar

Cada comando parte da pasta `app/` do repositório. No PowerShell 5.1 não existe `&&`: use `;` e confira `$LASTEXITCODE`.

| Ação | Windows 11 (PowerShell, principal) | `sh` (Linux/macOS/Git Bash) |
|---|---|---|
| Montar | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\montar-pacote.ps1` | `sh scripts/montar-pacote.sh` |
| Verificar | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verificar-pacote.ps1` | `sh scripts/verificar-pacote.sh` |

`montar-pacote` roda `pnpm install --frozen-lockfile` e `pnpm build` no frontend, `gradlew installDist` no backend e copia o resultado para `app/pacote/` (a pasta é ignorada pelo git). Atrás de proxy ou espelho que exige parâmetros, defina antes `PAINEL_PNPM_ARGS` e/ou `PAINEL_GRADLE_ARGS` (por exemplo `--offline` quando as dependências já estão no cache).

`verificar-pacote` falha com código diferente de 0 e uma mensagem curta se:

1. houver no pacote qualquer YAML real, arquivo de variáveis (além do `.env.example`), arquivo de banco ou trava, chave/certificado, a pasta `legacy/` ou artefato de contêiner;
2. faltar `bin/painel`, `bin/painel.bat`, o jar do painel em `lib/` ou `frontend/dist/index.html`;
3. o pacote **em modo produção sem `OIDC_ISSUER`** não recusar a subida (tem de terminar com erro citando `OIDC_ISSUER` e sem mostrar nenhum valor de segredo);
4. o pacote em modo desenvolvimento não responder `{"ok":true}` em `GET /api/health`.

A verificação usa a porta 8080 e pastas temporárias, nunca liga a chave do Jira (o arquivo de variáveis de teste não tem essa linha) e aborta com aviso se a porta já estiver em uso.

### 3.1 Versão, zip e SHA-256

Para entregar o pacote a outra máquina, passe a versão (`AAAA.MM.N`: ano, mês de 01 a 12 e número do pacote no mês, a partir de 1; qualquer outro formato é recusado):

| Windows 11 (PowerShell) | `sh` |
|---|---|
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\montar-pacote.ps1 -Versao 2026.11.1` | `sh scripts/montar-pacote.sh 2026.11.1` |

Além da pasta, sai `app/pacote/painel-2026.11.1.zip` (pasta raiz `painel-2026.11.1/`; mesma entrada e mesma data de build dão o mesmo SHA-256) e o `painel-2026.11.1.zip.sha256` ao lado, no formato `<sha256-em-minúsculas>  painel-2026.11.1.zip` (dois espaços, aceito por `sha256sum -c`). O script já roda a verificação do zip e termina com `PACOTE_OK painel-2026.11.1.zip <sha256>`. Sem a versão, o painel se identifica como `dev`. A versão aparece no rodapé da tela e em `GET /api/meta` (`versao`, `rotuloDeVersao`); o commit e a data de build ficam no `VERSAO.txt` do pacote e no log da subida, nunca na rota pública.

Conferir um zip recebido (copiado de outra máquina, com o `.sha256` junto):

| Windows 11 (PowerShell) | `sh` (precisa de `unzip`) |
|---|---|
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verificar-pacote.ps1 C:\caminho\painel-2026.11.1.zip` | `sh scripts/verificar-pacote.sh /caminho/painel-2026.11.1.zip` |
| ou só o hash: `(Get-FileHash .\painel-2026.11.1.zip -Algorithm SHA256).Hash.ToLower()` | ou `sha256sum -c painel-2026.11.1.zip.sha256` |

Com o zip, a verificação confere, **antes de extrair**: o nome (`painel-AAAA.MM.N.zip`), o tamanho (no máximo 200 MB), o SHA-256 contra o `.sha256`, que toda entrada está sob a pasta raiz `painel-AAAA.MM.N/` e que nenhuma entrada tem nome proibido (`.env`, `painel.env`, `*.db`, `*.db-wal`, `*.db-shm`, `*.lock`, chaves e certificados, `papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`, `status_map.yaml`, `jira_campos.yaml`, `calendario.yaml`, `vinculos.yaml`, `labels_atraso.yaml`, `auditoria.yaml`, `Filtros_Status_tool.txt`, `legacy/`, `node_modules/`, `.git/`). Depois extrai numa pasta temporária e roda as conferências acima sobre o conteúdo.

## 4. Pastas e variáveis: tudo o que é real fica FORA do pacote

Trocar o pacote por um mais novo não pode apagar nada. Por isso a configuração real, o arquivo de variáveis e os dados ficam em pastas próprias, apontadas por variáveis:

| Variável | Para quê |
|---|---|
| `PAINEL_CONFIG_DIR` | Pasta dos YAML reais (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `calendario.yaml`, `status_map.yaml`...). Em produção os arquivos reais são obrigatórios; os modelos `.exemplo` não valem |
| `DATABASE_PATH` | Arquivo SQLite do painel (por exemplo `D:\painel\dados\painel.db`). Criado na primeira subida |
| `PAINEL_ENV_FILE` | Arquivo de variáveis real (nunca no repositório nem no pacote; permissão só da conta do serviço) |
| `PAINEL_STATIC_DIR` | Build do frontend (`<pacote>/frontend/dist`). Em produção, caminho **absoluto** (o padrão relativo `frontend/dist` só vale em desenvolvimento) |
| `DATABASE_POOL_SIZE` | Conexões do pool (1 a 100; padrão 4). No SQLite só uma grava por vez; mais conexões só aumentam leitores |
| `PAINEL_HOST` | Interface em que o painel escuta (padrão `127.0.0.1`; veja a seção 7) |
| `DASHBOARD_PORT` | Porta em que o painel escuta (1 a 65535; padrão 8080) |
| `DASHBOARD_PROXY_CONFIAVEL` | Faixas CIDR de onde vem a conexão do proxy da empresa (padrão vazio). Só essas conexões podem informar `X-Forwarded-Proto`, `-For` e `-Host`; o `-For` é lido da direita para a esquerda, juntando todas as linhas do cabeçalho (proxies como o HAProxy acrescentam uma linha nova); `-Proto` e `-Host` só valem se todos os valores recebidos forem iguais (divergentes são ignorados, porque o valor da esquerda pode ser do cliente). Configure o proxy para SOBRESCREVER esses três cabeçalhos. Proxy na mesma máquina: `127.0.0.1/32,::1/128`. Obrigatória em produção |
| `PAINEL_CACHE_SEG` | Segundos em que as telas reaproveitam a leitura bruta do banco (padrão 60; `0` desliga). Sem isso, cada requisição relê as issues e o histórico inteiros |
| `RETENTION_MONTHS` | Meses de dados locais mantidos (6 a 600; padrão 24; o prazo final é do DPO) |
| `ACCESS_LOG_RETENTION_MONTHS` | Meses do registro de acesso (12 a 600; padrão 24) |
| `RETENTION_HISTORICO_MONTHS`, `RETENTION_ACCESS_LOG_MONTHS`, `RETENTION_DECISOES_MONTHS`, `RETENTION_EXECUCOES_MONTHS` | Retenção por categoria (1 a 120 meses; padrão `RETENTION_MONTHS`); valores finais do DPO |
| `DASHBOARD_DPO_ACEITE`, `DASHBOARD_DPO_ACEITE_REF` | Trava do DPO (LGPD): Auditoria e Integridade ficam fechadas em produção até o aceite, ligado só pelo usuário depois do parecer; com `true`, a referência do parecer é obrigatória |

As demais variáveis (sincronização, logs, métricas, backup) estão em `docs/operacao/configuracao.md` e no modelo `.env.example`.

## 5. Rodar o pacote

Exige JDK 21. Em produção os três caminhos (`PAINEL_CONFIG_DIR`, `DATABASE_PATH`, `PAINEL_STATIC_DIR`) são **absolutos**: um serviço iniciado na pasta errada criaria um banco vazio sem avisar, então caminho relativo recusa a subida (`CAMINHO_RELATIVO`).

Windows 11 (PowerShell):

```powershell
$env:PAINEL_CONFIG_DIR = 'D:\painel\config'
$env:DATABASE_PATH     = 'D:\painel\dados\painel.db'
$env:PAINEL_STATIC_DIR = 'D:\painel\pacote\painel\frontend\dist'
$env:PAINEL_ENV_FILE   = 'D:\painel\painel.env'
.\bin\painel.bat
```

`sh`:

```sh
cd /opt/painel/pacote/painel
PAINEL_CONFIG_DIR=/etc/painel DATABASE_PATH=/var/lib/painel/painel.db PAINEL_STATIC_DIR=/opt/painel/pacote/painel/frontend/dist PAINEL_ENV_FILE=/etc/painel/painel.env ./bin/painel
```

### Guardas de produção

Antes de abrir o banco, `main()` confere a configuração e recusa a subida com **todos** os códigos de uma vez (a mensagem nunca traz valor): `DEMO_EM_PRODUCAO`, `OIDC_ISSUER_SEM_HTTPS`, `REDIRECT_URI_SEM_HTTPS`, `ORIGEM_PUBLICA_AUSENTE` (`DASHBOARD_ORIGEM_PUBLICA` ausente ou sem `https://`), `REDIRECT_URI_FORA_DA_ORIGEM_PUBLICA`, `SESSION_SECRET_CURTO` (ausente, base64 inválido ou menos de 32 bytes), `DOMINIO_DO_USERNAME_AUSENTE` (estratégia de username que descarta o domínio sem `OIDC_DOMINIOS_PERMITIDOS`), `PROXY_NAO_CONFIGURADO` (`DASHBOARD_PROXY_CONFIAVEL` vazia), `ESCUTA_ABERTA_A_TODAS` (`PAINEL_HOST` vazio ou qualquer grafia do endereço "todas as interfaces": `0.0.0.0`, `0`, `0.0`, `::`, `::0`, `0:0:0:0:0:0:0:0`, `::ffff:0.0.0.0`...; a comparação é pelo endereço), `ESCUTA_NAO_LITERAL` (`PAINEL_HOST` que não é IP literal nem `localhost`: um nome poderia apontar para qualquer endereço) e `CAMINHO_RELATIVO`. Em modo de desenvolvimento não se aplicam.

Os scripts do pacote fixam o **perfil de produção**: sem `DASHBOARD_DEV_MODE` no arquivo de variáveis o painel sobe com o modo desenvolvimento desligado e **falha fechado** (não sobe) se faltar qualquer configuração obrigatória. Antes de subir, `painel validar-config` confere os YAML e as variáveis (veja `docs/operacao/runbook-diario.md`).

## 6. Login SSO/OIDC e sessão (obrigatório em produção)

Em produção o painel só sobe com o login configurado. Qualquer provedor OpenID Connect com *authorization code* e PKCE serve; o provedor concreto é da infraestrutura da empresa.

| Variável | Regra |
|---|---|
| `OIDC_ISSUER` | URL `https` do emissor; o `iss` do token precisa ser igual a ela |
| `OIDC_CLIENT_ID` | Identificador do painel cadastrado no provedor |
| `OIDC_CLIENT_SECRET` | Opcional (cliente público com PKCE); nunca vai para log nem para a tela |
| `OIDC_REDIRECT_URI` | Endereço público do painel terminado em `/auth/callback` (por exemplo `https://painel.exemplo.invalid/auth/callback`, com o endereço real), exatamente como cadastrada no provedor |
| `OIDC_CLAIM_USERNAME` | Claim do `id_token` com o username do Jira (padrão `preferred_username`); a escolha é da implantação |
| `OIDC_USERNAME_ESTRATEGIA` | Como a claim vira o username do Jira: `claim_direto` (padrão; Keycloak e similares), `parte_local_do_email` (claim `ana.silva@empresa`, vale o que vem antes do `@`; Entra ID) ou `conta_windows` (claim `DOMINIO\ana.silva`, vale o que vem depois da barra; ADFS). Uma só estratégia por instalação, sem tentativa em cascata; formato diferente do esperado recusa o login (401 `USERNAME_ILEGIVEL`). O resultado precisa ser igual ao `id` do `papeis.yaml` |
| `OIDC_DOMINIOS_PERMITIDOS` | **Obrigatória** com `parte_local_do_email` e `conta_windows` (sem ela a produção recusa a subida, `DOMINIO_DO_USERNAME_AUSENTE`, e nenhum login casa). Domínios ASCII separados por vírgula, comparação exata sem diferenciar maiúsculas: `empresa.invalid` (depois do `@`) ou `EMPRESA` (antes da barra). Sufixo e subdomínio parciais não valem. Motivo: um convidado B2B de outro domínio com a mesma parte local, ou uma conta de floresta confiável (`OUTRODOMINIO\gerente.exemplo`) teria a mesma parte local de uma pessoa do `papeis.yaml` e herdaria os papéis dela. Domínio fora da lista: 401 `USERNAME_ILEGIVEL`, sem eco do valor |
| `OIDC_HOSTS_PERMITIDOS` | Opcional: hosts aceitos nos endereços da descoberta (`authorization_endpoint`, `token_endpoint`, `jwks_uri`), separados por vírgula, só o nome do host. Com a lista, qualquer endereço fora dela é recusado; sem a lista, são recusados IP literal, `localhost`, `*.local` e `*.localhost` (com ou sem ponto final). **A validação é só pelo NOME, sem resolver DNS**: nomes que resolvem para IP interno (`intranet`, `*.internal`, `10.0.0.1.nip.io`, DNS rebinding) só são barrados com a lista `OIDC_HOSTS_PERMITIDOS`; em produção, defina-a. Com a lista o nome é comparado exato (`permitido.com.` com ponto final NÃO vale como `permitido.com`). O erro cita só o código `DESCOBERTA_ENDPOINT_NAO_PERMITIDO`, nunca o valor |
| `SESSION_SECRET` | Segredo que assina o cookie de sessão: base64, no mínimo 32 bytes (veja `docs/operacao/rotacao-de-segredos.md`) |

- Ausente **ou em branco**, qualquer uma das quatro obrigatórias derruba a subida; a mensagem cita só o nome da variável.
- Os **papéis nunca vêm do token**: vêm do `papeis.yaml` na data de hoje. Quem não está lá, ou cujo papel acabou, recebe 403.
- O `id_token` é validado com as chaves do próprio provedor (descoberta em `/.well-known/openid-configuration` e JWKS): só `RS256`, emissor igual ao configurado, `aud` com o `OIDC_CLIENT_ID`, `exp`, `nonce` do login e PKCE. O segredo do cliente, se houver, vai no corpo da troca do código (`client_secret_post`); um provedor que exija autenticação Basic nesse endpoint pede uma mudança de código (avise antes de implantar).
- Cookie `painel_sessao`: `HttpOnly`, `Secure`, `SameSite=Lax`, assinado, 8 horas. O painel escuta atrás do proxy da empresa, que termina o TLS.
- `GET /auth/login`, `GET /auth/callback` e `POST /auth/logout` gravam o registro de acesso, sem token.
- **A claim do papel precisa ser imutável e controlada pelo administrador do provedor.** O painel identifica a pessoa pela claim `OIDC_CLAIM_USERNAME` (padrão `preferred_username`) e a usa como `id` do `papeis.yaml`. Pelo OpenID Connect Core (seção 5.7), `preferred_username` NÃO é única nem estável: em muitos provedores a própria pessoa pode alterá-la, e quem a altera para o username de outra pessoa assume os papéis dela. Só use uma claim que o usuário não consiga editar e que o provedor nunca reaproveite para outra pessoa. O `sub` (identificador estável do provedor) é validado (presente e coerente) e hoje é **descartado**; o Plano 13 decide a claim definitiva e se o painel passa a vincular o `sub` ao username.
- **Saída até o provedor por proxy da empresa:** o cliente HTTP do login (`ClienteHttpOidc`, motor CIO do Ktor) não lê sozinho as propriedades de proxy da JVM; é preciso configurar o proxy nesse cliente (e o certificado raiz da empresa no repositório de confiança do JDK). Sem isso a descoberta e a troca do código falham com `PROVEDOR_INDISPONIVEL`. Quem faz essa configuração é o Plano 13; avise antes de implantar.
- **"Sair" encerra só a sessão do painel**, não a sessão no provedor: quem clica em "Sair" e entra de novo pode voltar sem digitar a senha, se a sessão do provedor continuar aberta. Encerrar a do provedor é feito no próprio provedor.
- Rotação do segredo da sessão sem derrubar ninguém: mova o valor atual para `SESSION_SECRET_ANTERIOR`, ponha o novo em `SESSION_SECRET` e reinicie; depois apague a anterior. O início da rotação fica gravado ao lado do banco (`painel.db.rotacao`), então reiniciar não reabre a janela do segredo antigo.

## 7. Endereço de escuta

O painel escuta em `127.0.0.1:8080` por padrão (`PAINEL_HOST`): só a própria máquina o alcança, e o proxy da empresa, instalado na mesma máquina, encaminha para ele e termina o TLS. Se o proxy estiver em **outra** máquina, defina `PAINEL_HOST` com **um** endereço da rede interna (em produção `0.0.0.0` e `::` são recusados: `ESCUTA_ABERTA_A_TODAS`) e `DASHBOARD_PROXY_CONFIAVEL` com o IP do proxy: isso expõe o painel a toda a rede dessa interface, então só com regra de firewall que libere apenas o proxy. O `painel validar-config` avisa (`ESCUTA_FORA_DO_LOOPBACK`) quando a escuta sai do loopback. A porta das métricas (9464) tem variável própria e também escuta só no loopback.

## 8. Um painel por arquivo de banco

O painel grava uma trava ao lado do banco (`painel.db.lock`). Uma segunda subida no mesmo arquivo é recusada. Não existe "outra réplica": o SQLite tem um gravador por vez, e a sincronização e as consultas dividem o mesmo arquivo (WAL: leituras não bloqueiam). Para trocar o pacote: pare o serviço, troque a pasta do pacote, suba de novo.

## 9. Ligar o Jira: ordem segura

O pacote chega à produção com o Jira **desligado**. Quem liga é só o usuário, à mão, no arquivo de variáveis, depois de seguir:

1. `docs/jira/checklist-primeira-conexao.md` (o que conferir e quando parar);
2. `docs/jira/runbook-sincronizacao.md` (como acompanhar e o que fazer se algo sair do esperado).

Os testes automáticos do projeto usam só fixtures e `MockEngine`, mesmo com a chave ligada: nenhum teste acessa o Jira real nem um provedor de identidade real.

## 10. Serviço, backup, atualização e reversão

| Assunto | Onde |
|---|---|
| Serviço do sistema operacional, logs, prontidão (`/api/health/pronto`), métricas, alertas, backup consistente do SQLite e restauração testada | Plano 12, documentos em `docs/operacao/` |
| Atualização com backup antes (`deploy/windows/atualizar.ps1`, `deploy/linux/atualizar.sh`) | `docs/implantacao/atualizacao.md` |
| Reversão, com ou sem o banco, e o ensaio da troca (`ensaio-troca`) | `docs/implantacao/reversao.md` e `docs/implantacao/atualizacao.md` |
| Segredos, permissão do arquivo de variáveis e verificação de logs | `docs/implantacao/segredos.md` |
| Proxy, TLS, serviço do sistema operacional, conta sem privilégio e firewall | Plano 13 (Tarefa 8, depois da decisão com a infraestrutura) |

**Regra de reversão:** o esquema do banco só avança, por migração. Toda migração nova precisa ser compatível com o pacote anterior **ou** ter backup feito antes da atualização; reverter é voltar a pasta do pacote anterior (e, se a migração não for compatível, restaurar o backup).

## 11. O que pedir à infraestrutura

- Provedor OIDC cadastrado (`OIDC_ISSUER`, `OIDC_CLIENT_ID`, segredo se exigir, URI de retorno `.../auth/callback`) e a claim que traz o username do Jira.
- Proxy reverso com TLS na frente do painel, encaminhando para `127.0.0.1:8080`, e a origem pública para `DASHBOARD_ORIGEM_PUBLICA` (obrigatória atrás de proxy; `https`).
- Conta de serviço **sem privilégio**, dona só das pastas de dados, de configuração e de backup; permissão restrita ao arquivo de variáveis.
- Regra de firewall: nada de entrada direta na porta 8080 além do proxy; a 9464 só para o coletor de métricas, se houver.
- Volume cifrado (BitLocker/LUKS) para o banco e para as cópias, e a cópia diária para fora do servidor.
- Se a saída até o provedor passar por proxy da empresa: certificado raiz no repositório de confiança do JDK.

## 12. Antes do uso real: DPO

O painel guarda auditoria de acessos e de alterações e tem a tela de Integridade (só gerente, sempre auditada). A base legal, o prazo de retenção e o texto de comunicação precisam da validação do DPO **antes** de dados reais entrarem. Até lá, só fixtures e demonstração.
