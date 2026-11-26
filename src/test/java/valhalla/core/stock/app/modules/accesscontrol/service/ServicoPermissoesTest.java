package valhalla.core.stock.app.modules.accesscontrol.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import valhalla.core.stock.app.modules.accesscontrol.dto.*;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.PermissionEffect;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.UserFunctionalityOverrideEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.FuncionalidadeRepository;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.auth.service.TokenStateService;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.establishments.repository.EstablishmentRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.error.AccessConfigurationConflictException;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServicoPermissoesTest {

    @Mock private ProfileRepository repositorioPerfil;
    @Mock private FuncionalidadeRepository repositorioFuncionalidade;
    @Mock private UserRepository repositorioUsuario;
    @Mock private TokenStateService servicoEstadoToken;
    @Mock private EstablishmentRepository repositorioEstabelecimento;

    private ServicoPermissoes servicoPermissoes;
    private UUID idEstabelecimento;
    private JwtAuthenticationToken autenticacao;

    @BeforeEach
    void preparar() {
        servicoPermissoes = new ServicoPermissoes(repositorioPerfil, repositorioFuncionalidade,
                repositorioUsuario, servicoEstadoToken, repositorioEstabelecimento,
                new BusinessEventLogger());
        idEstabelecimento = UUID.randomUUID();
        Jwt token = Jwt.withTokenValue("teste").header("alg", "none")
                .claim("establishmentId", idEstabelecimento.toString())
                .claim("userId", UUID.randomUUID().toString()).build();
        autenticacao = new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("ROLE_GERENTE")));
    }

    @Test
    void deveListarFuncionalidadesDisponiveis() {
        FuncionalidadeEntity f1 = FuncionalidadeEntity.builder().id(1).code("PROD_CRIAR").name("Criar").description("Desc").build();
        when(repositorioFuncionalidade.findAll()).thenReturn(List.of(f1));

        List<RespostaFuncionalidadeDto> lista = servicoPermissoes.listarFuncionalidadesDisponiveis();

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).codigo()).isEqualTo("PROD_CRIAR");
    }

    @Test
    void deveListarApenasPerfisDoEstabelecimentoAutenticado() {
        ProfileEntity p1 = perfil("Perfil 1");

        when(repositorioPerfil.findAllByEstablishment_IdOrderByNameAsc(idEstabelecimento)).thenReturn(List.of(p1));

        List<RespostaPerfilDto> perfis = servicoPermissoes.listarPerfis(autenticacao);

        assertThat(perfis).hasSize(1);
        assertThat(perfis.get(0).nome()).isEqualTo("Perfil 1");
    }

    @Test
    void deveCriarPerfilComFuncionalidadesValidas() {
        FuncionalidadeEntity f = FuncionalidadeEntity.builder().id(1).code("ESTOQUE_VER").build();
        when(repositorioPerfil.findByEstablishment_IdAndNameIgnoreCase(idEstabelecimento, "Estoquista"))
                .thenReturn(Optional.empty());
        when(repositorioFuncionalidade.findByCodeIn(Set.of("ESTOQUE_VER"))).thenReturn(List.of(f));
        when(repositorioEstabelecimento.getReferenceById(idEstabelecimento))
                .thenReturn(EstablishmentEntity.builder().id(idEstabelecimento).build());
        when(repositorioPerfil.saveAndFlush(any(ProfileEntity.class))).thenAnswer(inv -> {
            ProfileEntity salvo = inv.getArgument(0);
            salvo.setId(10);
            return salvo;
        });

        RespostaPerfilDto resposta = servicoPermissoes.criarPerfil(
                new RequisicaoPerfilDto("  Estoquista  ", "Acesso estoque", Set.of("ESTOQUE_VER")), autenticacao);

        assertThat(resposta.nome()).isEqualTo("Estoquista");
        assertThat(resposta.codigosFuncionalidades()).containsExactly("ESTOQUE_VER");
        verify(repositorioPerfil).saveAndFlush(any());
    }

    @Test
    void naoDeveCriarPerfilComNomeJaExistenteSemDiferenciarMaiusculas() {
        ProfileEntity perfilExistente = perfil("Atendente");
        when(repositorioPerfil.findByEstablishment_IdAndNameIgnoreCase(idEstabelecimento, "atendente"))
                .thenReturn(Optional.of(perfilExistente));

        assertThatThrownBy(() -> servicoPermissoes.criarPerfil(
                new RequisicaoPerfilDto("atendente", null, Set.of()), autenticacao))
                .isInstanceOf(AccessConfigurationConflictException.class);

        verify(repositorioPerfil, never()).saveAndFlush(any());
    }

    @Test
    void deveBuscarPerfilExistenteDoMesmoEstabelecimento() {
        ProfileEntity perfil = perfil("Garçom");
        when(repositorioPerfil.findById(1)).thenReturn(Optional.of(perfil));

        RespostaPerfilDto dto = servicoPermissoes.buscarPerfil(1, autenticacao);

        assertThat(dto.nome()).isEqualTo("Garçom");
    }

    @Test
    void deveFalharAoBuscarPerfilDeOutroEstabelecimento() {
        ProfileEntity perfil = perfil("Garçom");
        perfil.getEstablishment().setId(UUID.randomUUID());
        when(repositorioPerfil.findById(1)).thenReturn(Optional.of(perfil));

        assertThatThrownBy(() -> servicoPermissoes.buscarPerfil(1, autenticacao))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void naoDeveAtualizarPerfilComNomeEmBranco() {
        ProfileEntity perfil = perfil("Atendente");
        when(repositorioPerfil.findById(perfil.getId())).thenReturn(Optional.of(perfil));

        assertThatThrownBy(() -> servicoPermissoes.atualizarPerfil(perfil.getId(),
                new RequisicaoAtualizacaoPerfilDto("   ", null), autenticacao))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vazio");

        verify(repositorioPerfil, never()).saveAndFlush(any());
    }

    @Test
    void naoDeveAtualizarPerfilSemNenhumCampoInformado() {
        assertThatThrownBy(() -> servicoPermissoes.atualizarPerfil(1,
                new RequisicaoAtualizacaoPerfilDto(null, null), autenticacao))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Informe ao menos um campo");
    }

    @Test
    void deveAtualizarNomeEDescricaoDoPerfil() {
        ProfileEntity perfil = perfil("Atendente");
        when(repositorioPerfil.findById(1)).thenReturn(Optional.of(perfil));
        when(repositorioPerfil.findByEstablishment_IdAndNameIgnoreCase(idEstabelecimento, "Balcão"))
                .thenReturn(Optional.empty());
        when(repositorioPerfil.saveAndFlush(perfil)).thenReturn(perfil);

        RespostaPerfilDto dto = servicoPermissoes.atualizarPerfil(1,
                new RequisicaoAtualizacaoPerfilDto(" Balcão ", "Nova desc"), autenticacao);

        assertThat(dto.nome()).isEqualTo("Balcão");
        assertThat(dto.descricao()).isEqualTo("Nova desc");
    }

    @Test
    void deveExcluirPerfilSemUsuariosVinculados() {
        ProfileEntity perfil = perfil("Temporário");
        when(repositorioPerfil.findById(1)).thenReturn(Optional.of(perfil));
        when(repositorioUsuario.findAllByProfile_Id(1)).thenReturn(List.of());

        servicoPermissoes.excluirPerfil(1, autenticacao);

        verify(repositorioPerfil).delete(perfil);
    }

    @Test
    void naoDeveExcluirPerfilComUsuariosVinculados() {
        ProfileEntity perfil = perfil("Ocupado");
        when(repositorioPerfil.findById(1)).thenReturn(Optional.of(perfil));
        when(repositorioUsuario.findAllByProfile_Id(1)).thenReturn(List.of(usuario("Ocupado", Set.of())));

        assertThatThrownBy(() -> servicoPermissoes.excluirPerfil(1, autenticacao))
                .isInstanceOf(AccessConfigurationConflictException.class)
                .hasMessageContaining("possui usuários vinculados");

        verify(repositorioPerfil, never()).delete(any());
    }

    @Test
    void deveSubstituirFuncionalidadesDoPerfilERevogarTokensDosUsuarios() {
        ProfileEntity perfil = perfil("Operador");
        UserEntity u1 = usuario("Operador", Set.of());
        UserEntity gerente = usuario("Gerente", Set.of()); // garante que existe admin

        FuncionalidadeEntity f = FuncionalidadeEntity.builder().code("CAIXA_OPERAR").build();

        when(repositorioPerfil.findById(1)).thenReturn(Optional.of(perfil));
        when(repositorioFuncionalidade.findByCodeIn(Set.of("CAIXA_OPERAR"))).thenReturn(List.of(f));
        when(repositorioUsuario.findAllByEstablishment_Id(idEstabelecimento)).thenReturn(List.of(gerente, u1));
        when(repositorioUsuario.findAllByProfile_Id(1)).thenReturn(List.of(u1));
        when(repositorioPerfil.save(perfil)).thenReturn(perfil);

        RespostaPerfilDto dto = servicoPermissoes.substituirFuncionalidadesPerfil(
                1, Set.of("CAIXA_OPERAR"), autenticacao);

        assertThat(dto.codigosFuncionalidades()).containsExactly("CAIXA_OPERAR");
        verify(servicoEstadoToken).revokeAll(u1.getId());
    }

    @Test
    void naoDeveRemoverAPermissaoDoUltimoUsuarioQuePodeCriarUsuarios() {
        UserEntity usuario = usuario("Atendente", Set.of(sobrescrita("USUARIOS_CRIAR")));
        when(repositorioUsuario.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(repositorioFuncionalidade.findByCodeIn(Set.of())).thenReturn(List.of());
        when(repositorioUsuario.findAllByEstablishment_Id(idEstabelecimento)).thenReturn(List.of(usuario));

        assertThatThrownBy(() -> servicoPermissoes.substituirSobrescritasPermissaoUsuario(
                usuario.getId(), Set.of(), autenticacao))
                .isInstanceOf(AccessConfigurationConflictException.class);

        verify(repositorioUsuario, never()).save(any());
        verify(servicoEstadoToken, never()).revokeAll(any());
    }

    @Test
    void gerenteAtivoPermaneceAdministradorMesmoSemFuncionalidadesNoPerfil() {
        UserEntity gerente = usuario("Gerente", Set.of());
        when(repositorioUsuario.findById(gerente.getId())).thenReturn(Optional.of(gerente));
        when(repositorioFuncionalidade.findByCodeIn(Set.of())).thenReturn(List.of());
        when(repositorioUsuario.findAllByEstablishment_Id(idEstabelecimento)).thenReturn(List.of(gerente));

        assertDoesNotThrow(() -> servicoPermissoes.substituirSobrescritasPermissaoUsuario(
                gerente.getId(), Set.of(), autenticacao));

        verify(repositorioUsuario).save(gerente);
        verify(servicoEstadoToken).revokeAll(gerente.getId());
    }

    @Test
    void deveRejeitarSobrescritasComFuncionalidadesRepetidas() {
        UserEntity usuario = usuario("Atendente", Set.of());
        when(repositorioUsuario.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        Set<SobrescritaPermissaoUsuarioDto> repetidas = new LinkedHashSet<>(List.of(
                new SobrescritaPermissaoUsuarioDto("ESTOQUE_VER", PermissionEffect.GRANT),
                new SobrescritaPermissaoUsuarioDto("ESTOQUE_VER", PermissionEffect.REVOKE)
        ));

        assertThatThrownBy(() -> servicoPermissoes.substituirSobrescritasPermissaoUsuario(
                usuario.getId(), repetidas, autenticacao))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apenas uma sobrescrita");
    }

    @Test
    void deveSubstituirSobrescritasComSucesso() {
        UserEntity usuario = usuario("Atendente", Set.of());
        UserEntity admin = usuario("Gerente", Set.of());
        FuncionalidadeEntity f = FuncionalidadeEntity.builder().code("PRODUTOS_EDITAR").build();

        when(repositorioUsuario.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(repositorioFuncionalidade.findByCodeIn(Set.of("PRODUTOS_EDITAR"))).thenReturn(List.of(f));
        when(repositorioUsuario.findAllByEstablishment_Id(idEstabelecimento)).thenReturn(List.of(admin, usuario));

        Set<SobrescritaPermissaoUsuarioDto> novas = Set.of(
                new SobrescritaPermissaoUsuarioDto("PRODUTOS_EDITAR", PermissionEffect.GRANT));

        servicoPermissoes.substituirSobrescritasPermissaoUsuario(usuario.getId(), novas, autenticacao);

        verify(repositorioUsuario).save(usuario);
        verify(servicoEstadoToken).revokeAll(usuario.getId());
        assertThat(usuario.getFunctionalityOverrides()).hasSize(1);
    }

    @Test
    void devePermitirDesativarUsuarioComumQueNaoPodeCriarUsuarios() {
        UserEntity comum = usuario("Garçom", Set.of());
        assertDoesNotThrow(() -> servicoPermissoes.garantirUsuarioPodeSerDesativadoOuExcluido(comum));
    }

    @Test
    void devePermitirDesativarAdminSeOutroAdminExistir() {
        UserEntity admin1 = usuario("Gerente", Set.of());
        UserEntity admin2 = usuario("Gerente", Set.of());
        when(repositorioUsuario.findAllByEstablishment_Id(idEstabelecimento)).thenReturn(List.of(admin1, admin2));

        assertDoesNotThrow(() -> servicoPermissoes.garantirUsuarioPodeSerDesativadoOuExcluido(admin1));
    }

    @Test
    void deveImpedirDesativarUltimoAdminDoEstabelecimento() {
        UserEntity adminUnico = usuario("Gerente", Set.of());
        when(repositorioUsuario.findAllByEstablishment_Id(idEstabelecimento)).thenReturn(List.of(adminUnico));

        assertThatThrownBy(() -> servicoPermissoes.garantirUsuarioPodeSerDesativadoOuExcluido(adminUnico))
                .isInstanceOf(AccessConfigurationConflictException.class)
                .hasMessageContaining("último usuário ativo com USUARIOS_CRIAR");
    }

    @Test
    void deveBuscarPermissoesEfetivasDoUsuario() {
        FuncionalidadeEntity fPerfil = FuncionalidadeEntity.builder().code("ESTOQUE_VER").build();
        FuncionalidadeEntity fGrant = FuncionalidadeEntity.builder().code("ESTOQUE_AJUSTAR").build();

        ProfileEntity perfil = perfil("Estoquista");
        perfil.setFunctionalities(Set.of(fPerfil));

        UserEntity user = UserEntity.builder()
                .id(UUID.randomUUID())
                .profile(perfil)
                .establishment(perfil.getEstablishment())
                .status(UserStatus.ATIVO)
                .functionalityOverrides(new HashSet<>(List.of(
                        UserFunctionalityOverrideEntity.builder()
                                .functionality(fGrant)
                                .effect(PermissionEffect.GRANT)
                                .build())))
                .build();

        when(repositorioUsuario.findById(user.getId())).thenReturn(Optional.of(user));

        RespostaPermissoesUsuarioDto dto = servicoPermissoes.buscarPermissoesUsuario(user.getId(), autenticacao);

        assertThat(dto.permissoesPerfil()).contains("ESTOQUE_VER");
        assertThat(dto.permissoesEfetivas()).contains("ESTOQUE_VER", "ESTOQUE_AJUSTAR");
        assertThat(dto.sobrescritas()).extracting(SobrescritaPermissaoUsuarioDto::codigoFuncionalidade)
                .containsExactly("ESTOQUE_AJUSTAR");
    }

    @Test
    void deveBloquearCriacaoOuRenomeacaoParaVarianteNormalizadaDeGerente() {
        assertThatThrownBy(() -> servicoPermissoes.criarPerfil(
                new RequisicaoPerfilDto("Gérênte", "Bypass", Set.of()), autenticacao))
                .isInstanceOf(AccessConfigurationConflictException.class)
                .hasMessageContaining("reservado");

        ProfileEntity operador = perfil("Operador");
        when(repositorioPerfil.findById(1)).thenReturn(Optional.of(operador));

        assertThatThrownBy(() -> servicoPermissoes.atualizarPerfil(1,
                new RequisicaoAtualizacaoPerfilDto("Gerente!", null), autenticacao))
                .isInstanceOf(AccessConfigurationConflictException.class)
                .hasMessageContaining("reservado");
    }

    @Test
    void deveBloquearRenomeacaoExclusaoEEdicaoDeGerentePorNaoGerente() {
        ProfileEntity perfilGerente = perfil("Gerente");
        when(repositorioPerfil.findById(1)).thenReturn(Optional.of(perfilGerente));

        assertThatThrownBy(() -> servicoPermissoes.atualizarPerfil(1,
                new RequisicaoAtualizacaoPerfilDto("Outro Nome", null), autenticacao))
                .isInstanceOf(AccessConfigurationConflictException.class)
                .hasMessageContaining("não pode ser alterado");

        assertThatThrownBy(() -> servicoPermissoes.excluirPerfil(1, autenticacao))
                .isInstanceOf(AccessConfigurationConflictException.class)
                .hasMessageContaining("não pode ser excluído");

        Jwt tokenNaoGerente = Jwt.withTokenValue("teste").header("alg", "none")
                .claim("establishmentId", idEstabelecimento.toString())
                .claim("userId", UUID.randomUUID().toString()).build();
        JwtAuthenticationToken authNaoGerente = new JwtAuthenticationToken(
                tokenNaoGerente, List.of(new SimpleGrantedAuthority("PERFIS_GERENCIAR")));

        assertThatThrownBy(() -> servicoPermissoes.atualizarPerfil(1,
                new RequisicaoAtualizacaoPerfilDto(null, "Desc"), authNaoGerente))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> servicoPermissoes.substituirFuncionalidadesPerfil(1, Set.of(), authNaoGerente))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deveBloquearConcessaoDePermissoesNaoPossuidasEAutoEdicaoDeSobrescritas() {
        UUID atorId = UUID.randomUUID();
        Jwt tokenAtor = Jwt.withTokenValue("teste").header("alg", "none")
                .claim("establishmentId", idEstabelecimento.toString())
                .claim("userId", atorId.toString()).build();
        JwtAuthenticationToken authAtor = new JwtAuthenticationToken(
                tokenAtor,
                List.of(
                        new SimpleGrantedAuthority("PERFIS_GERENCIAR"),
                        new SimpleGrantedAuthority("PERMISSOES_GERENCIAR"),
                        new SimpleGrantedAuthority("PRODUTOS_CRIAR")
                ));

        when(repositorioPerfil.findByEstablishment_IdAndNameIgnoreCase(idEstabelecimento, "Supervisor"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicoPermissoes.criarPerfil(
                new RequisicaoPerfilDto("Supervisor", null, Set.of("USUARIOS_CRIAR")), authAtor))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("não possui");

        UserEntity proprioUsuario = usuario("Atendente", Set.of());
        proprioUsuario.setId(atorId);
        when(repositorioUsuario.findById(atorId)).thenReturn(Optional.of(proprioUsuario));

        assertThatThrownBy(() -> servicoPermissoes.substituirSobrescritasPermissaoUsuario(
                atorId,
                Set.of(new SobrescritaPermissaoUsuarioDto("USUARIOS_CRIAR", PermissionEffect.GRANT)),
                authAtor))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("próprias sobrescritas");

        UserEntity outroUsuario = usuario("Atendente", Set.of());
        when(repositorioUsuario.findById(outroUsuario.getId())).thenReturn(Optional.of(outroUsuario));

        assertThatThrownBy(() -> servicoPermissoes.substituirSobrescritasPermissaoUsuario(
                outroUsuario.getId(),
                Set.of(new SobrescritaPermissaoUsuarioDto("USUARIOS_CRIAR", PermissionEffect.GRANT)),
                authAtor))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("não possui");
    }

    private UserEntity usuario(String nomePerfil, Set<UserFunctionalityOverrideEntity> sobrescritas) {
        ProfileEntity perfil = perfil(nomePerfil);
        UserEntity usuario = UserEntity.builder().id(UUID.randomUUID()).profile(perfil)
                .establishment(perfil.getEstablishment()).status(UserStatus.ATIVO)
                .functionalityOverrides(new java.util.HashSet<>(sobrescritas)).build();
        sobrescritas.forEach(sobrescrita -> sobrescrita.setUser(usuario));
        return usuario;
    }

    private ProfileEntity perfil(String nome) {
        return ProfileEntity.builder().id(1).name(nome)
                .establishment(EstablishmentEntity.builder().id(idEstabelecimento).build())
                .functionalities(new HashSet<>()).build();
    }

    private UserFunctionalityOverrideEntity sobrescrita(String codigo) {
        return UserFunctionalityOverrideEntity.builder()
                .functionality(FuncionalidadeEntity.builder().code(codigo).build())
                .effect(PermissionEffect.GRANT).build();
    }
}
