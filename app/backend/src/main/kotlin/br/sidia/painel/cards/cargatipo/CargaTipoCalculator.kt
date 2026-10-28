package br.sidia.painel.cards.cargatipo

import br.sidia.painel.cards.esforco.EsforcoTask
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.sync.normalizarOsVersion
import br.sidia.painel.trabalho.IssueSnapshot
import java.util.Locale

/** O DTO e as tasks por trás de cada célula, por segmento (`abertas:tipo:<T>:os:<V>` e `fechadas:tipo:<T>:os:<V>`). */
class CargaTipoAnalise(val dto: CargaTipoDto, val segmentos: Map<String, List<IssueSnapshot>>)

/**
 * Carga por tipo e OS: as abertas (contagem e horas de fila pelas regras de planejamento) e as fechadas do período (contagem e
 * soma do índice de esforço) por tipo × OS Version. Task sem OS vai para a versão vazia (`""`, "sem OS"), por último. A OS agrupa
 * sem diferenciar maiúsculas nem espaços. Domínio puro; as listas já vêm filtradas pela visão de quem pergunta.
 */
class CargaTipoCalculator(private val motor: MotorDePessoas) {
    fun calcular(abertas: List<IssueSnapshot>, fechadas: List<IssueSnapshot>, esforcos: Map<String, EsforcoTask>): CargaTipoAnalise {
        val naoConcluidas = abertas.filter { motor.statusMap.category(it.status) != StatusCategory.DONE }
        val todas = naoConcluidas + fechadas

        val tipos = todas.map { it.issueType.trim() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        // Uma OS por chave (minúsculas, normalizada); o nome mostrado é a menor grafia normalizada. "sem OS" = chave vazia.
        val grafias = todas.mapNotNull { it.osVersion }.map(::normalizarOsVersion).filter { it.isNotEmpty() }
            .groupBy { it.lowercase(Locale.ROOT) }.mapValues { (_, g) -> g.min() }
        val versoes = grafias.values.sortedWith(String.CASE_INSENSITIVE_ORDER) + if (todas.any { chaveDaOs(it).isEmpty() }) listOf("") else emptyList()
        val nomeDaOs = { s: IssueSnapshot -> chaveDaOs(s).let { if (it.isEmpty()) "" else grafias.getValue(it) } }

        val abertasN = Array(tipos.size) { IntArray(versoes.size) }
        val abertasH = Array(tipos.size) { DoubleArray(versoes.size) }
        val fechadasN = Array(tipos.size) { IntArray(versoes.size) }
        val esforco = Array(tipos.size) { DoubleArray(versoes.size) }
        val segmentos = LinkedHashMap<String, MutableList<IssueSnapshot>>()
        fun celula(s: IssueSnapshot) = tipos.indexOf(s.issueType.trim()) to versoes.indexOf(nomeDaOs(s))

        for (s in naoConcluidas.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })) {
            val (i, j) = celula(s)
            abertasN[i][j]++
            abertasH[i][j] += motor.horas.de(s).valor
            segmentos.getOrPut("abertas:tipo:${tipos[i]}:os:${versoes[j]}") { mutableListOf() } += s
        }
        for (s in fechadas.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })) {
            val (i, j) = celula(s)
            fechadasN[i][j]++
            esforco[i][j] += esforcos[s.key]?.indice ?: 0.0
            segmentos.getOrPut("fechadas:tipo:${tipos[i]}:os:${versoes[j]}") { mutableListOf() } += s
        }
        val dto = CargaTipoDto(
            tipos, versoes, abertasH.map { it.toList() }, abertasN.map { it.toList() }, fechadasN.map { it.toList() },
            esforco.map { it.toList() }, emptyList(),
        )
        return CargaTipoAnalise(dto, segmentos)
    }

    private fun chaveDaOs(s: IssueSnapshot): String = s.osVersion?.let(::normalizarOsVersion)?.lowercase(Locale.ROOT).orEmpty()
}
