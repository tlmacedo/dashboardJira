package br.sidia.painel

import br.sidia.painel.auditoria.rotasDeAuditoria
import br.sidia.painel.cards.checklist.rotasDeChecklistReview
import br.sidia.painel.distribuicao.rotasDeDistribuicao
import br.sidia.painel.governanca.rotasDeGovernanca
import br.sidia.painel.inicio.rotasDeInicio
import br.sidia.painel.lgpd.rotasDoTitular
import br.sidia.painel.integridade.rotasDeIntegridade
import br.sidia.painel.cards.contrato.CatalogoDeCartoes
import br.sidia.painel.cards.contrato.catalogoPadrao
import br.sidia.painel.cards.contrato.rotasDeDrillGenerico
import br.sidia.painel.cards.contrato.rotasDoCatalogo
import br.sidia.painel.cards.registrarNegacaoSemPapel
import br.sidia.painel.cards.rotasDeOpcoesDeFiltro
import br.sidia.painel.cards.rotasDosCardsDeTrabalho
import br.sidia.painel.cards.rotasTempoEmStatus
import br.sidia.painel.config.ArquivoEnv
import br.sidia.painel.config.PerfilDeExecucao
import br.sidia.painel.config.Settings
import br.sidia.painel.db.AccessLog
import br.sidia.painel.db.CacheDeLeituras
import br.sidia.painel.demo.prepararDemonstracao
import br.sidia.painel.tarefa.rotasDeTarefa
import br.sidia.painel.http.ConfigCsrf
import br.sidia.painel.http.cabecalhosDeSeguranca
import br.sidia.painel.http.protecaoCsrf
import br.sidia.painel.http.registrarNegacaoCsrf
import br.sidia.painel.operacao.ConfiguradorDeLog
import br.sidia.painel.operacao.comandoDeOperacao
import kotlin.system.exitProcess
import br.sidia.painel.db.ObservadorDeAccessLog
import br.sidia.painel.operacao.LacoDeAlertas
import br.sidia.painel.operacao.MascaraDeLog
import br.sidia.painel.operacao.montarAvaliadorDeAlertas
import br.sidia.painel.operacao.rotasDeAlertas
import br.sidia.painel.operacao.Metricas
import br.sidia.painel.operacao.instalarMetricasHttp
import br.sidia.painel.operacao.iniciarServidorDeMetricas
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import br.sidia.painel.operacao.Prontidao
import br.sidia.painel.operacao.prontidaoPadrao
import br.sidia.painel.operacao.rotaProntidao
import br.sidia.painel.operacao.instalarLogDeRequisicao
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.DevAuthenticator
import br.sidia.painel.security.InicioDaRotacao
import br.sidia.painel.security.OidcAuthenticator
import br.sidia.painel.security.criarClienteHttpOidc
import br.sidia.painel.security.rotasDeLogin
import br.sidia.painel.security.installAccessControl
import br.sidia.painel.sync.Agendador
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.sync.SqliteSyncStateStore
import br.sidia.painel.sync.montarAgendador
import br.sidia.painel.sync.rotasSyncStatus
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.runBlocking
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.time.Clock

@Serializable
data class HealthResposta(val ok: Boolean)

@Serializable
data class MetaResposta(
    val devMode: Boolean,
    val liveEnabled: Boolean,
    val zone: String,
    val banner: String?,
    /** Flag do Jira ligada e nenhuma sincronização real ainda: a tela mostra "aguardando a primeira carga", não um painel vazio. */
    val aguardandoCargaDoJira: Boolean,
)

private val log = LoggerFactory.getLogger("br.sidia.painel")

fun main(args: Array<String>) {
    // Lê o arquivo de variáveis local (PAINEL_ENV_FILE ou ./.env) por baixo do ambiente do processo.
    val arquivoEnv = System.getenv("PAINEL_ENV_FILE")?.takeIf { it.isNotBlank() }?.let { java.nio.file.Path.of(it) }
        ?: java.nio.file.Path.of(".env")
    // A flag de acesso ao Jira vem SÓ desse arquivo (true liga; false ou ausente desliga).
    // No pacote instalado (perfil de produção) a ausência de DASHBOARD_DEV_MODE vale false; ver PerfilDeExecucao.
    val env = PerfilDeExecucao.aplicar(
        System.getProperty(PerfilDeExecucao.PROPRIEDADE),
        ArquivoEnv.combinarComFlagSoDoArquivo(ArquivoEnv.ler(arquivoEnv), System.getenv()),
    )
    // Subcomandos de operação (validar-config etc.) rodam e saem ANTES de abrir banco, trava ou servidor.
    comandoDeOperacao(args.toList(), env)?.let { exitProcess(it) }
    val settings = Settings.fromEnv(env)
    // O Logback iniciou antes da leitura do arquivo de variáveis: reaplica formato e pasta de log e registra os segredos
    // na máscara (nenhum token sai em log, nem escapado).
    ConfiguradorDeLog.aplicar(settings.operacao.logFormato, settings.operacao.logDir)
    MascaraDeLog.registrarSegredos(settings.valoresSecretos())
    // Falhas de configuração, papéis ou banco acontecem aqui, na subida, e não na primeira requisição.
    val context = AppContext.load(settings)
    // Dados fictícios (só em desenvolvimento, Jira desligado) ou recusa de banco misturado; ver DadosDeDemonstracao.
    try {
        prepararDemonstracao(context)
    } catch (e: Exception) {
        context.close()
        throw e
    }
    Runtime.getRuntime().addShutdownHook(Thread { context.close() })
    // O agendador para antes de o pool do banco fechar (AppContext.close); a retenção roda mesmo com o Jira desligado.
    val metricas = prepararMetricas(context)
    val agendador = montarAgendador(context, metricas = metricas)
    agendador?.let { context.aoFechar { runBlocking { it.parar() } } }
    // Leitura bruta do banco reaproveitada por poucos segundos (desligada nos testes; ver CacheDeLeituras). Depois da carga
    // de demonstração e antes de aceitar a primeira requisição.
    CacheDeLeituras.configurar(java.time.Duration.ofSeconds(settings.cacheDeLeituraSeg.toLong()))
    // Só escuta na interface de PAINEL_HOST (padrão 127.0.0.1): quem expõe o painel à rede escolhe isso de propósito.
    embeddedServer(Netty, port = 8080, host = settings.host) {
        module(context.settings, context, agendador = agendador, metricas = metricas)
        monitor.subscribe(ApplicationStopped) { context.close() }
    }.start(wait = true)
}

/**
 * Monta as métricas do processo: binders da JVM, pool do banco, estado da sincronização, falhas do registro de acesso e o
 * servidor da porta interna (fechado junto com o contexto). Tudo só observa; nada aqui pode impedir a subida.
 */
private fun prepararMetricas(context: AppContext): Metricas {
    val metricas = Metricas().registrarBindersDaJvm()
    (context.dataSource as? HikariDataSource)?.metricsTrackerFactory = MicrometerMetricsTrackerFactory(metricas.registro)
    context.dataSource?.let {
        metricas.vincularSincronizacao(SqliteSyncStateStore(it), context.settings.sync.instancias, context.clock)
    }
    ObservadorDeAccessLog.aoFalhar = { metricas.falhaDeAccessLog() }
    val op = context.settings.operacao
    iniciarServidorDeMetricas(metricas, op.metricasHost, op.metricasPorta)?.let { servidor -> context.aoFechar { servidor.close() } }
    return metricas
}

/**
 * Em desenvolvimento, sem login (relógio na zona do painel); em produção, o SSO/OIDC. Produção sem OIDC configurado
 * NÃO sobe (falha fechada; `Settings.fromEnv` já a barra na leitura das variáveis, isto cobre um `Settings` montado à mão).
 * O início da rotação do `SESSION_SECRET` é lido do arquivo ao lado do banco (e gravado na primeira subida da rotação).
 */
private fun autenticadorPadrao(context: AppContext): Authenticator {
    val settings = context.settings
    if (settings.dashboardDevMode) return DevAuthenticator(clock = Clock.system(settings.zone))
    val oidc = settings.oidc ?: throw IllegalStateException("OIDC_ISSUER é obrigatório em produção (login SSO/OIDC não configurado)")
    val http = criarClienteHttpOidc()
    context.aoFechar { http.close() }
    val inicioDaRotacao = InicioDaRotacao.resolver(
        InicioDaRotacao.arquivoAoLadoDe(settings.databasePath), oidc.sessao.anterior, context.clock.instant(),
    )
    return OidcAuthenticator(oidc, context.roles, context.clock, http, inicioDaRotacao)
}

/**
 * Módulo Ktor do painel: JSON, controle de acesso, rota de saúde e metadados do ambiente.
 *
 * A configuração efetiva é SEMPRE `context.settings`. O primeiro parâmetro existe só por
 * compatibilidade de assinatura (posicional) e é ignorado, para que os dois nunca divirjam em silêncio.
 */
fun Application.module(
    @Suppress("UNUSED_PARAMETER") settingsIgnorado: Settings? = null,
    context: AppContext,
    authenticator: Authenticator = autenticadorPadrao(context),
    agendador: Agendador? = null,
    catalogo: CatalogoDeCartoes = catalogoPadrao(),
    prontidao: Prontidao? = prontidaoPadrao(context),
    metricas: Metricas? = null,
) {
    val settings = context.settings
    val inicioDoProcesso = context.clock.instant()
    // Subida: fecha execuções órfãs de um processo anterior, tira suspensões (reiniciar equivale a religar a flag)
    // e só então inicia o agendador (ver `Agendador`). Falha do banco aqui não impede o painel de subir.
    monitor.subscribe(ApplicationStarted) { app ->
        context.dataSource?.let { ds ->
            try {
                val estado = SqliteSyncStateStore(ds)
                estado.fecharOrfas(inicioDoProcesso, context.clock.instant())
                estado.limparSuspensoes()
            } catch (e: Exception) {
                log.warn("Preparação da sincronização na subida falhou: {}", e.javaClass.simpleName)
            }
        }
        agendador?.iniciar(app)
    }
    monitor.subscribe(ApplicationStopping) { runBlocking { agendador?.parar() } }
    // Alertas operacionais: avaliados a cada 5 min no próprio processo (sem Jira), como o agendador.
    val avaliador = montarAvaliadorDeAlertas(context, metricas ?: Metricas())
    val lacoDeAlertas = avaliador?.let { LacoDeAlertas(it) }
    monitor.subscribe(ApplicationStarted) { app -> lacoDeAlertas?.iniciar(app) }
    monitor.subscribe(ApplicationStopping) { runBlocking { lacoDeAlertas?.parar() } }
    install(ContentNegotiation) { json() }
    // Id de requisição e uma linha de log por requisição, com o modelo da rota (ver LogDeRequisicao.kt).
    instalarLogDeRequisicao()
    // Contadores e duração por requisição (rota pelo modelo); fecha o /metrics na porta pública.
    metricas?.let { instalarMetricasHttp(it) }
    // CSP e demais cabeçalhos de segurança em toda resposta (ver CabecalhosDeSeguranca.kt).
    install(cabecalhosDeSeguranca(settings.dashboardDevMode))
    // Sem banco (só em testes) não há auditoria nem rotas de dados.
    val auditoria = context.dataSource?.let { AccessLog(it) }
    installAccessControl(settings, authenticator, aoNegarSemPapel = { call, principal ->
        auditoria?.let { registrarNegacaoSemPapel(it, context, call, principal) }
    })
    // CSRF em todo método mutante (depois do controle de acesso, para a auditoria já ver o principal).
    install(protecaoCsrf(ConfigCsrf(settings.origemPublica)) { call, codigo ->
        auditoria?.let { registrarNegacaoCsrf(it, context, call, codigo) }
    })
    if (settings.dashboardDevMode && settings.jiraLiveEnabled) {
        log.warn("ATENÇÃO: modo de desenvolvimento (sem login) com Jira real ligado: dados reais ficam expostos sem autenticação.")
    }
    val banner = when {
        !settings.dashboardDevMode -> null
        settings.jiraLiveEnabled -> "MODO DESENVOLVIMENTO: dados reais do Jira sem login"
        settings.dashboardDemoData -> "MODO DESENVOLVIMENTO: dados fictícios de demonstração"
        else -> "MODO DESENVOLVIMENTO"
    }
    if (!Files.isDirectory(settings.staticDir)) {
        log.warn("Diretório do frontend não encontrado: ${settings.staticDir} (defina PAINEL_STATIC_DIR); a SPA não será servida.")
    }
    routing {
        // Qualquer rota de API inexistente (em qualquer método) devolve 404 vazio, nunca o HTML da SPA.
        // As rotas explícitas abaixo têm precedência por serem mais específicas.
        route("/api/{...}") { handle { call.respond(HttpStatusCode.NotFound) } }
        route("/api") { handle { call.respond(HttpStatusCode.NotFound) } }
        // Build do frontend: arquivos reais, e index.html só para rotas de página (ver StaticSpa.kt).
        spaEstatica(settings.staticDir)
        context.dataSource?.let { ds ->
            rotasTempoEmStatus(context, SqliteIssueStore(ds), AccessLog(ds))
            rotasDoCatalogo(context, catalogo, AccessLog(ds))
            rotasDeDrillGenerico(context, catalogo, SqliteIssueStore(ds), AccessLog(ds))
            rotasDeAuditoria(context, AccessLog(ds))
            rotasDosCardsDeTrabalho(context, AccessLog(ds))
            rotasDeOpcoesDeFiltro(context, AccessLog(ds))
            rotasDeTarefa(context, AccessLog(ds))
            rotasDeChecklistReview(context, AccessLog(ds))
            rotasDeIntegridade(context, AccessLog(ds))
            rotasDeDistribuicao(context, AccessLog(ds))
            rotasDeGovernanca(context, AccessLog(ds))
            rotasDeInicio(context, catalogo, AccessLog(ds))
            rotasDoTitular(context, AccessLog(ds))
            rotasSyncStatus(context, SqliteSyncStateStore(ds), AccessLog(ds))
            avaliador?.let { rotasDeAlertas(context, it, AccessLog(ds)) { prontidao?.avaliar()?.codigo } }
        }
        // Login SSO/OIDC (só quando o autenticador é o OIDC, isto é, em produção): /auth/login, /auth/callback, /auth/logout.
        (authenticator as? OidcAuthenticator)?.let { rotasDeLogin(it, auditoria, context.clock) }
        get("/api/health") { call.respond(HealthResposta(ok = true)) }
        rotaProntidao(prontidao)
        get("/api/meta") {
            call.respond(
                MetaResposta(
                    devMode = settings.dashboardDevMode,
                    liveEnabled = settings.jiraLiveEnabled,
                    zone = settings.zone.id,
                    banner = banner,
                    aguardandoCargaDoJira = settings.jiraLiveEnabled && br.sidia.painel.sync.SincronizacaoReal.aguardandoPrimeiraCarga(context.dataSource),
                ),
            )
        }
    }
}
