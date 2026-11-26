package valhalla.core.stock.app.modules.relatorios.controller;

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
import valhalla.core.stock.app.modules.relatorios.dto.PerdasResponseDto;
import valhalla.core.stock.app.modules.relatorios.dto.RelatorioLinhas;
import valhalla.core.stock.app.modules.relatorios.dto.RelatorioResponseDto;
import valhalla.core.stock.app.modules.relatorios.service.RelatorioExportacaoService;
import valhalla.core.stock.app.modules.relatorios.service.RelatorioService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;
import valhalla.core.stock.app.shared.export.RespostaArquivoExcel;

@RestController
@Validated
@RequestMapping("/relatorios")
@Tag(name = "Relatórios", description = "Relatórios gerenciais agregados do estabelecimento autenticado, por período em dias.")
@SecurityRequirement(name = "accessTokenCookie")
public class RelatorioController {

    private static final String DIAS = "Últimos N dias, incluindo hoje. Padrão: 30; máximo: 365.";

    private final RelatorioService service;
    private final RelatorioExportacaoService exportacaoService;

    public RelatorioController(RelatorioService service, RelatorioExportacaoService exportacaoService) {
        this.service = service;
        this.exportacaoService = exportacaoService;
    }

    @GetMapping("/abc")
    @Operation(summary = "Curva ABC de faturamento por produto")
    public ResponseEntity<RelatorioResponseDto<RelatorioLinhas.CurvaAbc>> curvaAbc(
            @Parameter(description = DIAS, schema = @Schema(defaultValue = "30", minimum = "1", maximum = "365"))
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int dias
    ) {
        return ResponseEntity.ok(service.curvaAbc(dias));
    }

    @GetMapping("/abc/exportar")
    @Operation(summary = "Exportar Curva ABC em planilha Excel (.xlsx)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Planilha Excel gerada."),
            @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para visualizar relatórios.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<byte[]> exportarCurvaAbc(
            @Parameter(description = DIAS, schema = @Schema(defaultValue = "30", minimum = "1", maximum = "365"))
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int dias
    ) {
        RelatorioResponseDto<RelatorioLinhas.CurvaAbc> relatorio = service.curvaAbc(dias);
        byte[] arquivo = exportacaoService.gerarPlanilhaCurvaAbc(relatorio.itens());
        return RespostaArquivoExcel.anexo("curva-abc-" + dias + "d", arquivo);
    }

    @GetMapping("/margem-categorias")
    @Operation(summary = "Faturamento, CMV e margem bruta por categoria")
    public ResponseEntity<RelatorioResponseDto<RelatorioLinhas.MargemCategoria>> margemCategorias(
            @Parameter(description = DIAS, schema = @Schema(defaultValue = "30", minimum = "1", maximum = "365"))
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int dias
    ) {
        return ResponseEntity.ok(service.margemCategorias(dias));
    }

    @GetMapping("/ruptura")
    @Operation(summary = "Dias com estoque zerado e venda perdida estimada por produto")
    public ResponseEntity<RelatorioResponseDto<RelatorioLinhas.Ruptura>> rupturas(
            @Parameter(description = DIAS, schema = @Schema(defaultValue = "30", minimum = "1", maximum = "365"))
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int dias
    ) {
        return ResponseEntity.ok(service.rupturas(dias));
    }

    @GetMapping("/ruptura/exportar")
    @Operation(summary = "Exportar relatório de falta de estoque em planilha Excel (.xlsx)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Planilha Excel gerada."),
            @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para visualizar relatórios.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<byte[]> exportarRupturas(
            @Parameter(description = DIAS, schema = @Schema(defaultValue = "30", minimum = "1", maximum = "365"))
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int dias
    ) {
        RelatorioResponseDto<RelatorioLinhas.Ruptura> relatorio = service.rupturas(dias);
        byte[] arquivo = exportacaoService.gerarPlanilhaRuptura(relatorio.itens());
        return RespostaArquivoExcel.anexo("falta-estoque-" + dias + "d", arquivo);
    }

    @GetMapping("/horarios")
    @Operation(summary = "Vendas por dia da semana (0 = segunda) e hora")
    public ResponseEntity<RelatorioResponseDto<RelatorioLinhas.Horario>> horarios(
            @Parameter(description = DIAS, schema = @Schema(defaultValue = "30", minimum = "1", maximum = "365"))
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int dias
    ) {
        return ResponseEntity.ok(service.horarios(dias));
    }

    @GetMapping("/canais")
    @Operation(summary = "Comparativo de vendas por canal")
    public ResponseEntity<RelatorioResponseDto<RelatorioLinhas.Canal>> canais(
            @Parameter(description = DIAS, schema = @Schema(defaultValue = "30", minimum = "1", maximum = "365"))
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int dias
    ) {
        return ResponseEntity.ok(service.canais(dias));
    }

    @GetMapping("/perdas")
    @Operation(summary = "Perdas a custo por motivo e período")
    public ResponseEntity<PerdasResponseDto> perdas(
            @Parameter(description = DIAS, schema = @Schema(defaultValue = "30", minimum = "1", maximum = "365"))
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int dias
    ) {
        return ResponseEntity.ok(service.perdas(dias));
    }
}
