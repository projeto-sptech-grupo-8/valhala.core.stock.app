package valhalla.core.stock.app.modules.caixa.service;

public final class EventoAuditoriaCaixa {

    public static final String ABERTURA_CAIXA = "ABERTURA_CAIXA";
    public static final String FECHAMENTO_CAIXA = "FECHAMENTO_CAIXA";
    public static final String DIVERGENCIA_CAIXA = "DIVERGENCIA_CAIXA";
    public static final String DIVERGENCIA_CAIXA_RESOLVIDA = "DIVERGENCIA_CAIXA_RESOLVIDA";
    public static final String SANGRIA = "SANGRIA";
    public static final String REFORCO = "REFORCO";
    public static final String CONFIGURACAO_CAIXA_ALTERADA = "CONFIGURACAO_CAIXA_ALTERADA";

    public static final String ENTIDADE_SESSAO_CAIXA = "SESSAO_CAIXA";
    public static final String ENTIDADE_CONFIGURACAO_CAIXA = "CONFIGURACAO_CAIXA";

    private EventoAuditoriaCaixa() {
    }
}
