package valhalla.core.stock.app.modules.dashboard.controller;

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
import valhalla.core.stock.app.modules.dashboard.dto.DashboardResumoResponseDto;
import valhalla.core.stock.app.modules.dashboard.service.DashboardResumoService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;

@RestController
@Validated
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Indicadores consolidados do estabelecimento autenticado.")
@SecurityRequirement(name = "accessTokenCookie")
public class DashboardResumoController {

    private final DashboardResumoService dashboardResumoService;

    public DashboardResumoController(DashboardResumoService dashboardResumoService) {
        this.dashboardResumoService = dashboardResumoService;
    }

    @GetMapping("/resumo")
    @Operation(summary = "Obter resumo inicial da dashboard")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo retornado."),
            @ApiResponse(responseCode = "400", description = "Parâmetro de consulta inválido.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para visualizar estoque.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<DashboardResumoResponseDto> obterResumo(
            @Parameter(description = "Quantidade de meses na série de movimentações, incluindo o mês atual. Padrão: 6; máximo: 24.",
                    schema = @Schema(defaultValue = "6", minimum = "1", maximum = "24"))
            @RequestParam(defaultValue = "6") @Min(1) @Max(24) int meses
    ) {
        return ResponseEntity.ok(dashboardResumoService.obterResumo(meses));
    }
}
