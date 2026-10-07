package valhalla.core.stock.app.modules.estoque.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import valhalla.core.stock.app.modules.estoque.dto.IngredienteDrinkRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.IngredienteDrinkResponseDto;
import valhalla.core.stock.app.modules.estoque.dto.ReceitaDrinkRequestDto;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.modules.estoque.repository.ComposicaoDrinkRepository;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceitaDrinkServiceTest {

    @Mock private ProdutoRepository produtoRepository;
    @Mock private ComposicaoDrinkRepository composicaoDrinkRepository;
    @InjectMocks private ReceitaDrinkService receitaDrinkService;

    @AfterEach
    void limparContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void substituiReceitaComIngredienteFracionadoEUnitario() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity drink = produto(estabelecimentoId, ProdutoTipo.DRINK, false, "Vodka Energy");
        ProdutoEntity vodka = produto(estabelecimentoId, ProdutoTipo.PADRAO, true, "Vodka Absolut");
        ProdutoEntity energetico = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Red Bull");
        autenticarNoEstabelecimento(estabelecimentoId);

        when(produtoRepository.findById(drink.getId())).thenReturn(Optional.of(drink));
        when(produtoRepository.findById(vodka.getId())).thenReturn(Optional.of(vodka));
        when(produtoRepository.findById(energetico.getId())).thenReturn(Optional.of(energetico));
        when(composicaoDrinkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<IngredienteDrinkResponseDto> resposta = receitaDrinkService.substituirReceita(
                drink.getId(),
                new ReceitaDrinkRequestDto(List.of(
                        new IngredienteDrinkRequestDto(vodka.getId(), new BigDecimal("50"), UnidadeConsumoDrink.ML),
                        new IngredienteDrinkRequestDto(energetico.getId(), BigDecimal.ONE, UnidadeConsumoDrink.UN)
                ))
        );

        ArgumentCaptor<List<ComposicaoDrinkEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(composicaoDrinkRepository).deleteAllByDrinkId(drink.getId());
        verify(composicaoDrinkRepository).flush();
        verify(composicaoDrinkRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).extracting(ComposicaoDrinkEntity::getUnidadeConsumo)
                .containsExactly(UnidadeConsumoDrink.ML, UnidadeConsumoDrink.UN);
        assertThat(resposta).extracting(IngredienteDrinkResponseDto::produtoNome)
                .containsExactly("Vodka Absolut", "Red Bull");
    }

    @Test
    void rejeitaIngredienteComUnidadeIncompativelAntesDeAlterarReceita() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity drink = produto(estabelecimentoId, ProdutoTipo.DRINK, false, "Vodka Energy");
        ProdutoEntity vodka = produto(estabelecimentoId, ProdutoTipo.PADRAO, true, "Vodka Absolut");
        autenticarNoEstabelecimento(estabelecimentoId);
        when(produtoRepository.findById(drink.getId())).thenReturn(Optional.of(drink));
        when(produtoRepository.findById(vodka.getId())).thenReturn(Optional.of(vodka));

        assertThatThrownBy(() -> receitaDrinkService.substituirReceita(
                drink.getId(),
                new ReceitaDrinkRequestDto(List.of(
                        new IngredienteDrinkRequestDto(vodka.getId(), BigDecimal.ONE, UnidadeConsumoDrink.UN)
                ))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ingrediente Vodka Absolut deve usar a unidade ML");

        verify(composicaoDrinkRepository, never()).deleteAllByDrinkId(any());
    }

    private ProdutoEntity produto(
            UUID estabelecimentoId,
            ProdutoTipo tipo,
            boolean fracionado,
            String nome
    ) {
        ProdutoEntity produto = new ProdutoEntity();
        produto.setId(UUID.randomUUID());
        produto.setEstabelecimentoId(estabelecimentoId);
        produto.setTipo(tipo);
        produto.setFracionado(fracionado);
        produto.setAtivo(true);
        produto.setNome(nome);
        return produto;
    }

    private void autenticarNoEstabelecimento(UUID idEstabelecimento) {
        Jwt token = Jwt.withTokenValue("token-de-teste")
                .header("alg", "none")
                .claim("establishmentId", idEstabelecimento.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
    }
}
