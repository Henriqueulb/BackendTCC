package com.exemplo.medico

import com.exemplo.medico.database.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq

// DTOs

@Serializable
data class CadastroUsuarioDTO(
    val nome: String,
    val email: String,
    val senha: String,
    val telefone: String,
    val isAcompanhante: Boolean = false
)

@Serializable
data class VincularAcompanhanteDTO(
    val emailAcompanhante: String,
    val codigoConvite: String
)

@Serializable
data class LoginUsuarioDTO(
    val email: String,
    val senha: String
)

@Serializable
data class RespostaDTO(
    val mensagem: String,
    val sucesso: Boolean,
    val nomeUsuario: String? = null,
    val isAcompanhante: Boolean = false
)

@Serializable
data class ItemRotinaDTO(
    val id: Int,
    val titulo: String,
    val horario: String,
    val dose: String?,
    val feita: Boolean
)

@Serializable
data class NovoItemRotinaDTO(
    val idRotina: Int,
    val titulo: String,
    val horario: String,
    val dose: String?,
    val descricao: String? = null
)

@Serializable
data class RotinaResumoDTO(
    val idRotina: Int,
    val nomeRotina: String,
    val dataCriacao: String,
    val status: String
)

@Serializable
data class CriarRotinaDTO(
    val emailUsuario: String,
    val nomeRotina: String
)

@Serializable
data class HomeResumoDTO(
    val progresso: Float,
    val tarefas: List<ItemRotinaDTO>,
    val nomeUsuario: String
)

@Serializable
data class StatusRotinaDTO(
    val idItem: Int,
    val feito: Boolean,
    val data: String // formato "yyyy-MM-dd"
)

@Serializable
data class NovoSintomaDTO(
    val emailUsuario: String,
    val bemEstar: Int,
    val sintomas: Int
)

@Serializable
data class PerfilUsuarioDTO(
    val nome: String,
    val email: String,
    val telefone: String,
    val isAcompanhante: Boolean = false
)

@Serializable
data class AtualizarPerfilDTO(
    val emailBusca: String,
    val novoNome: String,
    val novoTelefone: String,
)

@Serializable
data class TrocarSenhaDTO(
    val email: String,
    val novaSenha: String
)

@Serializable
data class FichaMedicaDTO(
    val emailUsuario: String,
    val alergias: String,
    val medicacoes: String,
    val comorbidades: String
)

@Serializable
data class NotificacaoConfigDTO(
    val emailUsuario: String,
    val ativo: Boolean,
    val som: Boolean
)

@Serializable
data class CodigoConviteDTO(
    val codigo: String
)

@Serializable
data class AcompanhanteDTO(
    val idVinculo: Int,
    val nomeAcompanhante: String,
    val emailAcompanhante: String,
    val status: String
)

@Serializable
data class PacienteVinculadoDTO(
    val idVinculo: Int,
    val nomePaciente: String,
    val emailPaciente: String
)

// SERVIDOR

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    initDatabase()
    configureRouting()
}

// BANCO DE DADOS

fun initDatabase() {
    try {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
            driverClassName = "org.postgresql.Driver"
            username = "postgres"
            password = "admin"
            maximumPoolSize = 3
            isAutoCommit = false
            validate()
        }
        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.create(Usuarios, FichasMedicas, RotinasCuidados, ItensCuidado, Sintomas, Aderencias, Acompanhantes, Notificacoes)
        }
        println(">>> BANCO CONECTADO <<<")
    } catch (e: Exception) {
        println("!!! ERRO BANCO: ${e.message}")
    }
}

// ROTAS

fun Application.configureRouting() {
    routing {
        get("/") { call.respondText("API v7.0 - Correção Transaction Unit") }

        // LOGIN
        post("/login") {
            try {
                val dados = call.receive<LoginUsuarioDTO>()
                val emailTratado = dados.email.trim().lowercase()

                val usuarioRow = transaction {
                    Usuarios.select { (Usuarios.email eq emailTratado) and (Usuarios.senha eq dados.senha) }
                        .firstOrNull()
                }

                if (usuarioRow != null) {
                    val nome = usuarioRow[Usuarios.nome] ?: "Usuário"
                    call.respond(HttpStatusCode.OK, RespostaDTO("Sucesso", true, nome))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, RespostaDTO("Login inválido", false))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, RespostaDTO("Erro", false))
            }
        }

        // CADASTRO
        post("/cadastro") {
            try {
                val dados = call.receive<CadastroUsuarioDTO>()
                val emailT = dados.email.trim().lowercase()

                transaction {
                    if (Usuarios.select { Usuarios.email eq emailT }.count() > 0)
                        throw IllegalArgumentException("Email já existe")

                    Usuarios.insert {
                        it[nome] = dados.nome
                        it[email] = emailT
                        it[senha] = dados.senha
                        it[telefone] = dados.telefone
                        it[isAcompanhante] = dados.isAcompanhante
                    }
                }
                call.respond(HttpStatusCode.Created, RespostaDTO("Criado", true))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Conflict, RespostaDTO(e.message ?: "Erro", false))
            }
        }

        // HOME
        get("/home") {
            val email = call.request.queryParameters["email"]?.trim()?.lowercase() ?: return@get

            try {
                val inicioHoje = LocalDate.now().atStartOfDay()
                val fimHoje = LocalDate.now().atTime(23, 59, 59)

                val responseDTO = transaction {
                    val nomeUser = Usuarios.slice(Usuarios.nome)
                        .select { Usuarios.email eq email }
                        .singleOrNull()
                        ?.get(Usuarios.nome) ?: "Paciente"

                    // Busca tarefas
                    val itens = (ItensCuidado innerJoin RotinasCuidados)
                        .select { (RotinasCuidados.usuarioEmail eq email) and (RotinasCuidados.status eq "ATIVO") }
                        .orderBy(ItensCuidado.frequenciaHorario)

                    val listaFinal = mutableListOf<ItemRotinaDTO>()
                    var total = 0
                    var feitos = 0

                    for (row in itens) {
                        val id = row[ItensCuidado.idItem]
                        total++

                        // Verifica aderencia do dia
                        val feito = Aderencias.select {
                            (Aderencias.idItem eq id) and
                                    (Aderencias.dataExecucao greaterEq inicioHoje) and
                                    (Aderencias.dataExecucao lessEq fimHoje) and
                                    (Aderencias.statusConformidade eq true)
                        }.count() > 0

                        if (feito) feitos++

                        listaFinal.add(ItemRotinaDTO(
                            id = id,
                            titulo = row[ItensCuidado.nomeCuidado],
                            horario = row[ItensCuidado.frequenciaHorario],
                            dose = row[ItensCuidado.dose],
                            feita = feito
                        ))
                    }

                    val progresso = if (total > 0) feitos.toFloat() / total else 0f
                    HomeResumoDTO(progresso, listaFinal, nomeUser)
                }
                call.respond(HttpStatusCode.OK, responseDTO)

            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro", false))
            }
        }

        // CRIAR ROTINA
        get("/rotinas/{id}/itens") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, emptyList<ItemRotinaDTO>())
                return@get
            }
            try {
                val itens = transaction {
                    ItensCuidado.select { ItensCuidado.idRotina eq id }.map {
                        ItemRotinaDTO(
                            id = it[ItensCuidado.idItem],
                            titulo = it[ItensCuidado.nomeCuidado],
                            horario = it[ItensCuidado.frequenciaHorario],
                            dose = it[ItensCuidado.dose],
                            feita = false
                        )
                    }
                }
                call.respond(HttpStatusCode.OK, itens)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, emptyList<ItemRotinaDTO>())
            }
        }

        // ADICIONAR UM ITEM DENTRO DE UMA ROTINA
        post("/rotinas/itens") {
            try {
                val d = call.receive<NovoItemRotinaDTO>()
                transaction {
                    ItensCuidado.insert {
                        it[idRotina] = d.idRotina
                        it[nomeCuidado] = d.titulo
                        it[frequenciaHorario] = d.horario
                        it[dose] = d.dose
                        it[medicacao] = d.descricao
                    }
                }
                call.respond(HttpStatusCode.Created, RespostaDTO("Item salvo", true))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro: ${e.message}", false))
            }
        }

        // MARCAR ROTINA INTEIRA COMO CONCLUÍDA
        put("/rotinas/{id}/concluir") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, RespostaDTO("ID inválido", false))
                return@put
            }
            try {
                transaction {
                    RotinasCuidados.update({ RotinasCuidados.idRotina eq id }) {
                        it[status] = "CONCLUIDA"
                    }
                }
                call.respond(HttpStatusCode.OK, RespostaDTO("Rotina concluída", true))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro", false))
            }
        }

        post("/rotina/status") {
            try {
                val dto = call.receive<StatusRotinaDTO>()
                val dataBase = LocalDate.parse(dto.data)

                transaction<Unit> {
                    val inicioDia = dataBase.atStartOfDay()
                    val fimDia = dataBase.atTime(23, 59, 59)

                    if (dto.feito) {
                        // marcar cuidado
                        val doseOrig = ItensCuidado.slice(ItensCuidado.dose)
                            .select { ItensCuidado.idItem eq dto.idItem }
                            .singleOrNull()?.get(ItensCuidado.dose)

                        val jaTem = Aderencias.select {
                            (Aderencias.idItem eq dto.idItem) and
                                    (Aderencias.dataExecucao greaterEq inicioDia) and
                                    (Aderencias.dataExecucao lessEq fimDia)
                        }.count() > 0

                        if (!jaTem) {
                            Aderencias.insert {
                                it[idItem] = dto.idItem
                                // Grava com a data do celular + hora atual
                                it[dataExecucao] = LocalDateTime.of(dataBase, LocalTime.now())
                                it[statusConformidade] = true
                                it[doseRealizada] = doseOrig
                            }
                        }
                    } else {
                        // desmarcar
                        Aderencias.deleteWhere {
                            (idItem eq dto.idItem) and
                                    (dataExecucao greaterEq inicioDia) and
                                    (dataExecucao lessEq fimDia)
                        }
                    }
                }
                call.respond(HttpStatusCode.OK, RespostaDTO("Atualizado", true))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro", false))
            }
        }

        // DELETAR ROTINA
        delete("/rotina/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id != null) {
                transaction {
                    Aderencias.deleteWhere { idItem eq id }
                    ItensCuidado.deleteWhere { idItem eq id }
                }
                call.respond(HttpStatusCode.OK, RespostaDTO("Deletado", true))
            } else {
                call.respond(HttpStatusCode.BadRequest, RespostaDTO("ID invalido", false))
            }
        }
        post("/sintomas") {
            try {
                val dto = call.receive<NovoSintomaDTO>()
                val riscoCalculado = (dto.sintomas >= 7) || (dto.bemEstar <= 3)

                transaction {
                    Sintomas.insert {
                        it[usuarioEmail] = dto.emailUsuario
                        it[valorEvaBemEstar] = dto.bemEstar
                        it[valorEvaSintomas] = dto.sintomas
                        it[alertaRisco] = riscoCalculado
                    }
                }
                call.respond(HttpStatusCode.Created, RespostaDTO("Sintomas registrados", true))

            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro ao salvar", false))
            }
        }
        get("/perfil") {
            val email = call.request.queryParameters["email"]?.trim()?.lowercase()
            if (email == null) {
                call.respond(HttpStatusCode.BadRequest, RespostaDTO("Email obrigatório", false))
                return@get
            }

            try {
                val usuario = transaction {
                    Usuarios.select { Usuarios.email eq email }
                        .map {
                            PerfilUsuarioDTO(
                                nome = it[Usuarios.nome] ?: "",
                                email = it[Usuarios.email],
                                telefone = it[Usuarios.telefone] ?: "",
                                isAcompanhante = it[Usuarios.isAcompanhante]
                            )
                        }
                        .singleOrNull()
                }

                if (usuario != null) {
                    call.respond(HttpStatusCode.OK, usuario)
                } else {
                    call.respond(HttpStatusCode.NotFound, RespostaDTO("Não encontrado", false))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro interno", false))
            }
        }

        put("/perfil") {
            try {
                val dto = call.receive<AtualizarPerfilDTO>()

                transaction {
                    Usuarios.update({ Usuarios.email eq dto.emailBusca }) {
                        it[nome] = dto.novoNome
                        it[telefone] = dto.novoTelefone
                    }
                }
                call.respond(HttpStatusCode.OK, RespostaDTO("Atualizado com sucesso!", true))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro ao atualizar", false))
            }
        }
        put("/usuario/senha") {
            try {
                val dto = call.receive<TrocarSenhaDTO>()

                if (dto.novaSenha.length < 8) {
                    call.respond(HttpStatusCode.BadRequest, RespostaDTO("Senha muito curta (mínimo 8)", false))
                    return@put
                }

                transaction {
                    Usuarios.update({ Usuarios.email eq dto.email }) {
                        it[senha] = dto.novaSenha
                    }
                }
                call.respond(HttpStatusCode.OK, RespostaDTO("Senha alterada com sucesso", true))

            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro ao alterar senha", false))
            }
        }

        delete("/usuario") {
            val email = call.request.queryParameters["email"]?.trim()?.lowercase()
            if (email == null) {
                call.respond(HttpStatusCode.BadRequest, RespostaDTO("Email obrigatório", false))
                return@delete
            }

            try {
                transaction {

                    val deletados = Usuarios.deleteWhere { Usuarios.email eq email }

                    if (deletados > 0) {
                        println(">>> Usuário $email deletado com sucesso.")
                    } else {
                        throw Exception("Usuário não encontrado")
                    }
                }
                call.respond(HttpStatusCode.OK, RespostaDTO("Conta deletada permanentemente", true))

            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro ao deletar conta: ${e.message}", false))
            }
        }

        get("/ficha") {
            val email = call.request.queryParameters["email"]?.trim()?.lowercase()
            if (email == null) {
                call.respond(HttpStatusCode.BadRequest, RespostaDTO("Email obrigatório", false))
                return@get
            }

            try {
                val ficha = transaction {
                    FichasMedicas.select { FichasMedicas.usuarioEmail eq email }
                        .map {
                            FichaMedicaDTO(
                                emailUsuario = it[FichasMedicas.usuarioEmail],
                                alergias = it[FichasMedicas.alergias] ?: "",
                                medicacoes = it[FichasMedicas.medicacaoContinuo] ?: "",
                                comorbidades = it[FichasMedicas.comorbidade] ?: ""
                            )
                        }
                        .singleOrNull()
                }

                if (ficha != null) {
                    call.respond(HttpStatusCode.OK, ficha)
                } else {
                    call.respond(HttpStatusCode.OK, FichaMedicaDTO(email, "", "", ""))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro ao buscar ficha", false))
            }
        }

        post("/ficha") {
            try {
                val dto = call.receive<FichaMedicaDTO>()

                transaction {
                    // Verifica se ja existe
                    val existe = FichasMedicas.select { FichasMedicas.usuarioEmail eq dto.emailUsuario }.count() > 0

                    if (existe) {
                        // Atualiza
                        FichasMedicas.update({ FichasMedicas.usuarioEmail eq dto.emailUsuario }) {
                            it[alergias] = dto.alergias
                            it[medicacaoContinuo] = dto.medicacoes
                            it[comorbidade] = dto.comorbidades
                            it[dataAtualizacao] = LocalDateTime.now()
                        }
                    } else {
                        // Insere novo
                        FichasMedicas.insert {
                            it[usuarioEmail] = dto.emailUsuario
                            it[alergias] = dto.alergias
                            it[medicacaoContinuo] = dto.medicacoes
                            it[comorbidade] = dto.comorbidades
                            it[dataAtualizacao] = LocalDateTime.now()
                        }
                    }
                }
                call.respond(HttpStatusCode.OK, RespostaDTO("Dados médicos salvos!", true))

            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro ao salvar ficha", false))
            }
        }

        get("/notificacao") {
            val email = call.request.queryParameters["email"]?.trim()?.lowercase()
            if (email == null) {
                call.respond(HttpStatusCode.BadRequest, RespostaDTO("Email obrigatório", false))
                return@get
            }

            try {
                // Tenta buscar, se nao existir cria um padrao
                val config = transaction {
                    val existente = Notificacoes.select { Notificacoes.usuarioEmail eq email }.singleOrNull()

                    if (existente != null) {
                        NotificacaoConfigDTO(
                            emailUsuario = email,
                            ativo = existente[Notificacoes.statusAlerta],
                            som = (existente[Notificacoes.som] == "PADRAO")
                        )
                    } else {
                        // Cria padrao se nao existir
                        Notificacoes.insert {
                            it[usuarioEmail] = email
                            it[canal] = "PUSH"
                            it[som] = "PADRAO"
                            it[statusAlerta] = true
                        }
                        NotificacaoConfigDTO(email, true, true)
                    }
                }
                call.respond(HttpStatusCode.OK, config)

            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro interno", false))
            }
        }

        // CONFIG NOTIFICACAO
        post("/notificacao") {
            try {
                val dto = call.receive<NotificacaoConfigDTO>()

                transaction {
                    val count = Notificacoes.select { Notificacoes.usuarioEmail eq dto.emailUsuario }.count()
                    val somValor = if (dto.som) "PADRAO" else "SILENCIOSO"

                    if (count > 0) {
                        Notificacoes.update({ Notificacoes.usuarioEmail eq dto.emailUsuario }) {
                            it[statusAlerta] = dto.ativo
                            it[som] = somValor
                        }
                    } else {
                        Notificacoes.insert {
                            it[usuarioEmail] = dto.emailUsuario
                            it[canal] = "PUSH"
                            it[som] = somValor
                            it[statusAlerta] = dto.ativo
                        }
                    }
                }
                call.respond(HttpStatusCode.OK, RespostaDTO("Configuração salva", true))

            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro ao salvar", false))
            }
        }

        get("/acompanhantes/codigo") {
            val email = call.request.queryParameters["email"]?.trim()?.lowercase()
            if (email == null) {
                call.respond(HttpStatusCode.BadRequest, RespostaDTO("Email obrigatório", false))
                return@get
            }

            try {
                val codigoDTO = transaction {
                    val existente = Acompanhantes.select {
                        (Acompanhantes.usuarioPacienteEmail eq email) and
                                (Acompanhantes.status eq "PENDENTE") and
                                (Acompanhantes.dataExpiracao greaterEq LocalDateTime.now())
                    }.firstOrNull()

                    if (existente != null) {
                        CodigoConviteDTO(existente[Acompanhantes.codigoConvite])
                    } else {
                        // gera um novo codigo de 6 caracteres (letras e números)
                        val charPool = ('A'..'Z') + ('0'..'9')
                        val novoCodigo = (1..6)
                            .map { kotlin.random.Random.nextInt(0, charPool.size) }
                            .map(charPool::get)
                            .joinToString("")

                        Acompanhantes.insert {
                            it[usuarioPacienteEmail] = email
                            it[codigoConvite] = novoCodigo
                            // Define expiracao para 7 dias
                            it[dataExpiracao] = LocalDateTime.now().plusDays(7)
                            it[status] = "PENDENTE"
                        }
                        CodigoConviteDTO(novoCodigo)
                    }
                }
                call.respond(HttpStatusCode.OK, codigoDTO)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro ao gerar código", false))
            }
        }

        get("/acompanhantes") {
            val email = call.request.queryParameters["email"]?.trim()?.lowercase()
            if (email == null) {
                call.respond(HttpStatusCode.BadRequest, emptyList<AcompanhanteDTO>())
                return@get
            }

            try {
                val lista = transaction {
                    Acompanhantes.join(Usuarios, JoinType.INNER, additionalConstraint = { Acompanhantes.usuarioAcompanhanteEmail eq Usuarios.email })
                        .select {
                            (Acompanhantes.usuarioPacienteEmail eq email) and
                                    (Acompanhantes.status eq "ATIVO")
                        }
                        .map {
                            AcompanhanteDTO(
                                idVinculo = it[Acompanhantes.idVinculo],
                                nomeAcompanhante = it[Usuarios.nome] ?: "Desconhecido",
                                emailAcompanhante = it[Usuarios.email],
                                status = it[Acompanhantes.status]
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, lista)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, emptyList<AcompanhanteDTO>())
            }
        }

        delete("/acompanhantes/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, RespostaDTO("ID inválido", false))
                return@delete
            }
            try {
                transaction {
                    Acompanhantes.deleteWhere { Acompanhantes.idVinculo eq id }
                }
                call.respond(HttpStatusCode.OK, RespostaDTO("Acesso revogado", true))
            } catch(e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro ao revogar acesso", false))
            }
        }

        get("/rotinas") {
            val email = call.request.queryParameters["email"]?.trim()?.lowercase() ?: return@get
            try {
                val rotinas = transaction {
                    RotinasCuidados.select { RotinasCuidados.usuarioEmail eq email }
                        .orderBy(RotinasCuidados.dataCriacao to SortOrder.DESC)
                        .map {
                            RotinaResumoDTO(
                                idRotina = it[RotinasCuidados.idRotina],
                                nomeRotina = it[RotinasCuidados.nomeRotina],
                                dataCriacao = it[RotinasCuidados.dataCriacao].toString().split("T")[0],
                                status = it[RotinasCuidados.status]
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, rotinas)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, emptyList<RotinaResumoDTO>())
            }
        }

        post("/rotinas") {
            try {
                val dto = call.receive<CriarRotinaDTO>()
                transaction {
                    RotinasCuidados.insert {
                        it[usuarioEmail] = dto.emailUsuario
                        it[nomeRotina] = dto.nomeRotina
                        it[dataInicio] = LocalDate.now()
                        it[status] = "ATIVO"
                    }
                }
                call.respond(HttpStatusCode.Created, RespostaDTO("Rotina criada", true))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro", false))
            }
        }

        post("/rotinas/{id}/reutilizar") {
            val idOriginal = call.parameters["id"]?.toIntOrNull()
            if (idOriginal == null) {
                call.respond(HttpStatusCode.BadRequest, RespostaDTO("ID inválido", false))
                return@post
            }

            try {
                transaction {
                    // 1 Busca a rotina original
                    val rotinaOriginal = RotinasCuidados.select { RotinasCuidados.idRotina eq idOriginal }.singleOrNull()
                        ?: throw Exception("Rotina original não encontrada")

                    // 2 Cria a nova rotina baseada na antiga
                    val novoIdRotina = RotinasCuidados.insert {
                        it[usuarioEmail] = rotinaOriginal[usuarioEmail]
                        it[nomeRotina] = rotinaOriginal[nomeRotina]
                        it[dataInicio] = java.time.LocalDate.now()
                        it[status] = "ATIVO"
                    } get RotinasCuidados.idRotina

                    // 3 Busca todos os itens da rotina antiga
                    val itensAntigos = ItensCuidado.select { ItensCuidado.idRotina eq idOriginal }

                    // 4 Copia os itens para a nova rotina
                    for (item in itensAntigos) {
                        ItensCuidado.insert {
                            it[idRotina] = novoIdRotina // Aponta para a NOVA pasta
                            it[nomeCuidado] = item[nomeCuidado]
                            it[frequenciaHorario] = item[frequenciaHorario]
                            it[dose] = item[dose]
                            it[medicacao] = item[medicacao]
                        }
                    }
                }
                call.respond(HttpStatusCode.Created, RespostaDTO("Rotina reutilizada com sucesso", true))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, RespostaDTO("Erro ao reutilizar", false))
            }
        }

        post("/acompanhantes/vincular") {
            try {
                val dto = call.receive<VincularAcompanhanteDTO>()
                transaction {
                    val linhasAtualizadas = Acompanhantes.update({
                        (Acompanhantes.codigoConvite eq dto.codigoConvite) and
                                (Acompanhantes.status eq "PENDENTE")
                    }) {
                        it[usuarioAcompanhanteEmail] = dto.emailAcompanhante
                        it[status] = "ATIVO"
                    }

                    if (linhasAtualizadas == 0) {
                        throw IllegalArgumentException("Código inválido ou já utilizado")
                    }
                }
                call.respond(HttpStatusCode.OK, RespostaDTO("Vinculado com sucesso", true))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, RespostaDTO(e.message ?: "Erro", false))
            }
        }

        get("/acompanhantes/meus-pacientes/{email}") {
            val emailBusca = call.parameters["email"] ?: return@get call.respond(HttpStatusCode.BadRequest)

            try {
                val pacientes = transaction {
                    // Faz o Join explícito: Acompanhantes.usuario_paciente_email = Usuarios.email
                    Acompanhantes.join(
                        otherTable = Usuarios,
                        joinType = org.jetbrains.exposed.sql.JoinType.INNER,
                        onColumn = Acompanhantes.usuarioPacienteEmail,
                        otherColumn = Usuarios.email
                    )
                        .select {
                            (Acompanhantes.usuarioAcompanhanteEmail eq emailBusca) and
                                    (Acompanhantes.status eq "ATIVO")
                        }
                        .map {
                            PacienteVinculadoDTO(
                                idVinculo = it[Acompanhantes.idVinculo],
                                nomePaciente = it[Usuarios.nome] ?: "Desconhecido",
                                emailPaciente = it[Usuarios.email]
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, pacientes)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("erro" to (e.message ?: "Erro ao buscar pacientes")))
            }
        }

    }
}