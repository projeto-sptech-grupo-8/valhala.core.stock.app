package valhalla.core.stock.app.modules.users.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserUpdateDto(

        @JsonAlias("nome")
        @Size(
                max = 100,
                message = "O nome deve ter no máximo 100 caracteres"
        )
        @Schema(
                description = "Novo nome do usuário",
                example = "Lucas Peres"
        )
        String name,

        @Email(message = "E-mail inválido")
        @Schema(
                description = "Novo e-mail do usuário",
                example = "lucas.novo@exemplo.com"
        )
        String email,

        @JsonAlias("telefone")
        @Size(
                max = 13,
                message = "O telefone deve ter no máximo 13 caracteres"
        )
        @Schema(
                description = "Novo telefone do usuário",
                example = "11988888888"
        )
        String phone,

        @JsonAlias("senha")
        @Size(
                min = 8,
                max = 72,
                message = "A senha deve ter entre 8 e 72 caracteres"
        )
        @Schema(
                description = "Nova senha do usuário",
                example = "NovaSenha@123"
        )
        String password,

        @JsonAlias({"perfil", "perfilNome"})
        @Size(
                max = 100,
                message = "O perfil deve ter no máximo 100 caracteres"
        )
        @Schema(
                description = "Novo perfil de acesso do usuário",
                example = "GERENTE"
        )
        String profileName,

        @JsonAlias("status")
        @Schema(
                description = "Define se o usuário está ativo",
                example = "true"
        )
        Boolean active

) {
}