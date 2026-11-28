package valhalla.core.stock.app.modules.caixa.sessao.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import valhalla.core.stock.app.modules.caixa.security.ContextoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.dto.AuditoriaCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.sessao.entity.AuditoriaCaixaEntity;
import valhalla.core.stock.app.modules.caixa.sessao.enums.OrigemAuditoriaCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.repository.AuditoriaCaixaRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditoriaCaixaServiceTest {

    @Mock private AuditoriaCaixaRepository auditoriaCaixaRepository;
    @Mock private UserRepository userRepository;
    @Mock private ContextoCaixa contextoCaixa;

    @InjectMocks
    private AuditoriaCaixaService service;

    @Test
    void registrarComOrigemPreencheValorUsuarioEAutorizadorQuandoNecessario() {
        UUID estabId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID autorizadorId = UUID.randomUUID();
        UUID sessaoId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID pagId = UUID.randomUUID();

        when(userRepository.findById(usuarioId))
                .thenReturn(Optional.of(UserEntity.builder().id(usuarioId).name("Operador 1").build()));
        when(userRepository.findById(autorizadorId))
                .thenReturn(Optional.of(UserEntity.builder().id(autorizadorId).name("Gerente 1").build()));

        service.registrarComOrigem(
                null,
                estabId,
                usuarioId,
                sessaoId,
                EventoAuditoriaCaixa.DESCONTO_CONCEDIDO,
                EventoAuditoriaCaixa.ENTIDADE_PEDIDO,
                pedidoId.toString(),
                "Desconto",
                new BigDecimal("-12.345"),
                "VIP",
                pedidoId,
                "Mesa 1",
                pagId,
                autorizadorId,
                "   ",
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode()
        );

        ArgumentCaptor<AuditoriaCaixaEntity> captor = ArgumentCaptor.forClass(AuditoriaCaixaEntity.class);
        verify(auditoriaCaixaRepository).save(captor.capture());
        AuditoriaCaixaEntity salva = captor.getValue();
        assertThat(salva.getOrigem()).isEqualTo(OrigemAuditoriaCaixa.API);
        assertThat(salva.getValor()).isEqualByComparingTo("12.35");
        assertThat(salva.getUsuarioNome()).isEqualTo("Operador 1");
        assertThat(salva.getAutorizadoPorNome()).isEqualTo("Gerente 1");

        // Origem nula e usuarioId nulo => SISTEMA, valor null, autorizadorNome já informado
        service.registrarComOrigem(
                null,
                estabId,
                null,
                sessaoId,
                EventoAuditoriaCaixa.PAGAMENTO_POINT_EXPIRADO,
                EventoAuditoriaCaixa.ENTIDADE_PAGAMENTO_PEDIDO,
                pagId.toString(),
                "Timeout",
                null,
                null,
                pedidoId,
                null,
                pagId,
                autorizadorId,
                "Gerente Fixo",
                null,
                null
        );
        verify(auditoriaCaixaRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        AuditoriaCaixaEntity sistema = captor.getAllValues().getLast();
        assertThat(sistema.getOrigem()).isEqualTo(OrigemAuditoriaCaixa.SISTEMA);
        assertThat(sistema.getAutorizadoPorNome()).isEqualTo("Gerente Fixo");
        assertThat(sistema.getValor()).isNull();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listarValidaPeriodoEAvaliaEspecificacoesComESemFiltros() {
        UUID estabId = UUID.randomUUID();
        UUID sessaoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabId);

        assertThatThrownBy(() -> service.listar(
                null, null, null,
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 10),
                0, 20, "ocorridoEm", DirecaoOrdenacao.DESC
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A data inicial não pode ser posterior à data final");

        AuditoriaCaixaEntity entidade = new AuditoriaCaixaEntity();
        entidade.setId(1L);
        entidade.setEstabelecimentoId(estabId);
        entidade.setOrigem(OrigemAuditoriaCaixa.API);
        entidade.setTipoEvento("SANGRIA");

        when(auditoriaCaixaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entidade), PageRequest.of(0, 20), 1));

        RespostaPaginadaDto<AuditoriaCaixaResponseDto> comFiltros = service.listar(
                "  SANGRIA ", sessaoId, usuarioId,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                0, 20, "ocorridoEm", DirecaoOrdenacao.DESC
        );
        assertThat(comFiltros.itens()).hasSize(1);

        service.listar(
                "   ", null, null,
                LocalDate.of(2026, 11, 1), null,
                0, 20, "ocorridoEm", DirecaoOrdenacao.ASC
        );

        ArgumentCaptor<Specification<AuditoriaCaixaEntity>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(auditoriaCaixaRepository, org.mockito.Mockito.times(2))
                .findAll(captor.capture(), any(Pageable.class));

        Predicate predicate = mock(Predicate.class);
        Path path = mock(Path.class, inv -> Path.class.isAssignableFrom(inv.getMethod().getReturnType()) ? inv.getMock() : null);
        Root<AuditoriaCaixaEntity> root = mock(Root.class, inv -> Path.class.isAssignableFrom(inv.getMethod().getReturnType()) ? path : null);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class, inv ->
                Predicate.class.isAssignableFrom(inv.getMethod().getReturnType()) ? predicate : null);

        for (Specification<AuditoriaCaixaEntity> spec : captor.getAllValues()) {
            assertThat(spec.toPredicate(root, query, builder)).isNotNull();
        }
    }
}
