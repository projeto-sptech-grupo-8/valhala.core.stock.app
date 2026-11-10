package valhalla.core.stock.app.modules.caixa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.caixa.dto.TaxaPagamentoAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.TaxaPagamentoRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.TaxaPagamentoResponseDto;
import valhalla.core.stock.app.modules.caixa.entity.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.service.TaxaPagamentoService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.net.URI;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/caixa/taxas")
@Tag(name = "Taxas de pagamento", description = "Taxas estimadas por forma de pagamento e número de parcelas.")
@SecurityRequirement(name = "accessTokenCookie")
public class TaxaPagamentoController {

    private final TaxaPagamentoService taxaPagamentoService;

    public TaxaPagamentoController(TaxaPagamentoService taxaPagamentoService) {
        this.taxaPagamentoService = taxaPagamentoService;
    }

    @GetMapping
    @Operation(summary = "Listar taxas de pagamento do estabelecimento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Taxas retornadas."),
            @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para visualizar o caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaPaginadaDto<TaxaPagamentoResponseDto>> listarTaxas(
            @Parameter(description = "Forma de pagamento. Omitido: todas.")
            @RequestParam(required = false) FormaPagamentoCaixa forma,
            @Parameter(description = "Situação da taxa. Omitido: ativas e inativas.")
            @RequestParam(required = false) Boolean ativo,
            @Parameter(description = "Número da página, iniciado em 0. Padrão: 0.",
                    schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página. Padrão: 20; máximo: 100.",
                    schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @Parameter(description = "Campo de ordenação: forma, parcelas, taxaPercentual, criadoEm ou atualizadoEm. Padrão: forma.",
                    schema = @Schema(defaultValue = "forma"))
            @RequestParam(defaultValue = "forma") String ordenarPor,
            @Parameter(description = "Direção da ordenação: ASC ou DESC. Padrão: ASC.",
                    schema = @Schema(defaultValue = "ASC"))
            @RequestParam(defaultValue = "ASC") DirecaoOrdenacao direcao
    ) {
        return ResponseEntity.ok(taxaPagamentoService.listarTaxas(
                forma, ativo, pagina, tamanho, ordenarPor, direcao
        ));
    }

    @PostMapping
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Cadastrar taxa para uma forma e número de parcelas",
            description = """
                    A combinação (forma, parcelas) é única por estabelecimento. Quando
                    repassarAoCliente é nulo, vale a política de juros da configuração do caixa.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Taxa cadastrada."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para configurar o caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Já existe taxa para esta forma e número de parcelas.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<TaxaPagamentoResponseDto> criarTaxa(
            @Valid @RequestBody TaxaPagamentoRequestDto requestDto
    ) {
        TaxaPagamentoResponseDto taxa = taxaPagamentoService.criarTaxa(requestDto);
        return ResponseEntity.created(URI.create("/caixa/taxas/" + taxa.id())).body(taxa);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Atualizar os valores de uma taxa",
            description = "A forma e o número de parcelas identificam a linha e não são alterados por este endpoint."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Taxa atualizada."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou taxa de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Taxa não encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<TaxaPagamentoResponseDto> atualizarTaxa(
            @PathVariable("id") UUID id,
            @Valid @RequestBody TaxaPagamentoAtualizacaoRequestDto requestDto
    ) {
        return ResponseEntity.ok(taxaPagamentoService.atualizarTaxa(id, requestDto));
    }
}
