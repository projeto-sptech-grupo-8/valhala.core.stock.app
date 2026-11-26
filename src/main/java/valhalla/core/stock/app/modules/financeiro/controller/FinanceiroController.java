package valhalla.core.stock.app.modules.financeiro.controller;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.financeiro.dto.RequisicaoBaixaContaDto;
import valhalla.core.stock.app.modules.financeiro.dto.RequisicaoContaDto;
import valhalla.core.stock.app.modules.financeiro.dto.RespostaContaDto;
import valhalla.core.stock.app.modules.financeiro.dto.ResultadoFinanceiroDto;
import valhalla.core.stock.app.modules.financeiro.dto.ResumoFinanceiroDto;
import valhalla.core.stock.app.modules.financeiro.entity.CentroCusto;
import valhalla.core.stock.app.modules.financeiro.entity.StatusConta;
import valhalla.core.stock.app.modules.financeiro.entity.TipoConta;
import valhalla.core.stock.app.modules.financeiro.service.ContaFinanceiraService;
import valhalla.core.stock.app.modules.financeiro.service.FinanceiroService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/financeiro")
@Tag(name = "Financeiro", description = "Saldo, fluxo de caixa, resultado (DRE) e contas a pagar e receber.")
@SecurityRequirement(name = "accessTokenCookie")
public class FinanceiroController {

    private final FinanceiroService financeiroService;
    private final ContaFinanceiraService contaService;

    public FinanceiroController(FinanceiroService financeiroService, ContaFinanceiraService contaService) {
        this.financeiroService = financeiroService;
        this.contaService = contaService;
    }

    @GetMapping("/resumo")
    @Operation(summary = "Visão geral: saldo, contas dos próximos 30 dias, faturamento do mês e fluxo de 6 meses")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo calculado."),
            @ApiResponse(responseCode = "403", description = "Sem permissão VISUALIZAR_FINANCEIRO.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ResumoFinanceiroDto> resumo() {
        return ResponseEntity.ok(financeiroService.resumo());
    }

    @GetMapping("/resultado")
    @Operation(summary = "Resultado (DRE gerencial) e vendas por categoria do período",
            description = "Receita e CMV das vendas concluídas; despesas das contas a pagar fixas e variáveis com "
                    + "vencimento no período. Compras de mercadoria não entram (o custo já está no CMV).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado calculado."),
            @ApiResponse(responseCode = "400", description = "Período inválido ou maior que 366 dias.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ResultadoFinanceiroDto> resultado(
            @Parameter(description = "Primeiro dia do período (yyyy-MM-dd).", example = "2026-11-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @Parameter(description = "Último dia do período, inclusive (yyyy-MM-dd).", example = "2026-11-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
    ) {
        return ResponseEntity.ok(financeiroService.resultado(inicio, fim));
    }

    @GetMapping("/contas")
    @Operation(summary = "Listar contas a pagar e receber, ordenadas pelo vencimento")
    public ResponseEntity<RespostaPaginadaDto<RespostaContaDto>> listarContas(
            @RequestParam(required = false) TipoConta tipo,
            @RequestParam(required = false) StatusConta status,
            @RequestParam(required = false) CentroCusto centroCusto,
            @Parameter(description = "Vencimento a partir de (yyyy-MM-dd).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoDe,
            @Parameter(description = "Vencimento até (yyyy-MM-dd), inclusive.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoAte,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho
    ) {
        return ResponseEntity.ok(contaService.listar(tipo, status, centroCusto, vencimentoDe, vencimentoAte,
                pagina, tamanho));
    }

    @GetMapping("/contas/{idConta}")
    @Operation(summary = "Consultar conta")
    public ResponseEntity<RespostaContaDto> obterConta(@PathVariable UUID idConta) {
        return ResponseEntity.ok(contaService.obter(idConta));
    }

    @PostMapping("/contas")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(summary = "Cadastrar conta a pagar ou a receber")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta cadastrada."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão GERENCIAR_FINANCEIRO.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaContaDto> criarConta(@Valid @RequestBody RequisicaoContaDto requisicao) {
        RespostaContaDto conta = contaService.criar(requisicao);
        return ResponseEntity.created(URI.create("/financeiro/contas/" + conta.id())).body(conta);
    }

    @PutMapping("/contas/{idConta}")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(summary = "Editar conta em aberto")
    public ResponseEntity<RespostaContaDto> atualizarConta(
            @PathVariable UUID idConta,
            @Valid @RequestBody RequisicaoContaDto requisicao
    ) {
        return ResponseEntity.ok(contaService.atualizar(idConta, requisicao));
    }

    @PostMapping("/contas/{idConta}/baixa")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(summary = "Baixar conta (registrar pagamento ou recebimento)")
    public ResponseEntity<RespostaContaDto> baixarConta(
            @PathVariable UUID idConta,
            @Valid @RequestBody(required = false) RequisicaoBaixaContaDto requisicao
    ) {
        return ResponseEntity.ok(contaService.baixar(idConta, requisicao));
    }

    @PatchMapping("/contas/{idConta}/inativacao")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(summary = "Inativar conta em aberto")
    public ResponseEntity<RespostaContaDto> inativarConta(@PathVariable UUID idConta) {
        return ResponseEntity.ok(contaService.inativar(idConta));
    }
}
