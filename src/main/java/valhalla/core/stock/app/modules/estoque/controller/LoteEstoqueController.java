package valhalla.core.stock.app.modules.estoque.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.estoque.dto.LoteResponseDto;
import valhalla.core.stock.app.modules.estoque.dto.ResumoValidadeLotesResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.SituacaoValidadeLote;
import valhalla.core.stock.app.modules.estoque.service.LoteEstoqueService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/lotes")
@Tag(name = "Lotes e validade", description = "Saldo por lote, validade e indicadores de vencimento. "
        + "Lotes são criados pelas entradas e consumidos pelas saídas (FEFO) em /movimentacoes-estoque.")
@SecurityRequirement(name = "accessTokenCookie")
public class LoteEstoqueController {

    private final LoteEstoqueService loteEstoqueService;

    public LoteEstoqueController(LoteEstoqueService loteEstoqueService) {
        this.loteEstoqueService = loteEstoqueService;
    }

    @GetMapping
    @Operation(summary = "Listar lotes com saldo, filtrando por produto e situação de validade")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lotes retornados."),
            @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para visualizar estoque.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaPaginadaDto<LoteResponseDto>> listarLotes(
            @Parameter(description = "ID do produto. Omitido: todos os produtos.")
            @RequestParam(required = false) UUID produtoId,
            @Parameter(description = "Situação da validade. Omitido: todas.", example = "VENCE_EM_7_DIAS")
            @RequestParam(required = false) SituacaoValidadeLote situacao,
            @Parameter(description = "Só lotes com validade até hoje + N dias (inclui vencidos).", example = "30")
            @RequestParam(required = false) @Min(0) Integer venceEmDias,
            @Parameter(description = "Inclui lotes já zerados. Padrão: false.")
            @RequestParam(defaultValue = "false") boolean incluirZerados,
            @Parameter(description = "Número da página, iniciado em 0. Padrão: 0.", schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página. Padrão: 20; máximo: 100.", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @Parameter(description = "Campo de ordenação: validade, codigo, quantidadeAtual ou criadoEm. Padrão: validade.", schema = @Schema(defaultValue = "validade"))
            @RequestParam(defaultValue = "validade") String ordenarPor,
            @Parameter(description = "Direção da ordenação: ASC ou DESC. Padrão: ASC.", schema = @Schema(defaultValue = "ASC"))
            @RequestParam(defaultValue = "ASC") DirecaoOrdenacao direcao
    ) {
        return ResponseEntity.ok(loteEstoqueService.listarLotes(
                produtoId, situacao, venceEmDias, incluirZerados, pagina, tamanho, ordenarPor, direcao
        ));
    }

    @GetMapping("/resumo")
    @Operation(summary = "Indicadores de validade: vencidos, a vencer em 7 e 30 dias e valor em risco (a custo)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo retornado."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para visualizar estoque.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ResumoValidadeLotesResponseDto> resumir() {
        return ResponseEntity.ok(loteEstoqueService.resumir());
    }
}
