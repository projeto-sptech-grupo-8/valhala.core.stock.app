package valhalla.core.stock.app.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponseDto(

        @Schema(
                description = "Token de acesso utilizado para autenticar as requisições",
                example = "eyJhbGciOiJIUzI1NiJ9..."
        )
        String accessToken,

        @Schema(
                description = "Token utilizado para renovar a autenticação",
                example = "eyJhbGciOiJIUzI1NiJ9..."
        )
        String refreshToken,

        @Schema(
                description = "Nome de usuário associado à autenticação",
                example = "Lucas Peres"
        )
        String username

) {
}