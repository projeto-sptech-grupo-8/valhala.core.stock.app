package valhalla.core.stock.app.modules.caixa.fiscal.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import valhalla.core.stock.app.modules.caixa.exception.NotaFiscalEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.NotaFiscalJaEmitidaException;
import valhalla.core.stock.app.modules.caixa.exception.PedidoEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.CancelarNotaFiscalRequestDto;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.EmitirNfceRequestDto;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.NotaFiscalDetalheResponseDto;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.NotaFiscalResumoResponseDto;
import valhalla.core.stock.app.modules.caixa.fiscal.entity.ItemNotaFiscalEntity;
import valhalla.core.stock.app.modules.caixa.fiscal.entity.NotaFiscalEntity;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.AmbienteFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.StatusNotaFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.TipoNotaFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.repository.ItemNotaFiscalRepository;
import valhalla.core.stock.app.modules.caixa.fiscal.repository.NotaFiscalRepository;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal.ItemEmissaoFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal.ItemTributadoFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal.ResultadoCancelamentoFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal.ResultadoEmissaoFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal.SolicitacaoEmissaoFiscal;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaResumoResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.CanalPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusDescontoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ItemPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.SequenciaDocumentoRepository;
import valhalla.core.stock.app.modules.caixa.security.AutorizadorOperacaoCaixaService;
import valhalla.core.stock.app.modules.caixa.security.ContextoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.service.AuditoriaCaixaService;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.establishments.repository.EstablishmentRepository;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotaFiscalCaixaServiceTest {

    @Mock private NotaFiscalRepository notaFiscalRepository;
    @Mock private ItemNotaFiscalRepository itemNotaFiscalRepository;
    @Mock private PedidoRepository pedidoRepository;
    @Mock private ItemPedidoRepository itemPedidoRepository;
    @Mock private PagamentoPedidoRepository pagamentoPedidoRepository;
    @Mock private SequenciaDocumentoRepository sequenciaDocumentoRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private EstablishmentRepository establishmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProvedorEmissaoFiscal provedorEmissaoFiscal;
    @Mock private AutorizadorOperacaoCaixaService autorizadorOperacaoCaixaService;
    @Mock private AuditoriaCaixaService auditoriaCaixaService;
    @Mock private ContextoCaixa contextoCaixa;
    @Mock private BusinessEventLogger businessEventLogger;

    @InjectMocks
    private NotaFiscalCaixaService service;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listarNotasFiscaisEElegiveisValidaDatasEAvaliaEspecificacoes() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabelecimentoId);

        assertThatThrownBy(() -> service.listarNotasFiscais(
                null, null, null, null,
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 5),
                0, 20, "emitidaEm", DirecaoOrdenacao.DESC
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A data inicial não pode ser posterior à data final");

        assertThatThrownBy(() -> service.listarPedidosElegiveisParaEmissao(
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 5),
                0, 20, "concluidoEm", DirecaoOrdenacao.DESC
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A data inicial não pode ser posterior à data final");

        NotaFiscalEntity nota = nota(estabelecimentoId, pedidoId, StatusNotaFiscal.AUTORIZADA);
        PedidoEntity pedido = pedidoConcluido(estabelecimentoId, pedidoId);

        when(notaFiscalRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(nota), PageRequest.of(0, 20), 1));
        when(pedidoRepository.findAllById(any())).thenReturn(List.of(pedido));

        RespostaPaginadaDto<NotaFiscalResumoResponseDto> notas = service.listarNotasFiscais(
                StatusNotaFiscal.AUTORIZADA, pedidoId, "123.456.789-01", "0001",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20),
                0, 20, "emitidaEm", DirecaoOrdenacao.DESC
        );
        assertThat(notas.itens()).hasSize(1);
        assertThat(notas.itens().getFirst().pedidoNumero()).isEqualTo(10L);

        when(pedidoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pedido), PageRequest.of(0, 20), 1));
        when(itemPedidoRepository.findByPedidoIdInOrderByIdAsc(List.of(pedidoId))).thenReturn(List.of());
        when(pagamentoPedidoRepository.findByPedidoIdInOrderByCriadoEmAsc(List.of(pedidoId))).thenReturn(List.of());
        when(notaFiscalRepository.findByPedidoIdInOrderByCriadaEmDesc(List.of(pedidoId))).thenReturn(List.of());

        RespostaPaginadaDto<PedidoCaixaResumoResponseDto> elegiveis = service.listarPedidosElegiveisParaEmissao(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20),
                0, 20, "concluidoEm", DirecaoOrdenacao.DESC
        );
        assertThat(elegiveis.itens()).hasSize(1);

        ArgumentCaptor<Specification<NotaFiscalEntity>> notaSpecCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(notaFiscalRepository).findAll(notaSpecCaptor.capture(), any(Pageable.class));

        ArgumentCaptor<Specification<PedidoEntity>> pedidoSpecCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(pedidoRepository).findAll(pedidoSpecCaptor.capture(), any(Pageable.class));

        Predicate predicate = mock(Predicate.class);
        Path path = mock(Path.class, inv -> Path.class.isAssignableFrom(inv.getMethod().getReturnType()) ? inv.getMock() : null);
        Root root = mock(Root.class, inv -> Path.class.isAssignableFrom(inv.getMethod().getReturnType()) ? path : null);
        Subquery subquery = mock(Subquery.class, inv -> {
            Class<?> rt = inv.getMethod().getReturnType();
            if (Root.class.isAssignableFrom(rt)) return root;
            if (Subquery.class.isAssignableFrom(rt)) return inv.getMock();
            return null;
        });
        CriteriaQuery query = mock(CriteriaQuery.class, inv ->
                Subquery.class.isAssignableFrom(inv.getMethod().getReturnType()) ? subquery : null);
        CriteriaBuilder builder = mock(CriteriaBuilder.class, inv ->
                Predicate.class.isAssignableFrom(inv.getMethod().getReturnType()) ? predicate : null);

        assertThat(notaSpecCaptor.getValue().toPredicate(root, query, builder)).isNotNull();
        assertThat(pedidoSpecCaptor.getValue().toPredicate(root, query, builder)).isNotNull();
    }

    @Test
    void emitirNfceRateiaDescontoProporcionalmenteEValidaPreCondicoes() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabelecimentoId);
        when(contextoCaixa.usuarioId()).thenReturn(usuarioId);

        PedidoEntity emAtendimento = pedidoConcluido(estabelecimentoId, pedidoId);
        emAtendimento.setStatus(StatusPedidoCaixa.EM_ATENDIMENTO);
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(emAtendimento));

        assertThatThrownBy(() -> service.emitirNfce(new EmitirNfceRequestDto(pedidoId, null, null)))
                .isInstanceOf(PedidoEstadoInvalidoException.class);

        PedidoEntity concluido = pedidoConcluido(estabelecimentoId, pedidoId);
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(concluido));
        when(notaFiscalRepository.existsByPedidoIdAndStatus(pedidoId, StatusNotaFiscal.AUTORIZADA))
                .thenReturn(true);

        assertThatThrownBy(() -> service.emitirNfce(new EmitirNfceRequestDto(pedidoId, null, null)))
                .isInstanceOf(NotaFiscalJaEmitidaException.class);

        when(notaFiscalRepository.existsByPedidoIdAndStatus(pedidoId, StatusNotaFiscal.AUTORIZADA))
                .thenReturn(false);

        ItemPedidoEntity item1 = new ItemPedidoEntity();
        item1.setId(1L);
        item1.setProdutoId(UUID.randomUUID());
        item1.setDescricaoProduto("Item 1");
        item1.setQuantidade(BigDecimal.ONE);
        item1.setPrecoUnitario(new BigDecimal("10.00"));
        item1.setSubtotal(new BigDecimal("10.00"));

        ItemPedidoEntity item2 = new ItemPedidoEntity();
        item2.setId(2L);
        item2.setProdutoId(UUID.randomUUID());
        item2.setDescricaoProduto("Item 2");
        item2.setQuantidade(BigDecimal.ONE);
        item2.setPrecoUnitario(new BigDecimal("20.00"));
        item2.setSubtotal(new BigDecimal("20.00"));

        concluido.setSubtotal(new BigDecimal("30.00"));
        concluido.setDesconto(new BigDecimal("10.00"));
        concluido.setTotal(new BigDecimal("20.00"));

        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of(item1, item2));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.emitirNfce(new EmitirNfceRequestDto(pedidoId, "123", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CPF (11 dígitos) ou CNPJ (14 dígitos)");

        assertThatThrownBy(() -> service.emitirNfce(new EmitirNfceRequestDto(pedidoId, "111.111.111-11", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inválido");

        EstablishmentEntity estab = EstablishmentEntity.builder()
                .id(estabelecimentoId)
                .corporateName("Adega Meraki LTDA")
                .tradeName("Adega Meraki")
                .cnpj("12345678000199")
                .build();
        when(establishmentRepository.findById(estabelecimentoId)).thenReturn(Optional.of(estab));

        ProdutoEntity prod1 = new ProdutoEntity();
        prod1.setId(item1.getProdutoId());
        prod1.setTipo(ProdutoTipo.PADRAO);
        prod1.setSku("SKU-1");
        prod1.setUnidadeMedida("un");
        when(produtoRepository.findAllById(any())).thenReturn(List.of(prod1));

        when(sequenciaDocumentoRepository.proximoNumero(estabelecimentoId, "NFCE"))
                .thenReturn(5L);

        when(provedorEmissaoFiscal.emitirNfce(any(SolicitacaoEmissaoFiscal.class)))
                .thenAnswer(inv -> {
                    SolicitacaoEmissaoFiscal sol = inv.getArgument(0);
                    List<ItemEmissaoFiscal> itensSol = sol.itens();
                    assertThat(itensSol).hasSize(2);
                    BigDecimal somaDescontos = itensSol.stream()
                            .map(ItemEmissaoFiscal::valorDesconto)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    assertThat(somaDescontos).isEqualByComparingTo("10.00");
                    return new ResultadoEmissaoFiscal(
                            "INTERNO",
                            AmbienteFiscal.HOMOLOGACAO,
                            StatusNotaFiscal.AUTORIZADA,
                            "5",
                            "1",
                            "4".repeat(44),
                            "PROT-5",
                            "https://nfce.test/5",
                            JsonNodeFactory.instance.objectNode(),
                            "<nfe>ok</nfe>",
                            List.of(new ItemTributadoFiscal(1L, "5102", "2202", "102", "0", JsonNodeFactory.instance.objectNode()))
                    );
                });

        when(notaFiscalRepository.saveAndFlush(any(NotaFiscalEntity.class))).thenAnswer(inv -> {
            NotaFiscalEntity n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });
        when(itemNotaFiscalRepository.save(any(ItemNotaFiscalEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotaFiscalDetalheResponseDto detalhe = service.emitirNfce(
                new EmitirNfceRequestDto(pedidoId, "123.456.789-01", "João Silva")
        );

        assertThat(detalhe.status()).isEqualTo(StatusNotaFiscal.AUTORIZADA);
        assertThat(detalhe.itens()).hasSize(2);
        verify(businessEventLogger).success(eq("cash.nfce.issued"), eq("nfce"), any());
    }

    @Test
    void obterXmlCancelarNotaECancelarNoEstornoEBuscarUltimaNota() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID outroEstabId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID autorizadorId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabelecimentoId);

        NotaFiscalEntity deOutro = nota(outroEstabId, pedidoId, StatusNotaFiscal.AUTORIZADA);
        when(notaFiscalRepository.findById(deOutro.getId())).thenReturn(Optional.of(deOutro));
        assertThatThrownBy(() -> service.buscarNotaFiscal(deOutro.getId()))
                .isInstanceOf(AccessDeniedException.class);

        NotaFiscalEntity semXml = nota(estabelecimentoId, pedidoId, StatusNotaFiscal.AUTORIZADA);
        semXml.setXmlConteudo("   ");
        when(notaFiscalRepository.findById(semXml.getId())).thenReturn(Optional.of(semXml));
        assertThatThrownBy(() -> service.obterXmlNotaFiscal(semXml.getId()))
                .isInstanceOf(EntityNotFoundException.class);

        semXml.setXmlConteudo("<xml/>");
        NotaFiscalCaixaService.ArquivoXmlNotaFiscal xml = service.obterXmlNotaFiscal(semXml.getId());
        assertThat(xml.conteudoXml()).isEqualTo("<xml/>");

        // Cancelamento com status != AUTORIZADA
        NotaFiscalEntity jaCancelada = nota(estabelecimentoId, pedidoId, StatusNotaFiscal.CANCELADA);
        when(notaFiscalRepository.findById(jaCancelada.getId())).thenReturn(Optional.of(jaCancelada));
        when(contextoCaixa.usuarioId()).thenReturn(usuarioId);
        assertThatThrownBy(() -> service.cancelarNotaFiscal(
                jaCancelada.getId(), new CancelarNotaFiscalRequestDto("Motivo suficientemente longo", null, null)
        )).isInstanceOf(NotaFiscalEstadoInvalidoException.class);

        // Cancelamento com motivo < 15 chars
        assertThatThrownBy(() -> service.cancelarNotaFiscal(
                semXml.getId(), new CancelarNotaFiscalRequestDto("Curto", null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("15 caracteres");

        // Cancelamento com sucesso
        when(autorizadorOperacaoCaixaService.exigirAutorizador(eq("CAIXA_CANCELAR"), any(), any(), any()))
                .thenReturn(new AutorizadorOperacaoCaixaService.AutorizadorResolvido(autorizadorId, "Gerente"));
        PedidoEntity pedido = pedidoConcluido(estabelecimentoId, pedidoId);
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(provedorEmissaoFiscal.cancelarNfce(eq(semXml), any())).thenReturn(
                new ResultadoCancelamentoFiscal(
                        StatusNotaFiscal.CANCELADA, "CANC-1", LocalDateTime.now(), JsonNodeFactory.instance.objectNode()
                )
        );
        when(notaFiscalRepository.saveAndFlush(any(NotaFiscalEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemNotaFiscalRepository.findByNotaFiscalIdOrderByIdAsc(any())).thenReturn(List.of());
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of());

        NotaFiscalDetalheResponseDto cancelada = service.cancelarNotaFiscal(
                semXml.getId(), new CancelarNotaFiscalRequestDto("Erro no documento informado na nota", null, null)
        );
        assertThat(cancelada.status()).isEqualTo(StatusNotaFiscal.CANCELADA);

        // Cancelamento automático no estorno: sem nota ativa
        when(notaFiscalRepository.findFirstByPedidoIdAndStatusOrderByCriadaEmDesc(pedidoId, StatusNotaFiscal.AUTORIZADA))
                .thenReturn(Optional.empty());
        service.cancelarAutomaticamenteNoEstornoDoPedido(pedido, "Estorno", usuarioId, autorizadorId, "Gerente");
        verify(provedorEmissaoFiscal, org.mockito.Mockito.times(1)).cancelarNfce(any(), any());

        // Com nota ativa e motivo curto -> gera motivo automático >= 15 chars
        NotaFiscalEntity notaAtiva = nota(estabelecimentoId, pedidoId, StatusNotaFiscal.AUTORIZADA);
        when(notaFiscalRepository.findFirstByPedidoIdAndStatusOrderByCriadaEmDesc(pedidoId, StatusNotaFiscal.AUTORIZADA))
                .thenReturn(Optional.of(notaAtiva));
        when(provedorEmissaoFiscal.cancelarNfce(eq(notaAtiva), any())).thenReturn(
                new ResultadoCancelamentoFiscal(
                        StatusNotaFiscal.CANCELADA, "CANC-2", LocalDateTime.now(), JsonNodeFactory.instance.objectNode()
                )
        );
        service.cancelarAutomaticamenteNoEstornoDoPedido(pedido, "Desistiu", usuarioId, autorizadorId, "Gerente");
        assertThat(notaAtiva.getMotivoCancelamento()).contains("Cancelamento automático por estorno");

        // carregarUltimaNotaPorPedido prioriza AUTORIZADA sobre CANCELADA
        assertThat(service.carregarUltimaNotaPorPedido(List.of())).isEmpty();
        NotaFiscalEntity notaCancelada = nota(estabelecimentoId, pedidoId, StatusNotaFiscal.CANCELADA);
        NotaFiscalEntity notaAutorizada = nota(estabelecimentoId, pedidoId, StatusNotaFiscal.AUTORIZADA);
        when(notaFiscalRepository.findByPedidoIdInOrderByCriadaEmDesc(List.of(pedidoId)))
                .thenReturn(List.of(notaCancelada, notaAutorizada));
        Map<UUID, NotaFiscalEntity> mapa = service.carregarUltimaNotaPorPedido(List.of(pedidoId));
        assertThat(mapa.get(pedidoId)).isEqualTo(notaAutorizada);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void emitirListarECarregarNotaCobremCnpjsSemDescontoFiltrosEmBrancoESemPedido() {
        UUID estabId = UUID.randomUUID();
        UUID outroEstabId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID canceladorId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabId);
        when(contextoCaixa.usuarioId()).thenReturn(userId);

        // Tenant diferente ao emitir
        PedidoEntity pedidoOutro = pedidoConcluido(outroEstabId, pedidoId);
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedidoOutro));
        assertThatThrownBy(() -> service.emitirNfce(new EmitirNfceRequestDto(pedidoId, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        // Pedido sem itens e total zerado -> PedidoEstadoInvalidoException
        PedidoEntity pedido = pedidoConcluido(estabId, pedidoId);
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));
        when(notaFiscalRepository.existsByPedidoIdAndStatus(pedidoId, StatusNotaFiscal.AUTORIZADA)).thenReturn(false);
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of());
        assertThatThrownBy(() -> service.emitirNfce(new EmitirNfceRequestDto(pedidoId, null, null)))
                .isInstanceOf(PedidoEstadoInvalidoException.class);

        ItemPedidoEntity item = new ItemPedidoEntity();
        item.setId(9L);
        item.setProdutoId(UUID.randomUUID());
        item.setDescricaoProduto("Item Único");
        item.setQuantidade(BigDecimal.ONE);
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setSubtotal(new BigDecimal("50.00"));
        pedido.setTotal(BigDecimal.ZERO);
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of(item));
        assertThatThrownBy(() -> service.emitirNfce(new EmitirNfceRequestDto(pedidoId, null, null)))
                .isInstanceOf(PedidoEstadoInvalidoException.class);

        // CNPJ 14 dígitos, desconto = ZERO, produto ausente (fallback "UN") e itensTributados vazio (fallback "5405")
        pedido.setTotal(new BigDecimal("50.00"));
        pedido.setDesconto(BigDecimal.ZERO);
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of());
        when(establishmentRepository.findById(estabId)).thenReturn(Optional.of(
                EstablishmentEntity.builder().id(estabId).corporateName("Adega").tradeName("Adega").cnpj("12345678000199").build()
        ));
        when(produtoRepository.findAllById(any())).thenReturn(List.of());
        when(sequenciaDocumentoRepository.proximoNumero(estabId, "NFCE")).thenReturn(11L);
        when(provedorEmissaoFiscal.emitirNfce(any(SolicitacaoEmissaoFiscal.class))).thenReturn(
                new ResultadoEmissaoFiscal(
                        "INTERNO", AmbienteFiscal.HOMOLOGACAO, StatusNotaFiscal.AUTORIZADA,
                        "11", "1", "5".repeat(44), "PROT-11", "https://nfce.test/11",
                        JsonNodeFactory.instance.objectNode(), "<xml/>", List.of()
                )
        );
        when(notaFiscalRepository.saveAndFlush(any(NotaFiscalEntity.class))).thenAnswer(inv -> {
            NotaFiscalEntity n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });
        when(itemNotaFiscalRepository.save(any(ItemNotaFiscalEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        NotaFiscalDetalheResponseDto detalheCnpj = service.emitirNfce(
                new EmitirNfceRequestDto(pedidoId, "12.345.678/0001-99", "   ")
        );
        assertThat(detalheCnpj.contraparteDocumento()).isEqualTo("12345678000199");
        assertThat(detalheCnpj.itens().getFirst().cfop()).isEqualTo("5405");
        assertThat(detalheCnpj.itens().getFirst().unidadeMedida()).isEqualTo("UN");

        // listarNotasFiscais com filtros em branco / sem dígitos e página vazia
        when(notaFiscalRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        assertThat(service.listarNotasFiscais(null, null, "   ", "   ", null, null, 0, 20, "emitidaEm", DirecaoOrdenacao.DESC).itens())
                .isEmpty();
        assertThat(service.listarNotasFiscais(null, null, "---", null, null, null, 0, 20, "emitidaEm", DirecaoOrdenacao.DESC).itens())
                .isEmpty();

        // carregarUltimaNotaPorPedido(null) e mantendo AUTORIZADA antes de CANCELADA
        assertThat(service.carregarUltimaNotaPorPedido(null)).isEmpty();
        NotaFiscalEntity notaAutorizada = nota(estabId, pedidoId, StatusNotaFiscal.AUTORIZADA);
        NotaFiscalEntity notaCancelada = nota(estabId, pedidoId, StatusNotaFiscal.CANCELADA);
        when(notaFiscalRepository.findByPedidoIdInOrderByCriadaEmDesc(List.of(pedidoId)))
                .thenReturn(List.of(notaAutorizada, notaCancelada));
        assertThat(service.carregarUltimaNotaPorPedido(List.of(pedidoId)).get(pedidoId)).isEqualTo(notaAutorizada);

        // buscarNotaFiscal com pedidoId nulo e ambos usuários preenchidos
        NotaFiscalEntity semPedido = nota(estabId, null, StatusNotaFiscal.CANCELADA);
        semPedido.setEmitidaPorUsuarioId(userId);
        semPedido.setCanceladaPorUsuarioId(canceladorId);
        when(notaFiscalRepository.findById(semPedido.getId())).thenReturn(Optional.of(semPedido));
        when(itemNotaFiscalRepository.findByNotaFiscalIdOrderByIdAsc(semPedido.getId())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        NotaFiscalDetalheResponseDto detalheSemPedido = service.buscarNotaFiscal(semPedido.getId());
        assertThat(detalheSemPedido.pedidoId()).isNull();
    }

    private NotaFiscalEntity nota(UUID estabelecimentoId, UUID pedidoId, StatusNotaFiscal status) {
        NotaFiscalEntity n = new NotaFiscalEntity();
        n.setId(UUID.randomUUID());
        n.setEstabelecimentoId(estabelecimentoId);
        n.setPedidoId(pedidoId);
        n.setTipo(TipoNotaFiscal.NFCE);
        n.setModelo("65");
        n.setAmbiente(AmbienteFiscal.HOMOLOGACAO);
        n.setProvedor("INTERNO");
        n.setStatus(status);
        n.setNumero("1");
        n.setSerie("1");
        n.setChaveAcesso("1".repeat(44));
        n.setValorProdutos(new BigDecimal("50.00"));
        n.setValorDesconto(BigDecimal.ZERO);
        n.setValorTotal(new BigDecimal("50.00"));
        n.setEmitidaEm(LocalDateTime.now());
        n.setCriadaEm(LocalDateTime.now());
        n.setAtualizadaEm(LocalDateTime.now());
        return n;
    }

    private PedidoEntity pedidoConcluido(UUID estabelecimentoId, UUID pedidoId) {
        PedidoEntity p = new PedidoEntity();
        p.setId(pedidoId);
        p.setEstabelecimentoId(estabelecimentoId);
        p.setOperadorUsuarioId(UUID.randomUUID());
        p.setNumero(10L);
        p.setStatus(StatusPedidoCaixa.CONCLUIDO);
        p.setCanal(CanalPedidoCaixa.BALCAO);
        p.setStatusDesconto(StatusDescontoPedido.NENHUM);
        p.setSubtotal(new BigDecimal("50.00"));
        p.setDesconto(BigDecimal.ZERO);
        p.setTotal(new BigDecimal("50.00"));
        return p;
    }
}
