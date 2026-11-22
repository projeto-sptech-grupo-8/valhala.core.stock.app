package valhalla.core.stock.app.shared.exceptionhandler;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import valhalla.core.stock.app.modules.caixa.exception.AutorizacaoCaixaNegadaException;
import valhalla.core.stock.app.modules.caixa.exception.CaixaEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.CaixaJaExisteException;
import valhalla.core.stock.app.modules.caixa.exception.ConfiguracaoCaixaInvalidaException;
import valhalla.core.stock.app.modules.caixa.exception.EstoqueInsuficientePedidoException;
import valhalla.core.stock.app.modules.caixa.exception.IntegracaoMercadoPagoException;
import valhalla.core.stock.app.modules.caixa.exception.NotaFiscalEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.NotaFiscalJaEmitidaException;
import valhalla.core.stock.app.modules.caixa.exception.PagamentoPendenteFechamentoException;
import valhalla.core.stock.app.modules.caixa.exception.PedidoEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.RateLimitExcedidoException;
import valhalla.core.stock.app.modules.caixa.exception.SaldoInsuficienteCaixaException;
import valhalla.core.stock.app.modules.caixa.exception.SessaoCaixaEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.SessaoCaixaJaAbertaException;
import valhalla.core.stock.app.modules.caixa.exception.TaxaPagamentoJaExisteException;
import valhalla.core.stock.app.modules.caixa.exception.TerminalCaixaJaExisteException;
import valhalla.core.stock.app.modules.caixa.exception.TerminalOcupadoPointException;
import valhalla.core.stock.app.modules.caixa.exception.WebhookAssinaturaInvalidaException;
import valhalla.core.stock.app.modules.estoque.exception.CategoriaJaExisteException;
import valhalla.core.stock.app.modules.estoque.exception.CategoriaPossuiProdutosException;
import valhalla.core.stock.app.modules.estoque.exception.ProdutoPossuiMovimentacoesException;
import valhalla.core.stock.app.shared.error.AccessConfigurationConflictException;
import valhalla.core.stock.app.shared.error.EmailAlreadyExistsException;
import valhalla.core.stock.app.shared.error.InvalidRefreshTokenException;
import valhalla.core.stock.app.shared.error.InvalidUserUpdateException;
import valhalla.core.stock.app.shared.error.ProfileNotFoundException;
import valhalla.core.stock.app.shared.error.UserDeletionConflictException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapeiaExcecoesDeSegurancaEUsuario() {
        assertStatus(handler.handleAuthentication(), HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos");
        assertStatus(
                handler.handleInvalidRefreshToken(new InvalidRefreshTokenException("Token inválido")),
                HttpStatus.UNAUTHORIZED,
                "Token inválido"
        );
        assertStatus(
                handler.handleEmailAlreadyExists(new EmailAlreadyExistsException("E-mail duplicado")),
                HttpStatus.CONFLICT,
                "E-mail duplicado"
        );
        assertStatus(
                handler.handleProfileNotFound(new ProfileNotFoundException("Perfil não encontrado")),
                HttpStatus.NOT_FOUND,
                "Perfil não encontrado"
        );
        assertStatus(
                handler.handleEntityNotFound(new EntityNotFoundException("Entidade não encontrada")),
                HttpStatus.NOT_FOUND,
                "Entidade não encontrada"
        );
        assertStatus(
                handler.handleInvalidUserUpdate(new InvalidUserUpdateException("Atualização inválida")),
                HttpStatus.BAD_REQUEST,
                "Atualização inválida"
        );
        assertStatus(
                handler.handleUserDeletionConflict(new UserDeletionConflictException("Usuário possui vínculos", new RuntimeException())),
                HttpStatus.CONFLICT,
                "Usuário possui vínculos"
        );
        assertStatus(
                handler.handleAccessConfigurationConflict(new AccessConfigurationConflictException("Conflito acesso")),
                HttpStatus.CONFLICT,
                "Conflito acesso"
        );
    }

    @Test
    void mapeiaExcecoesDeEstoque() {
        assertStatus(
                handler.handleCategoriaJaExiste(new CategoriaJaExisteException("Categoria já existe")),
                HttpStatus.CONFLICT,
                "Categoria já existe"
        );
        assertStatus(
                handler.handleCategoriaPossuiProdutos(new CategoriaPossuiProdutosException("Categoria em uso", new RuntimeException())),
                HttpStatus.CONFLICT,
                "Categoria em uso"
        );
        assertStatus(
                handler.handleProdutoPossuiMovimentacoes(new ProdutoPossuiMovimentacoesException("Produto com movimentações", new RuntimeException())),
                HttpStatus.CONFLICT,
                "Produto com movimentações"
        );
    }

    @Test
    void mapeiaExcecoesDeCaixaFiscalEPoint() {
        assertStatus(
                handler.handleConfiguracaoCaixaInvalida(new ConfiguracaoCaixaInvalidaException("Config inválida")),
                HttpStatus.BAD_REQUEST,
                "Config inválida"
        );
        assertStatus(
                handler.handleCaixaJaExiste(new CaixaJaExisteException("Caixa já existe")),
                HttpStatus.CONFLICT,
                "Caixa já existe"
        );
        assertStatus(
                handler.handleTerminalCaixaJaExiste(new TerminalCaixaJaExisteException("Terminal já existe")),
                HttpStatus.CONFLICT,
                "Terminal já existe"
        );
        assertStatus(
                handler.handleTaxaPagamentoJaExiste(new TaxaPagamentoJaExisteException("Taxa já existe")),
                HttpStatus.CONFLICT,
                "Taxa já existe"
        );
        assertStatus(
                handler.handleCaixaEstadoInvalido(new CaixaEstadoInvalidoException("Caixa inativo")),
                HttpStatus.CONFLICT,
                "Caixa inativo"
        );
        assertStatus(
                handler.handleSessaoCaixaJaAberta(new SessaoCaixaJaAbertaException("Sessão aberta")),
                HttpStatus.CONFLICT,
                "Sessão aberta"
        );
        assertStatus(
                handler.handleSessaoCaixaEstadoInvalido(new SessaoCaixaEstadoInvalidoException("Sessão fechada")),
                HttpStatus.CONFLICT,
                "Sessão fechada"
        );
        assertStatus(
                handler.handlePedidoEstadoInvalido(new PedidoEstadoInvalidoException("Pedido inválido")),
                HttpStatus.CONFLICT,
                "Pedido inválido"
        );
        assertStatus(
                handler.handleNotaFiscalEstadoInvalido(new NotaFiscalEstadoInvalidoException("NF inválida")),
                HttpStatus.CONFLICT,
                "NF inválida"
        );
        assertStatus(
                handler.handleNotaFiscalJaEmitida(new NotaFiscalJaEmitidaException("NF já emitida")),
                HttpStatus.CONFLICT,
                "NF já emitida"
        );

        ResponseEntity<ApiErrorResponse> estoqueInsuficiente = handler.handleEstoqueInsuficientePedido(
                new EstoqueInsuficientePedidoException("Sem estoque", "Vodka", new BigDecimal("2"))
        );
        assertStatus(estoqueInsuficiente, HttpStatus.CONFLICT, "Sem estoque");
        assertThat(estoqueInsuficiente.getBody().fieldErrors())
                .containsEntry("estoque", "Saldo disponível de Vodka: 2");

        assertStatus(
                handler.handleAutorizacaoCaixaNegada(new AutorizacaoCaixaNegadaException("Autorização negada")),
                HttpStatus.FORBIDDEN,
                "Autorização negada"
        );

        ResponseEntity<ApiErrorResponse> saldoInsuficiente = handler.handleSaldoInsuficienteCaixa(
                new SaldoInsuficienteCaixaException("Saldo insuficiente", new BigDecimal("15.50"))
        );
        assertStatus(saldoInsuficiente, HttpStatus.CONFLICT, "Saldo insuficiente");
        assertThat(saldoInsuficiente.getBody().fieldErrors())
                .containsEntry("valor", "Disponível para sangria: R$ 15.50");

        ResponseEntity<ApiErrorResponse> pagamentoPendente = handler.handlePagamentoPendenteFechamento(
                new PagamentoPendenteFechamentoException("Pagamentos pendentes", Map.of("PENDENTE", 2L))
        );
        assertStatus(pagamentoPendente, HttpStatus.CONFLICT, "Pagamentos pendentes");
        assertThat(pagamentoPendente.getBody().fieldErrors())
                .containsEntry("pagamentosPendentes", "PENDENTE: 2");

        assertStatus(
                handler.handleTerminalOcupadoPoint(new TerminalOcupadoPointException("Terminal ocupado")),
                HttpStatus.CONFLICT,
                "Terminal ocupado"
        );

        assertStatus(
                handler.handleIntegracaoMercadoPago(new IntegracaoMercadoPagoException("Erro MP", HttpStatus.GATEWAY_TIMEOUT, true)),
                HttpStatus.GATEWAY_TIMEOUT,
                "Erro MP"
        );
        assertStatus(
                handler.handleIntegracaoMercadoPago(new IntegracaoMercadoPagoException("Erro MP sem status", null, true)),
                HttpStatus.BAD_GATEWAY,
                "Erro MP sem status"
        );
        assertStatus(
                handler.handleRateLimitExcedido(new RateLimitExcedidoException("Muitas requisições")),
                HttpStatus.TOO_MANY_REQUESTS,
                "Muitas requisições"
        );
        assertStatus(
                handler.handleWebhookAssinaturaInvalida(new WebhookAssinaturaInvalidaException("Assinatura inválida")),
                HttpStatus.UNAUTHORIZED,
                "Assinatura inválida"
        );
    }

    @Test
    void mapeiaExcecoesDeValidacaoEArgumentos() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "req");
        bindingResult.addError(new FieldError("req", "nome", "obrigatório"));
        bindingResult.addError(new FieldError("req", "nome", "segunda mensagem ignorada"));
        MethodArgumentNotValidException notValidEx = mock(MethodArgumentNotValidException.class);
        when(notValidEx.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiErrorResponse> validationResp = handler.handleValidation(notValidEx);
        assertStatus(validationResp, HttpStatus.BAD_REQUEST, "Dados de entrada inválidos");
        assertThat(validationResp.getBody().fieldErrors()).containsExactly(Map.entry("nome", "obrigatório"));

        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("listar.pagina");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("deve ser >= 0");

        ResponseEntity<ApiErrorResponse> constraintResp = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(violation))
        );
        assertStatus(constraintResp, HttpStatus.BAD_REQUEST, "Parâmetros de consulta inválidos");
        assertThat(constraintResp.getBody().fieldErrors()).containsEntry("pagina", "deve ser >= 0");

        MethodArgumentTypeMismatchException mismatch = mock(MethodArgumentTypeMismatchException.class);
        when(mismatch.getName()).thenReturn("status");
        ResponseEntity<ApiErrorResponse> mismatchResp = handler.handleArgumentTypeMismatch(mismatch);
        assertStatus(mismatchResp, HttpStatus.BAD_REQUEST, "Parâmetro de consulta inválido");
        assertThat(mismatchResp.getBody().fieldErrors())
                .containsEntry("status", "Valor inválido para o parâmetro informado");

        assertStatus(
                handler.handleIllegalArgument(new IllegalArgumentException("Argumento inválido")),
                HttpStatus.BAD_REQUEST,
                "Argumento inválido"
        );
    }

    private void assertStatus(
            ResponseEntity<ApiErrorResponse> response,
            HttpStatus expectedStatus,
            String expectedMessage
    ) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(expectedStatus.value());
        assertThat(response.getBody().message()).isEqualTo(expectedMessage);
    }
}
