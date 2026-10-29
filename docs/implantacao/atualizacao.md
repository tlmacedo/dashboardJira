# Atualização do painel

Atualizar é trocar a pasta da versão: o pacote novo é extraído ao lado do que está em uso, o serviço para, o banco ganha um backup, o ponteiro `atual` muda e o serviço volta. O pacote em uso nunca é sobrescrito. **A atualização não reverte sozinha**: depois de uma migração, voltar o banco é decisão humana (veja [reversão](reversao.md)).

## Disposição na máquina

| | Windows | Linux |
|---|---|---|
| Versões extraídas | `C:\Painel\app\<versao>\` (somente leitura para a conta do serviço) | `/opt/painel/<versao>/` |
| Versão ativa | `C:\Painel\app\atual` (junção) | `/opt/painel/atual` (link simbólico) |
| Dados | `C:\Painel\dados\` (`painel.env`, `config\`, `data\painel.db`, `versao-anterior.txt`) | `/var/lib/painel/` (banco, `versao-anterior.txt`), `/etc/painel/` (`painel.env`, `config/`) |
| Pacotes recebidos | `C:\Painel\recebidos\` | onde o operador deixar o zip e o `.sha256` |

No Linux, `PAINEL_DADOS`, `PAINEL_ENV_FILE`, `PAINEL_SERVICO` e `PAINEL_URL` mudam os padrões (`/var/lib/painel`, `/etc/painel/painel.env`, `painel`, `http://127.0.0.1:8080`). No Windows os parâmetros `-Servico` (padrão `Painel`) e `-Url` fazem o mesmo.

## Antes de atualizar

1. Receba o zip **e** o `.sha256` (veja `docs/implantacao.md`, seção 3.1) na pasta de recebidos. O nome do zip é `painel-AAAA.MM.N.zip`.
2. Leia a descrição da versão nova: ela declara se **cria migração** e, se criar, se o pacote anterior roda sobre o esquema novo. Se disser **"migração sem volta"**, a reversão exigirá restaurar o backup.
3. Confirme que há espaço em disco para o backup e que `BACKUP_DIR` está definido (`painel validar-config`).

## Atualizar

| Windows 11 (PowerShell como administrador) | Linux (`sh`, como root) |
|---|---|
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy\windows\atualizar.ps1 -Raiz C:\Painel -Pacote C:\Painel\recebidos\painel-2026.11.2.zip` | `sh deploy/linux/atualizar.sh /opt/painel /caminho/painel-2026.11.2.zip` |

(Os scripts também estão no pacote, em `implantacao/windows` e `implantacao/linux`.) Passos, nesta ordem, cada um imprime `OK <passo>`:

1. **SHA-256** do zip contra o `.sha256`. Diferente: para, sem extrair nada.
2. **Extrai** em `app\<versao>.tmp`, confere `bin\painel.bat` (Linux: `bin/painel`) e renomeia para `app\<versao>`. **Recusa** se a pasta da versão já existir.
3. **Registra a versão atual** em `versao-anterior.txt` (`versao=<anterior>`).
4. **Para o serviço** e espera o fim do processo: a trava `painel.db.lock` some junto. Com a tarefa agendada, a parada é brusca (o SQLite em WAL tolera).
5. **Backup do banco** com `painel backup-agora` (Plano 12), da versão atual; o nome da cópia vai para `versao-anterior.txt` (`backup=<arquivo>`). Se o backup falhar, o script religa a versão atual e **aborta sem trocar nada**.
6. **Troca `atual`** para a versão nova.
7. **Inicia o serviço** e espera `GET /api/health` por até 120 s (as migrações rodam na subida).
8. **Fumaça**: a saúde responde e `GET /api/meta` informa a versão nova (e, quando existir o script de fumaça da Tarefa 11, ele também roda).

Termina com `ATUALIZACAO_OK <versao>`. Falha no 7 ou no 8: imprime `FALHOU: execute reverter` com o comando exato e sai com código 1. **Nada é revertido automaticamente**: uma reversão automática depois de uma migração poderia piorar o estado.

`-SemServico` (sh: `--sem-servico`) pula os passos 4, 5, 7 e 8 e só serve ao ensaio.

**Como conferir:** `ATUALIZACAO_OK`; `Get-Item C:\Painel\app\atual | Select Target` (Linux `readlink /opt/painel/atual`) mostra a versão nova; o rodapé do painel mostra `v2026.11.2`; `GET /api/meta` traz `versao`.

## Ensaiar a troca sem serviço

`powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy\windows\ensaio-troca.ps1` (sh: `sh deploy/linux/ensaio-troca.sh`). Em pasta temporária, com dois pacotes de mentira, atualiza duas vezes, reverte e confere o destino de `atual` em cada etapa; termina com `TROCA_OK` e `REVERSAO_OK` e apaga tudo ao fim. Não toca no serviço nem em nenhuma pasta real.
