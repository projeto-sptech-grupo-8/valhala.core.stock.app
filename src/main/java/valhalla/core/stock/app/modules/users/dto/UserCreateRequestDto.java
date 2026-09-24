package valhalla.core.stock.app.modules.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequestDto(

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 100)
        @Schema(
                description = "Nome completo do usuário",
                example = "Lucas Peres"
        )
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Schema(
                description = "E-mail utilizado pelo usuário",
                example = "lucas@exemplo.com"
        )
        String email,

        @Size(
                max = 13,
                message = "O telefone deve ter no máximo 13 caracteres"
        )
        @Schema(
                description = "Telefone do usuário",
                example = "11999999999"
        )
        String phone,

        @NotBlank(message = "A senha é obrigatória")
        @Size(
                min = 8,
                max = 72,
                message = "A senha deve ter entre 8 e 72 caracteres"
        )
        @Schema(
                description = "Senha do usuário. Deve possuir entre 8 e 72 caracteres",
                example = "Senha@123"
        )
        String password,

        @NotBlank(message = "O perfil é obrigatório")
        @Size(
                max = 100,
                message = "O perfil deve ter no máximo 100 caracteres"
        )
        @Schema(
                description = "Nome do perfil de acesso do usuário",
                example = "GERENTE"
        )
        String profileName

) {
}