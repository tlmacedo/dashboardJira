# Reversão do painel

Reverter é voltar o ponteiro `atual` para a versão anterior (a de `versao-anterior.txt`, gravada pela [atualização](atualizacao.md)) e, **quando necessário**, restaurar o banco do backup feito antes de atualizar. O esquema do banco só avança (Flyway não tem migração "para baixo"), por isso a escolha entre "só o pacote" e "pacote e banco" é do operador.

## Quando usar cada forma

| Situação | O que fazer |
|---|---|
| A versão nova **não criou migração** (a descrição da versão diz isso) | Só o pacote: `reverter` sem `-RestaurarBanco`. O banco é o mesmo |
| A versão nova criou migração **compatível** com a anterior (a descrição declara que o pacote anterior roda sobre o esquema novo) | Só o pacote. A anterior sobe e funciona, ignorando o que não conhece |
| A versão nova declarou **"migração sem volta"**, ou a anterior não sobe/não funciona sobre o esquema novo, ou há **dúvida** | Pacote **e** banco: `-RestaurarBanco` (sh: `--restaurar-banco`) |

**O que o Flyway faz de fato** (registrado por teste, `ReversaoComBackupTest`): o pacote anterior sobre um banco que já tem uma migração que ele não conhece **sobe sem erro**; o Flyway ignora, por padrão, migrações aplicadas e desconhecidas de versão mais nova. Subir não quer dizer funcionar: o código antigo pode não saber lidar com colunas ou tabelas novas. Por isso a regra é: só o pacote quando a migração for **declaradamente compatível**; na dúvida, restaurar o backup.

## Reverter

| Windows 11 (PowerShell como administrador) | Linux (`sh`, como root) |
|---|---|
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy\windows\reverter.ps1 -Raiz C:\Painel` | `sh deploy/linux/reverter.sh /opt/painel` |
| com o banco: `... reverter.ps1 -Raiz C:\Painel -RestaurarBanco` | com o banco: `sh deploy/linux/reverter.sh /opt/painel --restaurar-banco` |
| outro backup: `... -RestaurarBanco -Backup D:\copias\painel-2026-11-03-020000.db` | `... --restaurar-banco --backup /var/backups/painel/painel-2026-11-03-020000.db` |

O que o script faz:

1. Lê `versao-anterior.txt` (em `C:\Painel\app\` / `/opt/painel/`, pasta só de administradores/root) e recusa se não houver (nenhuma atualização registrada). **Valida tudo o que lê antes de usar como caminho**, porque o que está nesse arquivo vira um programa executado com privilégio: a `versao` tem de ser `AAAA.MM.N` e uma pasta de verdade (nem link nem junção) diretamente dentro da pasta de versões, com `bin/painel` próprio; o `backup=` tem de ser só um nome de arquivo (sem `/`, `\`, `..`, espaço, controle nem traço inicial) e a cópia resolvida tem de ficar dentro de `BACKUP_DIR`. O `--backup`/`-Backup` informado na linha de comando passa pela mesma regra de pasta. Qualquer falha recusa antes de executar qualquer coisa.
2. Com `-RestaurarBanco`: a cópia é lida **uma única vez** para uma pasta temporária só de root/administradores, fora de `BACKUP_DIR` (a conta do serviço escreve em `BACKUP_DIR` e poderia trocar o arquivo por um link para um arquivo protegido entre a validação e o uso); a conferência e a restauração usam **só** esse temporário, e o banco restaurado é criado sem seguir links. Em seguida **confere a cópia** com `painel verificar-restauracao` (Plano 12) **antes** de tocar em qualquer coisa; se não der `RESTAURACAO OK`, para e nada muda.
3. Para o serviço.
4. Com `-RestaurarBanco`: o banco atual e os `-wal` e `-shm` saem **juntos** para o lado, como `painel.db.migrado-<data>`, `painel.db-wal.migrado-<data>` e `painel.db-shm.migrado-<data>`; **nada é apagado**. Depois a cópia é colocada no lugar.
5. Aponta `atual` para a versão anterior, inicia o serviço, espera `GET /api/health` e confere a versão em `/api/meta`.

Termina com `REVERSAO_OK <versao>`. Com `-RestaurarBanco`, rode `painel backup-agora` logo depois: o registro de backups voltou ao estado da cópia.

**O que se perde:** com o banco restaurado, tudo o que foi gravado **depois do backup** (sincronizações, decisões, registros de acesso) só existe no `*.migrado-<data>` guardado ao lado. Se for preciso recuperar algo, abra esse arquivo com o painel parado e copie o que for necessário; ele não é apagado por nenhum script.

**Como conferir:** `REVERSAO_OK`; `atual` aponta para a versão anterior; o rodapé do painel mostra a versão anterior; `painel verificar-restauracao` dá `RESTAURACAO OK`.

## Se a atualização falhou no meio

| Onde falhou | Estado | O que fazer |
|---|---|---|
| SHA-256, extração ou nome do zip | Nada mudou; o serviço segue na versão atual | Corrija o zip e repita |
| Backup | O script religa a versão atual e aborta; nada foi trocado | Corrija `BACKUP_DIR`/espaço e repita |
| Iniciar ou saúde ou fumaça | `atual` já aponta para a versão nova e o serviço pode estar fora do ar | Leia o log (`C:\Painel\logs\painel.log`; Linux `journalctl -u painel`), decida entre corrigir para frente ou `reverter` (com ou sem o banco, conforme a tabela acima) |
