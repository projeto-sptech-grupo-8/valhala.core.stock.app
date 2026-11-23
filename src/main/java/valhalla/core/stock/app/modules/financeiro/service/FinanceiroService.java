package valhalla.core.stock.app.modules.financeiro.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.financeiro.dto.ResultadoFinanceiroDto;
import valhalla.core.stock.app.modules.financeiro.dto.ResumoFinanceiroDto;
import valhalla.core.stock.app.modules.financeiro.repository.IndicadoresContasRepository;
import valhalla.core.stock.app.modules.relatorios.repository.IndicadoresVendaRepository;
import valhalla.core.stock.app.shared.security.ContextoAutenticado;
import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Indicadores financeiros. Receita, CMV e cancelamentos vêm das vendas; saldo, contas em aberto e despesas
 * vêm das contas a pagar e receber. Fluxo de caixa é realizado (pela data do recebimento/pagamento);
 * o resultado é por competência (vendas pela conclusão, despesas pelo vencimento).
 */
@Service
public class FinanceiroService {

    private static final int MESES_FLUXO = 6;
    private static final int DIAS_CONTAS = 30;
    private static final int PERIODO_MAXIMO_DIAS = 366;
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final IndicadoresVendaRepository vendas;
    private final IndicadoresContasRepository contas;

    public FinanceiroService(IndicadoresVendaRepository vendas, IndicadoresContasRepository contas) {
        this.vendas = vendas;
        this.contas = contas;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('VISUALIZAR_FINANCEIRO', authentication)")
    public ResumoFinanceiroDto resumo() {
        UUID estab = ContextoAutenticado.idEstabelecimento();
        LocalDate hoje = FusoHorarioOperacao.agora().toLocalDate();
        LocalDate amanha = hoje.plusDays(1);

        BigDecimal saldo = vendas.recebidoAcumulado(estab, amanha.atStartOfDay())
                .add(contas.saldoBaixas(estab, hoje));
        ResumoFinanceiroDto.ContasEmAberto aReceber = emAberto(estab, "RECEBER", hoje);
        ResumoFinanceiroDto.ContasEmAberto aPagar = emAberto(estab, "PAGAR", hoje);

        // mês atual até hoje × mesmo número de dias do mês anterior (limitado ao tamanho daquele mês)
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        LocalDate inicioAnterior = inicioMes.minusMonths(1);
        LocalDate fimAnterior = inicioAnterior.plusDays(
                Math.min(hoje.getDayOfMonth(), YearMonth.from(inicioAnterior).lengthOfMonth()));
        IndicadoresVendaRepository.Totais atual = vendas.totais(estab, inicioMes.atStartOfDay(), amanha.atStartOfDay());
        IndicadoresVendaRepository.Totais anterior = vendas.totais(estab, inicioAnterior.atStartOfDay(), fimAnterior.atStartOfDay());

        ResumoFinanceiroDto.Faturamento faturamento = new ResumoFinanceiroDto.Faturamento(
                atual.faturamento(), anterior.faturamento(),
                variacao(atual.faturamento(), anterior.faturamento()),
                dinheiro(atual.cmv()), percentual(atual.cmv(), atual.faturamento()), atual.vendas());

        return new ResumoFinanceiroDto(hoje, dinheiro(saldo), aReceber, aPagar,
                aPagar.valor().signum() == 0 ? null : saldo.divide(aPagar.valor(), 2, RoundingMode.HALF_UP),
                faturamento, fluxoCaixa(estab, inicioMes));
    }

    /** Resultado do período [inicio, fim], datas inclusivas. */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('VISUALIZAR_FINANCEIRO', authentication)")
    public ResultadoFinanceiroDto resultado(LocalDate inicio, LocalDate fim) {
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) >= PERIODO_MAXIMO_DIAS) {
            throw new IllegalArgumentException("O período pode ter no máximo 366 dias");
        }
        UUID estab = ContextoAutenticado.idEstabelecimento();
        IndicadoresVendaRepository.Totais totais = vendas.totais(
                estab, inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay());

        BigDecimal receitaBruta = totais.faturamento().add(totais.valorCancelado());
        BigDecimal receitaLiquida = totais.faturamento();
        BigDecimal lucroBruto = receitaLiquida.subtract(totais.cmv());
        var despesas = contas.despesasPorCategoria(estab, inicio, fim);
        BigDecimal fixas = despesas.getOrDefault("DESPESA_FIXA", BigDecimal.ZERO);
        BigDecimal variaveis = despesas.getOrDefault("DESPESA_VARIAVEL", BigDecimal.ZERO);
        BigDecimal lucroLiquido = lucroBruto.subtract(fixas).subtract(variaveis);

        List<ResultadoFinanceiroDto.VendaCategoria> porCategoria = vendas
                .faturamentoPorCategoria(estab, inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay()).stream()
                .map(linha -> new ResultadoFinanceiroDto.VendaCategoria(
                        linha.chave(), dinheiro(linha.valor()), percentual(linha.valor(), receitaLiquida)))
                .toList();

        return new ResultadoFinanceiroDto(inicio, fim,
                dinheiro(receitaBruta), dinheiro(totais.valorCancelado()), dinheiro(receitaLiquida),
                dinheiro(totais.cmv()), dinheiro(lucroBruto), percentual(lucroBruto, receitaLiquida),
                dinheiro(fixas), dinheiro(variaveis), dinheiro(lucroLiquido), percentual(lucroLiquido, receitaLiquida),
                percentual(totais.cmv(), receitaLiquida), totais.vendas(), porCategoria);
    }

    private ResumoFinanceiroDto.ContasEmAberto emAberto(UUID estab, String tipo, LocalDate hoje) {
        IndicadoresContasRepository.EmAberto linha = contas.emAberto(estab, tipo, hoje, hoje.plusDays(DIAS_CONTAS));
        return new ResumoFinanceiroDto.ContasEmAberto(
                linha.valor(), linha.quantidade(), linha.vencidas(), linha.vencemEmAte7Dias());
    }

    private List<ResumoFinanceiroDto.FluxoMensal> fluxoCaixa(UUID estab, LocalDate inicioMesAtual) {
        LocalDate primeiroDia = inicioMesAtual.minusMonths(MESES_FLUXO - 1L);
        LocalDate fimExclusivo = inicioMesAtual.plusMonths(1);
        var recebimentosVendas = vendas.recebimentosPorMes(estab, primeiroDia, fimExclusivo);
        var baixas = contas.baixasPorMes(estab, primeiroDia, fimExclusivo);
        YearMonth primeiroMes = YearMonth.from(primeiroDia);
        return IntStream.range(0, MESES_FLUXO)
                .mapToObj(primeiroMes::plusMonths)
                .map(mes -> {
                    BigDecimal receitas = recebimentosVendas.getOrDefault(mes, BigDecimal.ZERO)
                            .add(baixas.recebidas().getOrDefault(mes, BigDecimal.ZERO));
                    BigDecimal despesas = baixas.pagas().getOrDefault(mes, BigDecimal.ZERO);
                    return new ResumoFinanceiroDto.FluxoMensal(mes.toString(), dinheiro(receitas),
                            dinheiro(despesas), dinheiro(receitas.subtract(despesas)));
                })
                .toList();
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal todo) {
        return todo == null || todo.signum() == 0
                ? null
                : parte.multiply(CEM).divide(todo, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        return anterior.signum() == 0
                ? null
                : atual.subtract(anterior).multiply(CEM).divide(anterior, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
