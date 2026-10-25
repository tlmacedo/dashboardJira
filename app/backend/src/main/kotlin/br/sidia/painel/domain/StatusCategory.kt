package br.sidia.painel.domain

/** Categoria de um status do workflow, usada para somar tempo ativo, de espera e bloqueado. */
enum class StatusCategory { TODO, ACTIVE, WAIT, BLOCKED, DONE }
