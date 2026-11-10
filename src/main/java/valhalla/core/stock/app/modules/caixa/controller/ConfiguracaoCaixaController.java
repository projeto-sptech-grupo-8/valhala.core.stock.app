package valhalla.core.stock.app.modules.caixa.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.caixa.dto.ConfiguracaoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.ConfiguracaoCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.service.ConfiguracaoCaixaService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;

@RestController
@Validated
@RequestMapping("/caixa/configuracao")
@Tag(name = "Configuração do caixa", description = "Configuração das formas de pagamento e políticas comerciais do estabelecimento.")
@SecurityRequirement(name = "accessTokenCookie")
public class ConfiguracaoCaixaController {

    private final ConfiguracaoCaixaService configuracaoCaixaService;

    public ConfiguracaoCaixaController(ConfiguracaoCaixaService configuracaoCaixaService) {
        this.configuracaoCaixaService = configuracaoCaixaService;
    }

    @GetMapping
    @Operation(summary = "Consultar a configuração atual do caixa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Configuração retornada."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para visualizar o caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ConfiguracaoCaixaResponseDto> obterConfiguracao() {
        return ResponseEntity.ok(configuracaoCaixaService.obterConfiguracao());
    }

    @PutMapping
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Substituir a configuração do caixa",
            description = """
                    Substitui todos os campos de negócio da configuração do estabelecimento.
                    Campos controlados pelo servidor (id, estabelecimento, datas e autor) não
                    fazem parte do corpo. A ausência de um campo obrigatório retorna 400.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Configuração atualizada."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para configurar o caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ConfiguracaoCaixaResponseDto> atualizarConfiguracao(
            @Valid @RequestBody ConfiguracaoCaixaRequestDto requestDto
    ) {
        return ResponseEntity.ok(configuracaoCaixaService.atualizarConfiguracao(requestDto));
    }
}
