package valhalla.core.stock.app.modules.users.controller;

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
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponseDto> criarUsuario(
            @Valid @RequestBody UserCreateRequestDto dtoRequest
    ) {
        UserResponseDto usuarioCriado = userService.criarUsuario(dtoRequest);
        URI location = URI.create("/usuario/" + usuarioCriado.id());

        return ResponseEntity.created(location).body(usuarioCriado);
    }

    @GetMapping
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<List<UserResponseDto>> listarUsuarios() {
        return ResponseEntity.ok(userService.listarUsuarios());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<UserResponseDto> buscarUsuarioPorId(
            @PathVariable("id") UUID idUsuario
    ) {
        return ResponseEntity.ok(userService.buscarUsuario(idUsuario));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<UserResponseDto> atualizarUsuario(
            @PathVariable("id") UUID idUsuario,
            @Valid @RequestBody UserUpdateDto dtoUpdate
    ) {
        UserResponseDto usuarioAtualizado = userService.atualizarUsuario(idUsuario, dtoUpdate);
        return ResponseEntity.ok(usuarioAtualizado);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> buscarUsuarioAtual(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UserResponseDto usuario = userService.buscarUsuario(userIdFrom(jwt));
        return ResponseEntity.ok(usuario);
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponseDto> atualizarUsuarioAtual(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserUpdateDto dtoUpdate
    ) {
        UserResponseDto usuarioAtualizado = userService.atualizarUsuario(
                userIdFrom(jwt),
                dtoUpdate
        );
        return ResponseEntity.ok(usuarioAtualizado);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deletarUsuarioAtual(
            @AuthenticationPrincipal Jwt jwt
    ) {
        userService.deletarUsuario(userIdFrom(jwt));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<Void> deletarUsuarioPorId(
            @PathVariable("id") UUID idUsuario
    ) {
        userService.deletarUsuario(idUsuario);
        return ResponseEntity.noContent().build();
    }

    private UUID userIdFrom(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("userId"));
    }
}
