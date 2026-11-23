package valhalla.core.stock.app.modules.financeiro.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Agregados das contas a pagar e receber. Datas são dias (DATE), sem horário. */
@Repository
public class IndicadoresContasRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public IndicadoresContasRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Contas em aberto de um tipo com vencimento até a data (inclui as vencidas). */
    public record EmAberto(BigDecimal valor, long quantidade, long vencidas, long vencemEmAte7Dias) {
    }

    /** Baixas por mês, separadas por tipo. */
    public record Baixas(Map<YearMonth, BigDecimal> recebidas, Map<YearMonth, BigDecimal> pagas) {
    }

    public EmAberto emAberto(UUID estab, String tipo, LocalDate hoje, LocalDate vencimentoAte) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(valor), 0) AS valor, COUNT(*) AS quantidade,
                       COUNT(*) FILTER (WHERE vencimento < :hoje) AS vencidas,
                       COUNT(*) FILTER (WHERE vencimento >= :hoje AND vencimento <= :hoje7) AS em7
                FROM conta_financeira
                WHERE estabelecimento_id = :estab AND tipo = :tipo AND status = 'ABERTA'
                  AND vencimento <= :ate
                """, new MapSqlParameterSource()
                        .addValue("estab", estab).addValue("tipo", tipo)
                        .addValue("hoje", hoje).addValue("hoje7", hoje.plusDays(7)).addValue("ate", vencimentoAte),
                (rs, n) -> new EmAberto(rs.getBigDecimal("valor"), rs.getLong("quantidade"),
                        rs.getLong("vencidas"), rs.getLong("em7")));
    }

    public Baixas baixasPorMes(UUID estab, LocalDate primeiroDia, LocalDate fimExclusivo) {
        Map<YearMonth, BigDecimal> recebidas = new HashMap<>();
        Map<YearMonth, BigDecimal> pagas = new HashMap<>();
        jdbc.query("""
                SELECT tipo, CAST(date_trunc('month', pago_em) AS date) AS mes, SUM(valor_pago) AS valor
                FROM conta_financeira
                WHERE estabelecimento_id = :estab AND status = 'PAGA'
                  AND pago_em >= :de AND pago_em < :ate
                GROUP BY 1, 2
                """, new MapSqlParameterSource()
                        .addValue("estab", estab).addValue("de", primeiroDia).addValue("ate", fimExclusivo),
                rs -> {
                    Map<YearMonth, BigDecimal> destino = "RECEBER".equals(rs.getString("tipo")) ? recebidas : pagas;
                    destino.put(YearMonth.from(rs.getDate("mes").toLocalDate()), rs.getBigDecimal("valor"));
                });
        return new Baixas(recebidas, pagas);
    }

    /** Recebido menos pago em contas baixadas até a data (inclusive). */
    public BigDecimal saldoBaixas(UUID estab, LocalDate ate) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN tipo = 'RECEBER' THEN valor_pago ELSE -valor_pago END), 0)
                FROM conta_financeira
                WHERE estabelecimento_id = :estab AND status = 'PAGA' AND pago_em <= :ate
                """, new MapSqlParameterSource().addValue("estab", estab).addValue("ate", ate), BigDecimal.class);
    }

    /** Despesas do DRE por competência (vencimento no período), sem as canceladas e sem mercadoria. */
    public Map<String, BigDecimal> despesasPorCategoria(UUID estab, LocalDate inicio, LocalDate fim) {
        Map<String, BigDecimal> resultado = new HashMap<>();
        jdbc.query("""
                SELECT categoria, SUM(COALESCE(valor_pago, valor)) AS valor
                FROM conta_financeira
                WHERE estabelecimento_id = :estab AND tipo = 'PAGAR' AND status <> 'CANCELADA'
                  AND categoria IN ('DESPESA_FIXA', 'DESPESA_VARIAVEL')
                  AND vencimento >= :inicio AND vencimento <= :fim
                GROUP BY categoria
                """, new MapSqlParameterSource()
                        .addValue("estab", estab).addValue("inicio", inicio).addValue("fim", fim),
                rs -> {
                    resultado.put(rs.getString("categoria"), rs.getBigDecimal("valor"));
                });
        return resultado;
    }
}
