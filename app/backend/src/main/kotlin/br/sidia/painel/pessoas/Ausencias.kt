package br.sidia.painel.pessoas

import br.sidia.painel.security.RoleDirectory
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Ausência de [pessoa] de [de] a [ate] (ambas inclusivas), com [fracao] do dia (1,0 = dia inteiro). NÃO tem motivo. */
data class Ausencia(val pessoa: String, val de: LocalDate, val ate: LocalDate, val fracao: Double) {
    init {
        require(!de.isAfter(ate)) { "datas invertidas (de depois de ate)" }
        require(fracao > 0.0 && fracao <= 1.0) { "fracao fora de (0, 1]" }
    }

    fun cobre(dia: LocalDate): Boolean = !dia.isBefore(de) && !dia.isAfter(ate)
}

/**
 * As ausências do time, somente leitura. Com a mesma pessoa em dois períodos que se cruzam a carga falha; já [mais] (o
 * simulador) aceita a hipótese cruzando uma ausência real e então o dia vale a MAIOR fração.
 */
class Ausencias private constructor(lista: List<Ausencia>, validarSobreposicao: Boolean) : Iterable<Ausencia> {
    constructor(lista: List<Ausencia>) : this(lista, true)

    private val itens: List<Ausencia> = lista.toList()
    private val porPessoa: Map<String, List<Ausencia>> = itens.groupBy { it.pessoa }

    init {
        if (validarSobreposicao) {
            for ((pessoa, dela) in porPessoa) {
                val ordenadas = dela.sortedBy { it.de }
                for (i in 1 until ordenadas.size) {
                    require(ordenadas[i].de.isAfter(ordenadas[i - 1].ate)) { "ausências da mesma pessoa se sobrepõem" }
                }
            }
        }
    }

    /** Fração de ausência da pessoa no dia: `0.0` sem ausência. */
    fun fracaoEm(pessoa: String, dia: LocalDate): Double =
        porPessoa[pessoa].orEmpty().filter { it.cobre(dia) }.maxOfOrNull { it.fracao } ?: 0.0

    fun daPessoa(pessoa: String): List<Ausencia> = porPessoa[pessoa].orEmpty()

    /** As ausências que tocam o intervalo `[de, ate]` (inclusivo), na ordem de carga. */
    fun entre(de: LocalDate, ate: LocalDate): List<Ausencia> = itens.filter { !it.ate.isBefore(de) && !it.de.isAfter(ate) }

    /** Uma cópia com a ausência hipotética (para o simulador); não altera esta nem grava nada. */
    fun mais(hipotetica: Ausencia): Ausencias = Ausencias(itens + hipotetica, validarSobreposicao = false)

    override fun iterator(): Iterator<Ausencia> = itens.iterator()

    companion object {
        /**
         * Estrito: a chave `motivo` é recusada ("ausências não guardam motivo"), pessoa que não está no `papeis.yaml`,
         * datas inválidas ou invertidas e sobreposição da mesma pessoa falham citando o arquivo e o item (#N da lista).
         */
        fun fromYaml(path: Path, roles: RoleDirectory): Ausencias = lerEstrito(path) {
            val texto = Files.readString(path)
            // O motivo é dado sensível que o painel não guarda: a chave é recusada ANTES de ler qualquer valor.
            val lista = (Yaml.default.parseToYamlNode(texto) as? YamlMap)?.get<YamlList>("ausencias")
            lista?.items?.forEachIndexed { i, item ->
                require(item !is YamlMap || item.entries.keys.none { it.content == "motivo" }) {
                    "ausência #${i + 1}: ausências não guardam motivo (remova a chave 'motivo')"
                }
            }
            val bruto = Yaml.default.decodeFromString(AusenciasYaml.serializer(), texto)
            Ausencias(
                bruto.ausencias.mapIndexed { i, a ->
                    val n = i + 1
                    require(roles.conhece(a.pessoa)) { "ausência #$n: pessoa não existe no papeis.yaml" }
                    fun data(s: String, campo: String) = try {
                        LocalDate.parse(s)
                    } catch (e: DateTimeParseException) {
                        throw IllegalArgumentException("ausência #$n: '$campo' não é uma data aaaa-mm-dd")
                    }
                    try {
                        Ausencia(a.pessoa, data(a.de, "de"), data(a.ate, "ate"), a.fracao)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException("ausência #$n: ${e.message}")
                    }
                },
            )
        }
    }
}

@Serializable
private data class AusenciasYaml(val ausencias: List<AusenciaYaml> = emptyList())

@Serializable
private data class AusenciaYaml(val pessoa: String, val de: String, val ate: String, val fracao: Double = 1.0)
