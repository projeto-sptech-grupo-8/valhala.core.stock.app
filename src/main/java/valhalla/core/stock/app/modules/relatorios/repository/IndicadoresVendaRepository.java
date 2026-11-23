package valhalla.core.stock.app.modules.relatorios.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agregados de vendas para dashboard e financeiro. Períodos são [de, ate) em horário da operação.
 * Venda é o pedido do caixa CONCLUIDO e conta pela data de conclusão. Pedido ESTORNADO (venda concluída e depois
 * desfeita) fica fora do faturamento e entra em cancelamentos; pedido CANCELADO antes de concluir nunca foi venda.
 * Pagamento recebido é o APROVADO, pelo valor aplicado ao pedido (sem troco nem juros do cartão).
 */
@Repository
public class IndicadoresVendaRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public IndicadoresVendaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Totais(long vendas, BigDecimal faturamento, BigDecimal cmv, long cancelamentos,
                         BigDecimal valorCancelado) {
    }

    public record ValorPorChave(String chave, BigDecimal valor) {
    }

    public Totais totais(UUID estab, LocalDateTime de, LocalDateTime ate) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE pe.status = 'CONCLUIDO') AS vendas,
                       COALESCE(SUM(pe.total) FILTER (WHERE pe.status = 'CONCLUIDO'), 0) AS faturamento,
                       COALESCE(SUM((SELECT SUM(i.quantidade * COALESCE(i.custo_unitario, cp.custo_unitario, 0))
                                      FROM item_pedido i
                                      LEFT JOIN vw_custo_produto cp ON cp.produto_id = i.produto_id
                                      WHERE i.pedido_id = pe.id)) FILTER (WHERE pe.status = 'CONCLUIDO'), 0) AS cmv,
                       COUNT(*) FILTER (WHERE pe.status = 'ESTORNADO') AS cancelamentos,
                       COALESCE(SUM(pe.total) FILTER (WHERE pe.status = 'ESTORNADO'), 0) AS valor_cancelado
                FROM pedido pe
                WHERE pe.estabelecimento_id = :estab
                  AND pe.concluido_em >= :de AND pe.concluido_em < :ate
                """, params(estab, de, ate), (rs, n) -> new Totais(
                rs.getLong("vendas"), rs.getBigDecimal("faturamento"), rs.getBigDecimal("cmv"),
                rs.getLong("cancelamentos"), rs.getBigDecimal("valor_cancelado")));
    }

    /** Valor recebido por forma de pagamento nas vendas concluídas do período (dinheiro já sem o troco). */
    public List<ValorPorChave> recebidoPorForma(UUID estab, LocalDateTime de, LocalDateTime ate) {
        return jdbc.query("""
                SELECT pg.forma AS chave, SUM(pg.valor) AS valor
                FROM pagamento_pedido pg
                JOIN pedido pe ON pe.id = pg.pedido_id
                WHERE pe.estabelecimento_id = :estab AND pe.status = 'CONCLUIDO' AND pg.status = 'APROVADO'
                  AND pe.concluido_em >= :de AND pe.concluido_em < :ate
                GROUP BY pg.forma
                ORDER BY 2 DESC
                """, params(estab, de, ate), (rs, n) -> new ValorPorChave(rs.getString("chave"), rs.getBigDecimal("valor")));
    }

    /** Faturamento por categoria, com o desconto do pedido rateado entre os itens. */
    public List<ValorPorChave> faturamentoPorCategoria(UUID estab, LocalDateTime de, LocalDateTime ate) {
        return jdbc.query("""
                SELECT COALESCE(i.categoria_nome, c.nome) AS chave, SUM(i.subtotal * pe.total / NULLIF(pe.subtotal, 0)) AS valor
                FROM item_pedido i
                JOIN pedido pe ON pe.id = i.pedido_id
                JOIN produto p ON p.id = i.produto_id
                JOIN categoria c ON c.id = p.categoria_id
                WHERE pe.estabelecimento_id = :estab AND pe.status = 'CONCLUIDO'
                  AND pe.concluido_em >= :de AND pe.concluido_em < :ate
                GROUP BY 1
                ORDER BY 2 DESC
                """, params(estab, de, ate), (rs, n) -> new ValorPorChave(rs.getString("chave"), rs.getBigDecimal("valor")));
    }

    /** Regime de caixa: valores recebidos das vendas (pagamentos não estornados) por mês do recebimento. */
    public Map<YearMonth, BigDecimal> recebimentosPorMes(UUID estab, LocalDate primeiroDia, LocalDate fimExclusivo) {
        Map<YearMonth, BigDecimal> resultado = new LinkedHashMap<>();
        jdbc.query("""
                SELECT CAST(date_trunc('month', pg.pago_em) AS date) AS mes, SUM(pg.valor) AS valor
                FROM pagamento_pedido pg
                JOIN pedido pe ON pe.id = pg.pedido_id
                WHERE pe.estabelecimento_id = :estab AND pg.status = 'APROVADO'
                  AND pg.pago_em >= :de AND pg.pago_em < :ate
                GROUP BY 1
                """, params(estab, primeiroDia.atStartOfDay(), fimExclusivo.atStartOfDay()), rs -> {
            resultado.put(YearMonth.from(rs.getDate("mes").toLocalDate()), rs.getBigDecimal("valor"));
        });
        return resultado;
    }

    /** Tudo o que já foi recebido das vendas (pagamentos não estornados), para o saldo realizado. */
    public BigDecimal recebidoAcumulado(UUID estab, LocalDateTime ate) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(pg.valor), 0)
                FROM pagamento_pedido pg
                JOIN pedido pe ON pe.id = pg.pedido_id
                WHERE pe.estabelecimento_id = :estab AND pg.status = 'APROVADO' AND pg.pago_em < :ate
                """, new MapSqlParameterSource().addValue("estab", estab).addValue("ate", Timestamp.valueOf(ate)),
                BigDecimal.class);
    }

    private static MapSqlParameterSource params(UUID estab, LocalDateTime de, LocalDateTime ate) {
        return new MapSqlParameterSource()
                .addValue("estab", estab)
                .addValue("de", Timestamp.valueOf(de))
                .addValue("ate", Timestamp.valueOf(ate));
    }
}
