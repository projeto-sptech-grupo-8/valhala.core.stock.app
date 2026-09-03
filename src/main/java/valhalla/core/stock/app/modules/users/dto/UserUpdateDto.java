package valhalla.core.stock.app.modules.users.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserUpdateDto(
        @JsonAlias("nome")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String name,

        @Email(message = "E-mail inválido")
        String email,

        @JsonAlias("telefone")
        @Size(max = 13, message = "O telefone deve ter no máximo 13 caracteres")
        String phone,

        @JsonAlias("senha")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
        String password,

        @JsonAlias({"perfil", "perfilNome"})
        @Size(max = 100, message = "O perfil deve ter no máximo 100 caracteres")
        String profileName,

        @JsonAlias("status")
        Boolean active
) {}
