package valhalla.core.stock.app.modules.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequestDto(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 100)
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @Size(max = 13, message = "O telefone deve ter no máximo 13 caracteres")
        String phone,

        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 8, max = 72)
        String password,

        @NotBlank(message = "O perfil é obrigatório")
        @Size(max = 100, message = "O perfil deve ter no máximo 100 caracteres")
        String profileName
) {}
