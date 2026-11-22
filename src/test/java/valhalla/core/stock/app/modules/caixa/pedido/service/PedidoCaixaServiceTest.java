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
import valhalla.core.stock.app.modules.caixa.exception.PedidoEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.SessaoCaixaEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.fiscal.service.NotaFiscalCaixaService;
import valhalla.core.stock.app.modules.caixa.pedido.dto.CancelarPedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.ConciliarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.DecisaoDescontoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.DescontoPedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.ItemPedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaDetalheResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaResumoResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.RegistrarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.SequenciaDocumentoEntity;
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
import valhalla.core.stock.app.modules.caixa.point.service.OrquestradorPagamentoPointService;
import valhalla.core.stock.app.modules.caixa.security.AutorizadorOperacaoCaixaService;
import valhalla.core.stock.app.modules.caixa.security.ContextoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.sessao.enums.StatusSessaoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.repository.SessaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.sessao.service.AuditoriaCaixaService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoCaixaServiceTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private ItemPedidoRepository itemPedidoRepository;
    @Mock private PagamentoPedidoRepository pagamentoPedidoRepository;
    @Mock private SequenciaDocumentoRepository sequenciaDocumentoRepository;
    @Mock private SessaoCaixaRepository sessaoCaixaRepository;
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
        };
        service = new PedidoCaixaService(
                pedidoRepository,
                itemPedidoRepository,
                pagamentoPedidoRepository,
                sequenciaDocumentoRepository,
                sessaoCaixaRepository,
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
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.buscarParaAtualizacao(pedidoId, estabId)).thenReturn(Optional.of(pedido));
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

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.buscarParaAtualizacao(pedidoId, estabId)).thenReturn(Optional.of(pedido));
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
        when(pedidoRepository.buscarParaAtualizacao(pedidoId, estabId)).thenReturn(Optional.of(pedido));

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

        // Registrar pagamento: Point habilitada redireciona para Orquestrador
        ConfiguracaoCaixaEntity configPoint = ConfiguracaoCaixaMapper.criarPadrao(estabId);
        configPoint.setPointHabilitado(true);
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabId)).thenReturn(Optional.of(configPoint));
        RegistrarPagamentoPedidoRequestDto reqPix = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.PIX, new BigDecimal("50.00"), 1, null, "idem-1", null
        );
        service.registrarPagamento(pedidoId, reqPix);
        verify(orquestradorPagamentoPointService).iniciarPagamentoPoint(pedidoId, reqPix);

        // Contingência manual + DataIntegrityViolationException na idempotência
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
        when(pedidoRepository.saveAndFlush(any(PedidoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PedidoCaixaDetalheResponseDto pago = service.registrarPagamento(pedidoId, reqDinheiro);
        assertThat(pago.status()).isEqualTo(StatusPedidoCaixa.CONCLUIDO);
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
