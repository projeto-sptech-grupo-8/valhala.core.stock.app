package valhalla.core.stock.app.modules.caixa.integration;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.FuncionalidadeRepository;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.CanalPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusDescontoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.entity.AuditoriaCaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.CaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.sessao.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.TerminalCaixaEntity;
import valhalla.core.stock.app.modules.caixa.sessao.repository.AuditoriaCaixaRepository;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.CaixaRepository;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.ConfiguracaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ItemPedidoRepository;
import valhalla.core.stock.app.modules.caixa.sessao.repository.MovimentacaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.SequenciaDocumentoRepository;
import valhalla.core.stock.app.modules.caixa.sessao.repository.SessaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.TaxaPagamentoRepository;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.TerminalCaixaRepository;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.establishments.repository.EstablishmentRepository;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.modules.estoque.repository.CategoriaRepository;
import valhalla.core.stock.app.modules.estoque.repository.ComposicaoDrinkRepository;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.LoteEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoLoteRepository;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;
import valhalla.core.stock.app.support.EmbeddedPostgresTestDatabase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class CaixaIntegrationTestSupport {

    protected static final String SENHA = "senha-segura";
    private static final AtomicLong SEQUENCIA_PEDIDO_TESTE = new AtomicLong(900_000L);

    @Autowired protected MockMvc mockMvc;
    @Autowired protected EntityManager entityManager;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected EstablishmentRepository establishmentRepository;
    @Autowired protected ProfileRepository profileRepository;
    @Autowired protected FuncionalidadeRepository funcionalidadeRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected ConfiguracaoCaixaRepository configuracaoCaixaRepository;
    @Autowired protected CaixaRepository caixaRepository;
    @Autowired protected TerminalCaixaRepository terminalCaixaRepository;
    @Autowired protected TaxaPagamentoRepository taxaPagamentoRepository;
    @Autowired protected SessaoCaixaRepository sessaoCaixaRepository;
    @Autowired protected MovimentacaoCaixaRepository movimentacaoCaixaRepository;
    @Autowired protected PedidoRepository pedidoRepository;
    @Autowired protected ItemPedidoRepository itemPedidoRepository;
    @Autowired protected SequenciaDocumentoRepository sequenciaDocumentoRepository;
    @Autowired protected PagamentoPedidoRepository pagamentoPedidoRepository;
    @Autowired protected AuditoriaCaixaRepository auditoriaCaixaRepository;
    @Autowired protected CategoriaRepository categoriaRepository;
    @Autowired protected ProdutoRepository produtoRepository;
    @Autowired protected EstoqueRepository estoqueRepository;
    @Autowired protected ComposicaoDrinkRepository composicaoDrinkRepository;
    @Autowired protected MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    @Autowired protected LoteEstoqueRepository loteEstoqueRepository;
    @Autowired protected MovimentacaoLoteRepository movimentacaoLoteRepository;

    protected EstablishmentEntity estabelecimento;
    private final Map<String, String> tokensCsrf = new HashMap<>();

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry registry) {
        EmbeddedPostgresTestDatabase.registrarPropriedades(registry);
    }

    protected record UsuarioAutenticado(Cookie accessToken, UUID usuarioId) {
    }

    protected boolean limpaBancoPorTruncate() {
        return false;
    }

    @BeforeEach
    void prepararEstabelecimento() {
        if (limpaBancoPorTruncate()) {
            EmbeddedPostgresTestDatabase.limparTabelasOperacionais(jdbcTemplate);
        }
        estabelecimento = criarEstabelecimento("Meraki LTDA", "Meraki", "12345678000199");
    }

    @AfterEach
    void limparBancoAposTeste() {
        if (limpaBancoPorTruncate()) {
            EmbeddedPostgresTestDatabase.limparTabelasOperacionais(jdbcTemplate);
        }
    }

    protected EstablishmentEntity criarEstabelecimento(
            String razaoSocial,
            String nomeFantasia,
            String cnpj
    ) {
        return establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .corporateName(razaoSocial)
                .tradeName(nomeFantasia)
                .cnpj(cnpj)
                .active(true)
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
        ProfileEntity perfil = "Gerente".equalsIgnoreCase(nomePerfil)
                ? profileRepository.findByEstablishment_IdAndNameIgnoreCase(establishment.getId(), "Gerente")
                        .orElseGet(() -> profileRepository.saveAndFlush(ProfileEntity.builder()
                                .name("Gerente")
                                .establishment(establishment)
                                .functionalities(funcionalidades)
                                .build()))
                : profileRepository.saveAndFlush(ProfileEntity.builder()
                        .name(nomePerfil + " " + UUID.randomUUID().toString().substring(0, 6))
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

    protected ConfiguracaoCaixaEntity configuracao(EstablishmentEntity establishment) {
        ConfiguracaoCaixaEntity configuracao = new ConfiguracaoCaixaEntity();
        configuracao.setEstabelecimentoId(establishment.getId());
        configuracao.setPointHabilitado(false);
        return configuracaoCaixaRepository.saveAndFlush(configuracao);
    }

    protected CaixaEntity caixa(EstablishmentEntity establishment, String nome) {
        CaixaEntity caixa = new CaixaEntity();
        caixa.setEstabelecimentoId(establishment.getId());
        caixa.setNome(nome);
        caixa.setAtivo(true);
        return caixaRepository.saveAndFlush(caixa);
    }

    protected TerminalCaixaEntity terminal(CaixaEntity caixa, String terminalId, String nome) {
        TerminalCaixaEntity terminal = new TerminalCaixaEntity();
        terminal.setCaixa(caixa);
        terminal.setTerminalId(terminalId);
        terminal.setNome(nome);
        terminal.setAtivo(true);
        return terminalCaixaRepository.saveAndFlush(terminal);
    }

    protected SessaoCaixaEntity sessaoAberta(
            EstablishmentEntity establishment,
            UUID operadorUsuarioId,
            CaixaEntity caixa,
            String fundoTroco
    ) {
        SessaoCaixaEntity sessao = new SessaoCaixaEntity();
        sessao.setEstabelecimentoId(establishment.getId());
        sessao.setCaixa(caixa);
        sessao.setOperadorUsuarioId(operadorUsuarioId);
        sessao.setFundoTroco(new BigDecimal(fundoTroco));
        return sessaoCaixaRepository.saveAndFlush(sessao);
    }

    protected PagamentoPedidoEntity pagamento(
            UUID sessaoCaixaId,
            String forma,
            String status,
            String valor
    ) {
        SessaoCaixaEntity sessao = sessaoCaixaRepository.findById(sessaoCaixaId).orElseThrow();
        BigDecimal valorDecimal = new BigDecimal(valor);

        PedidoEntity pedido = new PedidoEntity();
        pedido.setEstabelecimentoId(sessao.getEstabelecimentoId());
        pedido.setOperadorUsuarioId(sessao.getOperadorUsuarioId());
        pedido.setNumero(SEQUENCIA_PEDIDO_TESTE.getAndIncrement());
        pedido.setTipo("VENDA_PDV");
        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_PAGAMENTO);
        pedido.setCanal(CanalPedidoCaixa.BALCAO);
        pedido.setSessaoCaixaId(sessaoCaixaId);
        pedido.setSubtotal(valorDecimal);
        pedido.setDesconto(BigDecimal.ZERO);
        pedido.setTotal(valorDecimal);
        pedido.setStatusDesconto(StatusDescontoPedido.NENHUM);
        PedidoEntity pedidoSalvo = pedidoRepository.saveAndFlush(pedido);

        FormaPagamentoCaixa formaEnum = FormaPagamentoCaixa.valueOf(forma);
        StatusPagamentoPedido statusEnum = StatusPagamentoPedido.valueOf(status);

        PagamentoPedidoEntity pagamento = new PagamentoPedidoEntity();
        pagamento.setId(UUID.randomUUID());
        pagamento.setPedidoId(pedidoSalvo.getId());
        pagamento.setSessaoCaixaId(sessaoCaixaId);
        pagamento.setForma(formaEnum);
        pagamento.setStatus(statusEnum);
        pagamento.setProvedor(formaEnum == FormaPagamentoCaixa.DINHEIRO ? "INTERNO" : "MERCADO_PAGO");
        pagamento.setValor(valorDecimal);
        pagamento.setValorTroco(BigDecimal.ZERO);
        pagamento.setValorEstornado(BigDecimal.ZERO);
        pagamento.setParcelas(1);
        if (statusEnum == StatusPagamentoPedido.APROVADO) {
            pagamento.setPagoEm(FusoHorarioOperacao.agora());
            if (formaEnum == FormaPagamentoCaixa.DINHEIRO) {
                pagamento.setValorRecebido(valorDecimal);
            }
        } else if (statusEnum == StatusPagamentoPedido.CANCELADO) {
            pagamento.setCanceladoEm(FusoHorarioOperacao.agora());
        } else if (statusEnum == StatusPagamentoPedido.ESTORNADO) {
            pagamento.setEstornadoEm(FusoHorarioOperacao.agora());
            pagamento.setValorEstornado(valorDecimal);
        }
        return pagamentoPedidoRepository.saveAndFlush(pagamento);
    }

    protected CategoriaEntity categoria(String nome) {
        CategoriaEntity categoria = new CategoriaEntity();
        categoria.setEstabelecimentoId(estabelecimento.getId());
        categoria.setNome(nome);
        categoria.setAtivo(true);
        return categoriaRepository.saveAndFlush(categoria);
    }

    protected ProdutoEntity produtoPadrao(
            String nome,
            CategoriaEntity categoria,
            boolean fracionado,
            String precoCusto,
            String precoVenda,
            String volumeMl,
            String saldoInicial
    ) {
        ProdutoEntity produto = new ProdutoEntity();
        produto.setEstabelecimentoId(estabelecimento.getId());
        produto.setCategoria(categoria);
        produto.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        produto.setNome(nome);
        produto.setTipo(ProdutoTipo.PADRAO);
        produto.setUnidadeMedida(fracionado ? "ml" : "un");
        produto.setPrecoCusto(new BigDecimal(precoCusto));
        produto.setPrecoVenda(new BigDecimal(precoVenda));
        produto.setFracionado(fracionado);
        produto.setVolumeEmbalagemMl(fracionado && volumeMl != null ? new BigDecimal(volumeMl) : null);
        produto.setAtivo(true);
        produto = produtoRepository.saveAndFlush(produto);

        EstoqueEntity estoque = new EstoqueEntity();
        estoque.setProduto(produto);
        estoque.setQuantidadeAtual(new BigDecimal(saldoInicial));
        estoque.setQuantidadeReservada(BigDecimal.ZERO);
        estoqueRepository.saveAndFlush(estoque);

        return produto;
    }

    protected ProdutoEntity drinkComReceita(
            String nome,
            CategoriaEntity categoria,
            String precoVenda,
            ProdutoEntity ingrediente,
            String quantidadePorDrink,
            UnidadeConsumoDrink unidadeConsumo
    ) {
        ProdutoEntity drink = new ProdutoEntity();
        drink.setEstabelecimentoId(estabelecimento.getId());
        drink.setCategoria(categoria);
        drink.setSku("DRK-" + UUID.randomUUID().toString().substring(0, 8));
        drink.setNome(nome);
        drink.setTipo(ProdutoTipo.DRINK);
        drink.setUnidadeMedida("un");
        drink.setPrecoCusto(BigDecimal.ZERO);
        drink.setPrecoVenda(new BigDecimal(precoVenda));
        drink.setFracionado(false);
        drink.setAtivo(true);
        drink = produtoRepository.saveAndFlush(drink);

        ComposicaoDrinkEntity comp = new ComposicaoDrinkEntity();
        comp.setDrink(drink);
        comp.setProduto(ingrediente);
        comp.setQuantidade(new BigDecimal(quantidadePorDrink));
        comp.setUnidadeConsumo(unidadeConsumo);
        composicaoDrinkRepository.saveAndFlush(comp);

        return drink;
    }

    protected LoteEstoqueEntity lote(
            ProdutoEntity produto,
            String codigo,
            LocalDate validade,
            String quantidade
    ) {
        EstoqueEntity estoque = estoqueRepository.findByProdutoId(produto.getId()).orElseThrow();
        BigDecimal qtd = new BigDecimal(quantidade);
        LoteEstoqueEntity lote = new LoteEstoqueEntity();
        lote.setEstoque(estoque);
        lote.setCodigo(codigo);
        lote.setValidade(validade);
        lote.setQuantidadeAtual(qtd);
        lote = loteEstoqueRepository.saveAndFlush(lote);

        estoque.setQuantidadeAtual(estoque.getQuantidadeAtual().add(qtd));
        estoqueRepository.saveAndFlush(estoque);
        return lote;
    }

    protected List<AuditoriaCaixaEntity> auditorias() {
        return auditoriaCaixaRepository.findAll();
    }
}
