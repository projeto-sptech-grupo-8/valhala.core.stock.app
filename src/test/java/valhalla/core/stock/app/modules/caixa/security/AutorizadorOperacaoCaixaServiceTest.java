package valhalla.core.stock.app.modules.caixa.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.security.PermissionAuthorizationService;
import valhalla.core.stock.app.modules.caixa.exception.AutorizacaoCaixaNegadaException;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.modules.users.service.UserAccessHistoryService;
import valhalla.core.stock.app.support.AutenticacaoTeste;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutorizadorOperacaoCaixaServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserAccessHistoryService userAccessHistoryService;

    private AutorizadorOperacaoCaixaService service;
    private UUID estabelecimentoId;
    private UUID operadorId;

    @BeforeEach
    void setUp() {
        PermissionAuthorizationService permissionAuthorizationService = new PermissionAuthorizationService();
        ContextoCaixa contextoCaixa = new ContextoCaixa();
        service = new AutorizadorOperacaoCaixaService(
                permissionAuthorizationService,
                userRepository,
                passwordEncoder,
                contextoCaixa,
                userAccessHistoryService
        );
        estabelecimentoId = UUID.randomUUID();
        operadorId = UUID.randomUUID();
        AutenticacaoTeste.autenticar(estabelecimentoId, operadorId, "ROLE_ATENDENTE", "CAIXA_OPERAR");
    }

    @AfterEach
    void tearDown() {
        AutenticacaoTeste.limpar();
    }

    @Test
    void deveResolverAutorizadorComCredenciaisValidasEPermissao() {
        UserEntity gerente = usuario("Gerente", "gerente@meraki.com", Set.of());
        when(userRepository.findWithAccessGraphByEmailIgnoreCase("gerente@meraki.com"))
                .thenReturn(Optional.of(gerente));
        when(passwordEncoder.matches("senha-correta", "hash")).thenReturn(true);

        var resolvido = service.exigirAutorizador(
                "CAIXA_CANCELAR",
                "  Gerente@Meraki.com ",
                "senha-correta",
                "Negado"
        );

        assertThat(resolvido.usuarioId()).isEqualTo(gerente.getId());
        assertThat(resolvido.nome()).isEqualTo("Gerente");
        verify(userAccessHistoryService, never()).registrarSenhaIncorreta(any());
    }

    @Test
    void deveRejeitarAutorizadorBloqueadoPorLockedUntil() {
        UserEntity gerente = usuario("Gerente", "gerente@meraki.com", Set.of());
        gerente.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findWithAccessGraphByEmailIgnoreCase("gerente@meraki.com"))
                .thenReturn(Optional.of(gerente));

        assertThatThrownBy(() -> service.exigirAutorizador(
                "CAIXA_CANCELAR",
                "gerente@meraki.com",
                "senha-correta",
                "Negado"
        ))
                .isInstanceOf(AutorizacaoCaixaNegadaException.class)
                .hasMessage("Credenciais de autorização inválidas ou sem permissão para esta operação");

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void deveRegistrarSenhaIncorretaQuandoSenhaDoAutorizadorFalhar() {
        UserEntity gerente = usuario("Gerente", "gerente@meraki.com", Set.of());
        when(userRepository.findWithAccessGraphByEmailIgnoreCase("gerente@meraki.com"))
                .thenReturn(Optional.of(gerente));
        when(passwordEncoder.matches("senha-errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.exigirAutorizador(
                "CAIXA_CANCELAR",
                "gerente@meraki.com",
                "senha-errada",
                "Negado"
        ))
                .isInstanceOf(AutorizacaoCaixaNegadaException.class)
                .hasMessage("Credenciais de autorização inválidas ou sem permissão para esta operação");

        verify(userAccessHistoryService).registrarSenhaIncorreta("gerente@meraki.com");
    }

    @Test
    void deveRetornarMesmaMensagemGenericaQuandoAutorizadorNaoPossuirPermissao() {
        UserEntity atendente = usuario("Atendente", "outro@meraki.com", Set.of("CAIXA_OPERAR"));
        when(userRepository.findWithAccessGraphByEmailIgnoreCase("outro@meraki.com"))
                .thenReturn(Optional.of(atendente));
        when(passwordEncoder.matches("senha-correta", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.exigirAutorizador(
                "CAIXA_ESTORNAR",
                "outro@meraki.com",
                "senha-correta",
                "Negado"
        ))
                .isInstanceOf(AutorizacaoCaixaNegadaException.class)
                .hasMessage("Credenciais de autorização inválidas ou sem permissão para esta operação");

        verify(userAccessHistoryService, never()).registrarSenhaIncorreta(any());
    }

    private UserEntity usuario(String perfilNome, String email, Set<String> permissoes) {
        Set<FuncionalidadeEntity> funcionalidades = new HashSet<>();
        for (String codigo : permissoes) {
            funcionalidades.add(FuncionalidadeEntity.builder().code(codigo).build());
        }
        EstablishmentEntity establishment = EstablishmentEntity.builder().id(estabelecimentoId).build();
        ProfileEntity profile = ProfileEntity.builder()
                .id(1)
                .name(perfilNome)
                .establishment(establishment)
                .functionalities(funcionalidades)
                .build();
        return UserEntity.builder()
                .id(UUID.randomUUID())
                .name(perfilNome)
                .email(email)
                .passwordHash("hash")
                .establishment(establishment)
                .profile(profile)
                .status(UserStatus.ATIVO)
                .build();
    }
}
