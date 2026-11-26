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
import valhalla.core.stock.app.modules.users.security.UserAuthorizationService;
import valhalla.core.stock.app.shared.error.AccessConfigurationConflictException;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicoPermissoes {
    private final ProfileRepository profileRepository;
    private final FuncionalidadeRepository funcionalidadeRepository;
    private final UserRepository userRepository;
    private final TokenStateService tokenStateService;
    private final EstablishmentRepository establishmentRepository;
    private final BusinessEventLogger businessEventLogger;

    @Transactional
    public java.util.List<RespostaFuncionalidadeDto> listarFuncionalidadesDisponiveis() {
        return funcionalidadeRepository.findAll().stream()
                .map(item -> new RespostaFuncionalidadeDto(item.getId(), item.getCode(),
                        item.getName(), item.getDescription())).toList();
    }

    @Transactional
    public java.util.List<RespostaPerfilDto> listarPerfis(Authentication autenticacao) {
        UUID idEstabelecimento = idEstabelecimentoAtual(autenticacao);
        return profileRepository.findAllByEstablishment_IdOrderByNameAsc(idEstabelecimento).stream()
                .map(this::paraRespostaPerfil).toList();
    }

    @Transactional
    public RespostaPerfilDto criarPerfil(RequisicaoPerfilDto requisicao, Authentication autenticacao) {
        UUID idEstabelecimento = idEstabelecimentoAtual(autenticacao);
        String nome = requisicao.nome().trim();
        validarNomePerfilDisponivel(idEstabelecimento, nome, null);
        Set<String> codigos = conjuntoVazioQuandoNulo(requisicao.codigosFuncionalidades());
        garantirAtorPossuiPermissoes(codigos, autenticacao);
        ProfileEntity perfil = ProfileEntity.builder()
                .name(nome).description(requisicao.descricao())
                .establishment(establishmentRepository.getReferenceById(idEstabelecimento))
                .functionalities(carregarFuncionalidadesPorCodigo(codigos))
                .build();
        ProfileEntity salvo = salvarPerfil(perfil);
        businessEventLogger.success("access.profile.created", "profile", salvo.getId());
        return paraRespostaPerfil(salvo);
    }

    @Transactional
    public RespostaPerfilDto buscarPerfil(Integer idPerfil, Authentication autenticacao) {
        ProfileEntity perfil = buscarPerfilNoEstabelecimentoAtual(idPerfil, autenticacao);
        return paraRespostaPerfil(perfil);
    }

    @Transactional
    public RespostaPerfilDto atualizarPerfil(Integer idPerfil, RequisicaoAtualizacaoPerfilDto requisicao,
                                              Authentication autenticacao) {
        if (requisicao.nome() == null && requisicao.descricao() == null) {
            throw new IllegalArgumentException("Informe ao menos um campo para atualização");
        }
        ProfileEntity perfil = buscarPerfilNoEstabelecimentoAtual(idPerfil, autenticacao);
        if (ehPerfilGerente(perfil.getName()) && !UserAuthorizationService.isManagerRole(autenticacao)) {
            throw new AccessDeniedException("Apenas gerente pode alterar o perfil Gerente");
        }
        if (requisicao.nome() != null) {
            String nome = requisicao.nome().trim();
            if (nome.isEmpty()) {
                throw new IllegalArgumentException("O nome do perfil não pode ficar vazio");
            }
            if (ehPerfilGerente(perfil.getName()) && !perfil.getName().equalsIgnoreCase(nome)) {
                throw new AccessConfigurationConflictException("O nome do perfil Gerente não pode ser alterado");
            }
            if (!ehPerfilGerente(perfil.getName()) && ehPerfilGerente(nome)) {
                throw new AccessConfigurationConflictException(
                        "O nome 'Gerente' é reservado ao perfil administrativo do estabelecimento");
            }
            validarNomePerfilDisponivel(perfil.getEstablishment().getId(), nome, idPerfil);
            perfil.setName(nome);
        }
        if (requisicao.descricao() != null) perfil.setDescription(requisicao.descricao());
        ProfileEntity salvo = salvarPerfil(perfil);
        businessEventLogger.success("access.profile.updated", "profile", salvo.getId());
        return paraRespostaPerfil(salvo);
    }

    @Transactional
    public void excluirPerfil(Integer idPerfil, Authentication autenticacao) {
        ProfileEntity perfil = buscarPerfilNoEstabelecimentoAtual(idPerfil, autenticacao);
        if (ehPerfilGerente(perfil.getName())) {
            throw new AccessConfigurationConflictException("O perfil Gerente não pode ser excluído");
        }
        if (!userRepository.findAllByProfile_Id(idPerfil).isEmpty()) {
            throw new AccessConfigurationConflictException(
                    "Perfil não pode ser excluído porque possui usuários vinculados");
        }
        profileRepository.delete(perfil);
        businessEventLogger.success("access.profile.deleted", "profile", idPerfil);
    }

    @Transactional
    public RespostaPerfilDto substituirFuncionalidadesPerfil(Integer idPerfil, Set<String> codigos,
                                                              Authentication autenticacao) {
        ProfileEntity perfil = profileRepository.findById(idPerfil)
                .orElseThrow(() -> new EntityNotFoundException("Perfil não encontrado"));
        garantirMesmoEstabelecimento(perfil.getEstablishment().getId(), autenticacao);
        if (ehPerfilGerente(perfil.getName()) && !UserAuthorizationService.isManagerRole(autenticacao)) {
            throw new AccessDeniedException("Apenas gerente pode alterar as funcionalidades do perfil Gerente");
        }
        Set<String> codigosNormalizados = conjuntoVazioQuandoNulo(codigos);
        garantirAtorPossuiPermissoes(codigosNormalizados, autenticacao);
        perfil.setFunctionalities(carregarFuncionalidadesPorCodigo(codigosNormalizados));
        garantirAoMenosUmUsuarioAtivoPodeCriarUsuarios(perfil.getEstablishment().getId());
        userRepository.findAllByProfile_Id(idPerfil)
                .forEach(usuario -> tokenStateService.revokeAll(usuario.getId()));
        ProfileEntity salvo = profileRepository.save(perfil);
        businessEventLogger.success("access.profile.permissions-replaced", "profile", salvo.getId());
        return paraRespostaPerfil(salvo);
    }

    @Transactional
    public void substituirSobrescritasPermissaoUsuario(UUID idUsuario,
            Set<SobrescritaPermissaoUsuarioDto> sobrescritasSolicitadas, Authentication autenticacao) {
        UserEntity usuario = userRepository.findById(idUsuario)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
        garantirMesmoEstabelecimento(usuario.getEstablishment().getId(), autenticacao);
        if (!UserAuthorizationService.isManagerRole(autenticacao)) {
            UUID idAtor = idUsuarioAtualOpcional(autenticacao);
            if (idAtor != null && idAtor.equals(idUsuario)) {
                throw new AccessDeniedException("Não é permitido alterar as próprias sobrescritas de permissão");
            }
            if (ehPerfilGerente(usuario.getProfile().getName())) {
                throw new AccessDeniedException("Apenas gerente pode alterar permissões de um gerente");
            }
        }
        Set<SobrescritaPermissaoUsuarioDto> sobrescritas = conjuntoVazioQuandoNulo(sobrescritasSolicitadas);
        long codigosDistintos = sobrescritas.stream().map(SobrescritaPermissaoUsuarioDto::codigoFuncionalidade)
                .distinct().count();
        if (codigosDistintos != sobrescritas.size()) {
            throw new IllegalArgumentException(
                    "Cada funcionalidade pode possuir apenas uma sobrescrita por usuário");
        }
        Set<String> codigosConcedidos = sobrescritas.stream()
                .filter(item -> item.efeito() == PermissionEffect.GRANT)
                .map(SobrescritaPermissaoUsuarioDto::codigoFuncionalidade)
                .collect(Collectors.toSet());
        garantirAtorPossuiPermissoes(codigosConcedidos, autenticacao);
        Set<FuncionalidadeEntity> funcionalidades = carregarFuncionalidadesPorCodigo(sobrescritas.stream()
                .map(SobrescritaPermissaoUsuarioDto::codigoFuncionalidade).collect(java.util.stream.Collectors.toSet()));
        java.util.Map<String, FuncionalidadeEntity> porCodigo = funcionalidades.stream()
                .collect(java.util.stream.Collectors.toMap(FuncionalidadeEntity::getCode, item -> item));
        Set<UserFunctionalityOverrideEntity> entidades = new LinkedHashSet<>();
        for (SobrescritaPermissaoUsuarioDto sobrescrita : sobrescritas) {
            entidades.add(UserFunctionalityOverrideEntity.builder().user(usuario)
                    .functionality(porCodigo.get(sobrescrita.codigoFuncionalidade()))
                    .effect(sobrescrita.efeito()).build());
        }
        // A coleção tem orphanRemoval: precisa ser a mesma instância. O flush remove as antigas antes
        // de inserir as novas (o Hibernate insere antes de remover no mesmo flush e violaria a unicidade).
        usuario.getFunctionalityOverrides().clear();
        userRepository.flush();
        usuario.getFunctionalityOverrides().addAll(entidades);
        garantirAoMenosUmUsuarioAtivoPodeCriarUsuarios(usuario.getEstablishment().getId());
        userRepository.save(usuario);
        tokenStateService.revokeAll(idUsuario);
        businessEventLogger.success("access.user-permissions-replaced", "user", idUsuario);
    }

    /** Valida uma alteração de perfil ou status antes de persistir o usuário. */
    @Transactional
    public void garantirAlteracaoUsuarioMantemAdministrador(UserEntity usuarioAlterado) {
        garantirAoMenosUmUsuarioAtivoPodeCriarUsuarios(usuarioAlterado.getEstablishment().getId());
    }

    /** Impede desativar ou excluir o último usuário que pode criar usuários. */
    @Transactional
    public void garantirUsuarioPodeSerDesativadoOuExcluido(UserEntity usuario) {
        if (!podeCriarUsuarios(usuario)) {
            return;
        }
        boolean anotherAdministratorExists = userRepository
                .findAllByEstablishment_Id(usuario.getEstablishment().getId()).stream()
                .anyMatch(candidato -> !candidato.getId().equals(usuario.getId())
                        && podeCriarUsuarios(candidato));
        if (!anotherAdministratorExists) {
            throw new AccessConfigurationConflictException(
                    "Não é possível remover o último usuário ativo com USUARIOS_CRIAR");
        }
    }

    @Transactional
    public RespostaPermissoesUsuarioDto buscarPermissoesUsuario(UUID idUsuario,
                                                               Authentication autenticacao) {
        UserEntity usuario = userRepository.findById(idUsuario)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
        garantirMesmoEstabelecimento(usuario.getEstablishment().getId(), autenticacao);
        Set<String> permissoesPerfil = usuario.getProfile().getFunctionalities().stream()
                .map(FuncionalidadeEntity::getCode)
                .collect(java.util.stream.Collectors.toSet());
        Set<SobrescritaPermissaoUsuarioDto> sobrescritas = usuario.getFunctionalityOverrides().stream()
                .map(item -> new SobrescritaPermissaoUsuarioDto(item.getFunctionality().getCode(),
                        item.getEffect()))
                .collect(java.util.stream.Collectors.toSet());
        return new RespostaPermissoesUsuarioDto(usuario.getProfile().getId(),
                usuario.getProfile().getName(), permissoesPerfil, sobrescritas,
                PermissionResolver.effectiveCodes(usuario));
    }

    private Set<FuncionalidadeEntity> carregarFuncionalidadesPorCodigo(Set<String> codigos) {
        Set<FuncionalidadeEntity> functionalities = new LinkedHashSet<>(
                funcionalidadeRepository.findByCodeIn(codigos));
        if (functionalities.size() != codigos.size()) {
            throw new EntityNotFoundException("Funcionalidade não encontrada");
        }
        return functionalities;
    }

    private RespostaPerfilDto paraRespostaPerfil(ProfileEntity perfil) {
        return new RespostaPerfilDto(perfil.getId(), perfil.getName(), perfil.getDescription(),
                perfil.getFunctionalities().stream().map(FuncionalidadeEntity::getCode)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private ProfileEntity buscarPerfilNoEstabelecimentoAtual(Integer idPerfil,
                                                              Authentication autenticacao) {
        ProfileEntity perfil = profileRepository.findById(idPerfil)
                .orElseThrow(() -> new EntityNotFoundException("Perfil não encontrado"));
        garantirMesmoEstabelecimento(perfil.getEstablishment().getId(), autenticacao);
        return perfil;
    }

    private void garantirAoMenosUmUsuarioAtivoPodeCriarUsuarios(UUID idEstabelecimento) {
        boolean possuiAdministrador = userRepository.findAllByEstablishment_Id(idEstabelecimento).stream()
                .anyMatch(this::podeCriarUsuarios);
        if (!possuiAdministrador) {
            throw new AccessConfigurationConflictException(
                    "O estabelecimento precisa manter ao menos um usuário ativo com USUARIOS_CRIAR");
        }
    }

    private boolean podeCriarUsuarios(UserEntity usuario) {
        return usuario.isActive() && ("GERENTE".equals(CustomUserDetailsService
                .normalizeRole(usuario.getProfile().getName()))
                || PermissionResolver.effectiveCodes(usuario).contains("USUARIOS_CRIAR"));
    }

    private <T> Set<T> conjuntoVazioQuandoNulo(Set<T> valores) {
        return valores == null ? Set.of() : valores;
    }

    private void garantirMesmoEstabelecimento(UUID idEstabelecimento, Authentication autenticacao) {
        UUID idEstabelecimentoAtual = idEstabelecimentoAtual(autenticacao);
        if (!idEstabelecimento.equals(idEstabelecimentoAtual)) {
            throw new AccessDeniedException("Acesso negado");
        }
    }

    private UUID idEstabelecimentoAtual(Authentication autenticacao) {
        if (!(autenticacao instanceof JwtAuthenticationToken tokenJwt)) {
            throw new AccessDeniedException("Sessão inválida");
        }
        try {
            return UUID.fromString(
                    tokenJwt.getToken().getClaimAsString("establishmentId"));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AccessDeniedException("Sessão inválida");
        }
    }

    private void validarNomePerfilDisponivel(UUID idEstabelecimento, String nome, Integer idPerfilAtual) {
        if (idPerfilAtual == null && ehPerfilGerente(nome)) {
            throw new AccessConfigurationConflictException(
                    "O nome 'Gerente' é reservado ao perfil administrativo do estabelecimento");
        }
        profileRepository.findByEstablishment_IdAndNameIgnoreCase(idEstabelecimento, nome)
                .filter(perfil -> !perfil.getId().equals(idPerfilAtual))
                .ifPresent(perfil -> {
                    throw new AccessConfigurationConflictException(
                            "Já existe um perfil com este nome no estabelecimento");
                });
        String nomeNormalizado = CustomUserDetailsService.normalizeRole(nome);
        boolean colisaoNormalizada = profileRepository.findAllByEstablishment_IdOrderByNameAsc(idEstabelecimento)
                .stream()
                .filter(perfil -> !perfil.getId().equals(idPerfilAtual))
                .anyMatch(perfil -> nomeNormalizado.equals(CustomUserDetailsService.normalizeRole(perfil.getName())));
        if (colisaoNormalizada) {
            throw new AccessConfigurationConflictException(
                    "Já existe um perfil com este nome no estabelecimento");
        }
    }

    private boolean ehPerfilGerente(String nome) {
        return nome != null && "GERENTE".equals(CustomUserDetailsService.normalizeRole(nome));
    }

    private UUID idUsuarioAtualOpcional(Authentication autenticacao) {
        if (!(autenticacao instanceof JwtAuthenticationToken tokenJwt)) {
            return null;
        }
        try {
            String rawUserId = tokenJwt.getToken().getClaimAsString("userId");
            return rawUserId == null || rawUserId.isBlank() ? null : UUID.fromString(rawUserId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void garantirAtorPossuiPermissoes(Set<String> codigos, Authentication autenticacao) {
        if (UserAuthorizationService.isManagerRole(autenticacao) || codigos == null || codigos.isEmpty()) {
            return;
        }
        Set<String> autoridadesAtor = autenticacao == null ? Set.of()
                : autenticacao.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .collect(Collectors.toSet());
        if (!autoridadesAtor.containsAll(codigos)) {
            throw new AccessDeniedException("Não é permitido conceder permissões que você não possui");
        }
    }

    private ProfileEntity salvarPerfil(ProfileEntity perfil) {
        try {
            return profileRepository.saveAndFlush(perfil);
        } catch (org.springframework.dao.DataIntegrityViolationException excecao) {
            throw new AccessConfigurationConflictException(
                    "Já existe um perfil com este nome no estabelecimento");
        }
    }
}
