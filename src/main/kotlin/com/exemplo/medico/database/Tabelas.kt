package com.exemplo.medico.database

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.dao.id.IntIdTable

// Tabela Usuario
object Usuarios : Table("usuario") {
    val email = varchar("email", 255)
    val senha = varchar("senha", 255)
    val nome = varchar("nome", 100).nullable()
    val telefone = varchar("telefone", 20).nullable()
    val isAcompanhante = bool("is_acompanhante").default(false)
    val dataCadastro = datetime("data_cadastro").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(email)
}

// Tabela ficha medica
object FichasMedicas : Table("ficha_medica") {
    val idFicha = integer("id_ficha").autoIncrement()
    val usuarioEmail = varchar("usuario_email", 255) references Usuarios.email
    val alergias = text("alergias").nullable()
    val medicacaoContinuo = text("medicacao_uso_continuo").nullable()
    val comorbidade = text("comorbidade").nullable()
    val dataAtualizacao = datetime("data_ultima_atualizacao").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(idFicha)
}

// tabela rotina de cuidado
object RotinasCuidados : Table("rotina_cuidado") {
    val idRotina = integer("id_rotina").autoIncrement()
    val usuarioEmail = varchar("usuario_email", 255) references Usuarios.email
    val nomeRotina = varchar("nome_rotina", 100)
    val dataInicio = date("data_inicio")
    val dataFim = date("data_fim").nullable()
    val status = varchar("status", 20).default("ATIVO")
    val dataCriacao = datetime("data_criacao").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(idRotina)
}

// tabela item cuidado
object ItensCuidado : Table("item_cuidado") {
    val idItem = integer("id_item").autoIncrement()
    val idRotina = integer("id_rotina").references(RotinasCuidados.idRotina, onDelete = ReferenceOption.CASCADE)
    val nomeCuidado = varchar("nome_cuidado", 100)
    val medicacao = varchar("medicacao", 100).nullable()
    val dose = varchar("dose", 50).nullable()
    val frequenciaHorario = varchar("frequencia_horario", 50)

    override val primaryKey = PrimaryKey(idItem)
}

// Tabela Sintomas

// Tabela Principal
object Sintomas : IntIdTable("sintoma", "id_registro") {
    val usuarioEmail = varchar("usuario_email", 255) references Usuarios.email
    val dataRegistro = datetime("data_registro").defaultExpression(CurrentDateTime)
    val valorEvaBemEstar = integer("valor_eva_bem_estar")
    val valorEvaSintomas = integer("valor_eva_sintomas")
    val alertaRisco = bool("alerta_risco").default(false)
    val registradoPorEmail = varchar("registrado_por_email", 255) references Usuarios.email
}

// Tabela de Detalhes
object DetalheSintomas : IntIdTable("detalhe_sintoma") {
    val registroSintomaId = reference("registro_sintoma_id", Sintomas)
    val nomeSintoma = varchar("nome_sintoma", 100)
    val intensidade = integer("intensidade")
}

// Tabela aderencia
object Aderencias : Table("aderencia") {
    val idAderencia = integer("id_aderencia").autoIncrement()
    val idItem = integer("id_item") references ItensCuidado.idItem
    val dataExecucao = datetime("data_execucao").defaultExpression(CurrentDateTime)
    val doseRealizada = varchar("dose_realizada", 50).nullable()
    val statusConformidade = bool("status_conformidade").nullable()
    val executadoPorEmail = varchar("executado_por_email", 255) references Usuarios.email

    override val primaryKey = PrimaryKey(idAderencia)
}

// tabela acompanhante
object Acompanhantes : Table("acompanhante") {
    val idVinculo = integer("id_vinculo").autoIncrement()
    val usuarioPacienteEmail = varchar("usuario_paciente_email", 255) references Usuarios.email
    val usuarioAcompanhanteEmail = varchar("usuario_acompanhante_email", 255).references(Usuarios.email).nullable()
    val codigoConvite = varchar("codigo_convite", 50)
    val dataExpiracao = datetime("data_expiracao")
    val status = varchar("status", 20).default("PENDENTE")

    override val primaryKey = PrimaryKey(idVinculo)
}

// tabela notificação
object Notificacoes : Table("notificacao") {
    val idNotificacao = integer("id_notificacao").autoIncrement()
    val usuarioEmail = varchar("usuario_email", 255) references Usuarios.email
    val canal = varchar("canal", 20).default("PUSH")
    val som = varchar("som", 50).nullable()
    val statusAlerta = bool("status_alerta").default(true)

    override val primaryKey = PrimaryKey(idNotificacao)
}