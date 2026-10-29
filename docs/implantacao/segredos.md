# Segredos e arquivo de variáveis protegido

Esta página diz onde cada segredo do painel fica na máquina de produção, quem o cria, como gerar, como injetar, como proteger o arquivo e como conferir que nada vazou para o log. Ela vale para a decisão padrão (arquivo `painel.env` com permissão restrita); se a empresa preferir um cofre, veja "Contrato do cofre" no fim.

Regras que não mudam:

- Nenhum segredo entra no repositório, no pacote, em argumento de comando, em log nem em resposta. O arquivo de variáveis real chama-se `painel.env`, existe **só no servidor** e é ignorado pelo git (`**/painel.env`). O repositório só traz o modelo `deploy/modelos/painel.env.exemplo` (e `implantacao/modelos/` dentro do pacote).
- **Não existe senha de banco:** o banco é um arquivo SQLite, protegido pela permissão do arquivo e da pasta. Não há o que rotacionar nem guardar.
- Quem edita o `painel.env` é uma pessoa autorizada da operação, num editor aberto como administrador (Windows) ou com `sudoedit` (Linux). Nenhuma IA abre, lê ou edita esse arquivo. A chave do acesso ao Jira (`JIRA_LIVE_ENABLED`) é do usuário e este documento nunca a liga.

## 1. Os segredos

| Segredo | Onde fica | Quem cria | Como gerar | Como injetar |
|---|---|---|---|---|
| `SESSION_SECRET` | `painel.env` | A operação, uma vez | `gerar-session-secret` (48 bytes aleatórios em base64; o painel exige no mínimo 32) | O próprio gerador grava a linha no arquivo, sem imprimir o valor |
| `SESSION_SECRET_ANTERIOR` | `painel.env`, só durante a rotação | O gerador, com `-Rotacionar` / `--rotacionar` | Recebe o valor que era o atual | Idem; apague a linha 8 horas depois da troca |
| `OIDC_CLIENT_SECRET` | `painel.env` | A infraestrutura (equipe do provedor de identidade) | Entregue por ela; opcional se o provedor aceitar cliente público com PKCE | A operação cola o valor no editor aberto como administrador; **nunca** em argumento de comando |
| `JIRA_SAMSUNG_TOKEN` e `JIRA_SIDIA_TOKEN` | `painel.env` | O **usuário**, no próprio Jira, **só quando ele decidir ligar a flag** (Plano 14) | Criados e revogados no Jira pelo usuário | O usuário os põe no arquivo; a produção sobe com a flag desligada e sem token |

Cada troca de valor exige reiniciar o serviço. Os procedimentos de rotação (quando, como, como conferir e como desfazer) estão em `docs/operacao/rotacao-de-segredos.md`.

## 2. Gerar o `SESSION_SECRET` sem ver o valor

| Windows 11 (PowerShell, como administrador) | Linux (`sh`, como root) |
|---|---|
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy\windows\gerar-session-secret.ps1 -Arquivo C:\Painel\dados\painel.env` | `sh deploy/linux/gerar-session-secret.sh /etc/painel/painel.env` |

O script grava a linha `SESSION_SECRET=` direto no arquivo e imprime apenas `SESSION_SECRET_GERADO`. Se a linha já tem valor, ele **recusa** (código 3) para não derrubar as sessões sem querer. Para trocar de propósito use `-Rotacionar` (sh: `--rotacionar`): o valor atual vira `SESSION_SECRET_ANTERIOR`, quem está logado continua logado e o script imprime `SESSION_SECRET_ROTACIONADO`.

## 3. Permissão do arquivo de variáveis

O processo do painel só **lê** o arquivo. Ninguém além dos administradores e da conta do serviço pode abri-lo.

Windows (SID no lugar de nome, para valer em qualquer idioma do Windows; a conta é a do serviço, por exemplo `.\svc-painel`):

```powershell
icacls "C:\Painel\dados\painel.env" /inheritance:r /grant:r "*S-1-5-32-544:F" "<conta>:R"
```

`*S-1-5-32-544` é o grupo Administradores. **Nenhuma entrada** para Everyone (`*S-1-1-0`), BUILTIN\Users (`*S-1-5-32-545`) nem Authenticated Users (`*S-1-5-11`). A função `Restringir-ArquivoDeVariaveis` de `deploy\windows\comum.ps1` aplica isso e a `Test-ArquivoDeVariaveisProtegido` confere.

Linux:

```sh
chown root:painel /etc/painel/painel.env
chmod 640 /etc/painel/painel.env
```

O dono (root) escreve, o grupo `painel` (o usuário do serviço) só lê, os demais nada. A função `restringir_arquivo_de_variaveis` de `deploy/linux/comum.sh` aplica isso e `arquivo_de_variaveis_protegido` confere (modo 640 ou mais restrito).

## 4. Primeira preparação (efeito fora do repositório)

Só depois de o usuário autorizar a instalação na máquina:

1. Copie o modelo para o caminho real (`C:\Painel\dados\painel.env` ou `/etc/painel/painel.env`) e ajuste os marcadores `exemplo.invalid`.
2. Rode o gerador do `SESSION_SECRET` (seção 2).
3. Cole o `OIDC_CLIENT_SECRET` entregue pela infraestrutura, se houver.
4. Aplique a permissão (seção 3).

**Como conferir:** a verificação de instalação passa o item de permissão do arquivo; o painel sobe sem `SESSION_SECRET_CURTO`; a verificação de logs (seção 5) termina com `LOG_LIMPO`.

## 5. Conferir que nenhum segredo vazou para o log

| Windows 11 (PowerShell) | Linux (`sh`) |
|---|---|
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy\windows\verificar-logs.ps1 -Raiz C:\Painel` | `sh deploy/linux/verificar-logs.sh /etc/painel/painel.env` |

Para cada variável do `painel.env` cujo nome contém `SECRET`, `PASSWORD` ou `TOKEN` e tem valor, o script procura esse valor nos logs (`C:\Painel\logs\*.log`; Linux: `journalctl -u painel --no-pager`) e imprime **só** `LOG_LIMPO` ou `LOG_COM_SEGREDO <NOME_DA_VARIAVEL>`, nunca o valor. Achou: trate como vazamento e rotacione o segredo (`docs/operacao/rotacao-de-segredos.md`).

## 6. Contrato do cofre (se a empresa escolher um)

O painel não conhece o cofre. A infraestrutura fornece um comando de pré-início que grava o `painel.env` com as permissões da seção 3 antes de cada subida: no NSSM, um evento `Start/Pre` (`AppEvents`); no systemd, `ExecStartPre=`. Fora isso, nada muda: as mesmas variáveis, a mesma verificação de logs e a mesma rotação, com a ressalva de que o valor da fonte da verdade passa a ser o cofre.
