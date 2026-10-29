# Perguntas frequentes

Versão em inglês: [`faq.md`](faq.md). Guia completo, tela por tela: [`guia-do-painel.md`](guia-do-painel.md) (em inglês: [`panel-guide.md`](panel-guide.md)). Os termos abaixo são os que as telas mostram.

> **Estado de hoje (29/10/2026).** O acesso ao Jira está desligado (flag `JIRA_LIVE_ENABLED=false`): o painel só mostra dados de demonstração fictícios. As respostas sobre sincronização e sobre números reais valem depois que o usuário ligar a flag. Os pontos que dependem do Plano 11 (tela "Meus dados", Trava do DPO na Auditoria) são **previstos** e ainda não estão no painel.

## 1. Por que "No prazo" e "No prazo ajustado" são diferentes?

São duas métricas lado a lado, e as duas usam as mesmas tasks: concluídas (categoria concluído **e** resolução preenchida) com vencimento no período. "No prazo" conta as que saíram até o vencimento, pelo dia da resolução no fuso de Manaus. "No prazo ajustado" conta também as que passaram do vencimento, mas têm um label que justifica o atraso (a lista está em `labels_atraso.yaml`). A diferença entre as duas é, portanto, o atraso justificado. Ver os cartões MT-01 (Metas e prazos), FE-05 (Fechadas) e MP-06 (Meu painel). Um label que está sob auditoria continua contando no "ajustado", mas aparece num contador à parte na Auditoria.

## 2. O que é "Indício, não prova"?

É o aviso fixo das telas de Auditoria e de Integridade. Um sinal mostra uma alteração ou um padrão que merece conferência humana (por exemplo, um vencimento alterado por quem não era autorizado). Ele não diz que alguém agiu mal: pode haver uma razão legítima. O caminho é conversar e conferir a task, nunca punir pelo número. Por isso a consulta a esses módulos é registrada e a Integridade é só do gerente.

## 3. O que significa "sem capacidade"?

Quando a capacidade líquida de uma pessoa numa semana é zero (férias, feriado, ausência cadastrada), dividir a demanda pela capacidade não faz sentido. O painel então mostra "sem capacidade" e as horas planejadas, em vez de uma porcentagem. Está nos cartões de Carga (CP-03), Capacidade (CA-05) e Plano 4–8 semanas (PL-01). Se a pessoa aparece "sem capacidade" e não deveria, confira `ausencias.yaml` e o calendário de feriados.

## 4. Por que o painel conta dias úteis?

Porque o prazo de trabalho não corre em fim de semana nem em feriado. O padrão é **Dias úteis**, com feriados do calendário versionado e o fuso America/Manaus; os filtros oferecem **Dias corridos** quando você quer o tempo de relógio. A unidade escolhida vale para os cartões de tempo, idade e ciclo da tela.

## 5. Por que não vejo a task de outra pessoa?

Porque cada papel tem a sua visão. O **Usuário** vê só o que é seu; o **Squad Leader** vê o próprio e o seu squad; **Coordenador** e **Gerente** veem todos. Uma pessoa fora da sua visão aparece como "outra pessoa", sem chave nem nome, e os totais dos cartões já são calculados só sobre o que você pode ver. Se precisa de uma visão maior, o papel é definido em `papeis.yaml`: fale com o dono da manutenção.

## 6. Por que um número mudou de ontem para hoje?

Três razões comuns: (1) a sincronização com o Jira roda a cada 10 minutos (padrão) e a reconciliação diária, às 03:00, corrige o que escapou; cada cartão mostra "atualizado há" com o momento da última sincronização; (2) as tasks mudaram de fato no Jira (status, responsável, vencimento, resolução); (3) o período móvel andou um dia (por exemplo, "últimos 90 dias"). Hoje, com a flag do Jira desligada, nada sincroniza e os dados de demonstração só mudam quando o banco é recriado.

## 7. Por que não há ranking de pessoas?

É uma decisão de produto. Ranking transforma indício em julgamento, incentiva o número em vez do trabalho e ignora que carga, tipo de task e ausências são diferentes. As tabelas de pessoas ficam em ordem alfabética, sem posição nem nota, e os indicadores de pessoa são capacidade e carga, não desempenho.

## 8. O que fazer quando um número parece errado?

1. Abra o número: a gaveta de tasks mostra as tasks que o compõem, com o mesmo total do cartão.
2. Leia o ⓘ do cartão ("O que mostra", "Como é calculado", "Fonte") ou o Mapa de cartões; a regra pode explicar a surpresa (por exemplo, só entram concluídas com resolução).
3. Veja a Qualidade dos dados: dado faltando ou desencontrado vem de lá.
4. Se ainda parecer errado, registre: cartão, filtros (a URL) e o que esperava, e envie ao coordenador ou ao dono da manutenção. O registro de divergências que acompanha a validação com o Jira real (Plano 14) **ainda não existe**; até lá, o aviso vai por este caminho e entra na lista de pendências.

## 9. Quem pode alterar responsável, vencimento e labels?

Só o Reporter da task (o PL), os coordenadores e o gerente, ou o Squad Leader do responsável podem trocar **responsável**, **vencimento** e **labels que evitam atraso**. As listas de coordenadores e Squad Leaders vêm de YAML com vigência por datas, e a conferência usa os papéis vigentes na data de cada alteração. Qualquer outra pessoa que altere esses campos gera alerta ou auditoria. O painel só lê: quem altera é o Jira, e a Auditoria apenas mostra o que mudou. Lembre: "Indício, não prova".
