package valhalla.core.stock.app.modules.establishments.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record EstabelecimentoAtualizacaoRequestDto(

        @JsonAlias({"nomeFantasia", "tradeName", "name"})
        @Size(max = 150, message = "O nome da adega deve ter no máximo 150 caracteres")
        @Schema(description = "Nome da adega", example = "Adega Meraki")
        String nome,

        @Size(max = 18, message = "O CNPJ deve ter no máximo 18 caracteres")
        @Schema(description = "CNPJ do estabelecimento (com ou sem pontuação)", example = "12.345.678/0001-99")
        String cnpj,

        @JsonAlias({"email", "contactEmail"})
        @Email(message = "E-mail de contato inválido")
        @Size(max = 255, message = "O e-mail de contato deve ter no máximo 255 caracteres")
        @Schema(description = "E-mail de contato do estabelecimento", example = "contato@meraki.com.br")
        String emailContato,

        @JsonAlias("phone")
        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres")
        @Schema(description = "Telefone de contato do estabelecimento", example = "(11) 4000-0000")
        String telefone,

        @JsonAlias({"logradouro", "address"})
        @Size(max = 150, message = "O endereço deve ter no máximo 150 caracteres")
        @Schema(description = "Endereço do estabelecimento", example = "R. das Bebidas, 420 · São Paulo")
        String endereco

) {
}
