package valhalla.core.stock.app.modules.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponseDto(

        @Schema(
                description = "Identificador único do usuário",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID id,

        @Schema(
                description = "Nome completo do usuário",
                example = "Lucas Peres"
        )
        String name,

        @Schema(
                description = "E-mail do usuário",
                example = "lucas@exemplo.com"
        )
        String email,

        @Schema(
                description = "Telefone do usuário",
                example = "11999999999"
        )
        String phone,

        UUID establishmentId,

        @Schema(
                description = "Identificador do perfil associado ao usuário",
                example = "1"
        )
        Integer profileId,

        @Schema(
                description = "Nome do perfil de acesso do usuário",
                example = "GERENTE"
        )
        String profileName,

        @Schema(
                description = "Indica se o usuário está ativo",
                example = "true"
        )
        Boolean active,

        @Schema(
                description = "Data e hora da última atualização do usuário",
                example = "2026-09-23T19:30:00"
        )
        LocalDateTime updatedAt,

        @Schema(
                description = "Data e hora de criação do usuário",
                example = "2026-09-20T14:15:00"
        )
        LocalDateTime createdAt

) {
}
