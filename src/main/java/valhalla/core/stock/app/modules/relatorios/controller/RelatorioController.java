package valhalla.core.stock.app.modules.relatorios.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
import valhalla.core.stock.app.modules.relatorios.service.RelatorioService;

@RestController
@Validated
@RequestMapping("/relatorios")
@Tag(name = "Relatórios", description = "Relatórios gerenciais agregados do estabelecimento autenticado, por período em dias.")
@SecurityRequirement(name = "accessTokenCookie")
public class RelatorioController {

    private static final String DIAS = "Últimos N dias, incluindo hoje. Padrão: 30; máximo: 365.";

    private final RelatorioService service;

    public RelatorioController(RelatorioService service) {
        this.service = service;
    }

    @GetMapping("/abc")
    @Operation(summary = "Curva ABC de faturamento por produto")
    public ResponseEntity<RelatorioResponseDto<RelatorioLinhas.CurvaAbc>> curvaAbc(
            @Parameter(description = DIAS, schema = @Schema(defaultValue = "30", minimum = "1", maximum = "365"))
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int dias
    ) {
        return ResponseEntity.ok(service.curvaAbc(dias));
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
