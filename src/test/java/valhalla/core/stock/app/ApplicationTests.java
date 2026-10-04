package valhalla.core.stock.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void devePublicarTodosOsEndpointsNoOpenApi() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.securitySchemes.accessTokenCookie.in")
						.value("cookie"))
				.andExpect(jsonPath("$.components.securitySchemes.accessTokenCookie.name")
						.value("accessToken"))
				.andExpect(jsonPath("$.paths['/auth/login'].post").exists())
				.andExpect(jsonPath("$.paths['/auth/refresh'].post").exists())
				.andExpect(jsonPath("$.paths['/auth/logout'].post.responses['204']").exists())
				.andExpect(jsonPath("$.paths['/auth/logout'].post.responses['401']").doesNotExist())
				.andExpect(jsonPath("$.paths['/usuario'].post").exists())
				.andExpect(jsonPath("$.paths['/usuario'].get").exists())
				.andExpect(jsonPath("$.paths['/usuario/me'].get").exists())
				.andExpect(jsonPath("$.paths['/usuario/me'].patch").exists())
				.andExpect(jsonPath("$.paths['/usuario/me'].delete").exists())
				.andExpect(jsonPath("$.paths['/usuario/{id}'].get").exists())
				.andExpect(jsonPath("$.paths['/usuario/{id}'].patch").exists())
				.andExpect(jsonPath("$.paths['/usuario/{id}'].delete").exists())
				.andExpect(jsonPath("$.paths['/autorizacoes/funcionalidades'].get").exists())
				.andExpect(jsonPath("$.paths['/autorizacoes/perfis'].get").exists())
				.andExpect(jsonPath("$.paths['/autorizacoes/perfis'].post").exists())
				.andExpect(jsonPath("$.paths['/autorizacoes/perfis/{profileId}'].get").exists())
				.andExpect(jsonPath("$.paths['/autorizacoes/perfis/{profileId}'].patch").exists())
				.andExpect(jsonPath("$.paths['/autorizacoes/perfis/{profileId}'].delete").exists())
				.andExpect(jsonPath("$.paths['/autorizacoes/perfis/{profileId}/funcionalidades/codigos'].put").exists())
				.andExpect(jsonPath("$.paths['/autorizacoes/usuarios/{userId}/sobrescritas-permissao'].put").exists())
				.andExpect(jsonPath("$.paths['/autorizacoes/usuarios/{userId}/permissoes'].get").exists())
				.andExpect(jsonPath("$.paths['/categorias'].get").exists())
				.andExpect(jsonPath("$.paths['/categorias'].post").exists())
				.andExpect(jsonPath("$.paths['/categorias/{id}'].get").exists())
				.andExpect(jsonPath("$.paths['/categorias/{id}'].patch").exists())
				.andExpect(jsonPath("$.paths['/categorias/{id}'].delete").exists())
				.andExpect(jsonPath("$.components.schemas.RespostaUsuarioDto.properties.nome").exists())
				.andExpect(jsonPath("$.components.schemas.RequisicaoCriacaoUsuarioDto.properties.senha").exists())
				.andExpect(jsonPath("$.components.schemas.RespostaPermissoesUsuarioDto.properties.permissoesEfetivas").exists());
	}

}
