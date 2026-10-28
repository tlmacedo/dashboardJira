package br.sidia.painel.cards.cargatipo

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Carga por tipo e OS (CG-01 e CG-02). As matrizes são `[i][j]` = `tipos[i]` × `versoes[j]`. Tipos e versões em
 * ordem alfabética; a versão vazia (`""`) é "sem OS" (linha/coluna própria, sempre por último; a tela traduz). [abertasH] e
 * [abertasN]: horas de fila e contagem das abertas; [fechadasN] e [esforco]: concluídas no período e a soma do índice de esforço
 * delas. Segmentos do drill: `abertas:tipo:<T>:os:<V>` e `fechadas:tipo:<T>:os:<V>`.
 */
@Serializable
data class CargaTipoDto(
    val tipos: List<String>,
    val versoes: List<String>,
    val abertasH: List<List<Double>>,
    val abertasN: List<List<Int>>,
    val fechadasN: List<List<Int>>,
    val esforco: List<List<Double>>,
    val avisos: List<AvisoDto>,
)
