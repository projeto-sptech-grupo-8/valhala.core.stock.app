package valhalla.core.stock.app.modules.caixa.service;

import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.caixa.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.exception.RateLimitExcedidoException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterCaixaService {

    private static final long JANELA_UM_MINUTO_MS = 60_000L;

    private final MercadoPagoPointProperties properties;
    private final ConcurrentHashMap<String, Deque<Long>> janelas = new ConcurrentHashMap<>();

    public RateLimiterCaixaService(MercadoPagoPointProperties properties) {
        this.properties = properties;
    }

    public void verificarLimiteCriacaoPoint(UUID usuarioId) {
        String chave = "point:criacao:" + usuarioId;
        consumir(
                chave,
                properties.rateLimitCriacaoPorMinuto(),
                "Limite de criação de cobranças na Point excedido. Aguarde alguns segundos e tente novamente."
        );
    }

    public void verificarLimiteWebhook(String origemIp) {
        String chave = "point:webhook:" + (origemIp == null || origemIp.isBlank() ? "desconhecido" : origemIp);
        consumir(
                chave,
                properties.rateLimitWebhookPorMinuto(),
                "Limite de notificações webhook por minuto excedido."
        );
    }

    public void limpar() {
        janelas.clear();
    }

    private void consumir(String chave, int limitePorMinuto, String mensagemErro) {
        long agora = System.currentTimeMillis();
        long corte = agora - JANELA_UM_MINUTO_MS;
        Deque<Long> fila = janelas.computeIfAbsent(chave, ignored -> new ArrayDeque<>());

        synchronized (fila) {
            while (!fila.isEmpty() && fila.peekFirst() <= corte) {
                fila.pollFirst();
            }
            if (fila.size() >= limitePorMinuto) {
                throw new RateLimitExcedidoException(mensagemErro);
            }
            fila.addLast(agora);
        }
    }
}
