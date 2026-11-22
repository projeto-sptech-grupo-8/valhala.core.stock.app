package valhalla.core.stock.app.modules.users.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.users.dto.RespostaHistoricoAcessoDto;
import valhalla.core.stock.app.modules.users.entity.TipoEventoAcessoUsuario;
import valhalla.core.stock.app.modules.users.entity.UserAccessHistoryEntity;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserAccessHistoryRepository;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAccessHistoryServiceTest {

    @Mock
    private UserAccessHistoryRepository userAccessHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RequestAccessMetadataExtractor metadataExtractor;

    private UserAccessHistoryService service;

    @BeforeEach
    void setUp() {
        service = new UserAccessHistoryService(
                userAccessHistoryRepository,
                userRepository,
                metadataExtractor
        );
    }

    @Test
    void deveRegistrarLoginSucessoAtualizandoUltimoLoginEZerandoTentativas() {
        UserEntity user = sampleUser();
        user.setLoginAttempts(3);
        when(metadataExtractor.extractCurrent())
                .thenReturn(new RequestAccessMetadataExtractor.AccessMetadata("10.0.0.24", "Windows · Chrome", "UA"));

        service.registrarLoginSucesso(user);

        assertThat(user.getLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(userRepository).save(user);

        ArgumentCaptor<UserAccessHistoryEntity> captor = ArgumentCaptor.forClass(UserAccessHistoryEntity.class);
        verify(userAccessHistoryRepository).save(captor.capture());
        UserAccessHistoryEntity saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(TipoEventoAcessoUsuario.LOGIN_SUCESSO);
        assertThat(saved.getSourceIp()).isEqualTo("10.0.0.24");
        assertThat(saved.getDevice()).isEqualTo("Windows · Chrome");
        assertThat(saved.getUserAgent()).isEqualTo("UA");
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getEstablishment()).isSameAs(user.getEstablishment());
    }

    @Test
    void deveRegistrarSenhaIncorretaQuandoEmailExistirEIncrementarTentativas() {
        UserEntity user = sampleUser();
        user.setLoginAttempts(null);
        when(userRepository.findByEmailIgnoreCase("op@meraki.com")).thenReturn(Optional.of(user));
        when(metadataExtractor.extractCurrent())
                .thenReturn(new RequestAccessMetadataExtractor.AccessMetadata("45.12.88.101", "Linux · Firefox", "UA"));

        service.registrarSenhaIncorreta("op@meraki.com");

        assertThat(user.getLoginAttempts()).isEqualTo(1);
        verify(userRepository).save(user);
        ArgumentCaptor<UserAccessHistoryEntity> captor = ArgumentCaptor.forClass(UserAccessHistoryEntity.class);
        verify(userAccessHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(TipoEventoAcessoUsuario.SENHA_INCORRETA);
    }

    @Test
    void naoDeveRegistrarSenhaIncorretaQuandoEmailNaoExistir() {
        when(userRepository.findByEmailIgnoreCase("desconhecido@meraki.com")).thenReturn(Optional.empty());

        service.registrarSenhaIncorreta("desconhecido@meraki.com");

        verifyNoInteractions(userAccessHistoryRepository, metadataExtractor);
    }

    @Test
    void deveRejeitarLimiteForaDoIntervaloPermitido() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.listarUltimosAcessos(userId, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O limite deve estar entre 1 e 50");

        assertThatThrownBy(() -> service.listarUltimosAcessos(userId, 51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O limite deve estar entre 1 e 50");

        verifyNoInteractions(userRepository, userAccessHistoryRepository);
    }

    @Test
    void deveLancarNotFoundQuandoUsuarioNaoExistirAoListarAcessos() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> service.listarUltimosAcessos(userId, 10))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Usuário não encontrado");

        verifyNoInteractions(userAccessHistoryRepository);
    }

    @Test
    void deveListarUltimosAcessosMapeados() {
        UUID userId = UUID.randomUUID();
        UserEntity user = sampleUser();
        LocalDateTime now = LocalDateTime.now();
        UserAccessHistoryEntity entry = UserAccessHistoryEntity.builder()
                .id(7L)
                .user(user)
                .establishment(user.getEstablishment())
                .eventType(TipoEventoAcessoUsuario.SENHA_ALTERADA)
                .sourceIp("10.0.0.24")
                .device("Windows · Chrome")
                .occurredAt(now)
                .build();

        when(userRepository.existsById(userId)).thenReturn(true);
        when(userAccessHistoryRepository.findByUser_IdOrderByOccurredAtDescIdDesc(userId, PageRequest.of(0, 10)))
                .thenReturn(List.of(entry));

        List<RespostaHistoricoAcessoDto> result = service.listarUltimosAcessos(userId, 10);

        assertThat(result).containsExactly(
                new RespostaHistoricoAcessoDto(7L, TipoEventoAcessoUsuario.SENHA_ALTERADA, "10.0.0.24", "Windows · Chrome", now)
        );
    }

    private UserEntity sampleUser() {
        EstablishmentEntity establishment = EstablishmentEntity.builder()
                .id(UUID.randomUUID())
                .corporateName("Meraki LTDA")
                .tradeName("Meraki")
                .cnpj("12345678000199")
                .build();
        ProfileEntity profile = ProfileEntity.builder()
                .id(1)
                .name("Atendente")
                .establishment(establishment)
                .build();
        return UserEntity.builder()
                .id(UUID.randomUUID())
                .name("Operador")
                .email("op@meraki.com")
                .passwordHash("hash")
                .establishment(establishment)
                .profile(profile)
                .status(UserStatus.ATIVO)
                .loginAttempts(0)
                .build();
    }
}
