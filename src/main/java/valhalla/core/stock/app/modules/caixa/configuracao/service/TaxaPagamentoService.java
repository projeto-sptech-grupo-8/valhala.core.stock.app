package valhalla.core.stock.app.modules.caixa.configuracao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.configuracao.dto.TaxaPagamentoAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.caixa.configuracao.dto.TaxaPagamentoRequestDto;
import valhalla.core.stock.app.modules.caixa.configuracao.dto.TaxaPagamentoResponseDto;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.PoliticaJurosCaixa;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.TaxaPagamentoEntity;
import valhalla.core.stock.app.modules.caixa.exception.ConfiguracaoCaixaInvalidaException;
import valhalla.core.stock.app.modules.caixa.exception.TaxaPagamentoJaExisteException;
import valhalla.core.stock.app.modules.caixa.configuracao.mapper.TaxaPagamentoMapper;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.ConfiguracaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.TaxaPagamentoRepository;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.Paginacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import valhalla.core.stock.app.modules.caixa.security.ContextoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.service.AuditoriaCaixaService;
import valhalla.core.stock.app.modules.caixa.sessao.service.EventoAuditoriaCaixa;

@Service
public class TaxaPagamentoService {

    private static final Set<String> CAMPOS_ORDENACAO =
            Set.of("forma", "parcelas", "taxaPercentual", "criadoEm", "atualizadoEm");

    private final TaxaPagamentoRepository taxaPagamentoRepository;
    private final ConfiguracaoCaixaRepository configuracaoCaixaRepository;
    private final AuditoriaCaixaService auditoriaCaixaService;
    private final ContextoCaixa contextoCaixa;
    private final BusinessEventLogger businessEventLogger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TaxaPagamentoService(
            TaxaPagamentoRepository taxaPagamentoRepository,
            ConfiguracaoCaixaRepository configuracaoCaixaRepository,
            AuditoriaCaixaService auditoriaCaixaService,
            ContextoCaixa contextoCaixa,
            BusinessEventLogger businessEventLogger
    ) {
        this.taxaPagamentoRepository = taxaPagamentoRepository;
        this.configuracaoCaixaRepository = configuracaoCaixaRepository;
        this.auditoriaCaixaService = auditoriaCaixaService;
        this.contextoCaixa = contextoCaixa;
        this.businessEventLogger = businessEventLogger;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_VISUALIZAR',
                authentication
            )
            """)
    public RespostaPaginadaDto<TaxaPagamentoResponseDto> listarTaxas(
            FormaPagamentoCaixa forma,
            Boolean ativo,
            int pagina,
            int tamanho,
            String ordenarPor,
            DirecaoOrdenacao direcao
    ) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        PoliticaJurosCaixa politicaGlobal = politicaGlobal(estabelecimentoId);

        Specification<TaxaPagamentoEntity> especificacao = (root, query, builder) ->
                builder.equal(root.get("estabelecimentoId"), estabelecimentoId);

        if (forma != null) {
            especificacao = especificacao.and((root, query, builder) ->
                    builder.equal(root.get("forma"), forma));
        }
        if (ativo != null) {
            especificacao = especificacao.and((root, query, builder) ->
                    builder.equal(root.get("ativo"), ativo));
        }

        Page<TaxaPagamentoEntity> resultado = taxaPagamentoRepository.findAll(
                especificacao,
                Paginacao.criar(pagina, tamanho, ordenarPor, direcao, CAMPOS_ORDENACAO)
        );

        return RespostaPaginadaDto.de(
                resultado,
                taxa -> TaxaPagamentoMapper.paraResposta(taxa, politicaGlobal)
        );
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_CONFIGURAR',
                authentication
            )
            """)
    public TaxaPagamentoResponseDto criarTaxa(TaxaPagamentoRequestDto dto) {
        validarFormaEParcelas(dto.forma(), dto.parcelas());
        validarPercentual(dto.taxaPercentual(), dto.repassarAoCliente());

        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        if (taxaPagamentoRepository.existsByEstabelecimentoIdAndFormaAndParcelas(
                estabelecimentoId,
                dto.forma(),
                dto.parcelas()
        )) {
            throw new TaxaPagamentoJaExisteException(
                    "Já existe uma taxa cadastrada para esta forma e número de parcelas"
            );
        }

        TaxaPagamentoEntity taxa = new TaxaPagamentoEntity();
        taxa.setEstabelecimentoId(estabelecimentoId);
        taxa.setForma(dto.forma());
        taxa.setParcelas(dto.parcelas());
        taxa.setTaxaPercentual(TaxaPagamentoMapper.escalaPercentual(dto.taxaPercentual()));
        taxa.setTaxaFixa(TaxaPagamentoMapper.escalaValor(dto.taxaFixa()));
        taxa.setRepassarAoCliente(dto.repassarAoCliente());
        taxa.setAtivo(dto.ativo());

        TaxaPagamentoEntity taxaSalva = salvarTaxa(taxa);
        PoliticaJurosCaixa politicaGlobal = politicaGlobal(estabelecimentoId);

        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                null,
                EventoAuditoriaCaixa.TAXA_PAGAMENTO_CRIADA,
                EventoAuditoriaCaixa.ENTIDADE_TAXA_PAGAMENTO,
                taxaSalva.getId().toString(),
                "Taxa cadastrada: " + taxaSalva.getForma() + " " + taxaSalva.getParcelas() + "x",
                null,
                null,
                null,
                objectMapper.valueToTree(TaxaPagamentoMapper.snapshot(taxaSalva))
        );

        businessEventLogger.success("cash.fee.created", "cash_fee", taxaSalva.getId());
        return TaxaPagamentoMapper.paraResposta(taxaSalva, politicaGlobal);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_CONFIGURAR',
                authentication
            )
            """)
    public TaxaPagamentoResponseDto atualizarTaxa(UUID id, TaxaPagamentoAtualizacaoRequestDto dto) {
        validarPercentual(dto.taxaPercentual(), dto.repassarAoCliente());

        UUID usuarioId = contextoCaixa.usuarioId();
        TaxaPagamentoEntity taxa = buscarDoEstabelecimento(id);
        Map<String, Object> dadosAnteriores = TaxaPagamentoMapper.snapshot(taxa);

        BigDecimal novoPercentual = TaxaPagamentoMapper.escalaPercentual(dto.taxaPercentual());
        BigDecimal novaFixa = TaxaPagamentoMapper.escalaValor(dto.taxaFixa());

        boolean alterado = taxa.getTaxaPercentual().compareTo(novoPercentual) != 0
                || taxa.getTaxaFixa().compareTo(novaFixa) != 0
                || !Objects.equals(taxa.getRepassarAoCliente(), dto.repassarAoCliente())
                || taxa.isAtivo() != dto.ativo();

        taxa.setTaxaPercentual(novoPercentual);
        taxa.setTaxaFixa(novaFixa);
        taxa.setRepassarAoCliente(dto.repassarAoCliente());
        taxa.setAtivo(dto.ativo());

        TaxaPagamentoEntity taxaSalva = salvarTaxa(taxa);
        PoliticaJurosCaixa politicaGlobal = politicaGlobal(taxaSalva.getEstabelecimentoId());

        if (alterado) {
            auditoriaCaixaService.registrar(
                    taxaSalva.getEstabelecimentoId(),
                    usuarioId,
                    null,
                    EventoAuditoriaCaixa.TAXA_PAGAMENTO_ALTERADA,
                    EventoAuditoriaCaixa.ENTIDADE_TAXA_PAGAMENTO,
                    taxaSalva.getId().toString(),
                    "Taxa atualizada: " + taxaSalva.getForma() + " " + taxaSalva.getParcelas() + "x",
                    null,
                    null,
                    objectMapper.valueToTree(dadosAnteriores),
                    objectMapper.valueToTree(TaxaPagamentoMapper.snapshot(taxaSalva))
            );

            businessEventLogger.success("cash.fee.updated", "cash_fee", taxaSalva.getId());
        }

        return TaxaPagamentoMapper.paraResposta(taxaSalva, politicaGlobal);
    }

    private TaxaPagamentoEntity buscarDoEstabelecimento(UUID id) {
        TaxaPagamentoEntity taxa = taxaPagamentoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Taxa de pagamento não encontrada"));

        if (!taxa.getEstabelecimentoId().equals(contextoCaixa.estabelecimentoId())) {
            throw new AccessDeniedException("Acesso negado");
        }
        return taxa;
    }

    private TaxaPagamentoEntity salvarTaxa(TaxaPagamentoEntity taxa) {
        try {
            return taxaPagamentoRepository.saveAndFlush(taxa);
        } catch (DataIntegrityViolationException excecao) {
            throw new TaxaPagamentoJaExisteException(
                    "Já existe uma taxa cadastrada para esta forma e número de parcelas"
            );
        }
    }

    private PoliticaJurosCaixa politicaGlobal(UUID estabelecimentoId) {
        return configuracaoCaixaRepository.findByEstabelecimentoId(estabelecimentoId)
                .map(ConfiguracaoCaixaEntity::getPoliticaJuros)
                .orElse(PoliticaJurosCaixa.ABSORVIDA_PELA_LOJA);
    }

    private void validarFormaEParcelas(FormaPagamentoCaixa forma, int parcelas) {
        if (forma == FormaPagamentoCaixa.DINHEIRO) {
            throw new ConfiguracaoCaixaInvalidaException(
                    "Pagamento em dinheiro não possui taxa"
            );
        }
        if (forma != FormaPagamentoCaixa.CREDITO && parcelas != 1) {
            throw new ConfiguracaoCaixaInvalidaException(
                    "Débito e Pix aceitam somente 1 parcela"
            );
        }
    }

    private void validarPercentual(BigDecimal taxaPercentual, Boolean repassarAoCliente) {
        if (Boolean.TRUE.equals(repassarAoCliente)
                && taxaPercentual.compareTo(new BigDecimal("100")) >= 0) {
            throw new ConfiguracaoCaixaInvalidaException(
                    "A taxa percentual deve ser menor que 100% quando repassada ao cliente"
            );
        }
    }
}
