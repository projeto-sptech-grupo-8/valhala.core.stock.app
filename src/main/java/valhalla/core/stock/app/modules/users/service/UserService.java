package valhalla.core.stock.app.modules.users.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.accesscontrol.security.PermissionResolver;
import valhalla.core.stock.app.modules.auth.security.CustomUserDetailsService;
import valhalla.core.stock.app.modules.auth.service.TokenStateService;
import valhalla.core.stock.app.modules.users.dto.RequisicaoAtualizacaoUsuarioDto;
import valhalla.core.stock.app.modules.users.dto.RequisicaoCriacaoUsuarioDto;
import valhalla.core.stock.app.modules.users.dto.RespostaUsuarioDto;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.mapper.UserMapper;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.modules.users.security.UserAuthorizationService;
import valhalla.core.stock.app.modules.accesscontrol.service.ServicoPermissoes;
import valhalla.core.stock.app.shared.error.EmailAlreadyExistsException;
import valhalla.core.stock.app.shared.error.InvalidUserUpdateException;
import valhalla.core.stock.app.shared.error.ProfileNotFoundException;
import valhalla.core.stock.app.shared.error.UserDeletionConflictException;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAuthorizationService userAuthorizationService;
    private final TokenStateService tokenStateService;
    private final ServicoPermissoes servicoPermissoes;
    private final BusinessEventLogger businessEventLogger;
    private final UserAccessHistoryService userAccessHistoryService;

    @Transactional
    @PreAuthorize("@permissionAuthorizationService.hasPermission('USUARIOS_CRIAR', authentication)")
    public RespostaUsuarioDto criarUsuario(RequisicaoCriacaoUsuarioDto dtoRequest) {
        if (dtoRequest.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha excede o limite de 72 bytes");
        }
        String emailNormalizado = dtoRequest.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new EmailAlreadyExistsException("Email informado já cadastrado");
        }

        UUID establishmentId = authenticatedEstablishmentId();
        String profileName = dtoRequest.nomePerfil().trim();
        ProfileEntity profile = profileRepository
                .findByEstablishment_IdAndNameIgnoreCase(establishmentId, profileName)
                .orElseThrow(() ->
                        new ProfileNotFoundException(
                                "Perfil não encontrado: " + profileName
                        )
                );
        garantirAtorPodeAtribuirPerfil(profile, SecurityContextHolder.getContext().getAuthentication());

        String passwordHash = passwordEncoder.encode(dtoRequest.senha());
        UserEntity user = UserMapper.toEntity(dtoRequest, passwordHash, profile);

        UserEntity savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException("Email informado já cadastrado");
        }

        businessEventLogger.success("user.created", "user", savedUser.getId());
        return UserMapper.toResponse(savedUser);
    }

    @Transactional
    @PreAuthorize("@permissionAuthorizationService.hasPermission('USUARIOS_VISUALIZAR', authentication)")
    public List<RespostaUsuarioDto> listarUsuarios() {
        return userRepository.findAllByEstablishment_Id(
                        authenticatedEstablishmentId(), Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(UserMapper::toResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize("@userAuthorizationService.canView(#idUsuario, authentication)")
    public RespostaUsuarioDto buscarUsuario(UUID idUsuario) {
        UserEntity user = userRepository.findById(idUsuario)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuário não encontrado"
                ));

        return UserMapper.toResponse(user);
    }

    @Transactional
    @PreAuthorize("@userAuthorizationService.canUpdate(#idUsuario, authentication)")
    public RespostaUsuarioDto atualizarUsuario(UUID idUsuario, RequisicaoAtualizacaoUsuarioDto updateDto) {
        UserEntity user = userRepository.findById(idUsuario)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        validateUpdateRequest(updateDto);

        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        boolean isManager = UserAuthorizationService.isManagerRole(authentication);
        UUID actorId = authenticatedUserId(authentication);
        boolean isSelf = actorId != null && actorId.equals(idUsuario);
        if (!isManager && !isSelf) {
            if (isManagerProfile(user.getProfile())) {
                throw new AccessDeniedException(
                        "Apenas gerente pode alterar a conta de um gerente"
                );
            }
            if (!atorPossuiTodasPermissoes(authentication, PermissionResolver.effectiveCodes(user))) {
                throw new AccessDeniedException(
                        "Não é permitido alterar a conta de um usuário com permissões superiores às suas"
                );
            }
        }

        boolean canEditUsers = userAuthorizationService
                .hasPermission(authentication, "USUARIOS_EDITAR");
        boolean authenticationChanged = false;
        boolean passwordChanged = false;

        if (!canEditUsers && (updateDto.nomePerfil() != null || updateDto.ativo() != null)) {
            throw new AccessDeniedException(
                    "Apenas gerente pode alterar perfil ou status"
            );
        }
        if (isSelf && !isManager && (updateDto.nomePerfil() != null || updateDto.ativo() != null)) {
            throw new AccessDeniedException(
                    "Não é permitido alterar o próprio perfil ou status"
            );
        }

        boolean emailAlterado = StringUtils.hasText(updateDto.email())
                && !user.getEmail().equalsIgnoreCase(updateDto.email().trim().toLowerCase(Locale.ROOT));
        boolean senhaAlterada = StringUtils.hasText(updateDto.senha());
        if (isSelf && (emailAlterado || senhaAlterada)) {
            if (!StringUtils.hasText(updateDto.senhaAtual())
                    || !passwordEncoder.matches(updateDto.senhaAtual(), user.getPasswordHash())) {
                throw new InvalidUserUpdateException(
                        "Informe a senha atual correta para alterar seu e-mail ou senha"
                );
            }
        }

        if (StringUtils.hasText(updateDto.nome())) {
            user.setName(updateDto.nome().trim());
        }

        if (StringUtils.hasText(updateDto.email())) {
            String novoEmail = updateDto.email().trim().toLowerCase(Locale.ROOT);

            if (!user.getEmail().equalsIgnoreCase(novoEmail)) {
                if (userRepository.existsByEmailIgnoreCase(novoEmail)) {
                    throw new EmailAlreadyExistsException("Email informado já está em uso por outro usuário");
                }
                user.setEmail(novoEmail);
                authenticationChanged = true;
            }
        }

        if (updateDto.telefone() != null) {
            String phone = updateDto.telefone().trim();
            user.setPhone(phone.isEmpty() ? null : phone);
        }

        if (StringUtils.hasText(updateDto.senha())) {
            String senhaCriptografada = passwordEncoder.encode(updateDto.senha());

            user.setPasswordHash(senhaCriptografada);
            authenticationChanged = true;
            passwordChanged = true;
        }

        if (StringUtils.hasText(updateDto.nomePerfil())) {
            String profileName = updateDto.nomePerfil().trim();
            ProfileEntity profile = profileRepository
                    .findByEstablishment_IdAndNameIgnoreCase(
                            user.getEstablishment().getId(), profileName)
                    .orElseThrow(() -> new ProfileNotFoundException(
                            "Perfil não encontrado: " + profileName
                    ));
            garantirAtorPodeAtribuirPerfil(profile, authentication);
            if (!Objects.equals(user.getProfile().getId(), profile.getId())) {
                user.setProfile(profile);
                authenticationChanged = true;
            }
        }

        if (updateDto.ativo() != null && user.isActive() != updateDto.ativo()) {
            user.setStatus(updateDto.ativo() ? UserStatus.ATIVO : UserStatus.INATIVO);
            authenticationChanged = true;
        }

        if (authenticationChanged) {
            servicoPermissoes.garantirAlteracaoUsuarioMantemAdministrador(user);
        }

        UserEntity savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException(
                    "Email informado já está em uso por outro usuário"
            );
        }

        if (authenticationChanged) {
            tokenStateService.revokeAll(savedUser.getId());
        }

        if (passwordChanged) {
            userAccessHistoryService.registrarSenhaAlterada(savedUser);
        }

        businessEventLogger.success("user.updated", "user", savedUser.getId());
        return UserMapper.toResponse(savedUser);
    }

    @Transactional
    @PreAuthorize("@userAuthorizationService.canDelete(#idUsuario, authentication)")
    public void deletarUsuario(UUID idUsuario) {
        UserEntity user = userRepository.findById(idUsuario)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuário não encontrado"
                ));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isManager = UserAuthorizationService.isManagerRole(authentication);
        UUID actorId = authenticatedUserId(authentication);
        boolean isSelf = actorId != null && actorId.equals(idUsuario);
        if (!isManager && !isSelf) {
            if (isManagerProfile(user.getProfile())) {
                throw new AccessDeniedException(
                        "Apenas gerente pode excluir a conta de um gerente"
                );
            }
            if (!atorPossuiTodasPermissoes(authentication, PermissionResolver.effectiveCodes(user))) {
                throw new AccessDeniedException(
                        "Não é permitido excluir a conta de um usuário com permissões superiores às suas"
                );
            }
        }

        try {
            servicoPermissoes.garantirUsuarioPodeSerDesativadoOuExcluido(user);
            tokenStateService.revokeAll(idUsuario);
            userRepository.delete(user);
            userRepository.flush();
            businessEventLogger.success("user.deleted", "user", idUsuario);
        } catch (DataIntegrityViolationException exception) {
            throw new UserDeletionConflictException(
                    "Usuário não pode ser excluído porque possui registros vinculados",
                    exception
            );
        }
    }

    private void validateUpdateRequest(RequisicaoAtualizacaoUsuarioDto updateDto) {
        if (updateDto.nome() == null
                && updateDto.email() == null
                && updateDto.telefone() == null
                && updateDto.senha() == null
                && updateDto.nomePerfil() == null
                && updateDto.ativo() == null) {
            throw new InvalidUserUpdateException(
                    "Informe ao menos um campo para atualização"
            );
        }

        if (updateDto.nome() != null && !StringUtils.hasText(updateDto.nome())) {
            throw new InvalidUserUpdateException("O nome não pode ficar vazio");
        }

        if (updateDto.email() != null && !StringUtils.hasText(updateDto.email())) {
            throw new InvalidUserUpdateException("O e-mail não pode ficar vazio");
        }

        if (updateDto.nomePerfil() != null
                && !StringUtils.hasText(updateDto.nomePerfil())) {
            throw new InvalidUserUpdateException("O perfil não pode ficar vazio");
        }

        if (updateDto.senha() != null && !StringUtils.hasText(updateDto.senha())) {
            throw new InvalidUserUpdateException("A senha não pode ficar vazia");
        }

        if (updateDto.senha() != null
                && updateDto.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidUserUpdateException("A senha excede o limite de 72 bytes");
        }
    }

    private void garantirAtorPodeAtribuirPerfil(ProfileEntity profile, Authentication authentication) {
        if (UserAuthorizationService.isManagerRole(authentication)) {
            return;
        }
        if (isManagerProfile(profile)) {
            throw new AccessDeniedException("Apenas gerente pode atribuir o perfil Gerente");
        }
        if (profile.getFunctionalities() == null || profile.getFunctionalities().isEmpty()) {
            return;
        }
        Set<String> atorPermissoes = authentication == null ? Set.of()
                : authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .collect(java.util.stream.Collectors.toSet());
        Set<String> perfilPermissoes = profile.getFunctionalities().stream()
                .map(FuncionalidadeEntity::getCode)
                .collect(java.util.stream.Collectors.toSet());
        if (!atorPermissoes.containsAll(perfilPermissoes)) {
            throw new AccessDeniedException("Não é permitido atribuir um perfil com permissões superiores às suas");
        }
    }

    private boolean atorPossuiTodasPermissoes(Authentication authentication, Set<String> permissoesAlvo) {
        if (permissoesAlvo == null || permissoesAlvo.isEmpty()) {
            return true;
        }
        Set<String> atorPermissoes = authentication == null ? Set.of()
                : authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(java.util.stream.Collectors.toSet());
        return atorPermissoes.containsAll(permissoesAlvo);
    }

    private boolean isManagerProfile(ProfileEntity profile) {
        return profile != null && "GERENTE".equals(CustomUserDetailsService.normalizeRole(profile.getName()));
    }

    private UUID authenticatedUserId(Authentication authentication) {
        if (!(authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt)) {
            return null;
        }
        String rawUserId = jwt.getToken().getClaimAsString("userId");
        if (!StringUtils.hasText(rawUserId)) {
            return null;
        }
        try {
            return UUID.fromString(rawUserId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private UUID authenticatedEstablishmentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt) {
            return UUID.fromString(jwt.getToken().getClaimAsString("establishmentId"));
        }
        throw new AccessDeniedException("Estabelecimento da sessão não encontrado");
    }
}
