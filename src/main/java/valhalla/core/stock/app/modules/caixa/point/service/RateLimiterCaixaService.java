package valhalla.core.stock.app.modules.caixa.point.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.caixa.point.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.exception.RateLimitExcedidoException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Service
public class RateLimiterCaixaService {

    static final int MAX_CHAVES_PADRAO = 10_000;
    private static final long JANELA_UM_MINUTO_MS = 60_000L;

    private final MercadoPagoPointProperties properties;
    private final int maxChaves;
    private final LongSupplier relogioMs;
    private final ConcurrentHashMap<String, JanelaUso> janelas = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiterCaixaService(MercadoPagoPointProperties properties) {
        this(properties, MAX_CHAVES_PADRAO, System::currentTimeMillis);
    }

    RateLimiterCaixaService(
            MercadoPagoPointProperties properties,
            int maxChaves,
            LongSupplier relogioMs
    ) {
        this.properties = properties;
        this.maxChaves = Math.max(1, maxChaves);
        this.relogioMs = relogioMs;
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

    int quantidadeChavesAtivas() {
        return janelas.size();
    }

    private void consumir(String chave, int limitePorMinuto, String mensagemErro) {
        long agora = relogioMs.getAsLong();
        long corte = agora - JANELA_UM_MINUTO_MS;
        if (!janelas.containsKey(chave) && janelas.size() >= maxChaves) {
            evictarExpiradosOuMaisAntigo(corte);
        }

        janelas.compute(chave, (ignored, existente) -> {
            JanelaUso janela = existente != null ? existente : new JanelaUso();
            limparExpirados(janela.timestamps, corte);
            if (janela.timestamps.size() >= limitePorMinuto) {
                throw new RateLimitExcedidoException(mensagemErro);
            }
            janela.timestamps.addLast(agora);
            janela.ultimoUsoMs = agora;
            return janela;
        });
    }

    private void evictarExpiradosOuMaisAntigo(long corte) {
        for (String chaveExistente : janelas.keySet()) {
            janelas.computeIfPresent(chaveExistente, (ignored, janela) -> {
                limparExpirados(janela.timestamps, corte);
                return janela.timestamps.isEmpty() ? null : janela;
            });
        }
        while (janelas.size() >= maxChaves) {
            String chaveMaisAntiga = null;
            long menorUltimoUso = Long.MAX_VALUE;
            for (Map.Entry<String, JanelaUso> entry : janelas.entrySet()) {
                long ultimoUso = entry.getValue().ultimoUsoMs;
                if (ultimoUso < menorUltimoUso) {
                    menorUltimoUso = ultimoUso;
                    chaveMaisAntiga = entry.getKey();
                }
            }
            if (chaveMaisAntiga == null) {
                break;
            }
            janelas.remove(chaveMaisAntiga);
        }
    }

    private void limparExpirados(Deque<Long> timestamps, long corte) {
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= corte) {
            timestamps.pollFirst();
        }
    }

    private static final class JanelaUso {
        private final Deque<Long> timestamps = new ArrayDeque<>();
        private volatile long ultimoUsoMs = Long.MIN_VALUE;
    }
}
