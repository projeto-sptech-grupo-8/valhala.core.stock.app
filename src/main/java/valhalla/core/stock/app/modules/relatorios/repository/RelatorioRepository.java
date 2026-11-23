package valhalla.core.stock.app.modules.relatorios.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import valhalla.core.stock.app.modules.estoque.entity.MotivoPerda;
import valhalla.core.stock.app.modules.relatorios.dto.RelatorioLinhas;
import valhalla.core.stock.app.modules.relatorios.dto.RelatorioResponseDto;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Consultas agregadas em SQL: o banco agrupa e o front só desenha. Todas filtram por estabelecimento e período. */
@Repository
public class RelatorioRepository {

    /**
     * Itens de pedidos concluídos no período, com categoria. A receita do item recebe sua parte do desconto
     * do pedido (proporcional ao subtotal), para que a soma das linhas feche com o faturamento dos totais.
     * O CMV usa o custo gravado no momento da venda; itens sem custo gravado usam o custo atual do produto.
     * A categoria é a gravada no item (a da época da venda), com a atual como fallback.
     */
    private static final String VENDAS = """
            WITH v AS (
                SELECT i.produto_id, pr.nome, COALESCE(i.categoria_nome, c.nome) AS categoria, i.quantidade,
                       i.subtotal * pe.total / NULLIF(pe.subtotal, 0) AS receita,
                       i.quantidade * COALESCE(i.custo_unitario, cp.custo_unitario, 0) AS cmv
                FROM item_pedido i
                JOIN pedido pe ON pe.id = i.pedido_id
                JOIN produto pr ON pr.id = i.produto_id
                JOIN categoria c ON c.id = pr.categoria_id
                LEFT JOIN vw_custo_produto cp ON cp.produto_id = i.produto_id
                WHERE pe.estabelecimento_id = :estab AND pe.status = 'CONCLUIDO'
                  AND pe.concluido_em >= :de AND pe.concluido_em < :ate
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public RelatorioRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RelatorioResponseDto.Totais totais(UUID estab, LocalDateTime de, LocalDateTime ate) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS vendas, COALESCE(SUM(total), 0) AS faturamento
                FROM pedido
                WHERE estabelecimento_id = :estab AND status = 'CONCLUIDO'
                  AND concluido_em >= :de AND concluido_em < :ate
                """, params(estab, de, ate),
                (rs, n) -> new RelatorioResponseDto.Totais(rs.getLong("vendas"), rs.getBigDecimal("faturamento")));
    }

    public List<RelatorioLinhas.CurvaAbc> curvaAbc(UUID estab, LocalDateTime de, LocalDateTime ate) {
        return jdbc.query(VENDAS + """
                SELECT t.*,
                       CASE WHEN t.acumulado - t.percentual < 80 THEN 'A'
                            WHEN t.acumulado - t.percentual < 95 THEN 'B' ELSE 'C' END AS classe
                FROM (
                    SELECT g.*,
                           ROW_NUMBER() OVER (ORDER BY g.receita DESC, g.produto_id) AS posicao,
                           g.receita * 100 / NULLIF(SUM(g.receita) OVER (), 0) AS percentual,
                           SUM(g.receita) OVER (ORDER BY g.receita DESC, g.produto_id) * 100
                               / NULLIF(SUM(g.receita) OVER (), 0) AS acumulado
                    FROM (SELECT produto_id, nome, categoria, SUM(quantidade) AS quantidade, SUM(receita) AS receita
                          FROM v GROUP BY produto_id, nome, categoria) g
                ) t
                ORDER BY t.posicao
                """, params(estab, de, ate),
                (rs, n) -> new RelatorioLinhas.CurvaAbc(
                        rs.getObject("produto_id", UUID.class), rs.getString("nome"), rs.getString("categoria"),
                        rs.getBigDecimal("quantidade"), rs.getBigDecimal("receita"), rs.getInt("posicao"),
                        rs.getBigDecimal("percentual"), rs.getBigDecimal("acumulado"), rs.getString("classe")));
    }

    public List<RelatorioLinhas.MargemCategoria> margemCategorias(UUID estab, LocalDateTime de, LocalDateTime ate) {
        return jdbc.query(VENDAS + """
                SELECT g.*, g.receita - g.cmv AS lucro,
                       (g.receita - g.cmv) * 100 / NULLIF(g.receita, 0) AS margem,
                       g.receita * 100 / NULLIF(SUM(g.receita) OVER (), 0) AS participacao
                FROM (SELECT categoria, SUM(quantidade) AS quantidade, SUM(receita) AS receita, SUM(cmv) AS cmv
                      FROM v GROUP BY categoria) g
                ORDER BY margem DESC NULLS LAST
                """, params(estab, de, ate),
                (rs, n) -> new RelatorioLinhas.MargemCategoria(
                        rs.getString("categoria"), rs.getBigDecimal("quantidade"), rs.getBigDecimal("receita"),
                        rs.getBigDecimal("cmv"), rs.getBigDecimal("lucro"), rs.getBigDecimal("margem"),
                        rs.getBigDecimal("participacao")));
    }

    /**
     * Dias com saldo zerado, reconstruídos pelas movimentações (do saldo zero até a movimentação seguinte).
     * Parte do saldo vigente no início do período (última movimentação anterior), sem varrer todo o histórico.
     * Venda perdida = dias zerados × média diária vendida nos dias em que havia estoque.
     */
    public List<RelatorioLinhas.Ruptura> rupturas(UUID estab, LocalDateTime de, LocalDateTime ate, int dias) {
        return jdbc.query(VENDAS + """
                , anterior AS (
                    SELECT DISTINCT ON (m.estoque_id) m.estoque_id, m.id, m.ocorrido_em, m.saldo_posterior
                    FROM movimentacao_estoque m
                    JOIN estoque e ON e.id = m.estoque_id
                    JOIN produto p ON p.id = e.produto_id
                    WHERE p.estabelecimento_id = :estab AND m.ocorrido_em < :de
                    ORDER BY m.estoque_id, m.ocorrido_em DESC, m.id DESC
                ), no_periodo AS (
                    SELECT m.estoque_id, m.id, m.ocorrido_em, m.saldo_posterior
                    FROM movimentacao_estoque m
                    JOIN estoque e ON e.id = m.estoque_id
                    JOIN produto p ON p.id = e.produto_id
                    WHERE p.estabelecimento_id = :estab AND m.ocorrido_em >= :de AND m.ocorrido_em < :ate
                ), mov AS (
                    SELECT e.produto_id, x.ocorrido_em AS ini, x.saldo_posterior,
                           LEAD(x.ocorrido_em) OVER (PARTITION BY x.estoque_id ORDER BY x.ocorrido_em, x.id) AS fim
                    FROM (SELECT * FROM anterior UNION ALL SELECT * FROM no_periodo) x
                    JOIN estoque e ON e.id = x.estoque_id
                ), zerado AS (
                    SELECT DISTINCT z.produto_id, CAST(d AS date) AS dia
                    FROM mov z
                    CROSS JOIN LATERAL generate_series(
                        CAST(GREATEST(z.ini, :de) AS date), CAST(COALESCE(z.fim, :ate) AS date), interval '1 day') d
                    WHERE z.saldo_posterior <= 0
                      AND CAST(d AS date) < CAST(:ate AS date)
                ), ilha AS (
                    SELECT produto_id, dia,
                           CASE WHEN dia - LAG(dia) OVER (PARTITION BY produto_id ORDER BY dia) = 1 THEN 0 ELSE 1 END AS novo
                    FROM zerado
                ), vendido AS (
                    SELECT produto_id, SUM(quantidade) AS quantidade FROM v GROUP BY produto_id
                ), r AS (
                    SELECT p.id, p.nome, c.nome AS categoria, p.preco_venda,
                           COUNT(ilha.dia)::int AS dias, COALESCE(SUM(ilha.novo), 0)::int AS ocorrencias,
                           COALESCE(MAX(vendido.quantidade), 0) AS vendido
                    FROM produto p
                    JOIN categoria c ON c.id = p.categoria_id
                    LEFT JOIN ilha ON ilha.produto_id = p.id
                    LEFT JOIN vendido ON vendido.produto_id = p.id
                    WHERE p.estabelecimento_id = :estab AND p.tipo = 'PADRAO' AND p.ativo
                    GROUP BY p.id, p.nome, c.nome, p.preco_venda
                ), estimado AS (
                    -- média diária só dos dias com estoque: zerado o período todo não há base de estimativa
                    SELECT r.*,
                           CASE WHEN :diasPeriodo - r.dias > 0
                                THEN ROUND(r.dias * r.vendido / (:diasPeriodo - r.dias), 0)
                                ELSE 0 END AS perdidas
                    FROM r
                )
                SELECT id, nome, categoria, dias, ocorrencias, perdidas, perdidas * preco_venda AS venda_perdida,
                       dias * 100.0 / :diasPeriodo AS percentual_dias
                FROM estimado
                ORDER BY venda_perdida DESC, dias DESC, nome
                """, params(estab, de, ate).addValue("diasPeriodo", dias),
                (rs, n) -> new RelatorioLinhas.Ruptura(
                        rs.getObject("id", UUID.class), rs.getString("nome"), rs.getString("categoria"),
                        rs.getInt("dias"), rs.getInt("ocorrencias"), rs.getBigDecimal("perdidas"),
                        rs.getBigDecimal("venda_perdida"), rs.getBigDecimal("percentual_dias")));
    }

    public List<RelatorioLinhas.Horario> horarios(UUID estab, LocalDateTime de, LocalDateTime ate) {
        return jdbc.query("""
                SELECT EXTRACT(ISODOW FROM pe.concluido_em)::int - 1 AS dia_semana,
                       EXTRACT(HOUR FROM pe.concluido_em)::int AS hora,
                       COUNT(*) AS vendas, SUM(pe.total) AS receita
                FROM pedido pe
                WHERE pe.estabelecimento_id = :estab AND pe.status = 'CONCLUIDO'
                  AND pe.concluido_em >= :de AND pe.concluido_em < :ate
                GROUP BY 1, 2
                ORDER BY 1, 2
                """, params(estab, de, ate),
                (rs, n) -> new RelatorioLinhas.Horario(
                        rs.getInt("dia_semana"), rs.getInt("hora"), rs.getLong("vendas"), rs.getBigDecimal("receita")));
    }

    public List<RelatorioLinhas.Canal> canais(UUID estab, LocalDateTime de, LocalDateTime ate) {
        return jdbc.query("""
                SELECT g.*, g.faturamento / g.vendas AS ticket_medio,
                       g.faturamento * 100 / NULLIF(SUM(g.faturamento) OVER (), 0) AS participacao,
                       g.itens / g.vendas AS itens_por_venda
                FROM (
                    SELECT CASE pe.canal WHEN 'BALCAO' THEN 'Balcão' WHEN 'MESA' THEN 'Mesa'
                                WHEN 'RETIRADA' THEN 'Retirada' WHEN 'DELIVERY' THEN 'Delivery'
                                ELSE initcap(lower(pe.canal)) END AS canal,
                           COUNT(*) AS vendas, SUM(pe.total) AS faturamento,
                           SUM((SELECT COALESCE(SUM(i.quantidade), 0) FROM item_pedido i WHERE i.pedido_id = pe.id)) AS itens
                    FROM pedido pe
                    WHERE pe.estabelecimento_id = :estab AND pe.status = 'CONCLUIDO'
                      AND pe.concluido_em >= :de AND pe.concluido_em < :ate
                    GROUP BY pe.canal
                ) g
                ORDER BY g.faturamento DESC
                """, params(estab, de, ate),
                (rs, n) -> new RelatorioLinhas.Canal(
                        rs.getString("canal"), rs.getLong("vendas"), rs.getBigDecimal("faturamento"),
                        rs.getBigDecimal("ticket_medio"), rs.getBigDecimal("participacao"),
                        rs.getBigDecimal("itens_por_venda")));
    }

    /**
     * Perdas a custo, só do tipo PERDA, agrupadas pelo motivo estruturado. Ajustes e estornos não são perda.
     * truncamento: unidade do date_trunc (day, week ou month) — vem de uma lista fixa no serviço, nunca do cliente.
     */
    public List<RelatorioLinhas.Perda> perdas(UUID estab, LocalDateTime de, LocalDateTime ate, String truncamento) {
        return jdbc.query("""
                SELECT CAST(date_trunc('%s', m.ocorrido_em) AS date) AS periodo,
                       m.motivo_perda,
                       ROUND(SUM(m.quantidade / CASE WHEN p.fracionado THEN NULLIF(p.volume_embalagem_ml, 0) ELSE 1 END
                           * COALESCE(cp.custo_unitario, 0)), 2) AS valor
                FROM movimentacao_estoque m
                JOIN estoque e ON e.id = m.estoque_id
                JOIN produto p ON p.id = e.produto_id
                LEFT JOIN vw_custo_produto cp ON cp.produto_id = p.id
                WHERE p.estabelecimento_id = :estab AND m.tipo = 'PERDA'
                  AND m.ocorrido_em >= :de AND m.ocorrido_em < :ate
                GROUP BY 1, 2
                ORDER BY 1, 2
                """.formatted(truncamento), params(estab, de, ate),
                (rs, n) -> new RelatorioLinhas.Perda(
                        rs.getDate("periodo").toLocalDate(),
                        MotivoPerda.valueOf(rs.getString("motivo_perda")).rotulo(),
                        rs.getBigDecimal("valor")));
    }

    private static MapSqlParameterSource params(UUID estab, LocalDateTime de, LocalDateTime ate) {
        return new MapSqlParameterSource()
                .addValue("estab", estab)
                .addValue("de", Timestamp.valueOf(de))
                .addValue("ate", Timestamp.valueOf(ate));
    }
}
