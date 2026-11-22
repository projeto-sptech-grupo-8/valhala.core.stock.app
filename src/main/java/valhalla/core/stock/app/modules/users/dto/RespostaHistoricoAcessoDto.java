package valhalla.core.stock.app.modules.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.users.entity.TipoEventoAcessoUsuario;

import java.time.LocalDateTime;

@Schema(description = "Registro de evento de acesso ou segurança da conta do usuário.")
public record RespostaHistoricoAcessoDto(

        @Schema(
                description = "Identificador único do registro de acesso.",
                example = "42"
        )
        Long id,

        @Schema(
                description = "Tipo do evento registrado na conta.",
                example = "LOGIN_SUCESSO"
        )
        TipoEventoAcessoUsuario tipoEvento,

        @Schema(
                description = "Endereço IP de origem da requisição.",
                example = "10.0.0.24"
        )
        String ipOrigem,

        @Schema(
                description = "Resumo do sistema operacional e navegador identificados.",
                example = "Windows · Chrome"
        )
        String dispositivo,

        @Schema(
                description = "Data e hora em que o evento ocorreu.",
                example = "2026-11-22T14:32:00"
        )
        LocalDateTime ocorridoEm
) {
}
