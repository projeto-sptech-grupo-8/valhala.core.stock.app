package valhalla.core.stock.app.modules.estoque.mapper;

import valhalla.core.stock.app.modules.estoque.dto.ProdutoAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class ProdutoMapper {

    private ProdutoMapper() {
    }

    public static ProdutoResponseDto paraResposta(ProdutoEntity produto, EstoqueEntity estoque) {
        return paraResposta(produto, estoque, null, null);
    }

    public static ProdutoResponseDto paraResposta(
            ProdutoEntity produto,
            EstoqueEntity estoque,
            String lote,
            LocalDate validade
    ) {
        BigDecimal quantidadeEstoque = estoque == null ? null : estoque.getQuantidadeAtual();
        BigDecimal estoqueMinimo = estoque == null ? null : estoque.getEstoqueMinimo();

        return new ProdutoResponseDto(
                produto.getId(),
                produto.getSku(),
                produto.getNome(),
                produto.getTipo(),
                produto.getCategoria().getId(),
                produto.getCategoria().getNome(),
                produto.getUnidadeMedida(),
                produto.getPrecoCusto(),
                produto.getPrecoVenda(),
                calcularMargem(produto),
                produto.isFracionado(),
                produto.getVolumeEmbalagemMl(),
                produto.isFardo(),
                produto.getUnidadesPorFardo(),
                produto.isAtivo(),
                quantidadeEstoque,
                estoqueMinimo,
                lote,
                validade,
                produto.getCriadoEm(),
                produto.getAtualizadoEm()
        );
    }

    public static void aplicarAtualizacao(
            ProdutoEntity produto,
            ProdutoAtualizacaoRequestDto atualizacao,
            CategoriaEntity categoria
    ) {
        if (atualizacao.nome() != null) {
            produto.setNome(normalizarObrigatorio(atualizacao.nome(), "O nome do produto não pode ficar vazio"));
        }
        if (categoria != null) {
            produto.setCategoria(categoria);
        }
        if (atualizacao.unidadeMedida() != null) {
            produto.setUnidadeMedida(normalizarObrigatorio(
                    atualizacao.unidadeMedida(), "A unidade de medida não pode ficar vazia"
            ));
        }
        if (atualizacao.precoVenda() != null) {
            produto.setPrecoVenda(atualizacao.precoVenda());
        }
        if (atualizacao.precoCusto() != null) {
            produto.setPrecoCusto(atualizacao.precoCusto());
        }
        if (atualizacao.fracionado() != null) {
            produto.setFracionado(atualizacao.fracionado());
        }
        if (atualizacao.volumeEmbalagemMl() != null) {
            produto.setVolumeEmbalagemMl(atualizacao.volumeEmbalagemMl());
        }
        if (atualizacao.fardo() != null) {
            produto.setFardo(atualizacao.fardo());
            if (!atualizacao.fardo()) {
                produto.setUnidadesPorFardo(null);
            }
        }
        if (atualizacao.unidadesPorFardo() != null && produto.isFardo()) {
            produto.setUnidadesPorFardo(atualizacao.unidadesPorFardo());
        }
        if (atualizacao.ativo() != null) {
            produto.setAtivo(atualizacao.ativo());
        }
    }

    private static BigDecimal calcularMargem(ProdutoEntity produto) {
        if (produto.getPrecoCusto() == null || produto.getPrecoCusto().signum() == 0) {
            return null;
        }

        return produto.getPrecoVenda()
                .subtract(produto.getPrecoCusto())
                .divide(produto.getPrecoVenda(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static String normalizarObrigatorio(String valor, String mensagemErro) {
        if (valor.isBlank()) {
            throw new IllegalArgumentException(mensagemErro);
        }
        return valor.trim();
    }
}
