package com.rivaisdolar.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rivaisdolar.app.ui.components.BotaoCapsula
import com.rivaisdolar.app.ui.components.BotaoCirculo
import com.rivaisdolar.app.ui.components.BotaoTexto
import com.rivaisdolar.app.ui.components.CampoTexto
import com.rivaisdolar.app.ui.components.CartaoSolido
import com.rivaisdolar.app.ui.components.Interruptor
import com.rivaisdolar.app.ui.components.Secao
import com.rivaisdolar.app.ui.components.Simbolo
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.Tipo
import kotlinx.serialization.Serializable

@Serializable private object RotaEntrar
@Serializable private object RotaCadastro
@Serializable private data class RotaConfirmar(val email: String)
@Serializable private object RotaRecuperar
@Serializable private data class RotaNovaSenha(val email: String)

/** Esqueleto comum: fundo preto, rolagem, título grande à esquerda. */
@Composable
private fun TelaFormulario(
    titulo: String,
    subtitulo: String?,
    aoVoltar: (() -> Unit)? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Cores.Fundo)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(38.dp)) {
            if (aoVoltar != null) BotaoCirculo("arrow_back", "Voltar", aoVoltar)
        }
        Spacer(Modifier.height(if (aoVoltar == null) 40.dp else 8.dp))
        Texto(titulo, Tipo.tituloGrande)
        if (subtitulo != null) Texto(subtitulo, Tipo.corpoSecundario)
        Spacer(Modifier.height(8.dp))
        conteudo()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Mensagens(ui: AuthUi) {
    ui.erro?.let { Texto(it, Tipo.pequeno, cor = Cores.Alerta, modifier = Modifier.padding(horizontal = 4.dp)) }
    ui.info?.let { Texto(it, Tipo.pequenoSecundario, modifier = Modifier.padding(horizontal = 4.dp)) }
}

@Composable
fun AuthFluxo(vm: AuthViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(nav) {
        nav.currentBackStackEntryFlow.collect { vm.limpar() }
    }
    NavHost(nav, startDestination = RotaEntrar) {
        composable<RotaEntrar> {
            var email by rememberSaveable { mutableStateOf("") }
            var senha by rememberSaveable { mutableStateOf("") }
            TelaFormulario("Rivais do Lar", "A faxina virou campeonato. Entre para ver o placar.") {
                CampoTexto(email, { email = it }, "E-mail", teclado = KeyboardType.Email)
                CampoTexto(senha, { senha = it }, "Senha", senha = true)
                Mensagens(ui)
                BotaoCapsula("Entrar", { vm.entrar(email, senha) }, habilitado = email.isNotBlank() && senha.isNotBlank(), carregando = ui.carregando)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BotaoTexto("Criar conta", { nav.navigate(RotaCadastro) })
                    BotaoTexto("Esqueci minha senha", { nav.navigate(RotaRecuperar) }, cor = Cores.TextoSecundario)
                }
                if (!vm.servidorEmbutido) {
                    Spacer(Modifier.height(24.dp))
                    Texto("Servidor: ${vm.servidorAtual.orEmpty()}", Tipo.subtitulo, Modifier.fillMaxWidth(), alinhamento = TextAlign.Center)
                    BotaoTexto("Trocar servidor", vm::trocarServidor, Modifier.align(Alignment.CenterHorizontally), cor = Cores.TextoSecundario)
                }
            }
        }
        composable<RotaCadastro> {
            var nome by rememberSaveable { mutableStateOf("") }
            var email by rememberSaveable { mutableStateOf("") }
            var senha by rememberSaveable { mutableStateOf("") }
            TelaFormulario("Criar conta", "Seu nome aparece no placar.", aoVoltar = { nav.popBackStack() }) {
                CampoTexto(nome, { nome = it.take(40) }, "Nome")
                CampoTexto(email, { email = it }, "E-mail", teclado = KeyboardType.Email)
                CampoTexto(senha, { senha = it }, "Senha (mínimo 6 caracteres)", senha = true)
                Mensagens(ui)
                BotaoCapsula(
                    "Criar conta",
                    { vm.cadastrar(nome, email, senha) { nav.navigate(RotaConfirmar(email.trim())) } },
                    habilitado = nome.isNotBlank() && email.isNotBlank() && senha.isNotBlank(),
                    carregando = ui.carregando,
                )
            }
        }
        composable<RotaConfirmar> { entrada ->
            val email = entrada.toRoute<RotaConfirmar>().email
            var codigo by rememberSaveable { mutableStateOf("") }
            TelaFormulario("Confirme seu e-mail", "Enviamos um código para $email.", aoVoltar = { nav.popBackStack() }) {
                CampoTexto(codigo, { codigo = it.filter(Char::isDigit).take(10) }, "Código", teclado = KeyboardType.Number)
                Mensagens(ui)
                BotaoCapsula("Confirmar", { vm.confirmar(email, codigo) }, habilitado = codigo.length >= 6, carregando = ui.carregando)
                BotaoTexto("Reenviar código", { vm.reenviar(email) }, Modifier.align(Alignment.CenterHorizontally), cor = Cores.TextoSecundario)
            }
        }
        composable<RotaRecuperar> {
            var email by rememberSaveable { mutableStateOf("") }
            TelaFormulario("Esqueci minha senha", "Mandamos um código para você criar uma senha nova.", aoVoltar = { nav.popBackStack() }) {
                CampoTexto(email, { email = it }, "E-mail", teclado = KeyboardType.Email)
                Mensagens(ui)
                BotaoCapsula(
                    "Enviar código",
                    { vm.enviarRecuperacao(email) { nav.navigate(RotaNovaSenha(email.trim())) } },
                    habilitado = email.isNotBlank(),
                    carregando = ui.carregando,
                )
            }
        }
        composable<RotaNovaSenha> { entrada ->
            val email = entrada.toRoute<RotaNovaSenha>().email
            var codigo by rememberSaveable { mutableStateOf("") }
            var senha by rememberSaveable { mutableStateOf("") }
            TelaFormulario("Nova senha", "Digite o código que chegou em $email.", aoVoltar = { nav.popBackStack() }) {
                CampoTexto(codigo, { codigo = it.filter(Char::isDigit).take(10) }, "Código", teclado = KeyboardType.Number)
                CampoTexto(senha, { senha = it }, "Nova senha", senha = true)
                Mensagens(ui)
                BotaoCapsula("Salvar e entrar", { vm.redefinir(email, codigo, senha) }, habilitado = codigo.length >= 6 && senha.isNotBlank(), carregando = ui.carregando)
            }
        }
    }
}

/** Primeira abertura de um APK gerado sem as credenciais do Supabase embutidas. */
@Composable
fun ServidorTela(vm: AuthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }
    var chave by rememberSaveable { mutableStateOf("") }
    TelaFormulario("Conectar ao servidor", "Este APK não veio com o servidor configurado. Use os dados do seu projeto Supabase.") {
        CampoTexto(url, { url = it }, "URL do projeto (https://xxxx.supabase.co)", teclado = KeyboardType.Uri)
        CampoTexto(chave, { chave = it }, "Chave anon (pública)", linhas = 3)
        Texto("No painel do Supabase: Project Settings, API. Nunca use a chave service_role aqui.", Tipo.subtitulo, Modifier.padding(horizontal = 4.dp))
        Mensagens(ui)
        BotaoCapsula("Conectar", { vm.conectar(url, chave) }, habilitado = url.isNotBlank() && chave.isNotBlank())
    }
}

/** Logado, mas ainda sem casa: criar uma ou entrar pelo código de 6 dígitos. */
@Composable
fun SemCasaTela(vm: AuthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var nome by rememberSaveable { mutableStateOf("") }
    var codigo by rememberSaveable { mutableStateOf("") }
    var exemplos by rememberSaveable { mutableStateOf(true) }
    TelaFormulario("Sua casa", "O jogo acontece entre duas pessoas na mesma casa.") {
        Secao("Entrar com código")
        CartaoSolido {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Texto("Quem criou a casa encontra o código na tela Casa.", Tipo.pequenoSecundario)
                CampoTexto(codigo, { codigo = it.filter(Char::isDigit).take(6) }, "Código de 6 dígitos", teclado = KeyboardType.Number)
                BotaoCapsula("Entrar na casa", { vm.entrarCasa(codigo) }, habilitado = codigo.length == 6, carregando = ui.carregando)
            }
        }
        Secao("Ou criar uma casa nova", Modifier.padding(top = 8.dp))
        CartaoSolido {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Texto("Você recebe um código para convidar a outra pessoa. Depois vocês criam as próprias tarefas.", Tipo.pequenoSecundario)
                CampoTexto(nome, { nome = it.take(40) }, "Nome da casa (ex.: Apê da Vila)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Texto("Começar com tarefas de exemplo", Tipo.linha)
                        Texto("Louça, banheiro, lixo, mercado... Dá pra editar ou remover depois.", Tipo.subtitulo)
                    }
                    Interruptor(exemplos, { exemplos = it })
                }
                BotaoCapsula("Criar casa", { vm.criarCasa(nome, exemplos) }, habilitado = nome.isNotBlank(), carregando = ui.carregando)
            }
        }
        Mensagens(ui)
        BotaoTexto("Sair da conta", vm::sair, Modifier.align(Alignment.CenterHorizontally), cor = Cores.TextoSecundario)
    }
}

@Composable
fun FalhaTela(mensagem: String, aoTentar: () -> Unit, aoSair: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Cores.Fundo).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Simbolo("cloud_off", tamanho = 40.sp, cor = Cores.TextoTerciario)
            Texto(mensagem, Tipo.corpoSecundario, alinhamento = TextAlign.Center)
            BotaoCapsula("Tentar de novo", aoTentar)
            BotaoTexto("Sair da conta", aoSair, cor = Cores.TextoSecundario)
        }
    }
}
