package valhalla.core.stock.app.modules.caixa.pedido.enums;

import java.util.EnumSet;
import java.util.Set;

public enum StatusPagamentoPedido {
    PENDENTE,
    PENDENTE_NA_POINT,
    EM_TERMINAL,
    APROVADO,
    RECUSADO,
    EXPIRADO,
    CANCELAMENTO_SOLICITADO,
    CANCELADO,
    ACAO_NECESSARIA,
    PENDENTE_CONCILIACAO,
    ESTORNO_EM_ANDAMENTO,
    ESTORNADO;

    public static final Set<StatusPagamentoPedido> PENDENTES_NO_FECHAMENTO =
            EnumSet.of(
                    PENDENTE,
                    PENDENTE_NA_POINT,
                    EM_TERMINAL,
                    CANCELAMENTO_SOLICITADO,
                    ACAO_NECESSARIA,
                    PENDENTE_CONCILIACAO,
                    ESTORNO_EM_ANDAMENTO
            );

    public boolean bloqueiaFechamento() {
        return PENDENTES_NO_FECHAMENTO.contains(this);
    }
}
