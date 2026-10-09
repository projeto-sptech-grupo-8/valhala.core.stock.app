package valhalla.core.stock.app.shared.exceptionhandler;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import valhalla.core.stock.app.shared.error.EmailAlreadyExistsException;
import valhalla.core.stock.app.shared.error.InvalidRefreshTokenException;
import valhalla.core.stock.app.shared.error.InvalidUserUpdateException;
import valhalla.core.stock.app.shared.error.ProfileNotFoundException;
import valhalla.core.stock.app.shared.error.UserDeletionConflictException;
import valhalla.core.stock.app.shared.logging.RequestFailureContext;
import valhalla.core.stock.app.shared.error.AccessConfigurationConflictException;
import valhalla.core.stock.app.modules.estoque.exception.CategoriaJaExisteException;
import valhalla.core.stock.app.modules.estoque.exception.CategoriaPossuiProdutosException;
import valhalla.core.stock.app.modules.estoque.exception.ProdutoPossuiMovimentacoesException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication() {
        LOGGER.atWarn().addKeyValue("event", "security.authentication.failed")
                .log("Authentication failed");
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "E-mail ou senha inválidos",
                Map.of()
        );
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRefreshToken(
            InvalidRefreshTokenException exception
    ) {
        LOGGER.atWarn().addKeyValue("event", "security.refresh-token.rejected")
                .log("Refresh token rejected");
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception
    ) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProfileNotFound(
            ProfileNotFoundException exception
    ) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(
            EntityNotFoundException exception
    ) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(InvalidUserUpdateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidUserUpdate(
            InvalidUserUpdateException exception
    ) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(UserDeletionConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleUserDeletionConflict(
            UserDeletionConflictException exception
    ) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(AccessConfigurationConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessConfigurationConflict(
            AccessConfigurationConflictException exception) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CategoriaJaExisteException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoriaJaExiste(
            CategoriaJaExisteException exception
    ) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CategoriaPossuiProdutosException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoriaPossuiProdutos(
            CategoriaPossuiProdutosException exception
    ) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ProdutoPossuiMovimentacoesException.class)
    public ResponseEntity<ApiErrorResponse> handleProdutoPossuiMovimentacoes(
            ProdutoPossuiMovimentacoesException exception
    ) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Dados de entrada inválidos",
                fieldErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String campo = violation.getPropertyPath().toString();
            campo = campo.substring(campo.lastIndexOf('.') + 1);
            fieldErrors.putIfAbsent(campo, violation.getMessage());
        });
        return buildResponse(HttpStatus.BAD_REQUEST, "Parâmetros de consulta inválidos", fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Parâmetro de consulta inválido",
                Map.of(exception.getName(), "Valor inválido para o parâmetro informado")
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            Map<String, String> fieldErrors
    ) {
        RequestFailureContext.markFailure("HTTP_" + status.value(), message);
        ApiErrorResponse response = new ApiErrorResponse(
                LocalDateTime.now(),
                status.value(),
                message,
                fieldErrors
        );

        return ResponseEntity.status(status).body(response);
    }
}
