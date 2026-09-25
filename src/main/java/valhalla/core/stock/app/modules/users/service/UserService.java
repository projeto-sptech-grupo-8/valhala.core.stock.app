package valhalla.core.stock.app.modules.users.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.auth.service.TokenStateService;
import valhalla.core.stock.app.modules.users.dto.UserCreateRequestDto;
import valhalla.core.stock.app.modules.users.dto.UserResponseDto;
import valhalla.core.stock.app.modules.users.dto.UserUpdateDto;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.mapper.UserMapper;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.modules.users.security.UserAuthorizationService;
import valhalla.core.stock.app.shared.error.EmailAlreadyExistsException;
import valhalla.core.stock.app.shared.error.InvalidUserUpdateException;
import valhalla.core.stock.app.shared.error.ProfileNotFoundException;
import valhalla.core.stock.app.shared.error.UserDeletionConflictException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAuthorizationService userAuthorizationService;
    private final TokenStateService tokenStateService;

    @Transactional
    @PreAuthorize("@permissionAuthorizationService.canManageUsers(authentication)")
    public UserResponseDto criarUsuario(UserCreateRequestDto dtoRequest) {
        String emailNormalizado = dtoRequest.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new EmailAlreadyExistsException("Email informado já cadastrado");
        }

        UUID establishmentId = authenticatedEstablishmentId();
        String profileName = dtoRequest.profileName().trim();
        ProfileEntity profile = profileRepository
                .findByEstablishment_IdAndNameIgnoreCase(establishmentId, profileName)
                .orElseThrow(() ->
                        new ProfileNotFoundException(
                                "Perfil não encontrado: " + profileName
                        )
                );

        String passwordHash = passwordEncoder.encode(dtoRequest.password());
        UserEntity user = UserMapper.toEntity(dtoRequest, passwordHash, profile);

        UserEntity savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException("Email informado já cadastrado");
        }

        return UserMapper.toResponse(savedUser);
    }

    @Transactional
    @PreAuthorize("@permissionAuthorizationService.canManageUsers(authentication)")
    public List<UserResponseDto> listarUsuarios() {
        return userRepository.findAllByEstablishment_Id(
                        authenticatedEstablishmentId(), Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(UserMapper::toResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize("@userAuthorizationService.canUpdate(#idUsuario, authentication)")
    public UserResponseDto buscarUsuario(UUID idUsuario) {
        UserEntity user = userRepository.findById(idUsuario)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuário não encontrado"
                ));

        return UserMapper.toResponse(user);
    }

    @Transactional
    @PreAuthorize("@userAuthorizationService.canUpdate(#idUsuario, authentication)")
    public UserResponseDto atualizarUsuario(UUID idUsuario, UserUpdateDto updateDto) {
        UserEntity user = userRepository.findById(idUsuario)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        validateUpdateRequest(updateDto);

        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        boolean manager = userAuthorizationService.isManager(authentication);
        boolean authenticationChanged = false;

        if (!manager && (updateDto.profileName() != null || updateDto.active() != null)) {
            throw new AccessDeniedException(
                    "Apenas gerente pode alterar perfil ou status"
            );
        }

        List<UserEntity> lista = new ArrayList<>();

            

        if (StringUtils.hasText(updateDto.name())) {
            user.setName(updateDto.name().trim());
        }

        if (StringUtils.hasText(updateDto.email())) {
            String novoEmail = updateDto.email().trim().toLowerCase(Locale.ROOT);

            if (!user.getEmail().equalsIgnoreCase(novoEmail)) {
                if (userRepository.existsByEmailIgnoreCase(novoEmail)) {
                    throw new EmailAlreadyExistsException("Email informado já está em uso por outro usuário");
                }
                user.setEmail(novoEmail);
            }
        }

        if (updateDto.phone() != null) {
            String phone = updateDto.phone().trim();
            user.setPhone(phone.isEmpty() ? null : phone);
        }

        if (StringUtils.hasText(updateDto.password())) {
            String senhaCriptografada = passwordEncoder.encode(updateDto.password());

            user.setPasswordHash(senhaCriptografada);
            authenticationChanged = true;
        }

        if (StringUtils.hasText(updateDto.profileName())) {
            String profileName = updateDto.profileName().trim();
            ProfileEntity profile = profileRepository
                    .findByEstablishment_IdAndNameIgnoreCase(
                            user.getEstablishment().getId(), profileName)
                    .orElseThrow(() -> new ProfileNotFoundException(
                            "Perfil não encontrado: " + profileName
                    ));
            if (!Objects.equals(user.getProfile().getId(), profile.getId())) {
                user.setProfile(profile);
                authenticationChanged = true;
            }
        }

        if (updateDto.active() != null && user.isActive() != updateDto.active()) {
            user.setStatus(updateDto.active() ? UserStatus.ATIVO : UserStatus.INATIVO);
            authenticationChanged = true;
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

        return UserMapper.toResponse(savedUser);
    }

    @Transactional
    @PreAuthorize("@userAuthorizationService.canUpdate(#idUsuario, authentication)")
    public void deletarUsuario(UUID idUsuario) {
        UserEntity user = userRepository.findById(idUsuario)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuário não encontrado"
                ));

        try {
            tokenStateService.revokeAll(idUsuario);
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new UserDeletionConflictException(
                    "Usuário não pode ser excluído porque possui registros vinculados",
                    exception
            );
        }
    }

    private void validateUpdateRequest(UserUpdateDto updateDto) {
        if (updateDto.name() == null
                && updateDto.email() == null
                && updateDto.phone() == null
                && updateDto.password() == null
                && updateDto.profileName() == null
                && updateDto.active() == null) {
            throw new InvalidUserUpdateException(
                    "Informe ao menos um campo para atualização"
            );
        }

        if (updateDto.name() != null && !StringUtils.hasText(updateDto.name())) {
            throw new InvalidUserUpdateException("O nome não pode ficar vazio");
        }

        if (updateDto.email() != null && !StringUtils.hasText(updateDto.email())) {
            throw new InvalidUserUpdateException("O e-mail não pode ficar vazio");
        }

        if (updateDto.profileName() != null
                && !StringUtils.hasText(updateDto.profileName())) {
            throw new InvalidUserUpdateException("O perfil não pode ficar vazio");
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
