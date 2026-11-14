package valhalla.core.stock.app.modules.caixa.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.dto.AuditoriaCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.entity.AuditoriaCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.OrigemAuditoriaCaixa;
import valhalla.core.stock.app.modules.caixa.mapper.AuditoriaCaixaMapper;
import valhalla.core.stock.app.modules.caixa.repository.AuditoriaCaixaRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.Paginacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
public class AuditoriaCaixaService {

    private final AuditoriaCaixaRepository auditoriaCaixaRepository;
    private final UserRepository userRepository;
    private final ContextoCaixa contextoCaixa;

    public AuditoriaCaixaService(
            AuditoriaCaixaRepository auditoriaCaixaRepository,
            UserRepository userRepository,
            ContextoCaixa contextoCaixa
    ) {
        this.auditoriaCaixaRepository = auditoriaCaixaRepository;
        this.userRepository = userRepository;
        this.contextoCaixa = contextoCaixa;
    }

    public void registrar(
            UUID estabelecimentoId,
            UUID usuarioId,
            UUID sessaoCaixaId,
            String tipoEvento,
            String entidade,
            String entidadeId,
            String descricao,
            BigDecimal valor,
            String motivo,
            JsonNode dadosAnteriores,
            JsonNode dadosNovos
    ) {
        registrar(
                estabelecimentoId,
                usuarioId,
                sessaoCaixaId,
                tipoEvento,
                entidade,
                entidadeId,
                descricao,
                valor,
                motivo,
                null,
                null,
                null,
                null,
                null,
                dadosAnteriores,
                dadosNovos
        );
    }

    public void registrar(
            UUID estabelecimentoId,
            UUID usuarioId,
            UUID sessaoCaixaId,
            String tipoEvento,
            String entidade,
            String entidadeId,
            String descricao,
            BigDecimal valor,
            String motivo,
            UUID pedidoId,
            String pedidoIdentificador,
            UUID pagamentoPedidoId,
            UUID autorizadoPorUsuarioId,
            String autorizadoPorNome,
            JsonNode dadosAnteriores,
            JsonNode dadosNovos
    ) {
        registrarComOrigem(
                usuarioId == null ? OrigemAuditoriaCaixa.SISTEMA : OrigemAuditoriaCaixa.API,
                estabelecimentoId,
                usuarioId,
                sessaoCaixaId,
                tipoEvento,
                entidade,
                entidadeId,
                descricao,
                valor,
                motivo,
                pedidoId,
                pedidoIdentificador,
                pagamentoPedidoId,
                autorizadoPorUsuarioId,
                autorizadoPorNome,
                dadosAnteriores,
                dadosNovos
        );
    }

    public void registrarComOrigem(
            OrigemAuditoriaCaixa origem,
            UUID estabelecimentoId,
            UUID usuarioId,
            UUID sessaoCaixaId,
            String tipoEvento,
            String entidade,
            String entidadeId,
            String descricao,
            BigDecimal valor,
            String motivo,
            UUID pedidoId,
            String pedidoIdentificador,
            UUID pagamentoPedidoId,
            UUID autorizadoPorUsuarioId,
            String autorizadoPorNome,
            JsonNode dadosAnteriores,
            JsonNode dadosNovos
    ) {
        AuditoriaCaixaEntity auditoria = new AuditoriaCaixaEntity();
        auditoria.setEstabelecimentoId(estabelecimentoId);
        auditoria.setUsuarioId(usuarioId);
        auditoria.setSessaoCaixaId(sessaoCaixaId);
        auditoria.setTipoEvento(tipoEvento);
        auditoria.setEntidade(entidade);
        auditoria.setEntidadeId(entidadeId);
        auditoria.setDescricao(descricao);
        auditoria.setMotivo(motivo);
        auditoria.setPedidoId(pedidoId);
        auditoria.setPedidoIdentificador(pedidoIdentificador);
        auditoria.setPagamentoPedidoId(pagamentoPedidoId);
        auditoria.setAutorizadoPorUsuarioId(autorizadoPorUsuarioId);
        auditoria.setAutorizadoPorNome(autorizadoPorNome);
        auditoria.setDadosAnteriores(dadosAnteriores);
        auditoria.setDadosNovos(dadosNovos);
        auditoria.setOrigem(origem != null
                ? origem
                : (usuarioId == null ? OrigemAuditoriaCaixa.SISTEMA : OrigemAuditoriaCaixa.API));

        if (valor != null) {
            auditoria.setValor(valor.abs().setScale(2, RoundingMode.HALF_UP));
        }

        if (usuarioId != null) {
            userRepository.findById(usuarioId)
                    .map(UserEntity::getName)
                    .ifPresent(auditoria::setUsuarioNome);
        }

        if (autorizadoPorUsuarioId != null && (autorizadoPorNome == null || autorizadoPorNome.isBlank())) {
            userRepository.findById(autorizadoPorUsuarioId)
                    .map(UserEntity::getName)
                    .ifPresent(auditoria::setAutorizadoPorNome);
        }

        auditoriaCaixaRepository.save(auditoria);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_VISUALIZAR',
                authentication
            )
            """)
    public RespostaPaginadaDto<AuditoriaCaixaResponseDto> listar(
            String tipoEvento,
            UUID sessaoCaixaId,
            UUID usuarioId,
            LocalDate dataInicio,
            LocalDate dataFim,
            int pagina,
            int tamanho,
            String ordenarPor,
            DirecaoOrdenacao direcao
    ) {
        validarPeriodo(dataInicio, dataFim);

        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        String tipoNormalizado = tipoEvento == null || tipoEvento.isBlank() ? null : tipoEvento.trim();

        Specification<AuditoriaCaixaEntity> especificacao =
                (root, query, builder) -> builder.equal(root.get("estabelecimentoId"), estabelecimentoId);

        if (tipoNormalizado != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.equal(root.get("tipoEvento"), tipoNormalizado)
            );
        }
        if (sessaoCaixaId != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.equal(root.get("sessaoCaixaId"), sessaoCaixaId)
            );
        }
        if (usuarioId != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.equal(root.get("usuarioId"), usuarioId)
            );
        }
        if (dataInicio != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.greaterThanOrEqualTo(
                            root.get("ocorridoEm"), dataInicio.atStartOfDay()
                    )
            );
        }
        if (dataFim != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.lessThan(
                            root.get("ocorridoEm"), dataFim.plusDays(1).atStartOfDay()
                    )
            );
        }

        Page<AuditoriaCaixaEntity> resultado = auditoriaCaixaRepository.findAll(
                especificacao,
                Paginacao.criar(
                        pagina,
                        tamanho,
                        ordenarPor,
                        direcao,
                        Set.of("ocorridoEm", "valor", "tipoEvento", "usuarioNome")
                )
        );

        return RespostaPaginadaDto.de(resultado, AuditoriaCaixaMapper::paraResposta);
    }

    private void validarPeriodo(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final");
        }
    }
}
