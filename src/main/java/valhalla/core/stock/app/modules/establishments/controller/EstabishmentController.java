package valhalla.core.stock.app.modules.establishments.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.establishments.dto.EstabelecimentoAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.establishments.dto.EstabelecimentoResponseDto;
import valhalla.core.stock.app.modules.establishments.service.EstabishmentService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;

@RestController
@RequestMapping("/estabelecimento")
@Tag(
        name = "Estabelecimento",
        description = "Consulta e atualização dos dados cadastrais do estabelecimento autenticado."
)
@SecurityRequirement(name = "accessTokenCookie")
public class EstabishmentController {

    private final EstabishmentService estabishmentService;

    public EstabishmentController(EstabishmentService estabishmentService) {
        this.estabishmentService = estabishmentService;
    }

    @GetMapping
    @Operation(
            summary = "Consultar dados do estabelecimento",
            description = "Retorna os dados cadastrais da adega vinculada à sessão autenticada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados do estabelecimento retornados com sucesso."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Estabelecimento não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<EstabelecimentoResponseDto> obterEstabelecimento() {
        return ResponseEntity.ok(estabishmentService.obterEstabelecimento());
    }

    @PatchMapping
    @SecurityRequirement(name = "csrfTokenHeader")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(
            summary = "Atualizar parcialmente dados do estabelecimento",
            description = "Atualiza os dados cadastrais da adega da sessão. Restrito ao perfil Gerente."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados do estabelecimento atualizados com sucesso."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Apenas gerente pode alterar dados do estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Estabelecimento não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CNPJ já cadastrado em outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<EstabelecimentoResponseDto> atualizarEstabelecimentoParcial(
            @Valid @RequestBody EstabelecimentoAtualizacaoRequestDto requestDto
    ) {
        return ResponseEntity.ok(estabishmentService.atualizarEstabelecimento(requestDto));
    }

    @PutMapping
    @SecurityRequirement(name = "csrfTokenHeader")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(
            summary = "Atualizar dados do estabelecimento",
            description = "Atualiza os dados cadastrais da adega da sessão. Restrito ao perfil Gerente."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados do estabelecimento atualizados com sucesso."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Apenas gerente pode alterar dados do estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Estabelecimento não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CNPJ já cadastrado em outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<EstabelecimentoResponseDto> atualizarEstabelecimento(
            @Valid @RequestBody EstabelecimentoAtualizacaoRequestDto requestDto
    ) {
        return ResponseEntity.ok(estabishmentService.atualizarEstabelecimento(requestDto));
    }
}
