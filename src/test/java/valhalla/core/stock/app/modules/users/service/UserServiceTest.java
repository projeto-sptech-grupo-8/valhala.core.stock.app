package valhalla.core.stock.app.modules.users.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.users.dto.UserCreateRequestDto;
import valhalla.core.stock.app.modules.users.dto.UserResponseDto;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.error.EmailAlreadyExistsException;
import valhalla.core.stock.app.shared.error.ProfileNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, profileRepository, passwordEncoder);
    }

    @Test
    void deveCriarUsuarioComEmailNormalizadoESenhaCodificada() {
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ProfileEntity profile = ProfileEntity.builder()
                .id(profileId)
                .name("Administrador")
                .build();
        UserCreateRequestDto request = new UserCreateRequestDto(
                "  Lucas Peres  ",
                "  LUCAS@EXEMPLO.COM  ",
                "senha-segura",
                "  Administrador  "
        );

        when(userRepository.existsByEmailIgnoreCase("lucas@exemplo.com")).thenReturn(false);
        when(profileRepository.findByNameIgnoreCase("Administrador"))
                .thenReturn(Optional.of(profile));
        when(passwordEncoder.encode("senha-segura")).thenReturn("senha-codificada");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });

        UserResponseDto response = userService.criarUsuario(request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        UserEntity savedUser = captor.getValue();

        assertAll(
                () -> assertEquals(userId, response.id()),
                () -> assertEquals("Lucas Peres", savedUser.getName()),
                () -> assertEquals("lucas@exemplo.com", savedUser.getEmail()),
                () -> assertEquals("senha-codificada", savedUser.getPasswordHash()),
                () -> assertNotEquals(request.password(), savedUser.getPasswordHash()),
                () -> assertTrue(savedUser.getActive())
        );
    }

    @Test
    void naoDeveCriarUsuarioQuandoEmailJaExistir() {
        UserCreateRequestDto request = requestValido();
        when(userRepository.existsByEmailIgnoreCase("lucas@exemplo.com")).thenReturn(true);

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> userService.criarUsuario(request)
        );

        verifyNoInteractions(profileRepository, passwordEncoder);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void naoDeveCriarUsuarioQuandoPerfilNaoExistir() {
        UserCreateRequestDto request = requestValido();
        when(userRepository.existsByEmailIgnoreCase("lucas@exemplo.com")).thenReturn(false);
        when(profileRepository.findByNameIgnoreCase(request.profileName()))
                .thenReturn(Optional.empty());

        assertThrows(
                ProfileNotFoundException.class,
                () -> userService.criarUsuario(request)
        );

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveTratarConflitoDeEmailOcorridoDurantePersistencia() {
        UserCreateRequestDto request = requestValido();
        ProfileEntity profile = ProfileEntity.builder()
                .id(UUID.randomUUID())
                .name("Atendente")
                .build();

        when(userRepository.existsByEmailIgnoreCase("lucas@exemplo.com"))
                .thenReturn(false);
        when(profileRepository.findByNameIgnoreCase("Atendente"))
                .thenReturn(Optional.of(profile));
        when(passwordEncoder.encode("senha-segura")).thenReturn("senha-codificada");
        when(userRepository.saveAndFlush(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException("e-mail duplicado"));

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> userService.criarUsuario(request)
        );
    }

    private UserCreateRequestDto requestValido() {
        return new UserCreateRequestDto(
                "Lucas Peres",
                "lucas@exemplo.com",
                "senha-segura",
                "Atendente"
        );
    }
}
