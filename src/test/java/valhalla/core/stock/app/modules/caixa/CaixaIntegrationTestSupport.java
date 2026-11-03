package valhalla.core.stock.app.modules.caixa;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.FuncionalidadeRepository;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.caixa.entity.AuditoriaCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.repository.AuditoriaCaixaRepository;
import valhalla.core.stock.app.modules.caixa.repository.ConfiguracaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.repository.MovimentacaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.repository.SessaoCaixaRepository;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class CaixaIntegrationTestSupport {

    protected static final String SENHA = "senha-segura";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected EntityManager entityManager;
    @Autowired protected ProfileRepository profileRepository;
    @Autowired protected FuncionalidadeRepository funcionalidadeRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected ConfiguracaoCaixaRepository configuracaoCaixaRepository;
    @Autowired protected SessaoCaixaRepository sessaoCaixaRepository;
    @Autowired protected MovimentacaoCaixaRepository movimentacaoCaixaRepository;
    @Autowired protected PagamentoPedidoRepository pagamentoPedidoRepository;
    @Autowired protected AuditoriaCaixaRepository auditoriaCaixaRepository;

    protected EstablishmentEntity estabelecimento;
    private final Map<String, String> tokensCsrf = new HashMap<>();

    protected record UsuarioAutenticado(Cookie accessToken, UUID usuarioId) {
    }

    @BeforeEach
    void prepararEstabelecimento() {
        estabelecimento = criarEstabelecimento("Meraki LTDA", "Meraki", "12345678000199");
    }

    protected EstablishmentEntity criarEstabelecimento(
            String razaoSocial,
            String nomeFantasia,
            String cnpj
    ) {
        return entityManager.merge(EstablishmentEntity.builder()
                .corporateName(razaoSocial)
                .tradeName(nomeFantasia)
                .cnpj(cnpj)
                .build());
    }

    protected UsuarioAutenticado autenticarGerente(String email) throws Exception {
        return autenticar(email, "Gerente", Set.of());
    }

    protected UsuarioAutenticado autenticarComPermissoes(String email, String... codigos)
            throws Exception {
        Set<FuncionalidadeEntity> funcionalidades = Arrays.stream(codigos)
                .map(this::funcionalidade)
                .collect(Collectors.toSet());
        return autenticar(email, "Operador", funcionalidades);
    }

    protected UsuarioAutenticado autenticarGerenteEm(
            EstablishmentEntity establishment,
            String email
    ) throws Exception {
        return autenticar(establishment, email, "Gerente", Set.of());
    }

    protected UsuarioAutenticado autenticarComPermissoesEm(
            EstablishmentEntity establishment,
            String email,
            String... codigos
    ) throws Exception {
        Set<FuncionalidadeEntity> funcionalidades = Arrays.stream(codigos)
                .map(this::funcionalidade)
                .collect(Collectors.toSet());
        return autenticar(establishment, email, "Operador", funcionalidades);
    }

    private UsuarioAutenticado autenticar(
            String email,
            String nomePerfil,
            Set<FuncionalidadeEntity> funcionalidades
    ) throws Exception {
        return autenticar(estabelecimento, email, nomePerfil, funcionalidades);
    }

    private UsuarioAutenticado autenticar(
            EstablishmentEntity establishment,
            String email,
            String nomePerfil,
            Set<FuncionalidadeEntity> funcionalidades
    ) throws Exception {
        ProfileEntity perfil = profileRepository.save(ProfileEntity.builder()
                .name(nomePerfil)
                .establishment(establishment)
                .functionalities(funcionalidades)
                .build());

        UserEntity usuario = userRepository.saveAndFlush(UserEntity.builder()
                .name(nomePerfil + " " + email.substring(0, email.indexOf('@')))
                .email(email)
                .passwordHash(passwordEncoder.encode(SENHA))
                .profile(perfil)
                .establishment(establishment)
                .status(UserStatus.ATIVO)
                .build());

        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie accessToken = login.getResponse().getCookie("accessToken");
        MvcResult csrf = mockMvc.perform(get("/auth/csrf").cookie(accessToken))
                .andExpect(status().isOk())
                .andReturn();
        tokensCsrf.put(
                accessToken.getValue(),
                JsonPath.read(csrf.getResponse().getContentAsString(), "$.token")
        );

        return new UsuarioAutenticado(accessToken, usuario.getId());
    }

    private FuncionalidadeEntity funcionalidade(String codigo) {
        List<FuncionalidadeEntity> existentes =
                funcionalidadeRepository.findByCodeIn(Set.of(codigo));
        if (!existentes.isEmpty()) {
            return existentes.getFirst();
        }
        return funcionalidadeRepository.saveAndFlush(FuncionalidadeEntity.builder()
                .code(codigo)
                .name(codigo)
                .description("Permissão de teste")
                .build());
    }

    protected RequestPostProcessor csrf(UsuarioAutenticado usuario) {
        return request -> {
            request.addHeader("X-XSRF-TOKEN", tokensCsrf.get(usuario.accessToken().getValue()));
            return request;
        };
    }

    protected ConfiguracaoCaixaEntity configuracao(EstablishmentEntity establishment, String terminalId) {
        ConfiguracaoCaixaEntity configuracao = new ConfiguracaoCaixaEntity();
        configuracao.setEstabelecimentoId(establishment.getId());
        configuracao.setTerminalId(terminalId);
        configuracao.setTerminalNome(terminalId == null ? null : "Caixa principal");
        configuracao.setPointHabilitado(terminalId != null);
        return configuracaoCaixaRepository.saveAndFlush(configuracao);
    }

    protected SessaoCaixaEntity sessaoAberta(
            EstablishmentEntity establishment,
            UUID operadorUsuarioId,
            String terminalId,
            String fundoTroco
    ) {
        SessaoCaixaEntity sessao = new SessaoCaixaEntity();
        sessao.setEstabelecimentoId(establishment.getId());
        sessao.setTerminalId(terminalId);
        sessao.setOperadorUsuarioId(operadorUsuarioId);
        sessao.setFundoTroco(new java.math.BigDecimal(fundoTroco));
        return sessaoCaixaRepository.saveAndFlush(sessao);
    }

    protected PagamentoPedidoEntity pagamento(
            UUID sessaoCaixaId,
            String forma,
            String status,
            String valor
    ) {
        PagamentoPedidoEntity pagamento = new PagamentoPedidoEntity();
        pagamento.setId(UUID.randomUUID());
        pagamento.setPedidoId(UUID.randomUUID());
        pagamento.setSessaoCaixaId(sessaoCaixaId);
        pagamento.setForma(valhalla.core.stock.app.modules.caixa.entity.FormaPagamentoCaixa
                .valueOf(forma));
        pagamento.setStatus(valhalla.core.stock.app.modules.caixa.entity.StatusPagamentoPedido
                .valueOf(status));
        pagamento.setValor(new java.math.BigDecimal(valor));
        return pagamentoPedidoRepository.saveAndFlush(pagamento);
    }

    protected List<AuditoriaCaixaEntity> auditorias() {
        return auditoriaCaixaRepository.findAll();
    }
}
