package com.rivaisdolar.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.rivaisdolar.app.data.local.AcaoPendenteDao
import com.rivaisdolar.app.data.local.AcaoPendenteEntity
import com.rivaisdolar.app.data.local.CacheDao
import com.rivaisdolar.app.data.local.CacheEntity
import com.rivaisdolar.app.data.model.Carta
import com.rivaisdolar.app.data.model.Casa
import com.rivaisdolar.app.data.model.Conquista
import com.rivaisdolar.app.data.model.Notificacao
import com.rivaisdolar.app.data.model.Previa
import com.rivaisdolar.app.data.model.Membro
import com.rivaisdolar.app.data.model.Profile
import com.rivaisdolar.app.data.model.Registro
import com.rivaisdolar.app.data.model.Tarefa
import com.rivaisdolar.app.data.model.TarefaCadastro
import com.rivaisdolar.app.data.model.TarefaNova
import com.rivaisdolar.app.data.model.Temporada
import com.rivaisdolar.app.data.model.Troca
import com.rivaisdolar.app.widget.WidgetPlacar
import com.rivaisdolar.app.di.EscopoApp
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class Jogador(
    val userId: String,
    val nome: String,
    val cor: String,
    val nivel: Int,
    val xp: Int,
    val moedas: Int,
    val sequenciaDias: Int,
    val provocacao: String,
    val dobradinha: Boolean = false,
    val horaFelizAte: String? = null,
)

@Serializable
data class CasaSnapshot(
    val meuId: String,
    val casa: Casa,
    val jogadores: List<Jogador>,
    val tarefas: List<Tarefa>,
    val temporada: Temporada?,
    val registros: List<Registro>,
    val ultimaEncerrada: Temporada?,
    val atualizadoEm: Long,
    val cartas: List<Carta> = emptyList(),
    val trocas: List<Troca> = emptyList(),
    val conquistas: List<Conquista> = emptyList(),
    val notificacoes: List<Notificacao> = emptyList(),
) {
    val eu: Jogador? get() = jogadores.firstOrNull { it.userId == meuId }
    val outro: Jogador? get() = jogadores.firstOrNull { it.userId != meuId }
    fun jogador(id: String?): Jogador? = jogadores.firstOrNull { it.userId == id }
    fun pontos(id: String?): Int = id?.let { temporada?.placar?.get(it) } ?: 0
    val pendentesParaMim: List<Registro>
        get() = registros.filter { it.status == "pendente" && it.userId != meuId }
    fun registroPendenteDa(tarefaId: String): Registro? =
        registros.firstOrNull { it.tarefaId == tarefaId && it.status == "pendente" }

    /** Cartas na mão (compradas e ainda não usadas). */
    fun mao(id: String = meuId): List<Carta> = cartas.filter { it.dono == id && it.usadaEm == null }

    val trocaParaMim: Troca? get() = trocas.firstOrNull { it.status == "pendente" && it.para == meuId }
    val naoLidas: Int get() = notificacoes.count { it.lidaEm == null }
}

data class Historico(val temporadas: List<Temporada>, val registrosDaSemana: List<Registro>)

sealed interface EstadoCasa {
    data object Carregando : EstadoCasa
    data object SemCasa : EstadoCasa
    data class Pronta(val snapshot: CasaSnapshot, val offline: Boolean) : EstadoCasa
    data class Falha(val mensagem: String) : EstadoCasa
}

sealed interface Resultado {
    data object Ok : Resultado
    /** Sem rede: guardado na fila local e reenviado quando a conexão voltar. */
    data object NaFila : Resultado
    data class Erro(val mensagem: String) : Resultado
}

/**
 * Estado da casa do usuário logado. Busca tudo do Supabase, assina o Realtime da casa e recarrega a
 * cada mudança (os dois celulares ficam iguais), guarda a última versão no Room e mantém uma fila
 * de ações feitas offline.
 */
@Singleton
class CasaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val provider: SupabaseProvider,
    private val cache: CacheDao,
    private val fila: AcaoPendenteDao,
    @EscopoApp private val escopo: CoroutineScope,
) {
    private val _estado = MutableStateFlow<EstadoCasa>(EstadoCasa.Carregando)
    val estado: StateFlow<EstadoCasa> = _estado.asStateFlow()

    private val _avisos = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val avisos: SharedFlow<String> = _avisos.asSharedFlow()

    val acoesNaFila: Flow<Int> = fila.contagem()

    private val gatilho = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val mutex = Mutex()
    private var usuario: String? = null
    private var trabalho: Job? = null
    private var canal: RealtimeChannel? = null
    private var canalCasa: String? = null
    private var escutaRealtime: Job? = null

    init {
        val conectividade = context.getSystemService(ConnectivityManager::class.java)
        conectividade?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (usuario != null) escopo.launch { recarregar() }
            }
        })
    }

    @OptIn(FlowPreview::class)
    fun iniciar(userId: String) {
        if (usuario == userId && trabalho?.isActive == true) return
        parar()
        usuario = userId
        _estado.value = EstadoCasa.Carregando
        trabalho = escopo.launch {
            lerCache(userId)?.let { _estado.value = EstadoCasa.Pronta(it, offline = true) }
            launch { gatilho.debounce(250).collect { recarregar() } }
            recarregar()
        }
    }

    fun parar() {
        trabalho?.cancel()
        trabalho = null
        usuario = null
        val antigo = canal
        canal = null
        canalCasa = null
        escutaRealtime?.cancel()
        if (antigo != null) escopo.launch { runCatching { provider.exigir().realtime.removeChannel(antigo) } }
        _estado.value = EstadoCasa.Carregando
    }

    suspend fun esquecerTudo() {
        // O aparelho deixa de receber push desta conta.
        if (usuario != null) runCatching { salvarTokenPush(null) }
        WidgetPlacar.limpar(context)
        parar()
        cache.limpar()
        fila.limpar()
    }

    fun atualizar() {
        gatilho.tryEmit(Unit)
    }

    suspend fun recarregar() = mutex.withLock {
        val uid = usuario ?: return@withLock
        try {
            val snapshot = buscar(uid)
            if (snapshot == null) {
                _estado.value = EstadoCasa.SemCasa
                return@withLock
            }
            _estado.value = EstadoCasa.Pronta(snapshot, offline = false)
            cache.salvar(CacheEntity("casa:$uid", JsonRivais.encodeToString(CasaSnapshot.serializer(), snapshot), System.currentTimeMillis()))
            WidgetPlacar.atualizar(context, snapshot)
            garantirRealtime(snapshot.casa.id)
            enviarFila()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val atual = _estado.value
            _estado.value = when {
                atual is EstadoCasa.Pronta -> atual.copy(offline = true)
                e.semConexao() -> EstadoCasa.Falha("Sem conexão com o servidor. Confira a internet.")
                else -> EstadoCasa.Falha(e.mensagem())
            }
        }
    }

    private suspend fun lerCache(uid: String): CasaSnapshot? =
        cache.buscar("casa:$uid")?.let { runCatching { JsonRivais.decodeFromString(CasaSnapshot.serializer(), it.json) }.getOrNull() }

    private suspend fun buscar(uid: String): CasaSnapshot? = coroutineScope {
        val c = provider.exigir()
        val meu = c.from("membros").select { filter { eq("user_id", uid) } }.decodeSingleOrNull<Membro>()
            ?: return@coroutineScope null
        val casaId = meu.casaId

        val casa = async { c.from("casas").select { filter { eq("id", casaId) } }.decodeSingle<Casa>() }
        val membros = async { c.from("membros").select { filter { eq("casa_id", casaId) } }.decodeList<Membro>() }
        val tarefas = async {
            c.from("tarefas").select {
                filter {
                    eq("casa_id", casaId)
                    eq("ativa", true)
                }
                order("titulo", Order.ASCENDING)
            }.decodeList<Tarefa>()
        }
        val temporadas = async {
            c.from("temporadas").select {
                filter { eq("casa_id", casaId) }
                order("numero", Order.DESCENDING)
                limit(2)
            }.decodeList<Temporada>()
        }
        val registros = async {
            c.from("registros").select {
                filter { eq("casa_id", casaId) }
                order("criado_em", Order.DESCENDING)
                limit(80)
            }.decodeList<Registro>()
        }

        val cartas = async {
            c.from("cartas").select {
                filter { eq("casa_id", casaId) }
                order("comprada_em", Order.DESCENDING)
                limit(60)
            }.decodeList<Carta>()
        }
        val trocas = async {
            c.from("trocas").select {
                filter { eq("casa_id", casaId) }
                order("criada_em", Order.DESCENDING)
                limit(20)
            }.decodeList<Troca>()
        }
        val conquistas = async { c.from("conquistas").select { filter { eq("casa_id", casaId) } }.decodeList<Conquista>() }
        val notificacoes = async {
            c.from("notificacoes").select {
                filter { eq("user_id", uid) }
                order("criada_em", Order.DESCENDING)
                limit(40)
            }.decodeList<Notificacao>()
        }

        val listaMembros = membros.await()
        val perfis = c.from("profiles").select { filter { isIn("id", listaMembros.map { it.userId }) } }
            .decodeList<Profile>().associateBy { it.id }
        val listaTemporadas = temporadas.await()

        CasaSnapshot(
            meuId = uid,
            casa = casa.await(),
            jogadores = listaMembros.sortedBy { it.cor }.map {
                Jogador(
                    userId = it.userId,
                    nome = perfis[it.userId]?.nome ?: "Jogador",
                    cor = it.cor,
                    nivel = it.nivel,
                    xp = it.xp,
                    moedas = it.moedas,
                    sequenciaDias = it.sequenciaDias,
                    provocacao = it.nivelProvocacao,
                    dobradinha = it.dobradinha,
                    horaFelizAte = it.horaFelizAte,
                )
            },
            tarefas = tarefas.await(),
            temporada = listaTemporadas.firstOrNull { !it.encerrada },
            registros = registros.await(),
            ultimaEncerrada = listaTemporadas.firstOrNull { it.encerrada },
            atualizadoEm = System.currentTimeMillis(),
            cartas = cartas.await(),
            trocas = trocas.await(),
            conquistas = conquistas.await(),
            notificacoes = notificacoes.await(),
        )
    }

    private suspend fun garantirRealtime(casaId: String) {
        if (canalCasa == casaId) return
        val c = provider.exigir()
        canal?.let { runCatching { c.realtime.removeChannel(it) } }
        escutaRealtime?.cancel()

        val novo = c.channel("casa-$casaId")
        val mudancas = listOf("tarefas", "registros", "temporadas", "membros", "cartas", "trocas", "conquistas").map { tabela ->
            novo.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = tabela
                filter("casa_id", FilterOperator.EQ, casaId)
            }
        } + novo.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "casas"
            filter("id", FilterOperator.EQ, casaId)
        } + novo.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "notificacoes"
            filter("user_id", FilterOperator.EQ, usuario ?: "")
        }
        escutaRealtime = escopo.launch { merge(*mudancas.toTypedArray()).collect { gatilho.tryEmit(Unit) } }
        novo.subscribe()
        canal = novo
        canalCasa = casaId
    }

    private suspend fun enviarFila() {
        val c = provider.exigir()
        for (acao in fila.todas()) {
            try {
                c.postgrest.rpc(acao.funcao, JsonRivais.parseToJsonElement(acao.parametros).jsonObject)
                fila.remover(acao)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e.semConexao()) return
                // O servidor recusou (ex.: a outra pessoa reservou antes). Não adianta tentar de novo.
                fila.remover(acao)
                _avisos.tryEmit("Não deu pra sincronizar \"${acao.descricao}\": ${e.mensagem()}")
            }
        }
    }

    private suspend fun rpc(funcao: String, parametros: JsonObject, descricao: String, enfileiravel: Boolean = false): Resultado =
        try {
            provider.exigir().postgrest.rpc(funcao, parametros)
            gatilho.tryEmit(Unit)
            Resultado.Ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (enfileiravel && e.semConexao()) {
                fila.inserir(AcaoPendenteEntity(funcao = funcao, parametros = parametros.toString(), descricao = descricao, criadaEm = System.currentTimeMillis()))
                Resultado.NaFila
            } else {
                Resultado.Erro(e.mensagem())
            }
        }

    private suspend fun escrever(bloco: suspend () -> Unit): Resultado =
        try {
            bloco()
            gatilho.tryEmit(Unit)
            Resultado.Ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Resultado.Erro(e.mensagem())
        }

    // Jogo ------------------------------------------------------------------------------------

    suspend fun reservar(tarefa: Tarefa) =
        rpc("reservar_tarefa", buildJsonObject { put("p_tarefa", tarefa.id) }, "Reservar ${tarefa.titulo}", enfileiravel = true)

    suspend fun liberar(tarefa: Tarefa) =
        rpc("liberar_tarefa", buildJsonObject { put("p_tarefa", tarefa.id) }, "Largar ${tarefa.titulo}", enfileiravel = true)

    /** Prévia do servidor (mesma conta do concluir). Null sem conexão: a tela usa a conta local. */
    suspend fun previa(tarefa: Tarefa): Previa? = try {
        provider.exigir().postgrest.rpc("previa_conclusao", buildJsonObject { put("p_tarefa", tarefa.id) }).decodeAs<Previa>()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }

    /**
     * Conclui com foto opcional: a foto sobe primeiro para <casa>/<usuário>/ no bucket "fotos".
     * Sem conexão só dá para enfileirar a conclusão sem foto.
     */
    suspend fun concluir(tarefa: Tarefa, foto: ByteArray? = null): Resultado {
        if (foto == null) {
            return rpc("concluir_tarefa", buildJsonObject { put("p_tarefa", tarefa.id) }, "Concluir ${tarefa.titulo}", enfileiravel = true)
        }
        val uid = usuario ?: return Resultado.Erro("Faça login de novo.")
        val caminho = "${tarefa.casaId}/$uid/${UUID.randomUUID()}.jpg"
        try {
            provider.exigir().storage.from("fotos").upload(caminho, foto) { contentType = ContentType.Image.JPEG }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return Resultado.Erro(if (e.semConexao()) "Sem conexão para enviar a foto. Tente sem foto ou espere a internet." else e.mensagem())
        }
        return rpc(
            "concluir_tarefa",
            buildJsonObject {
                put("p_tarefa", tarefa.id)
                put("p_foto_path", caminho)
            },
            "Concluir ${tarefa.titulo}",
        )
    }

    private val fotos = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > 12
    }

    suspend fun baixarFoto(caminho: String): ByteArray? {
        synchronized(fotos) { fotos[caminho] }?.let { return it }
        return try {
            provider.exigir().storage.from("fotos").downloadAuthenticated(caminho).also { synchronized(fotos) { fotos[caminho] = it } }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun validar(registro: Registro) =
        rpc("validar_registro", buildJsonObject { put("p_registro", registro.id) }, "Validar ${registro.titulo}", enfileiravel = true)

    suspend fun contestar(registro: Registro, comentario: String) =
        rpc(
            "contestar_registro",
            buildJsonObject {
                put("p_registro", registro.id)
                put("p_comentario", comentario)
            },
            "Contestar ${registro.titulo}",
            enfileiravel = true,
        )

    suspend fun definirAposta(texto: String) =
        rpc("definir_aposta", buildJsonObject { put("p_aposta", texto) }, "Definir aposta", enfileiravel = true)

    suspend fun definirMeta(alvo: Int, premio: String) =
        rpc(
            "definir_meta",
            buildJsonObject {
                put("p_alvo", alvo)
                put("p_premio", premio)
            },
            "Definir meta",
        )

    suspend fun resgatarPremio(temporada: Temporada) =
        rpc("resgatar_premio", buildJsonObject { put("p_temporada", temporada.id) }, "Resgatar prêmio")

    // Cartas e trocas --------------------------------------------------------------------------

    suspend fun comprarCarta(tipo: String) = rpc("comprar_carta", buildJsonObject { put("p_tipo", tipo) }, "Comprar carta")

    suspend fun usarCarta(carta: Carta, alvo: String? = null, alvo2: String? = null) =
        rpc(
            "usar_carta",
            buildJsonObject {
                put("p_carta", carta.id)
                alvo?.let { put("p_alvo", it) }
                alvo2?.let { put("p_alvo2", it) }
            },
            "Usar carta",
        )

    suspend fun responderTroca(troca: Troca, aceitar: Boolean) =
        rpc(
            "responder_troca",
            buildJsonObject {
                put("p_troca", troca.id)
                put("p_aceitar", aceitar)
            },
            "Responder troca",
        )

    suspend fun marcarNotificacoesLidas() = rpc("marcar_notificacoes_lidas", buildJsonObject { }, "Marcar lidas")

    /** Token do FCM deste aparelho (o servidor manda push para ele). */
    suspend fun salvarTokenPush(token: String?) = escrever {
        val uid = usuario ?: return@escrever
        provider.exigir().from("membros").update(buildJsonObject { put("fcm_token", token) }) { filter { eq("user_id", uid) } }
    }

    suspend fun historico(): Historico = coroutineScope {
        val c = provider.exigir()
        val s = (estado.value as? EstadoCasa.Pronta)?.snapshot ?: return@coroutineScope Historico(emptyList(), emptyList())
        val temporadas = async {
            c.from("temporadas").select {
                filter {
                    eq("casa_id", s.casa.id)
                    eq("encerrada", true)
                }
                order("numero", Order.DESCENDING)
                limit(12)
            }.decodeList<Temporada>()
        }
        val registros = async {
            val inicio = s.temporada?.inicio ?: return@async emptyList()
            c.from("registros").select {
                filter {
                    eq("casa_id", s.casa.id)
                    eq("status", "validado")
                    gte("criado_em", inicio)
                }
                order("criado_em", Order.ASCENDING)
                limit(500)
            }.decodeList<Registro>()
        }
        Historico(temporadas.await().reversed(), registros.await())
    }

    // Casa ------------------------------------------------------------------------------------

    suspend fun criarCasa(nome: String, exemplos: Boolean = true) =
        rpc(
            "criar_casa",
            buildJsonObject {
                put("p_nome", nome)
                put("p_exemplos", exemplos)
            },
            "Criar casa",
        )

    suspend fun entrarCasa(codigo: String) = rpc("entrar_casa", buildJsonObject { put("p_codigo", codigo) }, "Entrar na casa")

    suspend fun sairDaCasa() = rpc("sair_da_casa", buildJsonObject { }, "Sair da casa")

    suspend fun definirCor(cor: String) = rpc("definir_cor", buildJsonObject { put("p_cor", cor) }, "Trocar cor")

    suspend fun atualizarNome(nome: String) = escrever {
        val uid = usuario ?: return@escrever
        provider.exigir().from("profiles").update(buildJsonObject { put("nome", nome.trim()) }) { filter { eq("id", uid) } }
    }

    suspend fun atualizarCasa(nome: String? = null, tregua: Boolean? = null) = escrever {
        val casaId = (estado.value as? EstadoCasa.Pronta)?.snapshot?.casa?.id ?: return@escrever
        provider.exigir().from("casas").update(
            buildJsonObject {
                nome?.let { put("nome", it.trim()) }
                tregua?.let { put("modo_tregua", it) }
            },
        ) { filter { eq("id", casaId) } }
    }

    suspend fun atualizarProvocacao(nivel: String) = escrever {
        val uid = usuario ?: return@escrever
        provider.exigir().from("membros").update(buildJsonObject { put("nivel_provocacao", nivel) }) { filter { eq("user_id", uid) } }
    }

    suspend fun salvarTarefa(id: String?, cadastro: TarefaCadastro) = escrever {
        val c = provider.exigir()
        if (id == null) {
            val casaId = (estado.value as? EstadoCasa.Pronta)?.snapshot?.casa?.id ?: error("Casa não carregada.")
            c.from("tarefas").insert(
                TarefaNova(
                    casaId = casaId,
                    titulo = cadastro.titulo,
                    categoria = cadastro.categoria,
                    icone = cadastro.icone,
                    minutos = cadastro.minutos,
                    esforco = cadastro.esforco,
                    chatice = cadastro.chatice,
                    recorrencia = cadastro.recorrencia,
                    prazo = cadastro.prazo,
                ),
            )
        } else {
            c.from("tarefas").update(cadastro) { filter { eq("id", id) } }
        }
    }

    suspend fun arquivarTarefa(id: String) = escrever {
        provider.exigir().from("tarefas").update(buildJsonObject { put("ativa", false) }) { filter { eq("id", id) } }
    }
}
