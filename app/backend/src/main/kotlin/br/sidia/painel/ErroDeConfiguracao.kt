package br.sidia.painel

/**
 * Falha de configuração na subida, com código estável para o validador (`painel validar-config`): [codigo]
 * (`ARQUIVO_AUSENTE`, `EXEMPLO_EM_PRODUCAO`, `YAML_INVALIDO`, `CHAVE_DESCONHECIDA`, `PAPEIS_SEM_GERENTE_VIGENTE`), o
 * [arquivo] (só o nome relativo, nunca o caminho) e a [linha], quando o parser a informa. Nunca carrega o VALOR errado:
 * ele pode ser o nome de uma pessoa. A mensagem é a mesma de sempre (`IllegalStateException`), então nada muda para quem só captura essa classe.
 */
class ErroDeConfiguracao(
    val codigo: String,
    val arquivo: String,
    val linha: Int? = null,
    mensagem: String,
) : IllegalStateException(mensagem)
