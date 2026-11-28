package valhalla.core.stock.app.modules.caixa.pedido.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.configuracao.mapper.ConfiguracaoCaixaMapper;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.ConfiguracaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.exception.IntegracaoMercadoPagoException;
import valhalla.core.stock.app.modules.caixa.exception.PedidoEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.SaldoInsuficienteCaixaException;
import valhalla.core.stock.app.modules.caixa.exception.SessaoCaixaEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.fiscal.service.NotaFiscalCaixaService;
import valhalla.core.stock.app.modules.caixa.pedido.dto.CancelarPedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.ConciliarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.DecisaoDescontoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.DescontoPedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.EstornarPedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.ItemPedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaDetalheResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaResumoResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.RegistrarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.CanalPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusDescontoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.enums.TipoDescontoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ItemPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.SequenciaDocumentoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.strategy.ProcessadorPagamento;
import valhalla.core.stock.app.modules.caixa.point.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.point.service.OrquestradorPagamentoPointService;
import valhalla.core.stock.app.modules.caixa.security.AutorizadorOperacaoCaixaService;
import valhalla.core.stock.app.modules.caixa.security.ContextoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.entity.MovimentacaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.sessao.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.sessao.enums.StatusSessaoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.enums.TipoMovimentacaoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.repository.MovimentacaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.sessao.repository.SessaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.sessao.service.AuditoriaCaixaService;
import valhalla.core.stock.app.modules.caixa.sessao.service.CalculadoraFechamentoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.service.EventoAuditoriaCaixa;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.modules.estoque.service.MovimentacaoEstoqueService;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.http.HttpStatus;
import java.util.function.Consumer;

@ExtendWith(MockitoExtension.class)
class PedidoCaixaServiceTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private ItemPedidoRepository itemPedidoRepository;
    @Mock private PagamentoPedidoRepository pagamentoPedidoRepository;
    @Mock private SequenciaDocumentoRepository sequenciaDocumentoRepository;
    @Mock private SessaoCaixaRepository sessaoCaixaRepository;
    @Mock private MovimentacaoCaixaRepository movimentacaoCaixaRepository;
    @Mock private ConfiguracaoCaixaRepository configuracaoCaixaRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private UserRepository userRepository;
    @Mock private MovimentacaoEstoqueService movimentacaoEstoqueService;
    @Mock private AutorizadorOperacaoCaixaService autorizadorOperacaoCaixaService;
    @Mock private AuditoriaCaixaService auditoriaCaixaService;
    @Mock private ContextoCaixa contextoCaixa;
    @Mock private ProcessadorPagamento processadorPagamento;
    @Mock private OrquestradorPagamentoPointService orquestradorPagamentoPointService;
    @Mock private ReservaEstoqueService reservaEstoqueService;
    @Mock private NotaFiscalCaixaService notaFiscalCaixaService;
    @Mock private BusinessEventLogger businessEventLogger;

    private PedidoCaixaService service;

    @BeforeEach
    void configurar() {
        TransactionTemplate txTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(mock(TransactionStatus.class));
            }

            @Override
            public void executeWithoutResult(Consumer<TransactionStatus> action) {
                action.accept(mock(TransactionStatus.class));
            }
        };
        service = new PedidoCaixaService(
                pedidoRepository,
                itemPedidoRepository,
                pagamentoPedidoRepository,
                sequenciaDocumentoRepository,
                sessaoCaixaRepository,
                movimentacaoCaixaRepository,
                new CalculadoraFechamentoCaixa(),
                configuracaoCaixaRepository,
                produtoRepository,
                userRepository,
                movimentacaoEstoqueService,
                autorizadorOperacaoCaixaService,
                auditoriaCaixaService,
                contextoCaixa,
                List.of(processadorPagamento),
                orquestradorPagamentoPointService,
                reservaEstoqueService,
                notaFiscalCaixaService,
                txTemplate,
                businessEventLogger
        );
        lenient().when(notaFiscalCaixaService.carregarUltimaNotaPorPedido(any())).thenReturn(Map.of());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listarPedidosValidaDatasEAvaliaTodosOsFiltros() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID sessaoId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabelecimentoId);

        assertThatThrownBy(() -> service.listarPedidos(
                null, null, null,
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 10),
                0, 20, "criadoEm", DirecaoOrdenacao.DESC
        )).isInstanceOf(IllegalArgumentException.class);

        PedidoEntity pedido = pedidoBase(estabelecimentoId, UUID.randomUUID(), sessaoId, "100.00");
        when(pedidoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pedido), PageRequest.of(0, 20), 1));
        when(itemPedidoRepository.findByPedidoIdInOrderByIdAsc(List.of(pedido.getId()))).thenReturn(List.of());
        when(pagamentoPedidoRepository.findByPedidoIdInOrderByCriadoEmAsc(List.of(pedido.getId()))).thenReturn(List.of());

        RespostaPaginadaDto<PedidoCaixaResumoResponseDto> resp = service.listarPedidos(
                sessaoId, StatusPedidoCaixa.EM_ATENDIMENTO, CanalPedidoCaixa.BALCAO,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20),
                0, 20, "criadoEm", DirecaoOrdenacao.DESC
        );
        assertThat(resp.itens()).hasSize(1);

        ArgumentCaptor<Specification<PedidoEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(pedidoRepository).findAll(specCaptor.capture(), any(Pageable.class));

        Predicate predicate = mock(Predicate.class);
        Path path = mock(Path.class, inv -> Path.class.isAssignableFrom(inv.getMethod().getReturnType()) ? inv.getMock() : null);
        Root<PedidoEntity> root = mock(Root.class, inv -> Path.class.isAssignableFrom(inv.getMethod().getReturnType()) ? path : null);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class, inv ->
                Predicate.class.isAssignableFrom(inv.getMethod().getReturnType()) ? predicate : null);

        assertThat(specCaptor.getValue().toPredicate(root, query, builder)).isNotNull();
    }

    @Test
    void criarPedidoValidaSessaoAbertaECanalMesa() {
        UUID estabId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sessaoId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabId);
        when(contextoCaixa.usuarioId()).thenReturn(userId);

        when(sessaoCaixaRepository.findByIdAndEstabelecimentoId(sessaoId, estabId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.criarPedido(new PedidoCaixaRequestDto(sessaoId, CanalPedidoCaixa.BALCAO, null, null, null)))
                .isInstanceOf(EntityNotFoundException.class);

        SessaoCaixaEntity sessaoFechada = sessao(sessaoId, estabId, userId, StatusSessaoCaixa.FECHADA);
        when(sessaoCaixaRepository.findByIdAndEstabelecimentoId(sessaoId, estabId)).thenReturn(Optional.of(sessaoFechada));
        assertThatThrownBy(() -> service.criarPedido(new PedidoCaixaRequestDto(sessaoId, CanalPedidoCaixa.BALCAO, null, null, null)))
                .isInstanceOf(SessaoCaixaEstadoInvalidoException.class);

        SessaoCaixaEntity sessaoOutro = sessao(sessaoId, estabId, UUID.randomUUID(), StatusSessaoCaixa.ABERTA);
        when(sessaoCaixaRepository.findByIdAndEstabelecimentoId(sessaoId, estabId)).thenReturn(Optional.of(sessaoOutro));
        when(contextoCaixa.isGerente()).thenReturn(false);
        assertThatThrownBy(() -> service.criarPedido(new PedidoCaixaRequestDto(sessaoId, CanalPedidoCaixa.BALCAO, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        when(contextoCaixa.isGerente()).thenReturn(true);
        assertThatThrownBy(() -> service.criarPedido(new PedidoCaixaRequestDto(sessaoId, CanalPedidoCaixa.MESA, "   ", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MESA");

        // Sessão omitida e não encontrada
        when(sessaoCaixaRepository.findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
                estabId, userId, StatusSessaoCaixa.ABERTA
        )).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.criarPedido(null))
                .isInstanceOf(SessaoCaixaEstadoInvalidoException.class);
    }

    @Test
    void aplicarEDecidirDescontoCobreTodosOsRamosEValidacoes() {
        UUID estabId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID autorizadorId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabId);
        when(contextoCaixa.usuarioId()).thenReturn(userId);

        PedidoEntity pedido = pedidoBase(estabId, userId, UUID.randomUUID(), "100.00");
        pedido.setId(pedidoId);
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.saveAndFlush(any(PedidoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // Sem itens -> erro
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of());
        assertThatThrownBy(() -> service.aplicarOuSolicitarDesconto(
                pedidoId, new DescontoPedidoCaixaRequestDto(TipoDescontoPedido.VALOR, BigDecimal.TEN, null, null, null)
        )).isInstanceOf(PedidoEstadoInvalidoException.class);

        ItemPedidoEntity item = item(1L, pedidoId, "100.00");
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of(item));

        // Desconto >= subtotal
        assertThatThrownBy(() -> service.aplicarOuSolicitarDesconto(
                pedidoId, new DescontoPedidoCaixaRequestDto(TipoDescontoPedido.PERCENTUAL, new BigDecimal("100"), null, null, null)
        )).isInstanceOf(IllegalArgumentException.class);

        // Desconto reduz abaixo do pago
        PagamentoPedidoEntity pagAprovado = new PagamentoPedidoEntity();
        pagAprovado.setId(UUID.randomUUID());
        pagAprovado.setPedidoId(pedidoId);
        pagAprovado.setForma(FormaPagamentoCaixa.DINHEIRO);
        pagAprovado.setStatus(StatusPagamentoPedido.APROVADO);
        pagAprovado.setValor(new BigDecimal("90.00"));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of(pagAprovado));

        assertThatThrownBy(() -> service.aplicarOuSolicitarDesconto(
                pedidoId, new DescontoPedidoCaixaRequestDto(TipoDescontoPedido.VALOR, new BigDecimal("20.00"), null, null, null)
        )).isInstanceOf(PedidoEstadoInvalidoException.class);

        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of());
        ConfiguracaoCaixaEntity config = ConfiguracaoCaixaMapper.criarPadrao(estabId);
        config.setLimiteDescontoPercentual(new BigDecimal("10.00"));
        config.setLimiteDescontoValor(new BigDecimal("15.00"));
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabId)).thenReturn(Optional.of(config));

        // Dentro do limite (5% = 5.00 <= min(10.00, 15.00))
        PedidoCaixaDetalheResponseDto aprovadoDireto = service.aplicarOuSolicitarDesconto(
                pedidoId, new DescontoPedidoCaixaRequestDto(TipoDescontoPedido.PERCENTUAL, new BigDecimal("5.00"), "Fidelidade", null, null)
        );
        assertThat(aprovadoDireto.statusDesconto()).isEqualTo(StatusDescontoPedido.APROVADO);
        assertThat(aprovadoDireto.total()).isEqualByComparingTo("95.00");

        // Acima do limite sem autorizador e sem motivo
        when(autorizadorOperacaoCaixaService.tentarResolverAutorizador(eq("CAIXA_AUTORIZAR_DESCONTO"), any(), any()))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.aplicarOuSolicitarDesconto(
                pedidoId, new DescontoPedidoCaixaRequestDto(TipoDescontoPedido.VALOR, new BigDecimal("25.00"), "   ", null, null)
        )).isInstanceOf(IllegalArgumentException.class);

        // Acima do limite gerando PENDENTE_AUTORIZACAO
        PedidoCaixaDetalheResponseDto pendente = service.aplicarOuSolicitarDesconto(
                pedidoId, new DescontoPedidoCaixaRequestDto(TipoDescontoPedido.VALOR, new BigDecimal("25.00"), "Cliente VIP", null, null)
        );
        assertThat(pendente.statusDesconto()).isEqualTo(StatusDescontoPedido.PENDENTE_AUTORIZACAO);
        assertThat(pendente.descontoSolicitado()).isEqualByComparingTo("25.00");

        // Decisão: negar sem observação -> erro
        when(autorizadorOperacaoCaixaService.exigirAutorizador(eq("CAIXA_AUTORIZAR_DESCONTO"), any(), any(), any()))
                .thenReturn(new AutorizadorOperacaoCaixaService.AutorizadorResolvido(autorizadorId, "Gerente"));
        assertThatThrownBy(() -> service.decidirDescontoPendente(
                pedidoId, new DecisaoDescontoPedidoRequestDto(false, "   ", null, null)
        )).isInstanceOf(IllegalArgumentException.class);

        // Decisão: negar com observação
        PedidoCaixaDetalheResponseDto negado = service.decidirDescontoPendente(
                pedidoId, new DecisaoDescontoPedidoRequestDto(false, "Sem margem", null, null)
        );
        assertThat(negado.statusDesconto()).isEqualTo(StatusDescontoPedido.NEGADO);

        // Decidir quando não está mais pendente -> erro
        assertThatThrownBy(() -> service.decidirDescontoPendente(
                pedidoId, new DecisaoDescontoPedidoRequestDto(true, "Ok", null, null)
        )).isInstanceOf(PedidoEstadoInvalidoException.class);

        // Recolocar pendente e aprovar (primeiro simulando solicitado >= subtotal, depois válido)
        pedido.setStatusDesconto(StatusDescontoPedido.PENDENTE_AUTORIZACAO);
        pedido.setDescontoSolicitado(new BigDecimal("100.00"));
        assertThatThrownBy(() -> service.decidirDescontoPendente(
                pedidoId, new DecisaoDescontoPedidoRequestDto(true, "Ok", null, null)
        )).isInstanceOf(PedidoEstadoInvalidoException.class);

        pedido.setDescontoSolicitado(new BigDecimal("25.00"));
        PedidoCaixaDetalheResponseDto aprovado = service.decidirDescontoPendente(
                pedidoId, new DecisaoDescontoPedidoRequestDto(true, "Aprovado gerente", null, null)
        );
        assertThat(aprovado.statusDesconto()).isEqualTo(StatusDescontoPedido.APROVADO);
        assertThat(aprovado.total()).isEqualByComparingTo("75.00");

        // Acima do limite com autorizador imediato na própria requisição
        when(autorizadorOperacaoCaixaService.tentarResolverAutorizador(eq("CAIXA_AUTORIZAR_DESCONTO"), any(), any()))
                .thenReturn(Optional.of(new AutorizadorOperacaoCaixaService.AutorizadorResolvido(autorizadorId, "Gerente")));
        PedidoCaixaDetalheResponseDto imediato = service.aplicarOuSolicitarDesconto(
                pedidoId, new DescontoPedidoCaixaRequestDto(TipoDescontoPedido.VALOR, new BigDecimal("30.00"), "Gerente autorizou", "g@m.com", "123")
        );
        assertThat(imediato.statusDesconto()).isEqualTo(StatusDescontoPedido.APROVADO);
        assertThat(imediato.total()).isEqualByComparingTo("70.00");
    }

    @Test
    void atualizarPedidoRecalculaTotaisComDescontoEAuditaItensRemovidos() {
        UUID estabId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabId);
        when(contextoCaixa.usuarioId()).thenReturn(userId);

        PedidoEntity pedido = pedidoBase(estabId, userId, UUID.randomUUID(), "100.00");
        pedido.setId(pedidoId);
        pedido.setStatusDesconto(StatusDescontoPedido.APROVADO);
        pedido.setTipoDesconto(TipoDescontoPedido.VALOR);
        pedido.setValorDescontoInformado(new BigDecimal("80.00"));
        pedido.setDesconto(new BigDecimal("80.00"));
        pedido.setTotal(new BigDecimal("20.00"));

        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.saveAndFlush(any(PedidoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // Pagamento ativo impede edição
        PagamentoPedidoEntity pagPendente = new PagamentoPedidoEntity();
        pagPendente.setStatus(StatusPagamentoPedido.PENDENTE);
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of(pagPendente));
        assertThatThrownBy(() -> service.atualizarPedido(
                pedidoId, new PedidoCaixaAtualizacaoRequestDto(CanalPedidoCaixa.BALCAO, null, null, null)
        )).isInstanceOf(PedidoEstadoInvalidoException.class);

        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of());

        UUID produtoId = UUID.randomUUID();
        ItemPedidoEntity itemAnterior = item(1L, pedidoId, "100.00");
        itemAnterior.setProdutoId(produtoId);
        itemAnterior.setDescricaoProduto("Whisky");
        itemAnterior.setQuantidade(new BigDecimal("2"));
        itemAnterior.setPrecoUnitario(new BigDecimal("50.00"));
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of(itemAnterior));

        ProdutoEntity produto = new ProdutoEntity();
        produto.setId(produtoId);
        produto.setEstabelecimentoId(estabId);
        produto.setAtivo(true);
        produto.setNome("Whisky");
        produto.setPrecoVenda(new BigDecimal("50.00"));
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produto));
        when(movimentacaoEstoqueService.calcularCustoUnitario(produto)).thenReturn(new BigDecimal("30.00"));
        when(itemPedidoRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // Reduz para 1 unidade (50.00): desconto aprovado de 80.00 >= 50.00 deve ser limpo!
        PedidoCaixaDetalheResponseDto atualizado = service.atualizarPedido(
                pedidoId,
                new PedidoCaixaAtualizacaoRequestDto(
                        CanalPedidoCaixa.MESA,
                        "Mesa 02",
                        "Sem gelo",
                        List.of(new ItemPedidoCaixaRequestDto(produtoId, BigDecimal.ONE))
                )
        );
        assertThat(atualizado.subtotal()).isEqualByComparingTo("50.00");
        assertThat(atualizado.statusDesconto()).isEqualTo(StatusDescontoPedido.NENHUM);
        verify(auditoriaCaixaService).registrar(
                eq(estabId), eq(userId), any(), eq("ITEM_REMOVIDO"),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );

        // Lista vazia zera subtotal e total
        PedidoCaixaDetalheResponseDto zerado = service.atualizarPedido(
                pedidoId, new PedidoCaixaAtualizacaoRequestDto(null, null, null, List.of())
        );
        assertThat(zerado.subtotal()).isEqualByComparingTo("0.00");
        assertThat(zerado.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void cancelarConciliarERegistrarPagamentoComIdempotenciaCobreExcecoes() {
        UUID estabId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabId);
        when(contextoCaixa.usuarioId()).thenReturn(userId);

        PedidoEntity pedido = pedidoBase(estabId, userId, UUID.randomUUID(), "50.00");
        pedido.setId(pedidoId);
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));

        // Cancelar pedido com itens sem motivo
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of(item(1L, pedidoId, "50.00")));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of());
        assertThatThrownBy(() -> service.cancelarPedido(pedidoId, new CancelarPedidoCaixaRequestDto(" ", null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        // Conciliar pagamento em status inválido ou rejeição sem motivo
        PagamentoPedidoEntity pag = new PagamentoPedidoEntity();
        pag.setId(pagamentoId);
        pag.setPedidoId(pedidoId);
        pag.setForma(FormaPagamentoCaixa.PIX);
        pag.setStatus(StatusPagamentoPedido.APROVADO);
        pag.setValor(new BigDecimal("50.00"));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagamentoId, pedidoId)).thenReturn(Optional.of(pag));

        assertThatThrownBy(() -> service.conciliarPagamento(
                pedidoId, pagamentoId, new ConciliarPagamentoPedidoRequestDto(true, null, null)
        )).isInstanceOf(PedidoEstadoInvalidoException.class);

        pag.setStatus(StatusPagamentoPedido.PENDENTE_CONCILIACAO);
        assertThatThrownBy(() -> service.conciliarPagamento(
                pedidoId, pagamentoId, new ConciliarPagamentoPedidoRequestDto(false, " ", null)
        )).isInstanceOf(IllegalArgumentException.class);

        // Conciliar ACAO_NECESSARIA aprovando exige CAIXA_RESOLVER_DIVERGENCIA
        pag.setStatus(StatusPagamentoPedido.ACAO_NECESSARIA);
        when(autorizadorOperacaoCaixaService.exigirAutorizador(
                eq("CAIXA_RESOLVER_DIVERGENCIA"), any(), any(), any()
        )).thenReturn(new AutorizadorOperacaoCaixaService.AutorizadorResolvido(UUID.randomUUID(), "Gerente"));
        when(pagamentoPedidoRepository.saveAndFlush(any(PagamentoPedidoEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pedidoRepository.saveAndFlush(any(PedidoEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of(pag));
        PedidoCaixaDetalheResponseDto conciliado = service.conciliarPagamento(
                pedidoId, pagamentoId, new ConciliarPagamentoPedidoRequestDto(true, "Conferido", null, "g@m.com", "123")
        );
        assertThat(conciliado.status()).isEqualTo(StatusPedidoCaixa.CONCLUIDO);
        verify(auditoriaCaixaService).registrar(
                eq(estabId), eq(userId), any(), eq(EventoAuditoriaCaixa.CONCILIACAO_MANUAL),
                any(), any(), any(), any(), any(), eq(pedidoId), any(), eq(pagamentoId), any(), eq("Gerente"), any(), any()
        );

        // Registrar pagamento: Point habilitada redireciona para Orquestrador
        pedido.setStatus(StatusPedidoCaixa.EM_ATENDIMENTO);
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of());
        ConfiguracaoCaixaEntity configPoint = ConfiguracaoCaixaMapper.criarPadrao(estabId);
        configPoint.setPointHabilitado(true);
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabId)).thenReturn(Optional.of(configPoint));
        RegistrarPagamentoPedidoRequestDto reqPix = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.PIX, new BigDecimal("50.00"), 1, null, "idem-1", null
        );
        service.registrarPagamento(pedidoId, reqPix);
        verify(orquestradorPagamentoPointService).iniciarPagamentoPoint(pedidoId, reqPix);

        // Contingência manual + DataIntegrityViolationException recuperada em nova transação
        RegistrarPagamentoPedidoRequestDto reqDinheiro = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.DINHEIRO, new BigDecimal("50.00"), 1, new BigDecimal("50.00"), "idem-dup", null
        );
        SessaoCaixaEntity sessao = sessao(pedido.getSessaoCaixaId(), estabId, userId, StatusSessaoCaixa.ABERTA);
        when(sessaoCaixaRepository.findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
                estabId, userId, StatusSessaoCaixa.ABERTA
        )).thenReturn(Optional.of(sessao));
        when(processadorPagamento.suporta(eq(FormaPagamentoCaixa.DINHEIRO), any())).thenReturn(true);

        PagamentoPedidoEntity novoPag = new PagamentoPedidoEntity();
        novoPag.setId(UUID.randomUUID());
        novoPag.setPedidoId(pedidoId);
        novoPag.setSessaoCaixaId(sessao.getId());
        novoPag.setForma(FormaPagamentoCaixa.DINHEIRO);
        novoPag.setStatus(StatusPagamentoPedido.APROVADO);
        novoPag.setValor(new BigDecimal("50.00"));
        novoPag.setChaveIdempotencia("idem-dup");
        when(processadorPagamento.processar(any(), any(), any(), any())).thenReturn(novoPag);
        when(pagamentoPedidoRepository.saveAndFlush(novoPag)).thenThrow(new DataIntegrityViolationException("uq"));
        when(pagamentoPedidoRepository.findByPedidoIdAndChaveIdempotencia(pedidoId, "idem-dup"))
                .thenReturn(Optional.empty(), Optional.of(novoPag));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId))
                .thenReturn(List.of(), List.of(novoPag));

        PedidoCaixaDetalheResponseDto pago = service.registrarPagamento(pedidoId, reqDinheiro);
        assertThat(pago.pagamentos()).hasSize(1);
    }

    @Test
    void estornarPedidoEPagamentoExecutaSagaPointEProtegeGavetaDinheiro() {
        UUID estabId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID gerenteId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID sessaoId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabId);
        when(contextoCaixa.usuarioId()).thenReturn(userId);
        when(autorizadorOperacaoCaixaService.exigirAutorizador(eq("CAIXA_ESTORNAR"), any(), any(), any()))
                .thenReturn(new AutorizadorOperacaoCaixaService.AutorizadorResolvido(gerenteId, "Gerente"));

        PedidoEntity pedido = pedidoBase(estabId, userId, sessaoId, "100.00");
        pedido.setId(pedidoId);
        pedido.setStatus(StatusPedidoCaixa.CONCLUIDO);
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of());
        when(pedidoRepository.saveAndFlush(any(PedidoEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagamentoPedidoRepository.saveAndFlush(any(PagamentoPedidoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1) Estorno Point: falha não-transitória reverte ESTORNO_EM_ANDAMENTO -> APROVADO
        PagamentoPedidoEntity pagPoint = new PagamentoPedidoEntity();
        pagPoint.setId(UUID.randomUUID());
        pagPoint.setPedidoId(pedidoId);
        pagPoint.setSessaoCaixaId(sessaoId);
        pagPoint.setProvedor("MERCADO_PAGO");
        pagPoint.setOrderExternaId("ORD-EST-1");
        pagPoint.setForma(FormaPagamentoCaixa.PIX);
        pagPoint.setStatus(StatusPagamentoPedido.APROVADO);
        pagPoint.setValor(new BigDecimal("100.00"));
        pagPoint.setValorCobrado(new BigDecimal("100.00"));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of(pagPoint));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagPoint.getId(), pedidoId)).thenReturn(Optional.of(pagPoint));
        when(orquestradorPagamentoPointService.estornarOrderExterna("ORD-EST-1", pagPoint.getId()))
                .thenThrow(new IntegracaoMercadoPagoException("Erro 400", HttpStatus.BAD_REQUEST, false))
                .thenThrow(new IntegracaoMercadoPagoException("Timeout", HttpStatus.GATEWAY_TIMEOUT, true))
                .thenReturn(new OrderPointResultado(
                        "ORD-EST-1", "refunded", "refunded", "PAY-1", "refunded", "REF-1", new BigDecimal("100.00"), "{\"status\":\"refunded\"}"
                ));

        assertThatThrownBy(() -> service.estornarPedido(
                pedidoId, new EstornarPedidoCaixaRequestDto("Cliente desistiu", "g@m.com", "123")
        )).isInstanceOf(IntegracaoMercadoPagoException.class);
        assertThat(pagPoint.getStatus()).isEqualTo(StatusPagamentoPedido.APROVADO);

        // Falha transitória mantém ESTORNO_EM_ANDAMENTO para retry seguro
        assertThatThrownBy(() -> service.estornarPedido(
                pedidoId, new EstornarPedidoCaixaRequestDto("Cliente desistiu", "g@m.com", "123")
        )).isInstanceOf(IntegracaoMercadoPagoException.class);
        assertThat(pagPoint.getStatus()).isEqualTo(StatusPagamentoPedido.ESTORNO_EM_ANDAMENTO);

        // Retry conclui o estorno com sucesso
        PedidoCaixaDetalheResponseDto estornado = service.estornarPedido(
                pedidoId, new EstornarPedidoCaixaRequestDto("Cliente desistiu", "g@m.com", "123")
        );
        assertThat(estornado.status()).isEqualTo(StatusPedidoCaixa.ESTORNADO);
        assertThat(pagPoint.getStatus()).isEqualTo(StatusPagamentoPedido.ESTORNADO);

        // 2) Estorno individual em DINHEIRO: sessão aberta sem saldo -> 409; sessão fechada -> grava SANGRIA na nova sessão
        pedido.setStatus(StatusPedidoCaixa.PAGAMENTO_PARCIAL);
        PagamentoPedidoEntity pagDinheiro = new PagamentoPedidoEntity();
        pagDinheiro.setId(UUID.randomUUID());
        pagDinheiro.setPedidoId(pedidoId);
        pagDinheiro.setSessaoCaixaId(sessaoId);
        pagDinheiro.setProvedor("INTERNO");
        pagDinheiro.setForma(FormaPagamentoCaixa.DINHEIRO);
        pagDinheiro.setStatus(StatusPagamentoPedido.APROVADO);
        pagDinheiro.setValor(new BigDecimal("50.00"));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagDinheiro.getId(), pedidoId))
                .thenReturn(Optional.of(pagDinheiro));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId))
                .thenReturn(List.of(pagDinheiro));

        SessaoCaixaEntity sessaoAberta = sessao(sessaoId, estabId, userId, StatusSessaoCaixa.ABERTA);
        sessaoAberta.setFundoTroco(BigDecimal.ZERO);
        when(sessaoCaixaRepository.buscarParaAtualizacao(sessaoId)).thenReturn(Optional.of(sessaoAberta));
        when(pagamentoPedidoRepository.findBySessaoCaixaIdOrderByCriadoEmAsc(sessaoId)).thenReturn(List.of());
        when(movimentacaoCaixaRepository.findBySessaoCaixaIdOrderByOcorridoEmAsc(sessaoId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.estornarPagamento(
                pedidoId, pagDinheiro.getId(), new EstornarPedidoCaixaRequestDto("Erro troco", "g@m.com", "123")
        )).isInstanceOf(SaldoInsuficienteCaixaException.class);

        // Sessão original fechada, nova sessão aberta com fundo de troco de R$ 80 -> grava SANGRIA de R$ 50
        sessaoAberta.setStatus(StatusSessaoCaixa.FECHADA);
        UUID sessaoAtualId = UUID.randomUUID();
        SessaoCaixaEntity sessaoAtual = sessao(sessaoAtualId, estabId, userId, StatusSessaoCaixa.ABERTA);
        sessaoAtual.setFundoTroco(new BigDecimal("80.00"));
        when(sessaoCaixaRepository.findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
                estabId, userId, StatusSessaoCaixa.ABERTA
        )).thenReturn(Optional.of(sessaoAtual));
        when(sessaoCaixaRepository.buscarParaAtualizacao(sessaoAtualId)).thenReturn(Optional.of(sessaoAtual));
        when(pagamentoPedidoRepository.findBySessaoCaixaIdOrderByCriadoEmAsc(sessaoAtualId)).thenReturn(List.of());
        when(movimentacaoCaixaRepository.findBySessaoCaixaIdOrderByOcorridoEmAsc(sessaoAtualId)).thenReturn(List.of());

        PedidoCaixaDetalheResponseDto estornoIndividual = service.estornarPagamento(
                pedidoId, pagDinheiro.getId(), new EstornarPedidoCaixaRequestDto("Devolver ao cliente", "g@m.com", "123")
        );
        assertThat(estornoIndividual.status()).isEqualTo(StatusPedidoCaixa.EM_ATENDIMENTO);
        assertThat(pagDinheiro.getStatus()).isEqualTo(StatusPagamentoPedido.ESTORNADO);
        ArgumentCaptor<MovimentacaoCaixaEntity> sangriaCaptor = ArgumentCaptor.forClass(MovimentacaoCaixaEntity.class);
        verify(movimentacaoCaixaRepository).saveAndFlush(sangriaCaptor.capture());
        assertThat(sangriaCaptor.getValue().getTipo()).isEqualTo(TipoMovimentacaoCaixa.SANGRIA);
        assertThat(sangriaCaptor.getValue().getValor()).isEqualByComparingTo("50.00");
        verify(movimentacaoEstoqueService, times(1)).estornarEstoquePedido(any(), any(), any());
    }

    @Test
    void criarPedidoBuscarListarPagamentosERemoverDescontoCobremFluxosECasosDeBorda() {
        UUID estabId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID gerenteOutroId = UUID.randomUUID();
        UUID sessaoOpId = UUID.randomUUID();
        UUID sessaoOutroId = UUID.randomUUID();
        UUID produtoId = UUID.randomUUID();

        when(contextoCaixa.estabelecimentoId()).thenReturn(estabId);
        when(contextoCaixa.usuarioId()).thenReturn(userId);
        when(contextoCaixa.isGerente()).thenReturn(false);
        when(pedidoRepository.saveAndFlush(any(PedidoEntity.class))).thenAnswer(inv -> {
            PedidoEntity p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });
        when(itemPedidoRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sequenciaDocumentoRepository.proximoNumero(estabId, "PEDIDO")).thenReturn(15L);

        // Operador sem sessão aberta e sessaoCaixaId nulo -> SessaoCaixaEstadoInvalidoException
        when(sessaoCaixaRepository.findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
                estabId, userId, StatusSessaoCaixa.ABERTA
        )).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.criarPedido(null))
                .isInstanceOf(SessaoCaixaEstadoInvalidoException.class);

        // dto == null com sessão aberta -> cria pedido BALCAO vazio
        SessaoCaixaEntity sessaoOp = sessao(sessaoOpId, estabId, userId, StatusSessaoCaixa.ABERTA);
        when(sessaoCaixaRepository.findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
                estabId, userId, StatusSessaoCaixa.ABERTA
        )).thenReturn(Optional.of(sessaoOp));
        PedidoCaixaDetalheResponseDto balcaoVazio = service.criarPedido(null);
        assertThat(balcaoVazio.canal()).isEqualTo(CanalPedidoCaixa.BALCAO);
        assertThat(balcaoVazio.total()).isEqualByComparingTo("0.00");

        // Sessão explícita fechada e de outro operador sem ser gerente
        SessaoCaixaEntity sessaoOutro = sessao(sessaoOutroId, estabId, gerenteOutroId, StatusSessaoCaixa.FECHADA);
        when(sessaoCaixaRepository.findByIdAndEstabelecimentoId(sessaoOutroId, estabId))
                .thenReturn(Optional.of(sessaoOutro));
        assertThatThrownBy(() -> service.criarPedido(
                new PedidoCaixaRequestDto(sessaoOutroId, CanalPedidoCaixa.BALCAO, null, null, List.of())
        )).isInstanceOf(SessaoCaixaEstadoInvalidoException.class);

        sessaoOutro.setStatus(StatusSessaoCaixa.ABERTA);
        assertThatThrownBy(() -> service.criarPedido(
                new PedidoCaixaRequestDto(sessaoOutroId, CanalPedidoCaixa.BALCAO, null, null, List.of())
        )).isInstanceOf(AccessDeniedException.class);

        // Produto de outro estabelecimento e produto inativo
        when(contextoCaixa.isGerente()).thenReturn(true);
        valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity cat =
                new valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity();
        cat.setId(7);
        cat.setNome("Destilados");

        ProdutoEntity prod = new ProdutoEntity();
        prod.setId(produtoId);
        prod.setEstabelecimentoId(UUID.randomUUID());
        prod.setNome("Gin Importado");
        prod.setPrecoVenda(new BigDecimal("25.00"));
        prod.setAtivo(true);
        prod.setCategoria(cat);
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(prod));

        List<ItemPedidoCaixaRequestDto> doisItensMesmoProduto = List.of(
                new ItemPedidoCaixaRequestDto(produtoId, BigDecimal.ONE),
                new ItemPedidoCaixaRequestDto(produtoId, new BigDecimal("2"))
        );
        assertThatThrownBy(() -> service.criarPedido(
                new PedidoCaixaRequestDto(sessaoOutroId, CanalPedidoCaixa.MESA, "Mesa 05", "Obs", doisItensMesmoProduto)
        )).isInstanceOf(AccessDeniedException.class);

        prod.setEstabelecimentoId(estabId);
        prod.setAtivo(false);
        assertThatThrownBy(() -> service.criarPedido(
                new PedidoCaixaRequestDto(sessaoOutroId, CanalPedidoCaixa.MESA, "Mesa 05", "Obs", doisItensMesmoProduto)
        )).isInstanceOf(IllegalArgumentException.class);

        // Fluxo feliz MESA com merge de quantidades do mesmo produto e categoria populada
        prod.setAtivo(true);
        when(movimentacaoEstoqueService.calcularCustoUnitario(prod)).thenReturn(new BigDecimal("12.00"));
        PedidoCaixaDetalheResponseDto pedidoMesa = service.criarPedido(
                new PedidoCaixaRequestDto(sessaoOutroId, CanalPedidoCaixa.MESA, "Mesa 05", "Gelado", doisItensMesmoProduto)
        );
        assertThat(pedidoMesa.canal()).isEqualTo(CanalPedidoCaixa.MESA);
        assertThat(pedidoMesa.subtotal()).isEqualByComparingTo("75.00");
        assertThat(pedidoMesa.itens()).hasSize(1);
        assertThat(pedidoMesa.itens().getFirst().quantidade()).isEqualByComparingTo("3.000");

        // buscarPedido cross-tenant e feliz + listarPagamentosDoPedido
        PedidoEntity outroTenant = pedidoBase(UUID.randomUUID(), userId, sessaoOpId, "30.00");
        when(pedidoRepository.findById(outroTenant.getId())).thenReturn(Optional.of(outroTenant));
        assertThatThrownBy(() -> service.buscarPedido(outroTenant.getId()))
                .isInstanceOf(AccessDeniedException.class);

        PedidoEntity pExistente = pedidoBase(estabId, userId, sessaoOpId, "100.00");
        when(pedidoRepository.findById(pExistente.getId())).thenReturn(Optional.of(pExistente));
        when(pedidoRepository.buscarPorIdParaAtualizacao(pExistente.getId())).thenReturn(Optional.of(pExistente));
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pExistente.getId())).thenReturn(List.of());
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pExistente.getId())).thenReturn(List.of());

        assertThat(service.buscarPedido(pExistente.getId()).id()).isEqualTo(pExistente.getId());
        assertThat(service.listarPagamentosDoPedido(pExistente.getId())).isEmpty();

        // removerDesconto: NENHUM (não salva), PENDENTE_AUTORIZACAO e APROVADO
        org.mockito.Mockito.clearInvocations(pedidoRepository);
        PedidoCaixaDetalheResponseDto semDesc = service.removerDesconto(pExistente.getId());
        assertThat(semDesc.total()).isEqualByComparingTo("100.00");
        verify(pedidoRepository, never()).saveAndFlush(any(PedidoEntity.class));

        pExistente.setStatusDesconto(StatusDescontoPedido.PENDENTE_AUTORIZACAO);
        pExistente.setDesconto(BigDecimal.ZERO);
        pExistente.setDescontoSolicitado(new BigDecimal("15.00"));
        PedidoCaixaDetalheResponseDto pendDescRemovido = service.removerDesconto(pExistente.getId());
        assertThat(pendDescRemovido.statusDesconto()).isEqualTo(StatusDescontoPedido.NENHUM);

        pExistente.setStatusDesconto(StatusDescontoPedido.APROVADO);
        pExistente.setDesconto(new BigDecimal("10.00"));
        pExistente.setTotal(new BigDecimal("90.00"));
        PedidoCaixaDetalheResponseDto aprovDescRemovido = service.removerDesconto(pExistente.getId());
        assertThat(aprovDescRemovido.total()).isEqualByComparingTo("100.00");
        assertThat(aprovDescRemovido.desconto()).isEqualByComparingTo("0.00");
    }

    @Test
    void cancelarRegistrarPagamentoConciliarEEstornoEmDinheiroCobremRamosRestantes() {
        UUID estabId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID gerenteId = UUID.randomUUID();
        UUID sessaoId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabId);
        when(contextoCaixa.usuarioId()).thenReturn(userId);
        when(pedidoRepository.saveAndFlush(any(PedidoEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagamentoPedidoRepository.saveAndFlush(any(PagamentoPedidoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // 1) cancelarPedido: CONCLUIDO, com pagamento ativo e vazio com dto == null
        PedidoEntity pedido = pedidoBase(estabId, userId, sessaoId, "50.00");
        pedido.setId(pedidoId);
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));

        pedido.setStatus(StatusPedidoCaixa.CONCLUIDO);
        assertThatThrownBy(() -> service.cancelarPedido(pedidoId, null))
                .isInstanceOf(PedidoEstadoInvalidoException.class);

        pedido.setStatus(StatusPedidoCaixa.EM_ATENDIMENTO);
        PagamentoPedidoEntity pagAtivo = new PagamentoPedidoEntity();
        pagAtivo.setId(UUID.randomUUID());
        pagAtivo.setPedidoId(pedidoId);
        pagAtivo.setForma(FormaPagamentoCaixa.DINHEIRO);
        pagAtivo.setStatus(StatusPagamentoPedido.APROVADO);
        pagAtivo.setValor(new BigDecimal("50.00"));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of(pagAtivo));
        assertThatThrownBy(() -> service.cancelarPedido(pedidoId, null))
                .isInstanceOf(PedidoEstadoInvalidoException.class);

        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of());
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of());
        PedidoCaixaDetalheResponseDto descarteVazio = service.cancelarPedido(pedidoId, null);
        assertThat(descarteVazio.status()).isEqualTo(StatusPedidoCaixa.CANCELADO);
        assertThat(pedido.getMotivoCancelamento()).isEqualTo("Descarte de pedido vazio");

        // 2) registrarPagamento: pedido sem itens, valor > saldo, idempotência interna, sem processador,
        //    DataIntegrityViolationException não recuperada e contingenciaManual=true bypassando Point
        pedido.setStatus(StatusPedidoCaixa.EM_ATENDIMENTO);
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabId)).thenReturn(Optional.empty());
        RegistrarPagamentoPedidoRequestDto reqDinheiro50 = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.DINHEIRO, new BigDecimal("50.00"), 1, new BigDecimal("50.00"), "IDEM-1", null
        );
        assertThatThrownBy(() -> service.registrarPagamento(pedidoId, reqDinheiro50))
                .isInstanceOf(PedidoEstadoInvalidoException.class);

        ItemPedidoEntity item50 = item(1L, pedidoId, "50.00");
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of(item50));
        SessaoCaixaEntity sessaoAberta = sessao(sessaoId, estabId, userId, StatusSessaoCaixa.ABERTA);
        when(sessaoCaixaRepository.findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
                estabId, userId, StatusSessaoCaixa.ABERTA
        )).thenReturn(Optional.of(sessaoAberta));

        RegistrarPagamentoPedidoRequestDto reqExcedente = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.DINHEIRO, new BigDecimal("60.00"), 1, new BigDecimal("60.00"), null, null
        );
        assertThatThrownBy(() -> service.registrarPagamento(pedidoId, reqExcedente))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("excede o saldo em aberto");

        // Idempotência encontrada ainda dentro da mesma transação
        when(pagamentoPedidoRepository.findByPedidoIdAndChaveIdempotencia(pedidoId, "IDEM-1"))
                .thenReturn(Optional.of(pagAtivo));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of(pagAtivo));
        PedidoCaixaDetalheResponseDto idempInterna = service.registrarPagamento(pedidoId, reqDinheiro50);
        assertThat(idempInterna.pagamentos()).hasSize(1);
        verify(processadorPagamento, never()).processar(any(), any(), any(), any());

        // Sem processador disponível
        when(pagamentoPedidoRepository.findByPedidoIdAndChaveIdempotencia(pedidoId, "IDEM-SEM-PROC"))
                .thenReturn(Optional.empty());
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of());
        when(processadorPagamento.suporta(any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.registrarPagamento(
                pedidoId, new RegistrarPagamentoPedidoRequestDto(
                        FormaPagamentoCaixa.PIX, new BigDecimal("50.00"), 1, null, "IDEM-SEM-PROC", null
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nenhum processador disponível");

        // DataIntegrityViolationException sem chave de idempotência ou não recuperável -> repropaga
        when(processadorPagamento.suporta(eq(FormaPagamentoCaixa.DINHEIRO), any())).thenReturn(true);
        PagamentoPedidoEntity pagFailSave = new PagamentoPedidoEntity();
        pagFailSave.setId(UUID.randomUUID());
        pagFailSave.setPedidoId(pedidoId);
        pagFailSave.setSessaoCaixaId(sessaoId);
        pagFailSave.setForma(FormaPagamentoCaixa.DINHEIRO);
        pagFailSave.setStatus(StatusPagamentoPedido.APROVADO);
        pagFailSave.setValor(new BigDecimal("50.00"));
        when(processadorPagamento.processar(any(), any(), any(), any())).thenReturn(pagFailSave);
        when(pagamentoPedidoRepository.saveAndFlush(pagFailSave))
                .thenThrow(new DataIntegrityViolationException("constraint"));
        when(pagamentoPedidoRepository.findByPedidoIdAndChaveIdempotencia(pedidoId, "IDEM-NAO-REC"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarPagamento(
                pedidoId, new RegistrarPagamentoPedidoRequestDto(
                        FormaPagamentoCaixa.DINHEIRO, new BigDecimal("50.00"), 1, new BigDecimal("50.00"), "IDEM-NAO-REC", null
                )
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> service.registrarPagamento(
                pedidoId, new RegistrarPagamentoPedidoRequestDto(
                        FormaPagamentoCaixa.DINHEIRO, new BigDecimal("50.00"), 1, new BigDecimal("50.00"), null, null
                )
        )).isInstanceOf(DataIntegrityViolationException.class);

        // contingenciaManual = true com pointHabilitado = true -> executa fluxo síncrono sem Orquestrador
        ConfiguracaoCaixaEntity configPoint = ConfiguracaoCaixaMapper.criarPadrao(estabId);
        configPoint.setPointHabilitado(true);
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabId)).thenReturn(Optional.of(configPoint));
        when(processadorPagamento.suporta(eq(FormaPagamentoCaixa.PIX), any())).thenReturn(true);
        PagamentoPedidoEntity pagManual = new PagamentoPedidoEntity();
        pagManual.setId(UUID.randomUUID());
        pagManual.setPedidoId(pedidoId);
        pagManual.setSessaoCaixaId(sessaoId);
        pagManual.setProvedor("MANUAL");
        pagManual.setForma(FormaPagamentoCaixa.PIX);
        pagManual.setStatus(StatusPagamentoPedido.APROVADO);
        pagManual.setValor(new BigDecimal("50.00"));
        when(processadorPagamento.processar(any(), any(), any(), any())).thenReturn(pagManual);

        RegistrarPagamentoPedidoRequestDto reqContingencia = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.PIX, new BigDecimal("50.00"), 1, null, null, "NSU-1", null, true
        );
        PedidoCaixaDetalheResponseDto contResp = service.registrarPagamento(pedidoId, reqContingencia);
        assertThat(contResp.status()).isEqualTo(StatusPedidoCaixa.CONCLUIDO);
        verify(orquestradorPagamentoPointService, never()).iniciarPagamentoPoint(any(), any());

        // 3) conciliarPagamento rejeitando MERCADO_PAGO ACAO_NECESSARIA: sem outro Point vs com outro Point ativo
        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        UUID pagPointRejId = UUID.randomUUID();
        PagamentoPedidoEntity pagPointRej = new PagamentoPedidoEntity();
        pagPointRej.setId(pagPointRejId);
        pagPointRej.setPedidoId(pedidoId);
        pagPointRej.setSessaoCaixaId(sessaoId);
        pagPointRej.setProvedor("MERCADO_PAGO");
        pagPointRej.setForma(FormaPagamentoCaixa.PIX);
        pagPointRej.setStatus(StatusPagamentoPedido.ACAO_NECESSARIA);
        pagPointRej.setValor(new BigDecimal("50.00"));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagPointRejId, pedidoId)).thenReturn(Optional.of(pagPointRej));

        PagamentoPedidoEntity outroPointAtivo = new PagamentoPedidoEntity();
        outroPointAtivo.setId(UUID.randomUUID());
        outroPointAtivo.setPedidoId(pedidoId);
        outroPointAtivo.setProvedor("MERCADO_PAGO");
        outroPointAtivo.setForma(FormaPagamentoCaixa.CREDITO);
        outroPointAtivo.setStatus(StatusPagamentoPedido.EM_TERMINAL);
        outroPointAtivo.setValor(new BigDecimal("50.00"));

        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId))
                .thenReturn(List.of(pagPointRej, outroPointAtivo));
        service.conciliarPagamento(
                pedidoId, pagPointRejId, new ConciliarPagamentoPedidoRequestDto(false, "Cliente desistiu na tela", null)
        );
        verify(reservaEstoqueService, never()).liberarReservasAtivasDoPedido(any(), any(), any());

        pagPointRej.setStatus(StatusPagamentoPedido.ACAO_NECESSARIA);
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of(pagPointRej));
        service.conciliarPagamento(
                pedidoId, pagPointRejId, new ConciliarPagamentoPedidoRequestDto(false, "Sem outro Point ativo", null)
        );
        verify(reservaEstoqueService, times(1)).liberarReservasAtivasDoPedido(
                eq(pedidoId), eq(valhalla.core.stock.app.modules.caixa.pedido.enums.StatusReservaEstoque.LIBERADA), eq("Sem outro Point ativo")
        );

        // 4) resolverSessaoAbertaParaEstornoEmDinheiro: fallback para sessão aberta do autorizador e erro sem sessão aberta
        pedido.setStatus(StatusPedidoCaixa.PAGAMENTO_PARCIAL);
        UUID pagDinheiroId = UUID.randomUUID();
        PagamentoPedidoEntity pagDinheiro = new PagamentoPedidoEntity();
        pagDinheiro.setId(pagDinheiroId);
        pagDinheiro.setPedidoId(pedidoId);
        pagDinheiro.setSessaoCaixaId(sessaoId);
        pagDinheiro.setProvedor("INTERNO");
        pagDinheiro.setForma(FormaPagamentoCaixa.DINHEIRO);
        pagDinheiro.setStatus(StatusPagamentoPedido.APROVADO);
        pagDinheiro.setValor(new BigDecimal("20.00"));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagDinheiroId, pedidoId)).thenReturn(Optional.of(pagDinheiro));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of(pagDinheiro));
        when(autorizadorOperacaoCaixaService.exigirAutorizador(eq("CAIXA_ESTORNAR"), any(), any(), any()))
                .thenReturn(new AutorizadorOperacaoCaixaService.AutorizadorResolvido(gerenteId, "Gerente"));

        SessaoCaixaEntity sessaoOrigemFechada = sessao(sessaoId, estabId, userId, StatusSessaoCaixa.FECHADA);
        when(sessaoCaixaRepository.buscarParaAtualizacao(sessaoId)).thenReturn(Optional.of(sessaoOrigemFechada));
        when(sessaoCaixaRepository.findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
                estabId, userId, StatusSessaoCaixa.ABERTA
        )).thenReturn(Optional.empty());

        // Nem operador nem autorizador têm sessão aberta -> SessaoCaixaEstadoInvalidoException
        when(sessaoCaixaRepository.findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
                estabId, gerenteId, StatusSessaoCaixa.ABERTA
        )).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.estornarPagamento(
                pedidoId, pagDinheiroId, new EstornarPedidoCaixaRequestDto("Sem gaveta", "g@m.com", "123")
        )).isInstanceOf(SessaoCaixaEstadoInvalidoException.class);

        // Autorizador tem sessão aberta com saldo -> grava SANGRIA na sessão do autorizador
        UUID sessaoGerenteId = UUID.randomUUID();
        SessaoCaixaEntity sessaoGerente = sessao(sessaoGerenteId, estabId, gerenteId, StatusSessaoCaixa.ABERTA);
        sessaoGerente.setFundoTroco(new BigDecimal("100.00"));
        when(sessaoCaixaRepository.findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
                estabId, gerenteId, StatusSessaoCaixa.ABERTA
        )).thenReturn(Optional.of(sessaoGerente));
        when(sessaoCaixaRepository.buscarParaAtualizacao(sessaoGerenteId)).thenReturn(Optional.of(sessaoGerente));
        when(pagamentoPedidoRepository.findBySessaoCaixaIdOrderByCriadoEmAsc(sessaoGerenteId)).thenReturn(List.of());
        when(movimentacaoCaixaRepository.findBySessaoCaixaIdOrderByOcorridoEmAsc(sessaoGerenteId)).thenReturn(List.of());

        PedidoCaixaDetalheResponseDto estornoGerente = service.estornarPagamento(
                pedidoId, pagDinheiroId, new EstornarPedidoCaixaRequestDto("Devolução autorizador", "g@m.com", "123")
        );
        assertThat(estornoGerente.status()).isEqualTo(StatusPedidoCaixa.EM_ATENDIMENTO);
        ArgumentCaptor<MovimentacaoCaixaEntity> sangriaCaptor = ArgumentCaptor.forClass(MovimentacaoCaixaEntity.class);
        verify(movimentacaoCaixaRepository).saveAndFlush(sangriaCaptor.capture());
        assertThat(sangriaCaptor.getValue().getSessaoCaixaId()).isEqualTo(sessaoGerenteId);
        assertThat(sangriaCaptor.getValue().getTipo()).isEqualTo(TipoMovimentacaoCaixa.SANGRIA);
    }

    private PedidoEntity pedidoBase(UUID estabId, UUID userId, UUID sessaoId, String valor) {
        PedidoEntity p = new PedidoEntity();
        p.setId(UUID.randomUUID());
        p.setEstabelecimentoId(estabId);
        p.setOperadorUsuarioId(userId);
        p.setSessaoCaixaId(sessaoId);
        p.setNumero(1L);
        p.setStatus(StatusPedidoCaixa.EM_ATENDIMENTO);
        p.setCanal(CanalPedidoCaixa.BALCAO);
        p.setStatusDesconto(StatusDescontoPedido.NENHUM);
        p.setSubtotal(new BigDecimal(valor));
        p.setDesconto(BigDecimal.ZERO);
        p.setTotal(new BigDecimal(valor));
        return p;
    }

    private SessaoCaixaEntity sessao(UUID id, UUID estabId, UUID userId, StatusSessaoCaixa status) {
        SessaoCaixaEntity s = new SessaoCaixaEntity();
        s.setId(id);
        s.setEstabelecimentoId(estabId);
        s.setOperadorUsuarioId(userId);
        s.setStatus(status);
        return s;
    }

    private ItemPedidoEntity item(Long id, UUID pedidoId, String subtotal) {
        ItemPedidoEntity i = new ItemPedidoEntity();
        i.setId(id);
        i.setPedidoId(pedidoId);
        i.setProdutoId(UUID.randomUUID());
        i.setDescricaoProduto("Produto");
        i.setQuantidade(BigDecimal.ONE);
        i.setPrecoUnitario(new BigDecimal(subtotal));
        i.setSubtotal(new BigDecimal(subtotal));
        return i;
    }
}
