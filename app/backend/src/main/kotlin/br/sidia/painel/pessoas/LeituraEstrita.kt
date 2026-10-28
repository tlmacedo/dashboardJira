package br.sidia.painel.pessoas

import com.charleskorn.kaml.UnknownPropertyException
import com.charleskorn.kaml.YamlException
import kotlinx.serialization.SerializationException
import java.nio.file.Path
import java.time.DateTimeException

/**
 * Linha (1 a partir do topo) do item [numero] (1 a partir de 1) da lista [chave] do YAML, ou nulo se não der para achar.
 * Só devolve posição: nunca um valor lido. Usada pelo validador para dizer "arquivo:linha" sem citar dado pessoal.
 */
internal fun linhaDoItem(path: Path, chave: String, numero: Int): Int? =
    try {
        val raiz = com.charleskorn.kaml.Yaml.default.parseToYamlNode(java.nio.file.Files.readString(path))
        (raiz as? com.charleskorn.kaml.YamlMap)?.get<com.charleskorn.kaml.YamlList>(chave)?.items?.getOrNull(numero - 1)?.location?.line
    } catch (e: Exception) {
        null
    }

/**
 * Leitura estrita dos YAML de pessoas e planejamento: toda falha vira [IllegalArgumentException] que cita o arquivo e,
 * conforme o caso, a linha ou o item (#N). Nunca devolve o VALOR lido nem o NOME de chave lido (o arquivo tem dados
 * pessoais) e a exceção nova NÃO encadeia a causa: a mensagem do parser do YAML traz o valor. Os catches específicos vêm
 * ANTES do genérico porque os do kaml são subclasses de [IllegalArgumentException].
 */
internal fun <T> lerEstrito(path: Path, leitura: () -> T): T =
    try {
        leitura()
    } catch (e: UnknownPropertyException) {
        throw IllegalArgumentException("$path: chave desconhecida na linha ${e.line}")
    } catch (e: YamlException) {
        throw IllegalArgumentException("$path: formato inválido na linha ${e.line}, coluna ${e.column}")
    } catch (e: SerializationException) {
        throw IllegalArgumentException("$path: formato inválido (${e.javaClass.simpleName})")
    } catch (e: NumberFormatException) {
        throw IllegalArgumentException("$path: número inválido")
    } catch (e: DateTimeException) {
        throw IllegalArgumentException("$path: data inválida")
    } catch (e: IllegalArgumentException) {
        // Só as mensagens do próprio painel (item #N e regra violada, sem valor lido).
        throw IllegalArgumentException("$path: ${e.message}")
    } catch (e: RuntimeException) {
        throw IllegalArgumentException("$path: formato inválido (${e.javaClass.simpleName})")
    }
