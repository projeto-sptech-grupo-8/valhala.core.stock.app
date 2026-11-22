package valhalla.core.stock.app.modules.caixa.configuracao.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import valhalla.core.stock.app.modules.caixa.configuracao.dto.TaxaPagamentoAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.caixa.configuracao.dto.TaxaPagamentoRequestDto;
import valhalla.core.stock.app.modules.caixa.configuracao.dto.TaxaPagamentoResponseDto;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.TaxaPagamentoEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.PoliticaJurosCaixa;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.ConfiguracaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.TaxaPagamentoRepository;
import valhalla.core.stock.app.modules.caixa.exception.ConfiguracaoCaixaInvalidaException;
import valhalla.core.stock.app.modules.caixa.exception.TaxaPagamentoJaExisteException;
import valhalla.core.stock.app.modules.caixa.security.ContextoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.service.AuditoriaCaixaService;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxaPagamentoServiceTest {

    @Mock private TaxaPagamentoRepository taxaPagamentoRepository;
    @Mock private ConfiguracaoCaixaRepository configuracaoCaixaRepository;
    @Mock private AuditoriaCaixaService auditoriaCaixaService;
    @Mock private ContextoCaixa contextoCaixa;
    @Mock private BusinessEventLogger businessEventLogger;

    @InjectMocks
    private TaxaPagamentoService service;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listarTaxasAvaliaFiltrosDeFormaEAtivo() {
        UUID estabelecimentoId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabelecimentoId);
        ConfiguracaoCaixaEntity config = new ConfiguracaoCaixaEntity();
        config.setPoliticaJuros(PoliticaJurosCaixa.REPASSADA_AO_CLIENTE);
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabelecimentoId))
                .thenReturn(Optional.of(config));

        TaxaPagamentoEntity taxa = new TaxaPagamentoEntity();
        taxa.setId(UUID.randomUUID());
        taxa.setEstabelecimentoId(estabelecimentoId);
        taxa.setForma(FormaPagamentoCaixa.CREDITO);
        taxa.setParcelas(3);
        taxa.setTaxaPercentual(new BigDecimal("5.5000"));
        taxa.setTaxaFixa(BigDecimal.ZERO);
        taxa.setAtivo(true);

        when(taxaPagamentoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(taxa), PageRequest.of(0, 20), 1));

        RespostaPaginadaDto<TaxaPagamentoResponseDto> pagina = service.listarTaxas(
                FormaPagamentoCaixa.CREDITO, true, 0, 20, "parcelas", DirecaoOrdenacao.ASC
        );
        assertThat(pagina.itens()).hasSize(1);
        assertThat(pagina.itens().getFirst().repasseEfetivo()).isTrue();

        ArgumentCaptor<Specification<TaxaPagamentoEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(taxaPagamentoRepository).findAll(specCaptor.capture(), any(Pageable.class));

        Predicate predicate = mock(Predicate.class);
        Path path = mock(Path.class, inv -> Path.class.isAssignableFrom(inv.getMethod().getReturnType()) ? inv.getMock() : null);
        Root<TaxaPagamentoEntity> root = mock(Root.class, inv ->
                Path.class.isAssignableFrom(inv.getMethod().getReturnType()) ? path : null);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class, inv ->
                Predicate.class.isAssignableFrom(inv.getMethod().getReturnType()) ? predicate : null);

        assertThat(specCaptor.getValue().toPredicate(root, query, builder)).isNotNull();
    }

    @Test
    void criarTaxaValidaRegrasDuplicidadeEIntegridade() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabelecimentoId);
        when(contextoCaixa.usuarioId()).thenReturn(usuarioId);

        assertThatThrownBy(() -> service.criarTaxa(new TaxaPagamentoRequestDto(
                FormaPagamentoCaixa.DINHEIRO, 1, BigDecimal.ONE, BigDecimal.ZERO, false, true
        ))).isInstanceOf(ConfiguracaoCaixaInvalidaException.class)
                .hasMessage("Pagamento em dinheiro não possui taxa");

        assertThatThrownBy(() -> service.criarTaxa(new TaxaPagamentoRequestDto(
                FormaPagamentoCaixa.DEBITO, 2, BigDecimal.ONE, BigDecimal.ZERO, false, true
        ))).isInstanceOf(ConfiguracaoCaixaInvalidaException.class)
                .hasMessage("Débito e Pix aceitam somente 1 parcela");

        assertThatThrownBy(() -> service.criarTaxa(new TaxaPagamentoRequestDto(
                FormaPagamentoCaixa.CREDITO, 2, new BigDecimal("100.0000"), BigDecimal.ZERO, true, true
        ))).isInstanceOf(ConfiguracaoCaixaInvalidaException.class)
                .hasMessage("A taxa percentual deve ser menor que 100% quando repassada ao cliente");

        when(taxaPagamentoRepository.existsByEstabelecimentoIdAndFormaAndParcelas(
                estabelecimentoId, FormaPagamentoCaixa.CREDITO, 3
        )).thenReturn(true);

        assertThatThrownBy(() -> service.criarTaxa(new TaxaPagamentoRequestDto(
                FormaPagamentoCaixa.CREDITO, 3, new BigDecimal("5.0"), BigDecimal.ZERO, false, true
        ))).isInstanceOf(TaxaPagamentoJaExisteException.class);

        when(taxaPagamentoRepository.existsByEstabelecimentoIdAndFormaAndParcelas(
                estabelecimentoId, FormaPagamentoCaixa.PIX, 1
        )).thenReturn(false);
        when(taxaPagamentoRepository.saveAndFlush(any(TaxaPagamentoEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq_taxa"));

        assertThatThrownBy(() -> service.criarTaxa(new TaxaPagamentoRequestDto(
                FormaPagamentoCaixa.PIX, 1, new BigDecimal("1.0"), BigDecimal.ZERO, false, true
        ))).isInstanceOf(TaxaPagamentoJaExisteException.class);
    }

    @Test
    void atualizarTaxaControlaAlteracaoEIsolamentoDeEstabelecimento() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID outroEstabelecimentoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID taxaId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabelecimentoId);
        when(contextoCaixa.usuarioId()).thenReturn(usuarioId);

        when(taxaPagamentoRepository.findById(taxaId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.atualizarTaxa(taxaId, new TaxaPagamentoAtualizacaoRequestDto(
                new BigDecimal("2.0"), BigDecimal.ZERO, false, true
        ))).isInstanceOf(EntityNotFoundException.class);

        TaxaPagamentoEntity deOutro = new TaxaPagamentoEntity();
        deOutro.setId(taxaId);
        deOutro.setEstabelecimentoId(outroEstabelecimentoId);
        when(taxaPagamentoRepository.findById(taxaId)).thenReturn(Optional.of(deOutro));
        assertThatThrownBy(() -> service.atualizarTaxa(taxaId, new TaxaPagamentoAtualizacaoRequestDto(
                new BigDecimal("2.0"), BigDecimal.ZERO, false, true
        ))).isInstanceOf(AccessDeniedException.class);

        TaxaPagamentoEntity taxa = new TaxaPagamentoEntity();
        taxa.setId(taxaId);
        taxa.setEstabelecimentoId(estabelecimentoId);
        taxa.setForma(FormaPagamentoCaixa.CREDITO);
        taxa.setParcelas(2);
        taxa.setTaxaPercentual(new BigDecimal("4.5000"));
        taxa.setTaxaFixa(new BigDecimal("0.50"));
        taxa.setRepassarAoCliente(false);
        taxa.setAtivo(true);
        when(taxaPagamentoRepository.findById(taxaId)).thenReturn(Optional.of(taxa));
        when(taxaPagamentoRepository.saveAndFlush(any(TaxaPagamentoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabelecimentoId))
                .thenReturn(Optional.empty());

        // Sem alteração real -> sem auditoria
        service.atualizarTaxa(taxaId, new TaxaPagamentoAtualizacaoRequestDto(
                new BigDecimal("4.5"), new BigDecimal("0.5"), false, true
        ));
        verify(auditoriaCaixaService, never()).registrar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        // Com alteração -> audita
        TaxaPagamentoResponseDto atualizada = service.atualizarTaxa(taxaId, new TaxaPagamentoAtualizacaoRequestDto(
                new BigDecimal("5.25"), new BigDecimal("0.60"), true, true
        ));
        assertThat(atualizada.taxaPercentual()).isEqualByComparingTo("5.2500");
        assertThat(atualizada.repasseEfetivo()).isTrue();
        verify(auditoriaCaixaService).registrar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(businessEventLogger).success("cash.fee.updated", "cash_fee", taxaId);
    }
}
