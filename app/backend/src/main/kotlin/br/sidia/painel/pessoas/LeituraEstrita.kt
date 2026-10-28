package br.sidia.painel.pessoas

import com.charleskorn.kaml.UnknownPropertyException
import com.charleskorn.kaml.YamlException
import kotlinx.serialization.SerializationException
import java.nio.file.Path

/**
 * Leitura estrita dos YAML de pessoas e planejamento: toda falha vira [IllegalArgumentException] que cita o arquivo e,
 * conforme o caso, a chave desconhecida, a linha ou o item. Nunca devolve o VALOR lido (o arquivo tem dados pessoais) nem
 * deixa um erro genérico do parser escapar.
 */
internal fun <T> lerEstrito(path: Path, leitura: () -> T): T =
    try {
        leitura()
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("$path: ${e.message}")
    } catch (e: UnknownPropertyException) {
        throw IllegalArgumentException("$path: chave desconhecida '${e.propertyName}' (linha ${e.line})")
    } catch (e: YamlException) {
        throw IllegalArgumentException("$path: formato inválido na linha ${e.line}, coluna ${e.column}")
    } catch (e: SerializationException) {
        throw IllegalArgumentException("$path: formato inválido (${e.javaClass.simpleName})")
    }
