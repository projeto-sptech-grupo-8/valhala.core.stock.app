package valhalla.core.stock.app.modules.estoque.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoLoteEntity;
import valhalla.core.stock.app.modules.estoque.repository.LoteEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoLoteRepository;
import valhalla.core.stock.app.modules.estoque.strategy.PoliticaLote;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Mantém o saldo por lote de um estoque. Deve ser chamado com o estoque bloqueado (FOR UPDATE):
 * - crédito vai para o lote informado (ou SEM-LOTE), validando a validade do lote;
 * - débito consome os lotes por FEFO (vence antes, sai antes; sem validade por último), ou um lote específico;
 * - saldo que não está em nenhum lote (dados anteriores aos lotes) é reconciliado como SEM-LOTE.
 */
@Component
public class ControleLotesEstoque {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Comparator<LoteEstoqueEntity> FEFO = Comparator
            .comparing(LoteEstoqueEntity::getValidade, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(LoteEstoqueEntity::getCriadoEm, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(LoteEstoqueEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final LoteEstoqueRepository loteEstoqueRepository;
    private final MovimentacaoLoteRepository movimentacaoLoteRepository;
    private final Clock clock;

    @Autowired
    public ControleLotesEstoque(
            LoteEstoqueRepository loteEstoqueRepository,
            MovimentacaoLoteRepository movimentacaoLoteRepository
    ) {
        this(loteEstoqueRepository, movimentacaoLoteRepository, Clock.systemDefaultZone());
    }

    ControleLotesEstoque(
            LoteEstoqueRepository loteEstoqueRepository,
            MovimentacaoLoteRepository movimentacaoLoteRepository,
            Clock clock
    ) {
        this.loteEstoqueRepository = loteEstoqueRepository;
        this.movimentacaoLoteRepository = movimentacaoLoteRepository;
        this.clock = clock;
    }

    public record Alocacao(LoteEstoqueEntity lote, BigDecimal quantidade) {
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    /** Aplica nos lotes o efeito de uma movimentação; chamar antes de alterar o saldo do estoque. */
    public List<Alocacao> movimentar(
            EstoqueEntity estoque,
            PoliticaLote politica,
            BigDecimal quantidade,
            String codigoLote,
            LocalDate validade,
            Long loteId
    ) {
        if (politica == PoliticaLote.CREDITO) {
            if (loteId != null) {
                throw new IllegalArgumentException("O lote de origem (loteId) só se aplica a saídas, ajustes negativos e perdas");
            }
            return List.of(creditar(estoque, codigoLote, validade, quantidade));
        }
        if (validade != null) {
            throw new IllegalArgumentException("A validade só é informada em entradas e ajustes positivos");
        }
        return debitar(estoque, quantidade, politica == PoliticaLote.DEBITO_COM_VENCIDOS, codigoLote, loteId);
    }

    /** Saldo inicial do cadastro: o estoque já nasce com a quantidade, que vai para o lote SEM-LOTE. */
    public List<Alocacao> registrarSaldoInicial(EstoqueEntity estoque, BigDecimal quantidade) {
        List<LoteEstoqueEntity> lotes = reconciliar(estoque);
        return lotes.stream()
                .filter(lote -> LoteEstoqueEntity.SEM_LOTE.equals(lote.getCodigo()))
                .findFirst()
                .map(lote -> List.of(new Alocacao(lote, quantidade)))
                .orElse(List.of());
    }

    public void vincular(MovimentacaoEstoqueEntity movimentacao, List<Alocacao> alocacoes) {
        for (Alocacao alocacao : alocacoes) {
            MovimentacaoLoteEntity vinculo = new MovimentacaoLoteEntity();
            vinculo.setMovimentacao(movimentacao);
            vinculo.setLote(alocacao.lote());
            vinculo.setQuantidade(alocacao.quantidade());
            movimentacaoLoteRepository.save(vinculo);
        }
    }

    /** Códigos dos lotes movimentados, para o campo texto da movimentação (máx. 100 caracteres). */
    public static String descreverCodigos(List<Alocacao> alocacoes) {
        String codigos = alocacoes.stream()
                .map(alocacao -> alocacao.lote().getCodigo())
                .distinct()
                .collect(Collectors.joining(", "));
        if (codigos.isEmpty()) {
            return null;
        }
        return codigos.length() <= 100 ? codigos : codigos.substring(0, 97) + "...";
    }

    /**
     * Garante que a soma dos lotes é igual ao saldo do estoque: a diferença positiva vai para SEM-LOTE e
     * a negativa sai dos lotes por FEFO. Retorna os lotes do estoque já ajustados.
     */
    public List<LoteEstoqueEntity> reconciliar(EstoqueEntity estoque) {
        List<LoteEstoqueEntity> lotes = estoque.getId() == null
                ? new ArrayList<>()
                : new ArrayList<>(loteEstoqueRepository.findAllByEstoqueId(estoque.getId()));
        BigDecimal soma = lotes.stream().map(LoteEstoqueEntity::getQuantidadeAtual).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diferenca = estoque.getQuantidadeAtual().subtract(soma);
        if (diferenca.signum() > 0) {
            LoteEstoqueEntity semLote = lotes.stream()
                    .filter(lote -> LoteEstoqueEntity.SEM_LOTE.equals(lote.getCodigo()))
                    .findFirst()
                    .orElseGet(() -> {
                        LoteEstoqueEntity novo = novoLote(estoque, LoteEstoqueEntity.SEM_LOTE, null);
                        lotes.add(novo);
                        return novo;
                    });
            semLote.setQuantidadeAtual(semLote.getQuantidadeAtual().add(diferenca));
            loteEstoqueRepository.save(semLote);
        } else if (diferenca.signum() < 0) {
            BigDecimal restante = diferenca.negate();
            for (LoteEstoqueEntity lote : lotes.stream().sorted(FEFO).toList()) {
                BigDecimal consumo = lote.getQuantidadeAtual().min(restante);
                if (consumo.signum() > 0) {
                    lote.setQuantidadeAtual(lote.getQuantidadeAtual().subtract(consumo));
                    loteEstoqueRepository.save(lote);
                    restante = restante.subtract(consumo);
                }
            }
        }
        return lotes;
    }

    private Alocacao creditar(EstoqueEntity estoque, String codigoInformado, LocalDate validade, BigDecimal quantidade) {
        // entrada sem código mas com validade vira um lote identificado pela validade
        String codigo = codigoInformado != null
                ? codigoInformado
                : validade != null ? "VAL-" + validade : LoteEstoqueEntity.SEM_LOTE;
        List<LoteEstoqueEntity> lotes = reconciliar(estoque);
        LoteEstoqueEntity lote = lotes.stream()
                .filter(existente -> existente.getCodigo().equalsIgnoreCase(codigo))
                .findFirst()
                .orElse(null);
        if (lote == null) {
            lote = novoLote(estoque, codigo, validade);
        } else if (validade != null && !validade.equals(lote.getValidade())) {
            if (lote.getValidade() != null) {
                throw new IllegalArgumentException("O lote %s já está cadastrado com validade %s"
                        .formatted(lote.getCodigo(), DATA.format(lote.getValidade())));
            }
            if (lote.getQuantidadeAtual().signum() > 0) {
                throw new IllegalArgumentException(
                        "O lote %s já possui saldo sem validade; informe outro código de lote".formatted(lote.getCodigo()));
            }
            lote.setValidade(validade);
        }
        lote.setQuantidadeAtual(lote.getQuantidadeAtual().add(quantidade));
        loteEstoqueRepository.save(lote);
        return new Alocacao(lote, quantidade);
    }

    private List<Alocacao> debitar(
            EstoqueEntity estoque,
            BigDecimal quantidade,
            boolean permiteVencidos,
            String codigoLote,
            Long loteId
    ) {
        List<LoteEstoqueEntity> lotes = reconciliar(estoque);
        LocalDate hoje = hoje();

        if (loteId != null || codigoLote != null) {
            LoteEstoqueEntity lote = lotes.stream()
                    .filter(existente -> loteId != null
                            ? Objects.equals(existente.getId(), loteId)
                            : existente.getCodigo().equalsIgnoreCase(codigoLote))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Lote não encontrado para este produto"));
            if (!permiteVencidos && lote.vencidoEm(hoje)) {
                throw new IllegalArgumentException("O lote %s venceu em %s e não pode sair como venda/consumo; registre a perda"
                        .formatted(lote.getCodigo(), DATA.format(lote.getValidade())));
            }
            if (lote.getQuantidadeAtual().compareTo(quantidade) < 0) {
                throw new IllegalArgumentException("Saldo insuficiente no lote %s: disponível %s"
                        .formatted(lote.getCodigo(), lote.getQuantidadeAtual().stripTrailingZeros().toPlainString()));
            }
            lote.setQuantidadeAtual(lote.getQuantidadeAtual().subtract(quantidade));
            loteEstoqueRepository.save(lote);
            return List.of(new Alocacao(lote, quantidade));
        }

        List<LoteEstoqueEntity> candidatos = lotes.stream()
                .filter(lote -> lote.getQuantidadeAtual().signum() > 0)
                .filter(lote -> permiteVencidos || !lote.vencidoEm(hoje))
                .sorted(FEFO)
                .toList();
        BigDecimal disponivel = candidatos.stream().map(LoteEstoqueEntity::getQuantidadeAtual).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (disponivel.compareTo(quantidade) < 0) {
            BigDecimal vencido = lotes.stream()
                    .filter(lote -> lote.vencidoEm(hoje))
                    .map(LoteEstoqueEntity::getQuantidadeAtual)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!permiteVencidos && vencido.signum() > 0) {
                throw new IllegalArgumentException("Saldo dentro da validade insuficiente: %s vencido(s) no estoque; registre a perda dos lotes vencidos"
                        .formatted(vencido.stripTrailingZeros().toPlainString()));
            }
            throw new IllegalArgumentException("Estoque insuficiente para realizar a movimentação");
        }

        List<Alocacao> alocacoes = new ArrayList<>();
        BigDecimal restante = quantidade;
        for (LoteEstoqueEntity lote : candidatos) {
            if (restante.signum() == 0) {
                break;
            }
            BigDecimal consumo = lote.getQuantidadeAtual().min(restante);
            lote.setQuantidadeAtual(lote.getQuantidadeAtual().subtract(consumo));
            loteEstoqueRepository.save(lote);
            alocacoes.add(new Alocacao(lote, consumo));
            restante = restante.subtract(consumo);
        }
        return alocacoes;
    }

    private LoteEstoqueEntity novoLote(EstoqueEntity estoque, String codigo, LocalDate validade) {
        LoteEstoqueEntity lote = new LoteEstoqueEntity();
        lote.setEstoque(estoque);
        lote.setCodigo(codigo);
        lote.setValidade(validade);
        lote.setQuantidadeAtual(BigDecimal.ZERO);
        lote.setCriadoEm(LocalDateTime.now(clock));
        return lote;
    }
}
