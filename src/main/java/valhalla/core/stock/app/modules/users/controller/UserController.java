package valhalla.core.stock.app.modules.users.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import valhalla.core.stock.app.modules.users.dto.UserCreateRequestDto;
import valhalla.core.stock.app.modules.users.dto.UserResponseDto;
import valhalla.core.stock.app.modules.users.dto.UserUpdateDto;
import valhalla.core.stock.app.modules.users.service.UserService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/usuario")
@Tag(
        name = "Usuários",
        description = "Operações de gerenciamento de usuários."
)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @Operation(
            summary = "Criar usuário",
            description = "Cria um novo usuário. Operação permitida para usuários com perfil GERENTE."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Usuário criado com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados de entrada inválidos."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Usuário sem permissão."
            )
    })
    public ResponseEntity<UserResponseDto> criarUsuario(
            @Valid @RequestBody UserCreateRequestDto dtoRequest
    ) {
        UserResponseDto usuarioCriado = userService.criarUsuario(dtoRequest);
        URI location = URI.create("/usuario/" + usuarioCriado.id());

        return ResponseEntity.created(location).body(usuarioCriado);
    }

    @GetMapping
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(
            summary = "Listar usuários",
            description = "Retorna todos os usuários cadastrados. Requer perfil GERENTE."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de usuários retornada com sucesso."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Usuário sem permissão."
            )
    })
    public ResponseEntity<List<UserResponseDto>> listarUsuarios() {
        return ResponseEntity.ok(userService.listarUsuarios());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(
            summary = "Buscar usuário por ID",
            description = "Busca um usuário específico pelo seu UUID. Requer perfil GERENTE."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuário encontrado."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Usuário sem permissão."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuário não encontrado."
            )
    })
    public ResponseEntity<UserResponseDto> buscarUsuarioPorId(
            @PathVariable("id") UUID idUsuario
    ) {
        return ResponseEntity.ok(userService.buscarUsuario(idUsuario));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(
            summary = "Atualizar usuário por ID",
            description = "Atualiza os dados de um usuário específico. Requer perfil GERENTE."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuário atualizado com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados de entrada inválidos."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Usuário sem permissão."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuário não encontrado."
            )
    })
    public ResponseEntity<UserResponseDto> atualizarUsuario(
            @PathVariable("id") UUID idUsuario,
            @Valid @RequestBody UserUpdateDto dtoUpdate
    ) {
        UserResponseDto usuarioAtualizado =
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
                    description = "Usuário não autenticado."
            )
    })
    public ResponseEntity<UserResponseDto> buscarUsuarioAtual(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UserResponseDto usuario =
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
                    description = "Dados de entrada inválidos."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuário não autenticado."
            )
    })
    public ResponseEntity<UserResponseDto> atualizarUsuarioAtual(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserUpdateDto dtoUpdate
    ) {
        UserResponseDto usuarioAtualizado =
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
                    description = "Usuário não autenticado."
            )
    })
    public ResponseEntity<Void> deletarUsuarioAtual(
            @AuthenticationPrincipal Jwt jwt
    ) {
        userService.deletarUsuario(userIdFrom(jwt));

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(
            summary = "Excluir usuário por ID",
            description = "Exclui um usuário específico. Requer perfil GERENTE."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Usuário excluído com sucesso."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Usuário sem permissão."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuário não encontrado."
            )
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