package valhalla.core.stock.app.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Confirmação de uma operação de autenticação")
public record RespostaAutenticacaoDto(
        @Schema(example = "Autenticação realizada com sucesso") String mensagem,
        @Schema(example = "Administrador Inicial") String usuario
) { }
