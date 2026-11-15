package valhalla.core.stock.app.modules.caixa.configuracao.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.caixa.point.client.dto.TerminalMercadoPagoDto;
import valhalla.core.stock.app.modules.caixa.configuracao.dto.AtualizarModoOperacaoTerminalRequestDto;
import valhalla.core.stock.app.modules.caixa.configuracao.dto.ModoOperacaoTerminalResponseDto;
import valhalla.core.stock.app.modules.caixa.configuracao.service.TerminalPointService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/caixa/terminais")
@Tag(name = "Terminais Point (Mercado Pago)", description = "Sincronização e modo de operação (PDV/STANDALONE) das maquininhas Mercado Pago Point.")
@SecurityRequirement(name = "accessTokenCookie")
public class TerminalPointController {

    private final TerminalPointService terminalPointService;

    public TerminalPointController(TerminalPointService terminalPointService) {
        this.terminalPointService = terminalPointService;
    }

    @GetMapping("/mercado-pago")
    @Operation(
            summary = "Listar terminais Point disponíveis na conta Mercado Pago",
            description = "Consulta a API do Mercado Pago (/terminals/v1/list) para auxiliar o vínculo do terminal_id no cadastro de caixas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Terminais retornados."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão CAIXA_CONFIGURAR.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Falha de comunicação com a API do Mercado Pago.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<TerminalMercadoPagoDto>> listarTerminaisMercadoPago() {
        return ResponseEntity.ok(terminalPointService.listarTerminaisMercadoPago());
    }

    @PatchMapping("/{id}/modo-operacao")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Alternar modo de operação da Point Smart 2 (PDV ou STANDALONE)",
            description = "Envia PATCH /terminals/v1/setup para ativar o modo PDV integrado ao caixa ou retornar para STANDALONE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Modo de operação atualizado."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão CAIXA_CONFIGURAR.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Terminal não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Falha de comunicação com a API do Mercado Pago.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ModoOperacaoTerminalResponseDto> atualizarModoOperacao(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AtualizarModoOperacaoTerminalRequestDto requestDto
    ) {
        return ResponseEntity.ok(terminalPointService.atualizarModoOperacao(id, requestDto.modoOperacao()));
    }
}
