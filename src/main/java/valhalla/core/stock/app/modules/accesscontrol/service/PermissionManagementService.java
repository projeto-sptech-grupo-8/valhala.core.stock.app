package valhalla.core.stock.app.modules.accesscontrol.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.FuncionalidadeRepository;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.auth.service.TokenStateService;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionManagementService {
    private final ProfileRepository profileRepository;
    private final FuncionalidadeRepository funcionalidadeRepository;
    private final UserRepository userRepository;
    private final TokenStateService tokenStateService;

    @Transactional
    public void replaceProfilePermissions(Integer profileId, Set<Integer> ids,
                                           Authentication authentication) {
        ProfileEntity profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException("Perfil não encontrado"));
        ensureSameEstablishment(profile.getEstablishment().getId(), authentication);
        profile.setFunctionalities(loadFunctionalities(ids));
        profileRepository.save(profile);
        userRepository.findAllByProfile_Id(profileId)
                .forEach(user -> tokenStateService.revokeAll(user.getId()));
    }

    @Transactional
    public void replaceUserPermissions(UUID userId, Set<Integer> ids,
                                       Authentication authentication) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
        ensureSameEstablishment(user.getEstablishment().getId(), authentication);
        user.setDirectFunctionalities(loadFunctionalities(ids));
        userRepository.save(user);
        tokenStateService.revokeAll(userId);
    }

    private Set<FuncionalidadeEntity> loadFunctionalities(Set<Integer> ids) {
        Set<FuncionalidadeEntity> functionalities = new LinkedHashSet<>(
                funcionalidadeRepository.findAllById(ids));
        if (functionalities.size() != ids.size()) {
            throw new EntityNotFoundException("Funcionalidade não encontrada");
        }
        return functionalities;
    }

    private void ensureSameEstablishment(UUID establishmentId, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw new AccessDeniedException("Sessão inválida");
        }
        try {
            UUID currentEstablishment = UUID.fromString(
                    jwt.getToken().getClaimAsString("establishmentId"));
            if (!establishmentId.equals(currentEstablishment)) {
                throw new AccessDeniedException("Acesso negado");
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AccessDeniedException("Sessão inválida");
        }
    }
}
