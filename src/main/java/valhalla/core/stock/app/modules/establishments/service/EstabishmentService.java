package valhalla.core.stock.app.modules.establishments.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import valhalla.core.stock.app.modules.establishments.dto.EstabelecimentoAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.establishments.dto.EstabelecimentoResponseDto;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.establishments.repository.EstablishmentRepository;
import valhalla.core.stock.app.shared.error.CnpjAlreadyExistsException;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
import valhalla.core.stock.app.shared.validation.ValidadorDocumentoFiscal;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EstabishmentService {

    private final EstablishmentRepository establishmentRepository;
    private final BusinessEventLogger businessEventLogger;

    @Transactional(readOnly = true)
    public EstabelecimentoResponseDto obterEstabelecimento() {
        EstablishmentEntity establishment = buscarEstabelecimentoAutenticado();
        return toResponse(establishment);
    }

    @Transactional
    @PreAuthorize("hasRole('GERENTE')")
    public EstabelecimentoResponseDto atualizarEstabelecimento(
            EstabelecimentoAtualizacaoRequestDto dto
    ) {
        validarAtualizacao(dto);

        EstablishmentEntity establishment = buscarEstabelecimentoAutenticado();

        if (dto.nome() != null) {
            String nome = dto.nome().trim();
            if (nome.isEmpty()) {
                throw new IllegalArgumentException("O nome da adega não pode ficar vazio");
            }
            establishment.setTradeName(nome);
            if (!StringUtils.hasText(establishment.getCorporateName())) {
                establishment.setCorporateName(nome);
            }
        }

        if (dto.cnpj() != null) {
            String cnpjLimpo = dto.cnpj().replaceAll("\\D", "");
            if (cnpjLimpo.length() != 14) {
                throw new IllegalArgumentException("O CNPJ deve conter 14 dígitos numéricos");
            }
            if (!ValidadorDocumentoFiscal.isCnpjValido(cnpjLimpo)) {
                throw new IllegalArgumentException("O CNPJ informado é inválido");
            }
            if (!cnpjLimpo.equals(establishment.getCnpj())
                    && establishmentRepository.existsByCnpjAndIdNot(cnpjLimpo, establishment.getId())) {
                throw new CnpjAlreadyExistsException(
                        "Já existe um estabelecimento cadastrado com este CNPJ"
                );
            }
            establishment.setCnpj(cnpjLimpo);
        }

        if (dto.emailContato() != null) {
            String emailNormalizado = normalizarTextoOpcional(dto.emailContato());
            establishment.setContactEmail(
                    emailNormalizado == null ? null : emailNormalizado.toLowerCase(Locale.ROOT)
            );
        }

        if (dto.telefone() != null) {
            establishment.setPhone(normalizarTextoOpcional(dto.telefone()));
        }

        if (dto.endereco() != null) {
            establishment.setAddress(normalizarTextoOpcional(dto.endereco()));
        }

        EstablishmentEntity salvo;
        try {
            salvo = establishmentRepository.saveAndFlush(establishment);
        } catch (DataIntegrityViolationException exception) {
            throw new CnpjAlreadyExistsException(
                    "Já existe um estabelecimento cadastrado com este CNPJ"
            );
        }

        businessEventLogger.success("establishment.updated", "establishment", salvo.getId());
        return toResponse(salvo);
    }

    private EstablishmentEntity buscarEstabelecimentoAutenticado() {
        UUID establishmentId = authenticatedEstablishmentId();
        return establishmentRepository.findById(establishmentId)
                .orElseThrow(() -> new EntityNotFoundException("Estabelecimento não encontrado"));
    }

    private UUID authenticatedEstablishmentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String rawId = jwt.getToken().getClaimAsString("establishmentId");
            if (StringUtils.hasText(rawId)) {
                try {
                    return UUID.fromString(rawId);
                } catch (IllegalArgumentException ignored) {
                    // Cai na exceção abaixo
                }
            }
        }
        throw new AccessDeniedException("Estabelecimento da sessão não encontrado");
    }

    private void validarAtualizacao(EstabelecimentoAtualizacaoRequestDto dto) {
        if (dto == null
                || (dto.nome() == null
                && dto.cnpj() == null
                && dto.emailContato() == null
                && dto.telefone() == null
                && dto.endereco() == null)) {
            throw new IllegalArgumentException("Informe ao menos um campo para atualização");
        }
    }

    private String normalizarTextoOpcional(String valor) {
        String trimmed = valor.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private EstabelecimentoResponseDto toResponse(EstablishmentEntity establishment) {
        return new EstabelecimentoResponseDto(
                establishment.getId(),
                establishment.getTradeName(),
                establishment.getCnpj(),
                establishment.getContactEmail(),
                establishment.getPhone(),
                establishment.getAddress(),
                establishment.getActive(),
                establishment.getCreatedAt(),
                establishment.getUpdatedAt()
        );
    }
}
