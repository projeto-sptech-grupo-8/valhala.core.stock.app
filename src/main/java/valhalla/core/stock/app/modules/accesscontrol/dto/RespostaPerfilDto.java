package valhalla.core.stock.app.modules.accesscontrol.dto;

import java.util.Set;

public record RespostaPerfilDto(Integer id, String nome, String descricao,
                                Set<String> codigosFuncionalidades) { }
