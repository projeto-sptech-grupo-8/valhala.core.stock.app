package valhalla.core.stock.app.modules.users.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import valhalla.core.stock.app.modules.users.dto.RequisicaoAtualizacaoUsuarioDto;
import valhalla.core.stock.app.modules.users.dto.RequisicaoCriacaoUsuarioDto;
import valhalla.core.stock.app.modules.users.dto.RespostaUsuarioDto;
import valhalla.core.stock.app.modules.users.service.UserService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/usuario")
@Tag(
        name = "Usuários",
        description = "Operações de gerenciamento de usuários."
)
@SecurityRequirement(name = "accessTokenCookie")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @Operation(
            summary = "Criar usuário",
            description = "Cria usuário no estabelecimento da sessão. Requer USUARIOS_CRIAR."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Usuário criado com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Usuário sem permissão.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "E-mail já cadastrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaUsuarioDto> criarUsuario(
            @Valid @RequestBody RequisicaoCriacaoUsuarioDto dtoRequest
    ) {
        RespostaUsuarioDto usuarioCriado = userService.criarUsuario(dtoRequest);
        URI location = URI.create("/usuario/" + usuarioCriado.id());

        return ResponseEntity.created(location).body(usuarioCriado);
    }

    @GetMapping
    @PreAuthorize("@permissionAuthorizationService.hasPermission('USUARIOS_VISUALIZAR', authentication)")
    @Operation(
            summary = "Listar usuários",
            description = "Retorna usuários do estabelecimento da sessão. Requer USUARIOS_VISUALIZAR."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de usuários retornada com sucesso."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Usuário sem permissão.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<RespostaUsuarioDto>> listarUsuarios() {
        return ResponseEntity.ok(userService.listarUsuarios());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@userAuthorizationService.canView(#idUsuario, authentication)")
    @Operation(
            summary = "Buscar usuário por ID",
            description = "Busca o próprio usuário ou outro usuário quando houver USUARIOS_VISUALIZAR."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuário encontrado."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Usuário sem permissão.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuário não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaUsuarioDto> buscarUsuarioPorId(
            @PathVariable("id") UUID idUsuario
    ) {
        return ResponseEntity.ok(userService.buscarUsuario(idUsuario));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@userAuthorizationService.canUpdate(#idUsuario, authentication)")
    @Operation(
            summary = "Atualizar usuário por ID",
            description = "Atualiza o próprio usuário ou outro usuário com USUARIOS_EDITAR."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuário atualizado com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Usuário sem permissão.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuário não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "E-mail duplicado ou último administrador removido.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaUsuarioDto> atualizarUsuario(
            @PathVariable("id") UUID idUsuario,
            @Valid @RequestBody RequisicaoAtualizacaoUsuarioDto dtoUpdate
    ) {
        RespostaUsuarioDto usuarioAtualizado =
                userService.atualizarUsuario(idUsuario, dtoUpdate);

        return ResponseEntity.ok(usuarioAtualizado);
    }

    @GetMapping("/me")
    @Operation(
            summary = "Buscar usuário autenticado",
            description = "Retorna os dados do usuário atualmente autenticado."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Dados do usuário retornados com sucesso."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuário não autenticado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<RespostaUsuarioDto> buscarUsuarioAtual(
            @AuthenticationPrincipal Jwt jwt
    ) {
        RespostaUsuarioDto usuario =
                userService.buscarUsuario(userIdFrom(jwt));

        return ResponseEntity.ok(usuario);
    }

    @PatchMapping("/me")
    @Operation(
            summary = "Atualizar usuário autenticado",
            description = "Atualiza os dados do próprio usuário autenticado."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuário atualizado com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuário não autenticado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(responseCode = "403", description = "Não é permitido alterar perfil ou status.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "E-mail duplicado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaUsuarioDto> atualizarUsuarioAtual(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RequisicaoAtualizacaoUsuarioDto dtoUpdate
    ) {
        RespostaUsuarioDto usuarioAtualizado =
                userService.atualizarUsuario(
                        userIdFrom(jwt),
                        dtoUpdate
                );

        return ResponseEntity.ok(usuarioAtualizado);
    }

    @DeleteMapping("/me")
    @Operation(
            summary = "Excluir usuário autenticado",
            description = "Exclui a conta do próprio usuário autenticado."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Usuário excluído com sucesso."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuário não autenticado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(responseCode = "409", description = "Usuário possui vínculos ou é o último administrador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> deletarUsuarioAtual(
            @AuthenticationPrincipal Jwt jwt
    ) {
        userService.deletarUsuario(userIdFrom(jwt));

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@userAuthorizationService.canDelete(#idUsuario, authentication)")
    @Operation(
            summary = "Excluir usuário por ID",
            description = "Exclui o próprio usuário ou outro usuário com USUARIOS_EXCLUIR."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Usuário excluído com sucesso."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Usuário sem permissão.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuário não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Usuário possui vínculos ou é o último administrador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> deletarUsuarioPorId(
            @PathVariable("id") UUID idUsuario
    ) {
        userService.deletarUsuario(idUsuario);

        return ResponseEntity.noContent().build();
    }

    private UUID userIdFrom(Jwt jwt) {
        return UUID.fromString(
                jwt.getClaimAsString("userId")
        );
    }
}
