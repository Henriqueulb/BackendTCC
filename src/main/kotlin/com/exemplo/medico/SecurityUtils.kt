import at.favre.lib.crypto.bcrypt.BCrypt

object SecurityUtils {
    // Gera o hash da senha
    fun hashSenha(senhaTextoPuro: String): String {
        return BCrypt.withDefaults().hashToString(12, senhaTextoPuro.toCharArray())
    }

    // Compara a senha digitada com o hash do banco
    fun verificarSenha(senhaDigitada: String, hashNoBanco: String): Boolean {
        val result = BCrypt.verifyer().verify(senhaDigitada.toCharArray(), hashNoBanco)
        return result.verified
    }
}