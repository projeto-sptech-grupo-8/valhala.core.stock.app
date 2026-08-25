package valhalla.core.stock.app.modules.users.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.users.dto.UserCreateRequestDto;
import valhalla.core.stock.app.modules.users.dto.UserResponseDto;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.mapper.UserMapper;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.error.EmailAlreadyExistsException;
import valhalla.core.stock.app.shared.error.ProfileNotFoundException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

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
}
