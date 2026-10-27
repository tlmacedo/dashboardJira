# /revisar-tarefa.md: revisão de uma tarefa ou do conjunto

Porte das revisões que o Claude Code fazia com subagentes (`superpowers:requesting-code-review`, o comando `/code-review` e os agentes do `pr-review-toolkit`). Aqui é um checklist que você executa sozinho, com o mesmo rigor de um revisor externo. Fonte: `docs/guia-execucao-outra-ia.md`, seção 5.

## Escopo

- Tarefa: `git diff` (não commitado) ou `git diff HEAD~1` (último commit).
- Conjunto do plano: `git diff main..HEAD`.

Leia o diff inteiro antes de julgar. Para cada achado, anote: arquivo e linha, gravidade (**Critical**, **Important**, **Minor**), por que é problema e a correção proposta.

## 1. Testes (com mutação mental)

Para cada teste importante:
- Ele falharia se a linha principal do código fosse removida ou invertida? Faça a **mutação mental**; se houver dúvida, faça a real: quebre a linha, rode o teste, veja falhar, desfaça.
- A asserção compara o valor esperado de verdade (não só "não nulo" ou "não vazio")?
- Cobre a fronteira (dia que vira, lista vazia, um item, papel sem permissão)?
- Usa SQLite real em arquivo temporário (`testContextComBanco`) e `MockEngine` para o Jira? Nada de H2, mock de banco ou rede real.

## 2. Checklist do projeto

1. **Teste vazio**: passa mesmo com o código quebrado.
2. **Acesso sem `Visibility`**: toda consulta de issues, pessoas ou tempos passa pelo filtro do papel; parâmetros não ampliam a visão; maiúsculas, espaços e caracteres estranhos não furam o filtro; erro não vaza chave nem nome fora da visão.
3. **Dados pessoais** em banco, log ou fixture: `displayName`, e-mail, avatar, `accountId`, textos livres do Jira não são gravados.
4. **Isolamento entre instâncias**: sempre `(instance, key)`.
5. **Segredos**: token, senha e URL com credencial fora de `toString`, log, erro, resposta HTTP e relatório.
6. **SQL só parametrizado**; JQL só pelo `JqlBuilder`/`CardQueryFactory`.
7. **Datas e fuso**: `Instant` no banco; America/Manaus; relógio e calendário injetados; fronteiras `>=` / `<`.
8. **Dado ruim não derruba o card**: vira aviso com código e gravidade, nunca 500 ou zero.
9. **Sem ranking de pessoas**: ordem alfabética.
10. **Interface**: cor nunca é o único sinal; WCAG AA nos 3 temas; pt-BR e inglês completos; foco e `Esc` em gavetas; CSV com neutralização de fórmula (`=`, `+`, `-`, `@`, tab, CR, mesmo após espaços ou aspas).
11. **Fail-closed em produção**: configuração obrigatória ausente derruba a subida; só o modo dev cai nos `.exemplo`.
12. **Comentários em pt-BR**, estilo do arquivo vizinho, sem código morto.

## 3. Lentes extras (equivalentes aos agentes do pr-review-toolkit)

- **Falhas silenciosas**: `catch` que engole erro, fallback que esconde problema, `?: default` que mascara dado ausente.
- **Comentários**: dizem o que o código faz hoje? Nada desatualizado.
- **Tipos**: invariantes expressos no tipo (valores inválidos impossíveis de construir), sem `String` solta onde há um tipo do domínio.
- **Simplicidade**: duplicação, abstração sem uso, nomes confusos.
- **Aderência ao plano**: assinaturas exatas, nada fora do escopo da tarefa.

## 4. Fechamento

- Critical ou Important: corrija agora, rode os testes de novo e repita esta revisão no trecho alterado.
- Minor: corrija se for barato; senão, registre no arquivo de progresso como "Minor adiado" com o motivo.
- Só declare a tarefa concluída depois de rodar os testes e ver o resultado (número de testes e de falhas). Evidência antes de afirmação.
- Para o conjunto do plano, ofereça ao usuário, em pergunta com opções, uma revisão humana ou por outra IA antes da PR.
