package valhalla.core.stock.app.modules.users.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.users.dto.UserCreateRequestDto;
import valhalla.core.stock.app.modules.users.dto.UserResponseDto;
import valhalla.core.stock.app.modules.users.dto.UserUpdateDto;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.mapper.UserMapper;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.modules.users.security.UserAuthorizationService;
import valhalla.core.stock.app.shared.error.EmailAlreadyExistsException;
import valhalla.core.stock.app.shared.error.InvalidUserUpdateException;
import valhalla.core.stock.app.shared.error.ProfileNotFoundException;
import valhalla.core.stock.app.shared.error.UsuarioNaoAutorizadoException;
import valhalla.core.stock.app.shared.utils.AuthenticatorUtils;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAuthorizationService userAuthorizationService;
    private final AuthenticatorUtils authenticatorUtils;

    @Transactional
    @PreAuthorize("hasRole('GERENTE')")
    public UserResponseDto criarUsuario(UserCreateRequestDto dtoRequest) {
        String emailNormalizado = dtoRequest.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new EmailAlreadyExistsException("Email informado já cadastrado");
        }

        String profileName = dtoRequest.profileName().trim();
        ProfileEntity profile = profileRepository
                .findByNameIgnoreCase(profileName)
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
    @PreAuthorize("@userAuthorizationService.canUpdate(#idUsuario, authentication)")
    public UserResponseDto atualizarUsuario(UUID idUsuario, UserUpdateDto updateDto) {
        UserEntity user = userRepository.findById(idUsuario)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        validateUpdateRequest(updateDto);

        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        boolean manager = userAuthorizationService.isManager(authentication);

        if (!manager && (updateDto.profileName() != null || updateDto.active() != null)) {
            throw new AccessDeniedException(
                    "Apenas gerente pode alterar perfil ou status"
            );
        }

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
        }

        if (StringUtils.hasText(updateDto.profileName())) {
            String profileName = updateDto.profileName().trim();
            ProfileEntity profile = profileRepository
                    .findByNameIgnoreCase(profileName)
                    .orElseThrow(() -> new ProfileNotFoundException(
                            "Perfil não encontrado: " + profileName
                    ));
            user.setProfile(profile);
        }

        if (updateDto.active() != null) {
            user.setActive(updateDto.active());
        }

        UserEntity savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException(
                    "Email informado já está em uso por outro usuário"
            );
        }

        return UserMapper.toResponse(savedUser);
    }

    public ResponseEntity<Void> deleteUsuarioId(UUID id) {

        if (!userRepository.existsById(id)) {
            return ResponseEntity.status(404).build();
        }

        UUID idUsuarioAutenticado = authenticatorUtils.getUserId();

        if (!(idUsuarioAutenticado.equals(id)) || !(authenticatorUtils.hasRole("gerente"))) {
            throw new UsuarioNaoAutorizadoException("Usuario não possui permissão para excluir esse usuario");
        }

        return ResponseEntity.ok().build();
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
}
