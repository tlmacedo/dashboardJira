# Roteiro da sessão de treinamento

Roteiro comum às sessões por papel. O conteúdo de cada sessão está no material do papel: [Usuário](usuario.md), [Squad Leader](squad-leader.md), [Coordenação](coordenador.md) e [Gerente](gerente.md). Perguntas frequentes: [`perguntas-frequentes.md`](../manual/perguntas-frequentes.md) (inglês: [`faq.md`](../manual/faq.md)).

> **Estado de hoje (29/10/2026).** O acesso ao Jira está desligado (`JIRA_LIVE_ENABLED=false`). As sessões usam **só dados de demonstração** fictícios (`DASHBOARD_DEMO_DATA=true`, com `DASHBOARD_DEV_MODE=true`); nenhum número da sessão é do time. Quando o usuário ligar a flag, o roteiro continua o mesmo, mas a pessoa que conduz deve avisar que, a partir daí, os números são reais e a conversa sobre eles tem outra seriedade. Quando a flag for ligada, a primeira subida apaga sozinha os dados fictícios (só enquanto não houve nenhuma tentativa de sincronização), o painel mostra "Aguardando a primeira carga do Jira" até a primeira carga concluir, e o escopo por papel só vale com dados reais e login.

## Pré-requisitos

- **Acesso ao painel.** Em desenvolvimento (demonstração) não há login e uma faixa vermelha avisa; o papel se escolhe em **Papel (simulação)**, no menu, e isso muda **só o menu**: o servidor em modo de desenvolvimento mostra todos os dados, e o escopo de dados por papel (usuário vê o próprio, Squad Leader o squad) só vale em produção, com login. O Meu painel aparece zerado na demonstração. Em produção o acesso é por login único (SSO/OIDC).
- **Papel em `papeis.yaml`.** Em produção, cada pessoa só vê o que o seu papel permite; confirme com o dono da manutenção que o papel de cada participante está certo antes da sessão.
- **Dados de demonstração carregados.** Siga `app/README.md` (subir o painel com a demonstração ligada). Para recomeçar do zero, pare o painel e apague o arquivo do banco local (`app/data/painel.db`).
- **Sala e tela.** Um navegador com o painel aberto na tela inicial do papel e, de preferência, o tema Claro e pt-BR.
- **Registro.** Anote apenas data, papel e número de participantes (o formato do registro fica em `docs/treinamento/registro.md`, a criar depois do ponto de parada da Tarefa 9 do plano). Nenhum nome vai para o repositório.

## Agenda (30 a 45 min)

| Minutos | Bloco | O que fazer |
|---|---|---|
| 0 a 5 | Abertura | Para que serve o painel (gestão do time, não vigilância), o que ele não faz (sem ranking, não altera o Jira) e o estado de hoje (dados de demonstração). |
| 5 a 10 | Como ler | Cartão, ⓘ, gaveta de tasks com o mesmo total, símbolos `✓` `●` `▲` `■` sempre com texto, **Dias úteis** e **Dias corridos**. |
| 10 a 30 | Roteiro por tela | Percorrer as telas do material do papel, na ordem: pergunta, onde clicar, como ler o número e a armadilha comum. |
| 30 a 40 | Exercício | Os cinco passos do material, em dupla, com o gabarito no fim. |
| 40 a 45 | Fechamento | As perguntas abaixo e o registro da sessão. |

## Pergunta que abre cada tela

Em cada tela, comece perguntando "que pergunta esta tela responde?" e deixe a turma achar a resposta antes de mostrar. As perguntas estão no material do papel.

## Perguntas para fechar a sessão

1. Qual é a diferença entre "No prazo" e "No prazo ajustado"? (Perguntas frequentes, pergunta 1.)
2. O que quer dizer "Indício, não prova" e o que se faz diante de um sinal? (pergunta 2)
3. Por que uma pessoa pode aparecer "sem capacidade"? (pergunta 3)
4. Por que o painel não mostra a task de outra pessoa e não tem ranking? (perguntas 5 e 7)
5. O que fazer quando um número parece errado? (pergunta 8)
6. Onde cada pessoa vê o que o painel guarda sobre ela e como pede correção ou eliminação? (tela **Meus dados** e pergunta 10)

Dúvidas que as perguntas acima não cobrirem viram pergunta nova no FAQ (em pt-BR e em inglês, na mesma ordem) e são anotadas no registro da sessão.

## Depois da sessão

- Registrar data, papel e número de participantes.
- Passar ao dono da manutenção os pedidos de papel, ausência ou feriado que surgirem.
- Anotar para o FAQ as dúvidas novas.
