package br.sidia.painel.http

/**
 * Todo código de aviso e de erro que o backend pode emitir. A tela monta o texto pelo código, em pt-BR ou inglês:
 * aviso em `cardpage.aviso.<CODIGO>`, erro em `erros.<CODIGO>`, com `{param}` interpolado. A mensagem em português que
 * ainda viaja no `AvisoDto.mensagem` serve a log e compatibilidade; a tela nunca a mostra.
 *
 * Código novo entra aqui E nos arquivos de tradução (pt e en): `CodigosDeMensagemTest` falha se faltar um dos dois, ou se o
 * código-fonte emitir um código que não esteja listado.
 */
object CodigosDeMensagem {
    /** Códigos de `AvisoDto` (informativos e bloqueantes). */
    val avisos: Set<String> = setOf(
        "STATUS_SEM_MAPEAMENTO", "CONCLUSAO_DIVERGENTE", "CONCLUIDA_SEM_DATA", "CALCULO_INDISPONIVEL", "DATAS_INCONSISTENTES",
        "OS_INDISPONIVEL", "AMOSTRA_PEQUENA", "CAMPOS_DE_TRABALHO_INDISPONIVEIS", "SEM_HISTORICO_DO_TIPO", "DONE_SEM_RESOLUTION",
        "ORDEM_DO_FLUXO_AUSENTE", "SEM_VAZAO_HISTORICA", "EPICO_INDISPONIVEL", "OPCOES_TRUNCADAS",
        "CAMPOS_DO_DICIONARIO_INCOMPLETOS", "REPORTER_ALTERADO", "VENCIMENTO_ILEGIVEL", "ISSUES_IGNORADAS_STATUS",
        "VINCULO_AMBIGUO", "VINCULOS_INDISPONIVEIS", "PARES_EM_AMOSTRA_PEQUENA", "LISTA_PARCIAL",
        "PESSOAS_SEM_CADASTRO_NA_EQUIPE", "HORAS_ASSUMIDAS", "HORAS_ATUAIS_NO_PASSADO", "AMOSTRA_PEQUENA_NA_JANELA",
        "BLOQUEIO_FORA_DA_BASE", "SEM_VINCULOS_NA_BASE", "INDICIO_NAO_PROVA", "VALIDAR_BASE_LEGAL_COM_DPO",
    )

    /** Códigos de `ErroDto`: todo 400 (e demais erros de requisição) sai com um deles, nunca com texto. */
    val erros: Set<String> = setOf(
        "FILTRO_INVALIDO", "SEGMENTO_INVALIDO", "CHAVE_INVALIDA", "CSRF_AUSENTE", "ORIGEM_INVALIDA", "LOGIN_FALHOU",
        "PROVEDOR_INDISPONIVEL", "AGUARDANDO_ACEITE_DPO", "JA_DECIDIDA", "SUGESTAO_MUDOU", "DADOS_INDISPONIVEIS",
        "CORPO_INVALIDO", "MOTIVO_OBRIGATORIO", "MOTIVO_LONGO", "ESCOLHIDO_INVALIDO", "SEM_SUGESTAO",
    )

    /**
     * Constantes de texto que NÃO são mensagem: valores de enumeração (tipo de decisão, estado da bloqueadora, modo da
     * sincronização) e o código de um item da Qualidade dos dados, que a tela traduz por outras chaves.
     */
    val outros: Set<String> = setOf(
        "DISTRIBUICAO", "REBALANCEAMENTO", "VISIVEL", "OUTRO_SQUAD", "FORA_DA_BASE", "DESLIGADO_PELA_FLAG", "PERIODICO",
        "LIBERADA", "BLOQUEADA_SEM_CAUSA",
    )
}
