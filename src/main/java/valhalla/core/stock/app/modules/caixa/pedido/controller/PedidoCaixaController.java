package valhalla.core.stock.app.modules.caixa.pedido.controller;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.caixa.pedido.dto.CancelarPedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.ConciliarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.DecisaoDescontoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.DescontoPedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.EstornarPedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PagamentoPedidoResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaDetalheResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaResumoResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.RegistrarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.enums.CanalPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.point.service.OrquestradorPagamentoPointService;
import valhalla.core.stock.app.modules.caixa.pedido.service.PedidoCaixaService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/caixa/pedidos")
@Tag(name = "Pedidos do caixa (PDV)", description = "Gestão de pedidos simultâneos, descontos híbridos, pagamentos, conciliação manual e estorno no PDV.")
@SecurityRequirement(name = "accessTokenCookie")
public class PedidoCaixaController {

    private final PedidoCaixaService pedidoCaixaService;
    private final OrquestradorPagamentoPointService orquestradorPagamentoPointService;

    public PedidoCaixaController(
            PedidoCaixaService pedidoCaixaService,
            OrquestradorPagamentoPointService orquestradorPagamentoPointService
    ) {
        this.pedidoCaixaService = pedidoCaixaService;
        this.orquestradorPagamentoPointService = orquestradorPagamentoPointService;
    }

    @PostMapping
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Abrir um novo pedido no caixa",
            description = """
                    Cria um pedido vinculado à sessão aberta do operador (ou à sessão informada) com numeração
                    sequencial por estabelecimento e validação imediata de disponibilidade de estoque dos itens.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido criado."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão para operar o caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Sessão ou produto não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Sessão fechada ou estoque insuficiente.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PedidoCaixaDetalheResponseDto> criarPedido(
            @Valid @RequestBody(required = false) PedidoCaixaRequestDto requestDto
    ) {
        PedidoCaixaDetalheResponseDto pedido = pedidoCaixaService.criarPedido(requestDto);
        return ResponseEntity.created(URI.create("/caixa/pedidos/" + pedido.id())).body(pedido);
    }

    @GetMapping
    @Operation(summary = "Listar pedidos do caixa com filtros e paginação")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedidos retornados."),
            @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão para visualizar o caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaPaginadaDto<PedidoCaixaResumoResponseDto>> listarPedidos(
            @Parameter(description = "Filtrar por sessão de caixa.")
            @RequestParam(required = false) UUID sessaoCaixaId,
            @Parameter(description = "Filtrar por status do pedido.", example = "EM_ATENDIMENTO")
            @RequestParam(required = false) StatusPedidoCaixa status,
            @Parameter(description = "Filtrar por canal do pedido.", example = "MESA")
            @RequestParam(required = false) CanalPedidoCaixa canal,
            @Parameter(description = "Data inicial de criação no formato ISO (AAAA-MM-DD).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "Data final de criação no formato ISO (AAAA-MM-DD), inclusiva.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @Parameter(description = "Número da página, iniciado em 0.", schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página.", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @Parameter(description = "Campo de ordenação: criadoEm, atualizadoEm, concluidoEm, numero, total ou status.",
                    schema = @Schema(defaultValue = "atualizadoEm"))
            @RequestParam(defaultValue = "atualizadoEm") String ordenarPor,
            @Parameter(description = "Direção da ordenação: ASC ou DESC.", schema = @Schema(defaultValue = "DESC"))
            @RequestParam(defaultValue = "DESC") DirecaoOrdenacao direcao
    ) {
        return ResponseEntity.ok(pedidoCaixaService.listarPedidos(
                sessaoCaixaId,
                status,
                canal,
                dataInicio,
                dataFim,
                pagina,
                tamanho,
                ordenarPor,
                direcao
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar o detalhe completo de um pedido com itens e pagamentos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido encontrado."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Pedido de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PedidoCaixaDetalheResponseDto> buscarPedido(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(pedidoCaixaService.buscarPedido(id));
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Atualizar itens, canal ou identificador de um pedido em atendimento",
            description = "Substitui a lista de itens enquanto o pedido estiver EM_ATENDIMENTO e sem pagamentos ativos, auditando remoções/reduções de itens."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido atualizado."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido ou produto não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Pedido não editável ou estoque insuficiente.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PedidoCaixaDetalheResponseDto> atualizarPedido(
            @PathVariable("id") UUID id,
            @Valid @RequestBody PedidoCaixaAtualizacaoRequestDto requestDto
    ) {
        return ResponseEntity.ok(pedidoCaixaService.atualizarPedido(id, requestDto));
    }

    @PutMapping("/{id}/desconto")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Aplicar ou solicitar desconto no pedido",
            description = """
                    Aplica o desconto imediatamente quando estiver dentro do limite duplo (% e R$) ou quando
                    autorizado por usuário com CAIXA_AUTORIZAR_DESCONTO. Caso contrário, registra a solicitação
                    como PENDENTE_AUTORIZACAO para aprovação posterior do gerente.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Desconto aplicado ou solicitado."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Credenciais do autorizador inválidas ou sem permissão.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Estado inválido do pedido.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PedidoCaixaDetalheResponseDto> aplicarOuSolicitarDesconto(
            @PathVariable("id") UUID id,
            @Valid @RequestBody DescontoPedidoCaixaRequestDto requestDto
    ) {
        return ResponseEntity.ok(pedidoCaixaService.aplicarOuSolicitarDesconto(id, requestDto));
    }

    @PostMapping("/{id}/desconto/decisao")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Aprovar ou negar solicitação de desconto pendente",
            description = "Permite que um usuário com CAIXA_AUTORIZAR_DESCONTO aprove ou negue o desconto solicitado pelo operador."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Decisão registrada."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão CAIXA_AUTORIZAR_DESCONTO.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Não há desconto pendente de autorização.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PedidoCaixaDetalheResponseDto> decidirDescontoPendente(
            @PathVariable("id") UUID id,
            @Valid @RequestBody DecisaoDescontoPedidoRequestDto requestDto
    ) {
        return ResponseEntity.ok(pedidoCaixaService.decidirDescontoPendente(id, requestDto));
    }

    @DeleteMapping("/{id}/desconto")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Remover desconto ou solicitação de desconto do pedido",
            description = "Limpa qualquer desconto aprovado, pendente ou negado, restaurando o total para o subtotal e destravando o pedido para pagamento."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Desconto removido."),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Pedido já concluído, cancelado ou estornado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PedidoCaixaDetalheResponseDto> removerDesconto(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(pedidoCaixaService.removerDesconto(id));
    }

    @PostMapping("/{id}/cancelamento")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Cancelar um pedido ainda não pago",
            description = """
                    Pedidos vazios podem ser descartados por qualquer operador com CAIXA_OPERAR.
                    Pedidos com itens exigem motivo e a permissão CAIXA_CANCELAR (ou credenciais de autorizador).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido cancelado."),
            @ApiResponse(responseCode = "400", description = "Motivo ausente ou inválido.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão CAIXA_CANCELAR.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Pedido já possui pagamentos ou não pode ser cancelado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PedidoCaixaDetalheResponseDto> cancelarPedido(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) CancelarPedidoCaixaRequestDto requestDto
    ) {
        return ResponseEntity.ok(pedidoCaixaService.cancelarPedido(id, requestDto));
    }

    @PostMapping("/{id}/pagamentos")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Registrar pagamento (dinheiro ou contingência manual) no pedido",
            description = """
                    Suporta pagamento integral ou parcial/misto e idempotência por chaveIdempotencia.
                    Em DINHEIRO aprova imediatamente com cálculo de troco. Em DEBITO/CREDITO/PIX manual cria
                    registro PENDENTE_CONCILIACAO com snapshot de taxas. Ao atingir o total aprovado, conclui
                    o pedido e baixa o estoque automaticamente.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagamento processado."),
            @ApiResponse(responseCode = "400", description = "Dados do pagamento inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Estado inválido do pedido/sessão ou estoque insuficiente.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PedidoCaixaDetalheResponseDto> registrarPagamento(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RegistrarPagamentoPedidoRequestDto requestDto
    ) {
        return ResponseEntity.ok(pedidoCaixaService.registrarPagamento(id, requestDto));
    }

    @GetMapping("/{id}/pagamentos")
    @Operation(summary = "Listar todos os pagamentos e tentativas de um pedido para acompanhamento e auditoria")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagamentos retornados."),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<PagamentoPedidoResponseDto>> listarPagamentosDoPedido(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(pedidoCaixaService.listarPagamentosDoPedido(id));
    }

    @PostMapping("/{pedidoId}/pagamentos/{pagamentoId}/conciliacao")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Conciliar manualmente um pagamento pendente de conciliação",
            description = "Aprova (exige CAIXA_RESOLVER_DIVERGENCIA ou credenciais de autorizador) ou rejeita/cancela um pagamento pendente de conciliação."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conciliação registrada."),
            @ApiResponse(responseCode = "400", description = "Motivo obrigatório ao rejeitar.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Credenciais inválidas ou sem permissão CAIXA_RESOLVER_DIVERGENCIA ao aprovar.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido ou pagamento não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Pagamento não pode ser conciliado ou estoque insuficiente.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PedidoCaixaDetalheResponseDto> conciliarPagamento(
            @PathVariable("pedidoId") UUID pedidoId,
            @PathVariable("pagamentoId") UUID pagamentoId,
            @Valid @RequestBody ConciliarPagamentoPedidoRequestDto requestDto
    ) {
        return ResponseEntity.ok(pedidoCaixaService.conciliarPagamento(pedidoId, pagamentoId, requestDto));
    }

    @PostMapping("/{pedidoId}/pagamentos/{pagamentoId}/estorno")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Estornar um pagamento aprovado específico de um pedido em aberto",
            description = "Exige CAIXA_ESTORNAR (ou credenciais de autorizador). Para pagamentos Point solicita estorno na API do Mercado Pago; em DINHEIRO valida ou registra a saída na gaveta e recalcula o saldo do pedido."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagamento estornado."),
            @ApiResponse(responseCode = "400", description = "Motivo obrigatório.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão CAIXA_ESTORNAR.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido ou pagamento não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Pedido fechado, pagamento não aprovado ou saldo insuficiente em caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Falha ao estornar pagamento na API Point do Mercado Pago.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PedidoCaixaDetalheResponseDto> estornarPagamento(
            @PathVariable("pedidoId") UUID pedidoId,
            @PathVariable("pagamentoId") UUID pagamentoId,
            @Valid @RequestBody EstornarPedidoCaixaRequestDto requestDto
    ) {
        return ResponseEntity.ok(pedidoCaixaService.estornarPagamento(pedidoId, pagamentoId, requestDto));
    }

    @PostMapping("/{pedidoId}/pagamentos/{pagamentoId}/cancelamento-point")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Cancelar cobrança pendente na maquininha Point",
            description = "Solicita o cancelamento da Order na API Point do Mercado Pago, libera a reserva de estoque e desbloqueia o terminal."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitação de cancelamento processada."),
            @ApiResponse(responseCode = "404", description = "Pedido ou pagamento não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Cobrança não pode mais ser cancelada (ex.: já paga).",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Falha de comunicação com o Mercado Pago.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PedidoCaixaDetalheResponseDto> cancelarCobrancaPoint(
            @PathVariable("pedidoId") UUID pedidoId,
            @PathVariable("pagamentoId") UUID pagamentoId
    ) {
        return ResponseEntity.ok(orquestradorPagamentoPointService.cancelarCobrancaPoint(pedidoId, pagamentoId));
    }

    @PostMapping("/{pedidoId}/pagamentos/{pagamentoId}/sincronizar")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Sincronizar pagamento Point com a API do Mercado Pago",
            description = "Consulta o status atualizado da Order no Mercado Pago e aplica a transição no pagamento e no estoque."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sincronização concluída."),
            @ApiResponse(responseCode = "404", description = "Pedido ou pagamento não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Pagamento inválido para sincronização Point ou estoque insuficiente.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Falha de comunicação com o Mercado Pago.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PedidoCaixaDetalheResponseDto> sincronizarCobrancaPoint(
            @PathVariable("pedidoId") UUID pedidoId,
            @PathVariable("pagamentoId") UUID pagamentoId
    ) {
        return ResponseEntity.ok(orquestradorPagamentoPointService.sincronizarCobrancaPoint(pedidoId, pagamentoId));
    }

    @PostMapping("/{id}/estorno")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Estornar integralmente um pedido concluído",
            description = "Exige permissão CAIXA_ESTORNAR (ou credenciais de autorizador), marca os pagamentos como ESTORNADO e devolve os itens ao estoque via AJUSTE_POSITIVO."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido estornado."),
            @ApiResponse(responseCode = "400", description = "Motivo obrigatório.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão CAIXA_ESTORNAR.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Somente pedidos CONCLUIDO podem ser estornados.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PedidoCaixaDetalheResponseDto> estornarPedido(
            @PathVariable("id") UUID id,
            @Valid @RequestBody EstornarPedidoCaixaRequestDto requestDto
    ) {
        return ResponseEntity.ok(pedidoCaixaService.estornarPedido(id, requestDto));
    }
}
