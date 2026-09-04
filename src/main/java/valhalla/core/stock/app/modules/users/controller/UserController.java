package valhalla.core.stock.app.modules.users.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import valhalla.core.stock.app.modules.users.dto.UserCreateRequestDto;
import valhalla.core.stock.app.modules.users.dto.UserResponseDto;
import valhalla.core.stock.app.modules.users.dto.UserUpdateDto;
import valhalla.core.stock.app.modules.users.service.UserService;

import java.net.URI;
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

    @PatchMapping("/{id}")
    public ResponseEntity<UserResponseDto> atualizarUsuario(
            @PathVariable("id") UUID idUsuario,
            @Valid @RequestBody UserUpdateDto dtoUpdate
    ) {
        UserResponseDto usuarioAtualizado = userService.atualizarUsuario(idUsuario, dtoUpdate);
        return ResponseEntity.ok(usuarioAtualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarUsuarioPorId(
            @PathVariable("id") UUID idUsuario
    ) {
        userService.deletarUsuario(idUsuario);
        return ResponseEntity.noContent().build();
    }
}
