package valhalla.core.stock.app.modules.financeiro.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.financeiro.dto.RequisicaoBaixaContaDto;
import valhalla.core.stock.app.modules.financeiro.dto.RequisicaoContaDto;
import valhalla.core.stock.app.modules.financeiro.dto.RespostaContaDto;
import valhalla.core.stock.app.modules.financeiro.entity.CentroCusto;
import valhalla.core.stock.app.modules.financeiro.entity.ContaFinanceiraEntity;
import valhalla.core.stock.app.modules.financeiro.entity.StatusConta;
import valhalla.core.stock.app.modules.financeiro.entity.TipoConta;
import valhalla.core.stock.app.modules.financeiro.repository.ContaFinanceiraRepository;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;
import valhalla.core.stock.app.shared.security.ContextoAutenticado;
import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Cadastro e baixa das contas a pagar e receber. Só contas em aberto podem ser alteradas. */
@Service
public class ContaFinanceiraService {

    private static final String VISUALIZAR =
            "@permissionAuthorizationService.hasPermission('VISUALIZAR_FINANCEIRO', authentication)";
    private static final String GERENCIAR =
            "@permissionAuthorizationService.hasPermission('GERENCIAR_FINANCEIRO', authentication)";

    private final ContaFinanceiraRepository repository;
    private final BusinessEventLogger businessEventLogger;

    public ContaFinanceiraService(ContaFinanceiraRepository repository, BusinessEventLogger businessEventLogger) {
        this.repository = repository;
        this.businessEventLogger = businessEventLogger;
    }

    @Transactional
    @PreAuthorize(GERENCIAR)
    public RespostaContaDto criar(RequisicaoContaDto dto) {
        validarCategoria(dto);
        LocalDateTime agora = FusoHorarioOperacao.agora();
        ContaFinanceiraEntity conta = new ContaFinanceiraEntity();
        conta.setEstabelecimentoId(ContextoAutenticado.idEstabelecimento());
        conta.setCriadoPorUsuarioId(ContextoAutenticado.idUsuario());
        conta.setTipo(dto.tipo());
        conta.setStatus(StatusConta.ABERTA);
        conta.setCriadoEm(agora);
        aplicar(conta, dto, agora);
        repository.save(conta);
        businessEventLogger.success("finance.account.created", "financial-account", conta.getId());
        return paraResposta(conta);
    }

    @Transactional
    @PreAuthorize(GERENCIAR)
    public RespostaContaDto atualizar(UUID id, RequisicaoContaDto dto) {
        ContaFinanceiraEntity conta = buscarParaAtualizacao(id);
        exigirAberta(conta);
        if (dto.tipo() != conta.getTipo()) {
            throw new IllegalArgumentException("O tipo da conta não pode ser alterado");
        }
        validarCategoria(dto);
        aplicar(conta, dto, FusoHorarioOperacao.agora());
        businessEventLogger.success("finance.account.updated", "financial-account", conta.getId());
        return paraResposta(conta);
    }

    @Transactional
    @PreAuthorize(GERENCIAR)
    public RespostaContaDto baixar(UUID id, RequisicaoBaixaContaDto dto) {
        ContaFinanceiraEntity conta = buscarParaAtualizacao(id);
        exigirAberta(conta);
        LocalDate hoje = FusoHorarioOperacao.agora().toLocalDate();
        LocalDate pagoEm = dto == null || dto.pagoEm() == null ? hoje : dto.pagoEm();
        if (pagoEm.isAfter(hoje)) {
            throw new IllegalArgumentException("A data do pagamento não pode ser futura");
        }
        conta.setStatus(StatusConta.PAGA);
        conta.setPagoEm(pagoEm);
        conta.setValorPago(dto == null || dto.valorPago() == null ? conta.getValor() : dto.valorPago());
        conta.setAtualizadoEm(FusoHorarioOperacao.agora());
        businessEventLogger.success("finance.account.settled", "financial-account", conta.getId());
        return paraResposta(conta);
    }

    @Transactional
    @PreAuthorize(GERENCIAR)
    public RespostaContaDto inativar(UUID id) {
        ContaFinanceiraEntity conta = buscarParaAtualizacao(id);
        exigirAberta(conta);
        conta.setStatus(StatusConta.INATIVA);
        conta.setAtualizadoEm(FusoHorarioOperacao.agora());
        businessEventLogger.success("finance.account.inactivated", "financial-account", conta.getId());
        return paraResposta(conta);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VISUALIZAR)
    public RespostaContaDto obter(UUID id) {
        return repository.findByIdAndEstabelecimentoId(id, ContextoAutenticado.idEstabelecimento())
                .map(this::paraResposta)
                .orElseThrow(() -> new EntityNotFoundException("Conta não encontrada"));
    }

    /** Listagem ordenada por vencimento (as mais próximas primeiro). */
    @Transactional(readOnly = true)
    @PreAuthorize(VISUALIZAR)
    public RespostaPaginadaDto<RespostaContaDto> listar(
            TipoConta tipo,
            StatusConta status,
            CentroCusto centroCusto,
            LocalDate vencimentoDe,
            LocalDate vencimentoAte,
            int pagina,
            int tamanho
    ) {
        if (vencimentoDe != null && vencimentoAte != null && vencimentoDe.isAfter(vencimentoAte)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final");
        }
        UUID idEstabelecimento = ContextoAutenticado.idEstabelecimento();
        Specification<ContaFinanceiraEntity> filtro = (root, query, builder) ->
                builder.equal(root.get("estabelecimentoId"), idEstabelecimento);
        if (tipo != null) {
            filtro = filtro.and((root, query, builder) -> builder.equal(root.get("tipo"), tipo));
        }
        if (status != null) {
            filtro = filtro.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (centroCusto != null) {
            filtro = filtro.and((root, query, builder) -> builder.equal(root.get("centroCusto"), centroCusto));
        }
        if (vencimentoDe != null) {
            filtro = filtro.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("vencimento"), vencimentoDe));
        }
        if (vencimentoAte != null) {
            filtro = filtro.and((root, query, builder) -> builder.lessThanOrEqualTo(root.get("vencimento"), vencimentoAte));
        }
        Page<ContaFinanceiraEntity> resultado = repository.findAll(filtro, PageRequest.of(pagina, tamanho,
                Sort.by(Sort.Order.asc("vencimento"), Sort.Order.asc("descricao"))));
        return RespostaPaginadaDto.de(resultado, this::paraResposta);
    }

    private void aplicar(ContaFinanceiraEntity conta, RequisicaoContaDto dto, LocalDateTime agora) {
        conta.setDescricao(dto.descricao().trim());
        conta.setValor(dto.valor());
        conta.setVencimento(dto.vencimento());
        conta.setCentroCusto(dto.centroCusto());
        conta.setCategoria(dto.categoria());
        conta.setObservacao(dto.observacao() == null || dto.observacao().isBlank() ? null : dto.observacao().trim());
        conta.setAtualizadoEm(agora);
    }

    private void validarCategoria(RequisicaoContaDto dto) {
        if (dto.tipo() == TipoConta.PAGAR && dto.categoria() == null) {
            throw new IllegalArgumentException("Informe a categoria da conta a pagar");
        }
        if (dto.tipo() == TipoConta.RECEBER && dto.categoria() != null) {
            throw new IllegalArgumentException("Contas a receber não têm categoria");
        }
    }

    private void exigirAberta(ContaFinanceiraEntity conta) {
        if (conta.getStatus() != StatusConta.ABERTA) {
            throw new IllegalArgumentException(conta.getStatus() == StatusConta.PAGA
                    ? "A conta já foi baixada"
                    : "A conta está inativa");
        }
    }

    private ContaFinanceiraEntity buscarParaAtualizacao(UUID id) {
        return repository.buscarParaAtualizacao(id, ContextoAutenticado.idEstabelecimento())
                .orElseThrow(() -> new EntityNotFoundException("Conta não encontrada"));
    }

    private RespostaContaDto paraResposta(ContaFinanceiraEntity conta) {
        Integer dias = conta.getStatus() == StatusConta.ABERTA
                ? (int) ChronoUnit.DAYS.between(FusoHorarioOperacao.agora().toLocalDate(), conta.getVencimento())
                : null;
        return new RespostaContaDto(conta.getId(), conta.getTipo(), conta.getDescricao(), conta.getValor(),
                conta.getVencimento(), conta.getCentroCusto(), conta.getCategoria(), conta.getStatus(),
                conta.getPagoEm(), conta.getValorPago(), conta.getObservacao(), dias, conta.getCriadoEm());
    }
}
