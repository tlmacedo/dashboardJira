package br.sidia.painel.cards

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Um filtro rápido nomeado (ideia I-27): um botão acima dos cards que aplica uma combinação de filtros que JÁ existem
 * (`aplica`: parâmetro de URL -> valores). Não é JQL do Jira e nunca nomeia pessoa ("minhas" é o Meu painel).
 */
@Serializable
data class FiltroRapidoDto(val id: String, val rotuloPt: String, val rotuloEn: String, val aplica: Map<String, List<String>>)

/**
 * `filtros_rapidos.yaml` (versionado, somente leitura, sem dado pessoal; o `.exemplo` vale quando o real não existe).
 * Leitura estrita: parâmetro fora da lista (`squad`, `tipo`, `prioridade`, `os`, `unit`, `campo.<slug>`), valor vazio ou
 * comprido demais, id repetido ou versão desconhecida falham citando o arquivo. A visão do papel continua valendo: o botão
 * só põe os filtros na URL, e o backend os aplica como aplica qualquer filtro.
 */
object FiltrosRapidos {
    private val log = LoggerFactory.getLogger(FiltrosRapidos::class.java)
    private val PARAMETROS = setOf("squad", "tipo", "prioridade", "os", "unit")
    private val FORMATO_DE_CAMPO = Regex("campo\\.[a-z0-9-]{1,40}")
    private val FORMATO_DE_ID = Regex("[a-z0-9-]{1,40}")
    private const val MAX_VALOR = 120

    private val UNITS = setOf("util", "corrido")

    /** [slugsDoDicionario] não nulo: `campo.<slug>` precisa existir no dicionário; `unit` só `util` ou `corrido`, um valor. */
    fun fromYaml(path: Path, slugsDoDicionario: Set<String>? = null): List<FiltroRapidoDto> {
        val arquivo = try {
            Yaml.default.decodeFromString(ArquivoRapidos.serializer(), Files.readString(path))
        } catch (e: SerializationException) {
            throw IllegalArgumentException("$path: YAML de filtros rápidos inválido", e)
        }
        require(arquivo.versao == 1) { "$path: versao ${arquivo.versao} não suportada (esperado: 1)" }
        val vistos = HashSet<String>()
        return arquivo.filtros.map { f ->
            require(FORMATO_DE_ID.matches(f.id)) { "$path: id de filtro rápido inválido" }
            require(vistos.add(f.id)) { "$path: filtro rápido repetido: ${f.id}" }
            require(f.rotuloPt.isNotBlank() && f.rotuloEn.isNotBlank()) { "$path: filtro rápido ${f.id} sem rótulo em pt e en" }
            require(f.aplica.isNotEmpty()) { "$path: filtro rápido ${f.id} não aplica nada" }
            for ((param, valores) in f.aplica) {
                require(param in PARAMETROS || FORMATO_DE_CAMPO.matches(param)) { "$path: parâmetro '${param.take(40)}' não permitido em ${f.id}" }
                require(valores.isNotEmpty()) { "$path: ${f.id}/$param sem valor" }
                if (param == "unit") {
                    require(valores.size == 1 && valores[0] in UNITS) { "$path: unit inválido em ${f.id}" }
                }
                if (slugsDoDicionario != null && param.startsWith("campo.")) {
                    require(param.removePrefix("campo.") in slugsDoDicionario) { "$path: campo desconhecido em ${f.id}" }
                }
                require(valores.all { it.isNotBlank() && it.length <= MAX_VALOR && it.none(Char::isISOControl) }) {
                    "$path: valor inválido em ${f.id}/$param"
                }
            }
            FiltroRapidoDto(f.id, f.rotuloPt, f.rotuloEn, f.aplica)
        }
    }

    /**
     * Lê o arquivo real ou, na falta dele, o modelo; sem nenhum dos dois a lista é vazia (o recurso é opcional). Arquivo
     * inválido não derruba o painel: vai para o log (só o tipo do erro) e a lista fica vazia.
     */
    fun ler(configDir: Path, slugsDoDicionario: Set<String>? = null): List<FiltroRapidoDto> {
        val arquivo = listOf("filtros_rapidos.yaml", "filtros_rapidos.exemplo.yaml").map(configDir::resolve).firstOrNull(Files::isRegularFile)
            ?: return emptyList()
        return try {
            fromYaml(arquivo, slugsDoDicionario)
        } catch (e: IllegalArgumentException) {
            log.warn("filtros_rapidos inválido ({}): nenhum filtro rápido será oferecido", e.javaClass.simpleName)
            emptyList()
        } catch (e: java.io.IOException) {
            log.warn("filtros_rapidos inválido ({}): nenhum filtro rápido será oferecido", e.javaClass.simpleName)
            emptyList()
        }
    }
}

@Serializable
private data class ArquivoRapidos(val versao: Int, val filtros: List<RapidoYaml> = emptyList())

@Serializable
private data class RapidoYaml(val id: String, val rotuloPt: String, val rotuloEn: String, val aplica: Map<String, List<String>> = emptyMap())
