package valhalla.core.stock.app.modules.accesscontrol.dto;

import java.util.Set;

/** Dados que a tela administrativa usa para explicar e editar os acessos. */
public record RespostaPermissoesUsuarioDto(
        Integer idPerfil,
        String nomePerfil,
        Set<String> permissoesPerfil,
        Set<SobrescritaPermissaoUsuarioDto> sobrescritas,
        Set<String> permissoesEfetivas
) { }
