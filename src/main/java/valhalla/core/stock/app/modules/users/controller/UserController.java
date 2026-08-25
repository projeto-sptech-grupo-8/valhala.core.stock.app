package valhalla.core.stock.app.modules.users.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import valhalla.core.stock.app.modules.users.dto.UserCreateRequestDto;
import valhalla.core.stock.app.modules.users.dto.UserResponseDto;
import valhalla.core.stock.app.modules.users.service.UserService;

import java.net.URI;

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
}
