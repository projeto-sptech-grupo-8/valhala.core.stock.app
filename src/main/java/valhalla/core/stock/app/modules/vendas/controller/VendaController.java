package valhalla.core.stock.app.modules.vendas.controller;

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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.vendas.dto.RequisicaoCancelamentoVendaDto;
import valhalla.core.stock.app.modules.vendas.dto.RequisicaoVendaDto;
import valhalla.core.stock.app.modules.vendas.dto.RespostaVendaDto;
import valhalla.core.stock.app.modules.vendas.entity.CanalVenda;
import valhalla.core.stock.app.modules.vendas.entity.StatusPedido;
import valhalla.core.stock.app.modules.vendas.service.VendaService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/vendas")
@Tag(name = "Vendas", description = "Vendas do caixa, com baixa e estorno automáticos do estoque.")
@SecurityRequirement(name = "accessTokenCookie")
public class VendaController {

    private final VendaService vendaService;

    public VendaController(VendaService vendaService) {
        this.vendaService = vendaService;
    }

    @PostMapping
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(summary = "Finalizar venda",
            description = "Grava a venda com os preços do cadastro e baixa o estoque (FEFO, sem lotes vencidos). "
                    + "Drinks baixam os ingredientes da receita. Sem saldo em qualquer item, nada é gravado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Venda registrada."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos, pagamento insuficiente ou estoque insuficiente.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão REGISTRAR_VENDA ou produto de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaVendaDto> registrar(@Valid @RequestBody RequisicaoVendaDto requisicao) {
        RespostaVendaDto resposta = vendaService.registrar(requisicao);
        return ResponseEntity.created(URI.create("/vendas/" + resposta.id())).body(resposta);
    }

    @PostMapping("/{idVenda}/cancelamento")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(summary = "Cancelar venda",
            description = "Marca a venda como cancelada, estorna os pagamentos e devolve os itens aos lotes de origem.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Venda cancelada."),
            @ApiResponse(responseCode = "400", description = "Motivo ausente ou venda já cancelada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão CANCELAR_VENDA.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Venda não encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaVendaDto> cancelar(
            @PathVariable UUID idVenda,
            @Valid @RequestBody RequisicaoCancelamentoVendaDto requisicao
    ) {
        return ResponseEntity.ok(vendaService.cancelar(idVenda, requisicao.motivo()));
    }

    @GetMapping("/{idVenda}")
    @Operation(summary = "Consultar venda")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Venda encontrada."),
            @ApiResponse(responseCode = "403", description = "Sem permissão REGISTRAR_VENDA ou VISUALIZAR_RELATORIOS.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Venda não encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaVendaDto> obter(@PathVariable UUID idVenda) {
        return ResponseEntity.ok(vendaService.obter(idVenda));
    }

    @GetMapping
    @Operation(summary = "Listar vendas com filtros e paginação")
    public ResponseEntity<RespostaPaginadaDto<RespostaVendaDto>> listar(
            @Parameter(description = "Data inicial inclusiva (yyyy-MM-dd), pela data da venda.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @Parameter(description = "Data final inclusiva (yyyy-MM-dd), pela data da venda.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) CanalVenda canal,
            @Parameter(schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @Parameter(description = "numero, criadoEm ou total.", schema = @Schema(defaultValue = "criadoEm"))
            @RequestParam(defaultValue = "criadoEm") String ordenarPor,
            @Parameter(schema = @Schema(defaultValue = "DESC"))
            @RequestParam(defaultValue = "DESC") DirecaoOrdenacao direcao
    ) {
        return ResponseEntity.ok(vendaService.listar(
                dataInicial, dataFinal, status, canal, pagina, tamanho, ordenarPor, direcao));
    }
}
