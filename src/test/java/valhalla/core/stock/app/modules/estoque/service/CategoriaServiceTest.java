package valhalla.core.stock.app.modules.estoque.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.exception.CategoriaPossuiProdutosException;
import valhalla.core.stock.app.modules.estoque.repository.CategoriaRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private CategoriaService categoriaService;

    @AfterEach
    void limparContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void impedeExcluirCategoriaComProdutosVinculados() {
        UUID idEstabelecimento = UUID.randomUUID();
        CategoriaEntity categoria = categoria(idEstabelecimento);
        autenticarNoEstabelecimento(idEstabelecimento);
        when(categoriaRepository.findById(categoria.getId())).thenReturn(Optional.of(categoria));
        doThrow(new DataIntegrityViolationException("Chave estrangeira violada"))
                .when(categoriaRepository).flush();

        assertThatThrownBy(() -> categoriaService.excluirCategoria(categoria.getId()))
                .isInstanceOf(CategoriaPossuiProdutosException.class)
                .hasMessage("Categoria não pode ser excluída porque possui produtos vinculados");

        verify(categoriaRepository).delete(categoria);
    }

    private CategoriaEntity categoria(UUID idEstabelecimento) {
        CategoriaEntity categoria = new CategoriaEntity();
        categoria.setId(1);
        categoria.setEstabelecimentoId(idEstabelecimento);
        categoria.setNome("Bebidas");
        categoria.setAtivo(true);
        return categoria;
    }

    private void autenticarNoEstabelecimento(UUID idEstabelecimento) {
        Jwt token = Jwt.withTokenValue("token-de-teste")
                .header("alg", "none")
                .claim("establishmentId", idEstabelecimento.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
    }
}
