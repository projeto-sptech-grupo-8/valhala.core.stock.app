package valhalla.core.stock.app.modules.establishments.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

public record EstabelecimentoResponseDto(

        @Schema(description = "Identificador único do estabelecimento", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Nome da adega", example = "Adega Meraki")
        String nome,

        @Schema(description = "CNPJ do estabelecimento (14 dígitos)", example = "12345678000199")
        String cnpj,

        @Schema(description = "E-mail de contato do estabelecimento", example = "contato@meraki.com.br")
        String emailContato,

        @Schema(description = "Telefone de contato do estabelecimento", example = "1140000000")
        String telefone,

        @Schema(description = "Endereço do estabelecimento", example = "R. das Bebidas, 420 · São Paulo")
        String endereco,

        @Schema(description = "Indica se o estabelecimento está ativo", example = "true")
        Boolean ativo,

        @Schema(description = "Data e hora de criação do estabelecimento", example = "2026-09-20T14:15:00")
        LocalDateTime criadoEm,

        @Schema(description = "Data e hora da última atualização do estabelecimento", example = "2026-09-23T19:30:00")
        LocalDateTime atualizadoEm

) {
}
