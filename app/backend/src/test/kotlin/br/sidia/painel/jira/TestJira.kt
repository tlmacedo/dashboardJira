package br.sidia.painel.jira

import br.sidia.painel.jql.JqlQuery

/**
 * Monta um [SearchRequest] de teste a partir de um JQL avulso. Só os testes constroem o JQL à mão:
 * em produção ele vem do construtor de JQL, dentro de um [JqlQuery]. [instancia] precisa ser a do cliente
 * sob teste (nos testes do cliente, `a`; nos da fábrica, o id da instância criada).
 */
fun req(
    jql: String = "x",
    fields: List<String> = listOf("summary"),
    expandChangelog: Boolean = false,
    startAt: Int = 0,
    maxResults: Int = 100,
    instancia: String = "a",
): SearchRequest = SearchRequest(JqlQuery(instancia, jql, fields, expandChangelog), startAt, maxResults)
