package valhalla.core.stock.app.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoRedefinicaoSenhaDto(

        @NotBlank(message = "O token é obrigatório")
        @Size(max = 255, message = "Token inválido")
        @Schema(
                description = "Token recebido no link de recuperação enviado por e-mail",
                example = "q1w2e3r4t5y6u7i8o9p0a1s2d3f4g5h6j7k8l9z0x1c"
        )
        String token,

        @NotBlank(message = "A nova senha é obrigatória")
        @Size(
                min = 8,
                max = 72,
                message = "A senha deve ter entre 8 e 72 caracteres"
        )
        @Schema(
                description = "Nova senha do usuário. Deve possuir entre 8 e 72 caracteres",
                example = "NovaSenha@123"
        )
        @JsonAlias("password")
        String novaSenha

) {
}
