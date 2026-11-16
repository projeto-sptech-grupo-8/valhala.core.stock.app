package valhalla.core.stock.app.modules.caixa.sessao.service;

public final class EventoAuditoriaCaixa {

    public static final String ABERTURA_CAIXA = "ABERTURA_CAIXA";
    public static final String FECHAMENTO_CAIXA = "FECHAMENTO_CAIXA";
    public static final String DIVERGENCIA_CAIXA = "DIVERGENCIA_CAIXA";
    public static final String DIVERGENCIA_CAIXA_RESOLVIDA = "DIVERGENCIA_CAIXA_RESOLVIDA";
    public static final String SANGRIA = "SANGRIA";
    public static final String REFORCO = "REFORCO";
    public static final String CONFIGURACAO_CAIXA_ALTERADA = "CONFIGURACAO_CAIXA_ALTERADA";
    public static final String CAIXA_CRIADO = "CAIXA_CRIADO";
    public static final String CAIXA_ALTERADO = "CAIXA_ALTERADO";
    public static final String CAIXA_EXCLUIDO = "CAIXA_EXCLUIDO";
    public static final String TERMINAL_CAIXA_CRIADO = "TERMINAL_CAIXA_CRIADO";
    public static final String TERMINAL_CAIXA_ALTERADO = "TERMINAL_CAIXA_ALTERADO";
    public static final String TAXA_PAGAMENTO_CRIADA = "TAXA_PAGAMENTO_CRIADA";
    public static final String TAXA_PAGAMENTO_ALTERADA = "TAXA_PAGAMENTO_ALTERADA";

    public static final String PEDIDO_CRIADO = "PEDIDO_CRIADO";
    public static final String PEDIDO_CANCELADO = "PEDIDO_CANCELADO";
    public static final String ITEM_REMOVIDO = "ITEM_REMOVIDO";
    public static final String DESCONTO_CONCEDIDO = "DESCONTO_CONCEDIDO";
    public static final String DESCONTO_SOLICITADO = "DESCONTO_SOLICITADO";
    public static final String DESCONTO_ACIMA_DO_LIMITE = "DESCONTO_ACIMA_DO_LIMITE";
    public static final String DESCONTO_NEGADO = "DESCONTO_NEGADO";
    public static final String DESCONTO_REMOVIDO = "DESCONTO_REMOVIDO";
    public static final String PAGAMENTO_REGISTRADO = "PAGAMENTO_REGISTRADO";
    public static final String PAGAMENTO_POINT_ENVIADO = "PAGAMENTO_POINT_ENVIADO";
    public static final String PAGAMENTO_POINT_APROVADO = "PAGAMENTO_POINT_APROVADO";
    public static final String PAGAMENTO_POINT_RECUSADO = "PAGAMENTO_POINT_RECUSADO";
    public static final String PAGAMENTO_POINT_EXPIRADO = "PAGAMENTO_POINT_EXPIRADO";
    public static final String PAGAMENTO_POINT_FALHOU = "PAGAMENTO_POINT_FALHOU";
    public static final String PAGAMENTO_POINT_CANCELAMENTO_SOLICITADO = "PAGAMENTO_POINT_CANCELAMENTO_SOLICITADO";
    public static final String CONCILIACAO_MANUAL = "CONCILIACAO_MANUAL";
    public static final String PAGAMENTO_CANCELADO = "PAGAMENTO_CANCELADO";
    public static final String PAGAMENTO_ESTORNADO = "PAGAMENTO_ESTORNADO";

    public static final String ENTIDADE_SESSAO_CAIXA = "SESSAO_CAIXA";
    public static final String ENTIDADE_CONFIGURACAO_CAIXA = "CONFIGURACAO_CAIXA";
    public static final String ENTIDADE_CAIXA = "CAIXA";
    public static final String ENTIDADE_TERMINAL_CAIXA = "TERMINAL_CAIXA";
    public static final String ENTIDADE_TAXA_PAGAMENTO = "TAXA_PAGAMENTO";
    public static final String ENTIDADE_PEDIDO = "PEDIDO";
    public static final String ENTIDADE_PAGAMENTO_PEDIDO = "PAGAMENTO_PEDIDO";

    private EventoAuditoriaCaixa() {
    }
}
