package valhalla.core.stock.app.modules.estoque.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoLoteEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.repository.LoteEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoLoteRepository;
import valhalla.core.stock.app.modules.estoque.service.ControleLotesEstoque.Alocacao;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControleLotesEstoqueTest {

    private static final Instant AGORA = Instant.parse("2026-11-15T12:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 15);

    @Mock private LoteEstoqueRepository loteEstoqueRepository;
    @Mock private MovimentacaoLoteRepository movimentacaoLoteRepository;

    private ControleLotesEstoque controleLotes;

    @BeforeEach
    void configurar() {
        controleLotes = new ControleLotesEstoque(
                loteEstoqueRepository,
                movimentacaoLoteRepository,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
        lenient().when(loteEstoqueRepository.save(any(LoteEstoqueEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void saldoNaoVencidoDisponivelExcluiVencidosEReservaEConsideraSaldoLegadoSemLote() {
        EstoqueEntity estoque = estoque(1, "15.000", "2.000");
        LoteEstoqueEntity vencido = lote(estoque, 10L, "L-VENCIDO", HOJE.minusDays(1), "4.000");
        LoteEstoqueEntity valido = lote(estoque, 11L, "L-VALIDO", HOJE.plusDays(10), "6.000");
        // Soma lotes = 10, estoqueAtual = 15 => 5 unidades ainda sem lote (SEM-LOTE não vence)
        // Saldo válido = 6 + 5 = 11; disponível após reserva de 2 = 9
        when(loteEstoqueRepository.findAllByEstoqueId(1)).thenReturn(List.of(vencido, valido));

        assertThat(controleLotes.saldoNaoVencidoDisponivel(estoque)).isEqualByComparingTo("9.000");
    }

    @Test
    void devolverParaLotesRestauraExatamenteOsLotesOriginais() {
        EstoqueEntity estoque = estoque(2, "5.000", "0.000");
        LoteEstoqueEntity antes = lote(estoque, 20L, "VENCE-ANTES", HOJE.plusDays(2), "0.000");
        LoteEstoqueEntity depois = lote(estoque, 21L, "VENCE-DEPOIS", HOJE.plusDays(20), "5.000");
        when(loteEstoqueRepository.findAllByEstoqueId(2)).thenReturn(List.of(antes, depois));

        MovimentacaoEstoqueEntity saida = new MovimentacaoEstoqueEntity();
        saida.setId(99L);

        List<Alocacao> devolvidos = controleLotes.devolverParaLotes(
                estoque,
                new BigDecimal("5.000"),
                List.of(
                        vinculo(saida, antes, "3.000"),
                        vinculo(saida, depois, "2.000")
                )
        );

        assertThat(antes.getQuantidadeAtual()).isEqualByComparingTo("3.000");
        assertThat(depois.getQuantidadeAtual()).isEqualByComparingTo("7.000");
        assertThat(devolvidos).hasSize(2);
        assertThat(ControleLotesEstoque.descreverCodigos(devolvidos)).isEqualTo("VENCE-ANTES, VENCE-DEPOIS");
    }

    @Test
    void devolverParaLotesSemVinculosMantemFallbackParaSemLote() {
        EstoqueEntity estoque = estoque(3, "4.000", "0.000");
        when(loteEstoqueRepository.findAllByEstoqueId(3)).thenReturn(List.of());

        List<Alocacao> devolvidos = controleLotes.devolverParaLotes(
                estoque,
                new BigDecimal("2.000"),
                List.of()
        );

        assertThat(devolvidos).hasSize(1);
        assertThat(devolvidos.getFirst().lote().getCodigo()).isEqualTo(LoteEstoqueEntity.SEM_LOTE);
        assertThat(devolvidos.getFirst().lote().getQuantidadeAtual()).isEqualByComparingTo("6.000");
    }

    private EstoqueEntity estoque(Integer id, String atual, String reservada) {
        ProdutoEntity produto = new ProdutoEntity();
        produto.setId(UUID.randomUUID());
        produto.setNome("Produto Teste");
        EstoqueEntity estoque = new EstoqueEntity();
        estoque.setId(id);
        estoque.setProduto(produto);
        estoque.setQuantidadeAtual(new BigDecimal(atual));
        estoque.setQuantidadeReservada(new BigDecimal(reservada));
        return estoque;
    }

    private LoteEstoqueEntity lote(
            EstoqueEntity estoque,
            Long id,
            String codigo,
            LocalDate validade,
            String quantidade
    ) {
        LoteEstoqueEntity lote = new LoteEstoqueEntity();
        lote.setId(id);
        lote.setEstoque(estoque);
        lote.setCodigo(codigo);
        lote.setValidade(validade);
        lote.setQuantidadeAtual(new BigDecimal(quantidade));
        return lote;
    }

    private MovimentacaoLoteEntity vinculo(
            MovimentacaoEstoqueEntity movimentacao,
            LoteEstoqueEntity lote,
            String quantidade
    ) {
        MovimentacaoLoteEntity vinculo = new MovimentacaoLoteEntity();
        vinculo.setMovimentacao(movimentacao);
        vinculo.setLote(lote);
        vinculo.setQuantidade(new BigDecimal(quantidade));
        return vinculo;
    }
}
