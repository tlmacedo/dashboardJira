# Progresso: correções da auditoria final da main (29/10/2026)

Branch `fix/auditoria-final` (a partir da `main` em 1ad9529). Sem PR aberto. Relatório de origem: auditoria final da main (veredito REPROVADO por I-1 a I-3; sem Critical).

## O que foi feito

- **I-1 (regra 1):** `scripts/tempo_em_status.py` agora lê SÓ a linha da flag de `app/.env` (da raiz do repositório, nunca do ambiente do processo) e sai com código 2, com mensagem sem eco de segredo, ANTES de qualquer rede ou leitura de credencial. Mesmo parser do painel (`ArquivoEnv`): nome exato com caixa, última ocorrência, `export`, aspas e comentário final opcionais, valor aceito como o app (`true` sem diferenciar caixa); arquivo ausente, ilegível, sem a linha ou qualquer outro valor = desligada. Testes Python provam 0 chamadas de rede e 0 leituras da credencial. `ScriptsNaoAcessamJiraSemTravaTest` varre scripts, `app/scripts`, `deploy`, `docs` e `app/frontend/scripts` e falha se aparecer cliente de rede que cite o Jira sem checar a flag. Guia `docs/guia/07-historico-de-status.md` avisa da trava.
- **M-6:** `docs/jira/diagnostico-conexao.ps1` usa `-cmatch` no nome e `-ceq` no valor (já em minúsculas, como o booleano do app). Teste estático.
- **I-3:** as 3 provas do `como_servico` deixaram de usar `assumeTrue`: sem ser root rodam os scripts e o `chown` por `sudo -n env ...`; sem privilégio pulam localmente e FALHAM com `CI=true` em Linux (`Ferramentas.exigir`). `PAINEL_TESTE_FORCAR_SUDO=1` simula o caminho do CI sendo root.
- **M-3:** `Settings` não cita mais o valor lido (fuso, pool, caminhos, booleanos) nem encadeia a causa. SESSION_SECRET, OIDC_CLIENT_SECRET, tokens e o aceite/ref do DPO nunca eram citados (conferido). Teste percorre 15 variáveis.
- **M-4:** `ci.yml`, job `frontend`: passo "Orçamento do bundle" depois da CSP. Nomes dos jobs e os 4 checks obrigatórios intactos.

## Rulings

- **Varredura de scripts (I-1):** além do `tempo_em_status.py` só têm cliente de rede `app/scripts/verificar-pacote.{sh,ps1}` e `deploy/linux/{comum,atualizar}.sh` e `deploy/windows/comum.ps1`. Todos falam apenas com o `/api/health` do próprio painel (loopback; `PAINEL_URL` tem padrão `127.0.0.1`), nunca com o Jira: a trava não se aplica e eles ficam numa lista LOCAIS do teste, com motivo, e só valem enquanto citarem endereço de loopback ou `.invalid` e nenhum `/rest/api`. `docs/jira/diagnostico-conexao.ps1` já tinha a trava. **`legacy/` não foi alterado**: `legacy/backend/jiradash/jira/client.py` (e `teams.py`) abre conexão ao Jira sem a trava da flag; só relato (legacy é referência, não evoluir). Quem rodar o app Python antigo contra um Jira real contraria a regra 1 fora do alcance do hook de IA; decisão do usuário se o `legacy/` deve ser removido (Plano 8) ou receber a trava.
- **Valor da flag nos scripts (I-1/M-6):** o app aceita o valor `true` sem diferenciar caixa (`booleano` faz `lowercase`) e o nome com caixa exata. Os scripts seguem exatamente isso (o PowerShell com `ToLowerInvariant()` + `-ceq`), em vez de exigir minúsculas, para a trava de scripts nunca divergir da do app.
- **I-3, escolha do `sudo -n` no teste em vez de passo dedicado no `ci.yml`:** o runner do GitHub tem sudo sem senha e o usuário `nobody`; assim nada muda no pipeline e não há Gradle como root deixando arquivos de root em `build/`. Os arquivos que o root e o `nobody` criam na pasta temporária do teste são apagados por `sudo rm -rf` ao fim. Conferido localmente com usuário comum + sudoers `NOPASSWD` + `CI=true`: 35 testes, 0 pulados; removendo a baixa de privilégio de `comum.sh` o teste falha; sem sudo, as 3 provas falham (não pulam).
- **M-3:** a causa encadeada foi descartada (a mensagem do JDK repete o valor). Perde-se o rastro técnico da causa, que só repetia o texto digitado.

## Minor não corrigidos nesta branch

- **M-1** (`access_log` grava valor textual cru de filtro nas rotas sensíveis): **adiado**. Decisão já registrada: o texto é limitado a 100 caracteres, digitado pelo próprio consultante e só aparece para quem tem acesso ao registro de acessos. Recomendação futura: gravar só datas ISO válidas ou `(invalido)` nas rotas sensíveis.
- **M-2** (modo dev + flag ligada + escuta aberta não é recusado): **aceito**. Decisão já tomada e fixada em `GuardasDeProducaoTest` (as guardas não se aplicam em modo dev; a subida emite WARN). Revisitar se o pacote passar a ser distribuído sem o perfil de produção.
- **M-5** (cadeia de suprimentos: actions por tag em vez de SHA, `distributionSha256Sum` do wrapper do Gradle, `packageManager` no `package.json` e pnpm 9.12.3 no CI contra 10.x local): **recomendação ao usuário**, sem alteração aqui; mudar pins de actions e do wrapper altera a cadeia de CI e deve ser decisão dele.
- **M-7** (`/api/meta` pública consulta o banco a cada chamada com a flag ligada): **adiado**, custo baixo; um cache curto resolve se virar problema.
- **M-8** (3 avisos de lint `only-export-components`): **adiado**, sem efeito funcional.

## Pendências

- I-2 (HANDOFF.md defasado) é de documentação e está fora do escopo desta branch; o relatório indica que outro agente pode estar cuidando.
