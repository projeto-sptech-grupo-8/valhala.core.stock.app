package valhalla.core.stock.app.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Schema(
                description = "E-mail utilizado para realizar a autenticação",
                example = "usuario@exemplo.com"
        )
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @Schema(
                description = "Senha utilizada para realizar a autenticação",
                example = "Senha@123"
        )
        String password

) {
}