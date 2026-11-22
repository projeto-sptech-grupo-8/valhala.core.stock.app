package valhalla.core.stock.app.modules.estoque.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.persistence.EntityNotFoundException;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoLoteEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.repository.LoteEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoLoteRepository;
import valhalla.core.stock.app.modules.estoque.service.ControleLotesEstoque.Alocacao;
import valhalla.core.stock.app.modules.estoque.strategy.PoliticaLote;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
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

    @Test
    void devolverParaLotesComSomaDivergenteCodigoFallbackERecriacaoDeLote() {
        EstoqueEntity estoque = estoque(4, "10.000", "0.000");
        LoteEstoqueEntity loteMesmoCodigo = lote(estoque, 999L, "L-COD", HOJE.plusDays(10), "5.000");
        LoteEstoqueEntity outro = lote(estoque, 1000L, "OUTRO", HOJE.plusDays(20), "5.000");
        when(loteEstoqueRepository.findAllByEstoqueId(4)).thenReturn(List.of(loteMesmoCodigo, outro));

        MovimentacaoEstoqueEntity saida = new MovimentacaoEstoqueEntity();
        LoteEstoqueEntity origemPorCodigo = lote(estoque, 55L, "L-COD", HOJE.plusDays(10), "0.000");
        LoteEstoqueEntity origemInexistente = lote(estoque, 88L, "L-NOVO", HOJE.plusDays(15), "0.000");

        // Soma divergente cai no fallback para SEM-LOTE
        List<Alocacao> somaDivergente = controleLotes.devolverParaLotes(
                estoque,
                new BigDecimal("4.000"),
                List.of(vinculo(saida, origemPorCodigo, "2.000"))
        );
        assertThat(somaDivergente.getFirst().lote().getCodigo()).isEqualTo(LoteEstoqueEntity.SEM_LOTE);

        // Match por código+validade e recriação de lote ausente
        when(loteEstoqueRepository.findAllByEstoqueId(4)).thenReturn(List.of(loteMesmoCodigo, outro));
        List<Alocacao> devolvidos = controleLotes.devolverParaLotes(
                estoque,
                new BigDecimal("3.000"),
                List.of(
                        vinculo(saida, origemPorCodigo, "1.000"),
                        vinculo(saida, origemInexistente, "2.000")
                )
        );
        assertThat(loteMesmoCodigo.getQuantidadeAtual()).isEqualByComparingTo("6.000");
        assertThat(devolvidos).hasSize(2);
        assertThat(devolvidos.get(1).lote().getCodigo()).isEqualTo("L-NOVO");
        assertThat(devolvidos.get(1).lote().getQuantidadeAtual()).isEqualByComparingTo("2.000");
    }

    @Test
    void reconciliarAjustaDiferencasPositivasENegativasPorFefo() {
        // Diferença positiva cria SEM-LOTE
        EstoqueEntity estoquePositivo = estoque(5, "12.000", "0.000");
        LoteEstoqueEntity existente = lote(estoquePositivo, 1L, "L1", HOJE.plusDays(5), "8.000");
        when(loteEstoqueRepository.findAllByEstoqueId(5)).thenReturn(List.of(existente));

        List<LoteEstoqueEntity> aposPositivo = controleLotes.reconciliar(estoquePositivo);
        assertThat(aposPositivo).hasSize(2);
        assertThat(aposPositivo.get(1).getCodigo()).isEqualTo(LoteEstoqueEntity.SEM_LOTE);
        assertThat(aposPositivo.get(1).getQuantidadeAtual()).isEqualByComparingTo("4.000");

        // Diferença negativa consome por FEFO
        EstoqueEntity estoqueNegativo = estoque(6, "3.000", "0.000");
        LoteEstoqueEntity maisProximo = lote(estoqueNegativo, 2L, "L-CEDO", HOJE.plusDays(1), "4.000");
        LoteEstoqueEntity maisDistante = lote(estoqueNegativo, 3L, "L-TARDE", HOJE.plusDays(10), "5.000");
        when(loteEstoqueRepository.findAllByEstoqueId(6)).thenReturn(List.of(maisDistante, maisProximo));

        controleLotes.reconciliar(estoqueNegativo);
        assertThat(maisProximo.getQuantidadeAtual()).isEqualByComparingTo("0.000");
        assertThat(maisDistante.getQuantidadeAtual()).isEqualByComparingTo("3.000");
    }

    @Test
    void creditarValidaRegrasDeLoteEValidadeEConsultaSaldoInicial() {
        EstoqueEntity estoque = estoque(7, "10.000", "0.000");
        LoteEstoqueEntity comValidade = lote(estoque, 10L, "L-VAL", HOJE.plusDays(10), "5.000");
        LoteEstoqueEntity semValidadeComSaldo = lote(estoque, 11L, "L-SEM-VAL", null, "5.000");
        LoteEstoqueEntity semValidadeZerado = lote(estoque, 12L, "L-ZERO", null, "0.000");
        when(loteEstoqueRepository.findAllByEstoqueId(7))
                .thenReturn(List.of(comValidade, semValidadeComSaldo, semValidadeZerado));

        assertThatThrownBy(() -> controleLotes.movimentar(
                estoque, PoliticaLote.CREDITO, BigDecimal.ONE, "L-VAL", HOJE.plusDays(15), 10L
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loteId");

        assertThatThrownBy(() -> controleLotes.movimentar(
                estoque, PoliticaLote.CREDITO, BigDecimal.ONE, "L-VAL", HOJE.plusDays(15), null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("já está cadastrado com validade");

        assertThatThrownBy(() -> controleLotes.movimentar(
                estoque, PoliticaLote.CREDITO, BigDecimal.ONE, "L-SEM-VAL", HOJE.plusDays(15), null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("já possui saldo sem validade");

        // Lote zerado sem validade pode receber validade; entrada sem código mas com validade gera código VAL-...
        List<Alocacao> alocado = controleLotes.movimentar(
                estoque, PoliticaLote.CREDITO, new BigDecimal("3.000"), "L-ZERO", HOJE.plusDays(20), null
        );
        assertThat(alocado.getFirst().lote().getValidade()).isEqualTo(HOJE.plusDays(20));

        List<Alocacao> porValidade = controleLotes.movimentar(
                estoque, PoliticaLote.CREDITO, new BigDecimal("2.000"), null, HOJE.plusDays(30), null
        );
        assertThat(porValidade.getFirst().lote().getCodigo()).isEqualTo("VAL-" + HOJE.plusDays(30));

        // Saldo inicial sem lote/validade gera SEM-LOTE; apenas com validade gera VAL-...
        EstoqueEntity novo = estoque(null, "8.000", "0.000");
        List<Alocacao> inicialSemLote = controleLotes.registrarSaldoInicial(novo, new BigDecimal("8.000"));
        assertThat(inicialSemLote).hasSize(1);
        assertThat(inicialSemLote.getFirst().lote().getCodigo()).isEqualTo(LoteEstoqueEntity.SEM_LOTE);

        List<Alocacao> inicialComValidade = controleLotes.registrarSaldoInicial(novo, null, null, HOJE.plusDays(40));
        assertThat(inicialComValidade.getFirst().lote().getCodigo()).isEqualTo("VAL-" + HOJE.plusDays(40));
        assertThat(inicialComValidade.getFirst().quantidade()).isEqualByComparingTo("0");
    }

    @Test
    void debitarPorLoteIdOuCodigoLoteValidaVencimentoESaldo() {
        EstoqueEntity estoque = estoque(8, "12.000", "0.000");
        LoteEstoqueEntity vencido = lote(estoque, 20L, "L-VENC", HOJE.minusDays(2), "5.000");
        LoteEstoqueEntity valido = lote(estoque, 21L, "L-OK", HOJE.plusDays(15), "7.000");
        when(loteEstoqueRepository.findAllByEstoqueId(8)).thenReturn(List.of(vencido, valido));

        assertThatThrownBy(() -> controleLotes.movimentar(
                estoque, PoliticaLote.DEBITO_SEM_VENCIDOS, BigDecimal.ONE, null, HOJE.plusDays(5), null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validade");

        assertThatThrownBy(() -> controleLotes.movimentar(
                estoque, PoliticaLote.DEBITO_SEM_VENCIDOS, BigDecimal.ONE, null, null, 999L
        )).isInstanceOf(EntityNotFoundException.class);

        assertThatThrownBy(() -> controleLotes.movimentar(
                estoque, PoliticaLote.DEBITO_SEM_VENCIDOS, BigDecimal.ONE, "L-VENC", null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("venceu em");

        assertThatThrownBy(() -> controleLotes.movimentar(
                estoque, PoliticaLote.DEBITO_SEM_VENCIDOS, new BigDecimal("10.000"), null, null, 21L
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Saldo insuficiente no lote");

        assertThatThrownBy(() -> controleLotes.movimentar(
                estoque, PoliticaLote.DEBITO_SEM_VENCIDOS, new BigDecimal("10.000"), null, null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Saldo dentro da validade insuficiente");

        // Débito com vencidos permitido por loteId
        List<Alocacao> baixadoVencido = controleLotes.movimentar(
                estoque, PoliticaLote.DEBITO_COM_VENCIDOS, new BigDecimal("2.000"), null, null, 20L
        );
        assertThat(baixadoVencido.getFirst().lote()).isEqualTo(vencido);
        assertThat(vencido.getQuantidadeAtual()).isEqualByComparingTo("3.000");

        // Débito por código do lote válido
        List<Alocacao> baixadoPorCodigo = controleLotes.movimentar(
                estoque, PoliticaLote.DEBITO_SEM_VENCIDOS, new BigDecimal("3.000"), "l-ok", null, null
        );
        assertThat(baixadoPorCodigo.getFirst().lote()).isEqualTo(valido);
        assertThat(valido.getQuantidadeAtual()).isEqualByComparingTo("4.000");

        // descreverCodigos vazio e truncado
        assertThat(ControleLotesEstoque.descreverCodigos(List.of())).isNull();
        EstoqueEntity est = estoque(9, "10.000", "0.000");
        String codigoLongo = "LOTE-" + "X".repeat(60);
        String codigoLongo2 = "LOTE-" + "Y".repeat(60);
        String descrito = ControleLotesEstoque.descreverCodigos(List.of(
                new Alocacao(lote(est, 1L, codigoLongo, null, "1"), BigDecimal.ONE),
                new Alocacao(lote(est, 2L, codigoLongo2, null, "1"), BigDecimal.ONE)
        ));
        assertThat(descrito).hasSize(100).endsWith("...");

        // Vincular movimentação
        MovimentacaoEstoqueEntity mov = new MovimentacaoEstoqueEntity();
        controleLotes.vincular(mov, baixadoPorCodigo);
        verify(movimentacaoLoteRepository).save(any(MovimentacaoLoteEntity.class));
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
