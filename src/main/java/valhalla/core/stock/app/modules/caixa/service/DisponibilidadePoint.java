package valhalla.core.stock.app.modules.caixa.service;

import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.caixa.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.exception.CaixaEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.repository.ConfiguracaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.repository.TerminalCaixaRepository;

import java.util.UUID;


@Component
public class DisponibilidadePoint {

    private final TerminalCaixaRepository terminalCaixaRepository;
    private final ConfiguracaoCaixaRepository configuracaoCaixaRepository;

    public DisponibilidadePoint(
            TerminalCaixaRepository terminalCaixaRepository,
            ConfiguracaoCaixaRepository configuracaoCaixaRepository
    ) {
        this.terminalCaixaRepository = terminalCaixaRepository;
        this.configuracaoCaixaRepository = configuracaoCaixaRepository;
    }

    public boolean possuiTerminalAtivo(UUID estabelecimentoId) {
        return terminalCaixaRepository.contarAtivosDoEstabelecimento(estabelecimentoId) > 0;
    }

    public void exigirTerminalAtivoSePointHabilitada(UUID estabelecimentoId) {
        boolean pointHabilitada = configuracaoCaixaRepository
                .findByEstabelecimentoId(estabelecimentoId)
                .map(ConfiguracaoCaixaEntity::isPointHabilitado)
                .orElse(false);

        if (pointHabilitada && !possuiTerminalAtivo(estabelecimentoId)) {
            throw new CaixaEstadoInvalidoException(
                    "A Point está habilitada: mantenha ao menos um terminal ativo ou desabilite a Point antes"
            );
        }
    }
}
