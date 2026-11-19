package valhalla.core.stock.app.modules.caixa.fiscal.controller;

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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.CancelarNotaFiscalRequestDto;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.EmitirNfceRequestDto;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.NotaFiscalDetalheResponseDto;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.NotaFiscalResumoResponseDto;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.StatusNotaFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.service.NotaFiscalCaixaService;
import valhalla.core.stock.app.modules.caixa.fiscal.service.NotaFiscalCaixaService.ArquivoXmlNotaFiscal;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaResumoResponseDto;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/caixa/notas-fiscais")
@Tag(
        name = "Notas fiscais do caixa (NFC-e pós-venda)",
        description = "Emissão pós-venda de NFC-e (modelo 65) a partir de pedidos concluídos no caixa, consulta para impressão de DANFE 80mm, download de XML e cancelamento fiscal."
)
@SecurityRequirement(name = "accessTokenCookie")
public class NotaFiscalCaixaController {

    private final NotaFiscalCaixaService notaFiscalCaixaService;

    public NotaFiscalCaixaController(NotaFiscalCaixaService notaFiscalCaixaService) {
        this.notaFiscalCaixaService = notaFiscalCaixaService;
    }

    @PostMapping("/emitir")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Emitir NFC-e (modelo 65) a partir de um pedido concluído no caixa",
            description = """
                    Emite uma NFC-e para um pedido com status CONCLUIDO que ainda não possua nota AUTORIZADA.
                    Aplica automaticamente a tributação simplificada do Simples Nacional, gera numeração sequencial
                    atômica por estabelecimento, calcula a chave de acesso de 44 dígitos (Módulo 11) e persiste o XML 4.00.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "NFC-e emitida e autorizada com sucesso."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada ou documento do consumidor inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão para operar o caixa ou pedido de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Pedido não está CONCLUIDO ou já possui NFC-e autorizada ativa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<NotaFiscalDetalheResponseDto> emitirNfce(
            @Valid @RequestBody EmitirNfceRequestDto requestDto
    ) {
        NotaFiscalDetalheResponseDto nota = notaFiscalCaixaService.emitirNfce(requestDto);
        return ResponseEntity.created(URI.create("/caixa/notas-fiscais/" + nota.id())).body(nota);
    }

    @GetMapping
    @Operation(summary = "Listar NFC-e emitidas no estabelecimento com filtros e paginação")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notas fiscais retornadas."),
            @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão para visualizar o caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaPaginadaDto<NotaFiscalResumoResponseDto>> listarNotasFiscais(
            @Parameter(description = "Filtrar por status da nota fiscal.", example = "AUTORIZADA")
            @RequestParam(required = false) StatusNotaFiscal status,
            @Parameter(description = "Filtrar por identificador do pedido de origem.")
            @RequestParam(required = false) UUID pedidoId,
            @Parameter(description = "Filtrar pelo CPF ou CNPJ do consumidor (com ou sem máscara).")
            @RequestParam(required = false) String documentoCliente,
            @Parameter(description = "Filtrar pelo número da NFC-e.")
            @RequestParam(required = false) String numero,
            @Parameter(description = "Data inicial de emissão no formato ISO (AAAA-MM-DD).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "Data final de emissão no formato ISO (AAAA-MM-DD), inclusiva.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @Parameter(description = "Número da página, iniciado em 0.", schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página.", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @Parameter(description = "Campo de ordenação: emitidaEm, criadaEm, atualizadaEm, numero, valorTotal ou status.",
                    schema = @Schema(defaultValue = "emitidaEm"))
            @RequestParam(defaultValue = "emitidaEm") String ordenarPor,
            @Parameter(description = "Direção da ordenação: ASC ou DESC.", schema = @Schema(defaultValue = "DESC"))
            @RequestParam(defaultValue = "DESC") DirecaoOrdenacao direcao
    ) {
        return ResponseEntity.ok(notaFiscalCaixaService.listarNotasFiscais(
                status,
                pedidoId,
                documentoCliente,
                numero,
                dataInicio,
                dataFim,
                pagina,
                tamanho,
                ordenarPor,
                direcao
        ));
    }

    @GetMapping("/pedidos-elegiveis")
    @Operation(
            summary = "Listar pedidos concluídos elegíveis para emissão pós-venda de NFC-e",
            description = "Retorna apenas pedidos com status CONCLUIDO do estabelecimento que ainda não possuem NFC-e AUTORIZADA ativa."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedidos elegíveis retornados."),
            @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão para visualizar o caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaPaginadaDto<PedidoCaixaResumoResponseDto>> listarPedidosElegiveis(
            @Parameter(description = "Data inicial de conclusão do pedido no formato ISO (AAAA-MM-DD).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "Data final de conclusão do pedido no formato ISO (AAAA-MM-DD), inclusiva.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @Parameter(description = "Número da página, iniciado em 0.", schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página.", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @Parameter(description = "Campo de ordenação: concluidoEm, criadoEm, numero ou total.",
                    schema = @Schema(defaultValue = "concluidoEm"))
            @RequestParam(defaultValue = "concluidoEm") String ordenarPor,
            @Parameter(description = "Direção da ordenação: ASC ou DESC.", schema = @Schema(defaultValue = "DESC"))
            @RequestParam(defaultValue = "DESC") DirecaoOrdenacao direcao
    ) {
        return ResponseEntity.ok(notaFiscalCaixaService.listarPedidosElegiveisParaEmissao(
                dataInicio,
                dataFim,
                pagina,
                tamanho,
                ordenarPor,
                direcao
        ));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Consultar o detalhe completo de uma NFC-e para visualização e impressão de DANFE 80mm",
            description = "Retorna cabeçalho fiscal, chave de 44 dígitos, protocolo, URL de QR Code, emitente, itens tributados e pagamentos aprovados."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "NFC-e encontrada."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "NFC-e pertence a outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NFC-e não encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<NotaFiscalDetalheResponseDto> buscarNotaFiscal(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(notaFiscalCaixaService.buscarNotaFiscal(id));
    }

    @GetMapping(value = "/{id}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(
            summary = "Baixar o arquivo XML 4.00 da NFC-e",
            description = "Retorna o documento XML no padrão SEFAZ 4.00 com cabeçalho Content-Disposition para download."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "XML da NFC-e retornado."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "NFC-e pertence a outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NFC-e ou XML não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<byte[]> baixarXmlNotaFiscal(
            @PathVariable("id") UUID id
    ) {
        ArquivoXmlNotaFiscal arquivo = notaFiscalCaixaService.obterXmlNotaFiscal(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "xml", StandardCharsets.UTF_8));
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(arquivo.nomeArquivo(), StandardCharsets.UTF_8)
                        .build()
        );
        return ResponseEntity.ok()
                .headers(headers)
                .body(arquivo.conteudoXml().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/{id}/cancelamento")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Cancelar uma NFC-e autorizada",
            description = """
                    Exige justificativa com no mínimo 15 caracteres e permissão CAIXA_CANCELAR (ou credenciais de autorizador).
                    Ao cancelar a NFC-e, o pedido de origem permanece CONCLUIDO e volta a permitir a emissão de uma nova NFC-e substituta.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "NFC-e cancelada com sucesso."),
            @ApiResponse(responseCode = "400", description = "Justificativa ausente ou com menos de 15 caracteres.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão CAIXA_CANCELAR ou credenciais de autorizador inválidas.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NFC-e não encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A NFC-e já está cancelada ou não permite cancelamento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<NotaFiscalDetalheResponseDto> cancelarNotaFiscal(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CancelarNotaFiscalRequestDto requestDto
    ) {
        return ResponseEntity.ok(notaFiscalCaixaService.cancelarNotaFiscal(id, requestDto));
    }
}
