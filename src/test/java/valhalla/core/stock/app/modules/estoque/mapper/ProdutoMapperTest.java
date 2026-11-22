package valhalla.core.stock.app.modules.estoque.mapper;

import org.junit.jupiter.api.Test;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdutoMapperTest {

    @Test
    void mapeiaRespostaComESemEstoqueECalculaMargemQuandoCustoPositivo() {
        CategoriaEntity categoria = new CategoriaEntity();
        categoria.setId(10);
        categoria.setNome("Destilados");

        ProdutoEntity produto = new ProdutoEntity();
        produto.setId(UUID.randomUUID());
        produto.setSku("PRD-12345678");
        produto.setNome("Gin Tanqueray");
        produto.setTipo(ProdutoTipo.PADRAO);
        produto.setCategoria(categoria);
        produto.setUnidadeMedida("un");
        produto.setPrecoCusto(new BigDecimal("80.00"));
        produto.setPrecoVenda(new BigDecimal("100.00"));
        produto.setFracionado(true);
        produto.setVolumeEmbalagemMl(new BigDecimal("750"));
        produto.setFardo(true);
        produto.setUnidadesPorFardo(6);
        produto.setAtivo(true);

        EstoqueEntity estoque = new EstoqueEntity();
        estoque.setQuantidadeAtual(new BigDecimal("1500"));
        estoque.setEstoqueMinimo(new BigDecimal("750"));

        LocalDate validade = LocalDate.of(2027, 1, 15);
        ProdutoResponseDto comEstoque = ProdutoMapper.paraResposta(produto, estoque, "L01", validade);
        assertThat(comEstoque.margemPercentual()).isEqualByComparingTo("20.00");
        assertThat(comEstoque.quantidadeEstoque()).isEqualByComparingTo("1500");
        assertThat(comEstoque.estoqueMinimo()).isEqualByComparingTo("750");
        assertThat(comEstoque.lote()).isEqualTo("L01");
        assertThat(comEstoque.validade()).isEqualTo(validade);

        produto.setPrecoCusto(null);
        ProdutoResponseDto semCusto = ProdutoMapper.paraResposta(produto, null);
        assertThat(semCusto.margemPercentual()).isNull();
        assertThat(semCusto.quantidadeEstoque()).isNull();

        produto.setPrecoCusto(BigDecimal.ZERO);
        ProdutoResponseDto custoZero = ProdutoMapper.paraResposta(produto, null);
        assertThat(custoZero.margemPercentual()).isNull();
    }

    @Test
    void aplicaAtualizacaoParcialEValidaCamposObrigatoriosEmBranco() {
        CategoriaEntity antiga = new CategoriaEntity();
        antiga.setId(1);
        antiga.setNome("Antiga");

        CategoriaEntity nova = new CategoriaEntity();
        nova.setId(2);
        nova.setNome("Nova");

        ProdutoEntity produto = new ProdutoEntity();
        produto.setCategoria(antiga);
        produto.setNome("Produto Original");
        produto.setUnidadeMedida("un");
        produto.setPrecoCusto(new BigDecimal("10.00"));
        produto.setPrecoVenda(new BigDecimal("20.00"));
        produto.setFracionado(false);
        produto.setFardo(false);
        produto.setAtivo(true);

        ProdutoAtualizacaoRequestDto completa = new ProdutoAtualizacaoRequestDto(
                "  Novo Nome  ",
                2,
                " ml ",
                new BigDecimal("25.00"),
                new BigDecimal("12.00"),
                true,
                new BigDecimal("750"),
                new BigDecimal("5"),
                true,
                12,
                false
        );

        ProdutoMapper.aplicarAtualizacao(produto, completa, nova);
        assertThat(produto.getNome()).isEqualTo("Novo Nome");
        assertThat(produto.getCategoria()).isEqualTo(nova);
        assertThat(produto.getUnidadeMedida()).isEqualTo("ml");
        assertThat(produto.getPrecoVenda()).isEqualByComparingTo("25.00");
        assertThat(produto.getPrecoCusto()).isEqualByComparingTo("12.00");
        assertThat(produto.isFracionado()).isTrue();
        assertThat(produto.getVolumeEmbalagemMl()).isEqualByComparingTo("750");
        assertThat(produto.isFardo()).isTrue();
        assertThat(produto.getUnidadesPorFardo()).isEqualTo(12);
        assertThat(produto.isAtivo()).isFalse();

        // Desativar fardo deve limpar unidadesPorFardo
        ProdutoMapper.aplicarAtualizacao(produto, new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, null, null, null, false, 24, null
        ), null);
        assertThat(produto.isFardo()).isFalse();
        assertThat(produto.getUnidadesPorFardo()).isNull();

        // Todos nulos: nada é alterado
        ProdutoMapper.aplicarAtualizacao(produto, new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, null, null, null, null, null, null
        ), null);
        assertThat(produto.getNome()).isEqualTo("Novo Nome");

        // Nome ou unidade em branco
        assertThatThrownBy(() -> ProdutoMapper.aplicarAtualizacao(produto, new ProdutoAtualizacaoRequestDto(
                "   ", null, null, null, null, null, null, null, null, null, null
        ), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O nome do produto não pode ficar vazio");

        assertThatThrownBy(() -> ProdutoMapper.aplicarAtualizacao(produto, new ProdutoAtualizacaoRequestDto(
                null, null, "   ", null, null, null, null, null, null, null, null
        ), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A unidade de medida não pode ficar vazia");
    }

    @Test
    void validaPreenchimentoDeAoMenosUmCampoNoDtoDeAtualizacao() {
        assertThat(new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, null, null, null, null, null, null
        ).isAtualizacaoValida()).isFalse();

        assertThat(new ProdutoAtualizacaoRequestDto("A", null, null, null, null, null, null, null, null, null, null).isAtualizacaoValida()).isTrue();
        assertThat(new ProdutoAtualizacaoRequestDto(null, 1, null, null, null, null, null, null, null, null, null).isAtualizacaoValida()).isTrue();
        assertThat(new ProdutoAtualizacaoRequestDto(null, null, "un", null, null, null, null, null, null, null, null).isAtualizacaoValida()).isTrue();
        assertThat(new ProdutoAtualizacaoRequestDto(null, null, null, BigDecimal.ONE, null, null, null, null, null, null, null).isAtualizacaoValida()).isTrue();
        assertThat(new ProdutoAtualizacaoRequestDto(null, null, null, null, BigDecimal.ONE, null, null, null, null, null, null).isAtualizacaoValida()).isTrue();
        assertThat(new ProdutoAtualizacaoRequestDto(null, null, null, null, null, true, null, null, null, null, null).isAtualizacaoValida()).isTrue();
        assertThat(new ProdutoAtualizacaoRequestDto(null, null, null, null, null, null, BigDecimal.TEN, null, null, null, null).isAtualizacaoValida()).isTrue();
        assertThat(new ProdutoAtualizacaoRequestDto(null, null, null, null, null, null, null, BigDecimal.ZERO, null, null, null).isAtualizacaoValida()).isTrue();
        assertThat(new ProdutoAtualizacaoRequestDto(null, null, null, null, null, null, null, null, true, null, null).isAtualizacaoValida()).isTrue();
        assertThat(new ProdutoAtualizacaoRequestDto(null, null, null, null, null, null, null, null, null, 6, null).isAtualizacaoValida()).isTrue();
        assertThat(new ProdutoAtualizacaoRequestDto(null, null, null, null, null, null, null, null, null, null, false).isAtualizacaoValida()).isTrue();
    }
}
