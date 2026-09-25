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
import valhalla.core.stock.app.modules.accesscontrol.entity.PermissionEffect;
import valhalla.core.stock.app.modules.accesscontrol.entity.UserFunctionalityOverrideEntity;
import valhalla.core.stock.app.modules.accesscontrol.dto.*;
import valhalla.core.stock.app.modules.establishments.repository.EstablishmentRepository;
import valhalla.core.stock.app.modules.accesscontrol.repository.FuncionalidadeRepository;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.auth.service.TokenStateService;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.modules.accesscontrol.security.PermissionResolver;
import valhalla.core.stock.app.modules.auth.security.CustomUserDetailsService;
import valhalla.core.stock.app.shared.error.AccessConfigurationConflictException;

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
    private final EstablishmentRepository establishmentRepository;

    @Transactional
    public java.util.List<FunctionalityResponseDto> listAvailableFunctionalities() {
        return funcionalidadeRepository.findAll().stream()
                .map(item -> new FunctionalityResponseDto(item.getId(), item.getCode(),
                        item.getName(), item.getDescription())).toList();
    }

    @Transactional
    public java.util.List<ProfileResponseDto> listProfiles(Authentication authentication) {
        UUID establishmentId = currentEstablishmentId(authentication);
        return profileRepository.findAll().stream()
                .filter(profile -> establishmentId.equals(profile.getEstablishment().getId()))
                .map(this::toProfileResponse).toList();
    }

    @Transactional
    public ProfileResponseDto createProfile(ProfileRequestDto request, Authentication authentication) {
        UUID establishmentId = currentEstablishmentId(authentication);
        ProfileEntity profile = ProfileEntity.builder()
                .name(request.name().trim()).description(request.description())
                .establishment(establishmentRepository.getReferenceById(establishmentId))
                .functionalities(loadFunctionalitiesByCode(emptyWhenNull(request.functionalityCodes())))
                .build();
        return toProfileResponse(profileRepository.save(profile));
    }

    @Transactional
    public ProfileResponseDto getProfile(Integer profileId, Authentication authentication) {
        ProfileEntity profile = findProfileInCurrentEstablishment(profileId, authentication);
        return toProfileResponse(profile);
    }

    @Transactional
    public ProfileResponseDto updateProfile(Integer profileId, ProfileUpdateRequestDto request,
                                            Authentication authentication) {
        ProfileEntity profile = findProfileInCurrentEstablishment(profileId, authentication);
        if (request.name() != null) profile.setName(request.name().trim());
        if (request.description() != null) profile.setDescription(request.description());
        return toProfileResponse(profileRepository.save(profile));
    }

    @Transactional
    public void deleteProfile(Integer profileId, Authentication authentication) {
        ProfileEntity profile = findProfileInCurrentEstablishment(profileId, authentication);
        if (!userRepository.findAllByProfile_Id(profileId).isEmpty()) {
            throw new AccessConfigurationConflictException(
                    "Perfil não pode ser excluído porque possui usuários vinculados");
        }
        profileRepository.delete(profile);
    }

    @Transactional
    public void replaceProfilePermissions(Integer profileId, Set<Integer> ids,
                                           Authentication authentication) {
        ProfileEntity profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException("Perfil não encontrado"));
        ensureSameEstablishment(profile.getEstablishment().getId(), authentication);
        profile.setFunctionalities(loadFunctionalities(ids));
        ensureAtLeastOneActiveUserCanCreateUsers(profile.getEstablishment().getId());
        profileRepository.save(profile);
        userRepository.findAllByProfile_Id(profileId)
                .forEach(user -> tokenStateService.revokeAll(user.getId()));
    }

    @Transactional
    public ProfileResponseDto replaceProfileFunctionalities(Integer profileId, Set<String> codes,
                                                            Authentication authentication) {
        ProfileEntity profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException("Perfil não encontrado"));
        ensureSameEstablishment(profile.getEstablishment().getId(), authentication);
        profile.setFunctionalities(loadFunctionalitiesByCode(emptyWhenNull(codes)));
        ensureAtLeastOneActiveUserCanCreateUsers(profile.getEstablishment().getId());
        userRepository.findAllByProfile_Id(profileId)
                .forEach(user -> tokenStateService.revokeAll(user.getId()));
        return toProfileResponse(profileRepository.save(profile));
    }

    @Transactional
    public void replaceUserPermissions(UUID userId, Set<Integer> ids,
                                       Authentication authentication) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
        ensureSameEstablishment(user.getEstablishment().getId(), authentication);
        Set<UserFunctionalityOverrideEntity> overrides = new LinkedHashSet<>();
        loadFunctionalities(ids).forEach(functionality -> overrides.add(
                UserFunctionalityOverrideEntity.builder().user(user)
                        .functionality(functionality).effect(PermissionEffect.GRANT).build()));
        user.setFunctionalityOverrides(overrides);
        userRepository.save(user);
        tokenStateService.revokeAll(userId);
    }

    @Transactional
    public void replaceUserPermissionOverrides(UUID userId,
            Set<UserPermissionOverrideDto> requestedOverrides, Authentication authentication) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
        ensureSameEstablishment(user.getEstablishment().getId(), authentication);
        Set<UserPermissionOverrideDto> overrides = emptyWhenNull(requestedOverrides);
        long distinctCodes = overrides.stream().map(UserPermissionOverrideDto::functionalityCode)
                .distinct().count();
        if (distinctCodes != overrides.size()) {
            throw new IllegalArgumentException(
                    "Cada funcionalidade pode possuir apenas uma sobrescrita por usuário");
        }
        Set<FuncionalidadeEntity> functionalities = loadFunctionalitiesByCode(overrides.stream()
                .map(UserPermissionOverrideDto::functionalityCode).collect(java.util.stream.Collectors.toSet()));
        java.util.Map<String, FuncionalidadeEntity> byCode = functionalities.stream()
                .collect(java.util.stream.Collectors.toMap(FuncionalidadeEntity::getCode, item -> item));
        Set<UserFunctionalityOverrideEntity> entities = new LinkedHashSet<>();
        for (UserPermissionOverrideDto override : overrides) {
            entities.add(UserFunctionalityOverrideEntity.builder().user(user)
                    .functionality(byCode.get(override.functionalityCode()))
                    .effect(override.effect()).build());
        }
        user.setFunctionalityOverrides(entities);
        ensureAtLeastOneActiveUserCanCreateUsers(user.getEstablishment().getId());
        userRepository.save(user);
        tokenStateService.revokeAll(userId);
    }

    /** Valida uma alteração de perfil ou status antes de persistir o usuário. */
    @Transactional
    public void ensureUserChangeKeepsAdministrator(UserEntity changedUser) {
        ensureAtLeastOneActiveUserCanCreateUsers(changedUser.getEstablishment().getId());
    }

    /** Impede desativar ou excluir o último usuário que pode criar usuários. */
    @Transactional
    public void ensureUserCanBeDeactivatedOrDeleted(UserEntity user) {
        if (!canCreateUsers(user)) {
            return;
        }
        boolean anotherAdministratorExists = userRepository
                .findAllByEstablishment_Id(user.getEstablishment().getId()).stream()
                .anyMatch(candidate -> !candidate.getId().equals(user.getId())
                        && canCreateUsers(candidate));
        if (!anotherAdministratorExists) {
            throw new AccessConfigurationConflictException(
                    "Não é possível remover o último usuário ativo com USUARIOS_CRIAR");
        }
    }

    @Transactional
    public UserPermissionsResponseDto getUserPermissions(UUID userId,
                                                          Authentication authentication) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
        ensureSameEstablishment(user.getEstablishment().getId(), authentication);
        Set<String> profilePermissions = user.getProfile().getFunctionalities().stream()
                .map(FuncionalidadeEntity::getCode)
                .collect(java.util.stream.Collectors.toSet());
        Set<UserPermissionOverrideDto> overrides = user.getFunctionalityOverrides().stream()
                .map(item -> new UserPermissionOverrideDto(item.getFunctionality().getCode(),
                        item.getEffect()))
                .collect(java.util.stream.Collectors.toSet());
        return new UserPermissionsResponseDto(user.getProfile().getId(),
                user.getProfile().getName(), profilePermissions, overrides,
                PermissionResolver.effectiveCodes(user));
    }

    private Set<FuncionalidadeEntity> loadFunctionalities(Set<Integer> ids) {
        Set<FuncionalidadeEntity> functionalities = new LinkedHashSet<>(
                funcionalidadeRepository.findAllById(ids));
        if (functionalities.size() != ids.size()) {
            throw new EntityNotFoundException("Funcionalidade não encontrada");
        }
        return functionalities;
    }

    private Set<FuncionalidadeEntity> loadFunctionalitiesByCode(Set<String> codes) {
        Set<FuncionalidadeEntity> functionalities = new LinkedHashSet<>(
                funcionalidadeRepository.findByCodeIn(codes));
        if (functionalities.size() != codes.size()) {
            throw new EntityNotFoundException("Funcionalidade não encontrada");
        }
        return functionalities;
    }

    private ProfileResponseDto toProfileResponse(ProfileEntity profile) {
        return new ProfileResponseDto(profile.getId(), profile.getName(), profile.getDescription(),
                profile.getFunctionalities().stream().map(FuncionalidadeEntity::getCode)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private ProfileEntity findProfileInCurrentEstablishment(Integer profileId,
                                                            Authentication authentication) {
        ProfileEntity profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException("Perfil não encontrado"));
        ensureSameEstablishment(profile.getEstablishment().getId(), authentication);
        return profile;
    }

    private void ensureAtLeastOneActiveUserCanCreateUsers(UUID establishmentId) {
        boolean hasAdministrator = userRepository.findAllByEstablishment_Id(establishmentId).stream()
                .anyMatch(this::canCreateUsers);
        if (!hasAdministrator) {
            throw new AccessConfigurationConflictException(
                    "O estabelecimento precisa manter ao menos um usuário ativo com USUARIOS_CRIAR");
        }
    }

    private boolean canCreateUsers(UserEntity user) {
        return user.isActive() && ("GERENTE".equals(CustomUserDetailsService
                .normalizeRole(user.getProfile().getName()))
                || PermissionResolver.effectiveCodes(user).contains("USUARIOS_CRIAR"));
    }

    private <T> Set<T> emptyWhenNull(Set<T> values) {
        return values == null ? Set.of() : values;
    }

    private void ensureSameEstablishment(UUID establishmentId, Authentication authentication) {
        UUID currentEstablishment = currentEstablishmentId(authentication);
        if (!establishmentId.equals(currentEstablishment)) {
            throw new AccessDeniedException("Acesso negado");
        }
    }

    private UUID currentEstablishmentId(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw new AccessDeniedException("Sessão inválida");
        }
        try {
            return UUID.fromString(
                    jwt.getToken().getClaimAsString("establishmentId"));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AccessDeniedException("Sessão inválida");
        }
    }
}
