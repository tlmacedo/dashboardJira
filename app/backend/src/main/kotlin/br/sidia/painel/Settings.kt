package br.sidia.painel

import java.time.ZoneId

/** Configuração mínima do painel; a Tarefa 2 a estende com as flags de ambiente. */
data class Settings(val zone: ZoneId = ZoneId.of("America/Manaus")) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromEnv(env: Map<String, String> = System.getenv()): Settings = Settings()
    }
}
