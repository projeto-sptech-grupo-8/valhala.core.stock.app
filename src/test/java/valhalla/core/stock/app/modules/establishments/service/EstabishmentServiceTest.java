package valhalla.core.stock.app.modules.establishments.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import valhalla.core.stock.app.modules.establishments.dto.EstabelecimentoAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.establishments.dto.EstabelecimentoResponseDto;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.establishments.repository.EstablishmentRepository;
import valhalla.core.stock.app.shared.error.CnpjAlreadyExistsException;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstabishmentServiceTest {

    @Mock
    private EstablishmentRepository establishmentRepository;

    private EstabishmentService service;
    private UUID establishmentId;

    @BeforeEach
    void setUp() {
        service = new EstabishmentService(establishmentRepository, new BusinessEventLogger());
        establishmentId = UUID.randomUUID();
        autenticar(establishmentId.toString());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveObterDadosDoEstabelecimentoAutenticadoELancarErrosDeSessaoOuNaoEncontrado() {
        EstablishmentEntity entidade = estabelecimento(establishmentId, "Meraki LTDA", "Adega Meraki", "12345678000195");
        entidade.setContactEmail("contato@meraki.com.br");
        entidade.setPhone("1140000000");
        entidade.setAddress("R. das Bebidas, 420");
        when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(entidade));

        EstabelecimentoResponseDto resposta = service.obterEstabelecimento();

        assertThat(resposta.id()).isEqualTo(establishmentId);
        assertThat(resposta.nome()).isEqualTo("Adega Meraki");
        assertThat(resposta.cnpj()).isEqualTo("12345678000195");
        assertThat(resposta.emailContato()).isEqualTo("contato@meraki.com.br");
        assertThat(resposta.telefone()).isEqualTo("1140000000");
        assertThat(resposta.endereco()).isEqualTo("R. das Bebidas, 420");
        assertThat(resposta.ativo()).isTrue();

        when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.obterEstabelecimento())
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Estabelecimento não encontrado");

        autenticar("nao-e-uuid");
        assertThatThrownBy(() -> service.obterEstabelecimento())
                .isInstanceOf(AccessDeniedException.class);

        Jwt semClaim = Jwt.withTokenValue("test").header("alg", "none").subject("user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(semClaim));
        assertThatThrownBy(() -> service.obterEstabelecimento())
                .isInstanceOf(AccessDeniedException.class);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "pass")
        );
        assertThatThrownBy(() -> service.obterEstabelecimento())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deveAtualizarTodosOsCamposENormalizarDados() {
        EstablishmentEntity entidade = estabelecimento(establishmentId, "  ", "Nome Antigo", "00000000000191");
        when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(entidade));
        when(establishmentRepository.existsByCnpjAndIdNot("12345678000195", establishmentId)).thenReturn(false);
        when(establishmentRepository.saveAndFlush(any(EstablishmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EstabelecimentoAtualizacaoRequestDto dto = new EstabelecimentoAtualizacaoRequestDto(
                "  Adega Meraki Premium  ",
                "12.345.678/0001-95",
                "  Contato@Meraki.com.br ",
                " (11) 4000-0000 ",
                " R. das Bebidas, 420 · São Paulo "
        );

        EstabelecimentoResponseDto resposta = service.atualizarEstabelecimento(dto);

        assertThat(resposta.nome()).isEqualTo("Adega Meraki Premium");
        assertThat(entidade.getCorporateName()).isEqualTo("Adega Meraki Premium");
        assertThat(resposta.cnpj()).isEqualTo("12345678000195");
        assertThat(resposta.emailContato()).isEqualTo("contato@meraki.com.br");
        assertThat(resposta.telefone()).isEqualTo("(11) 4000-0000");
        assertThat(resposta.endereco()).isEqualTo("R. das Bebidas, 420 · São Paulo");
    }

    @Test
    void deveLimparCamposOpcionaisEManterMesmoCnpj() {
        EstablishmentEntity entidade = estabelecimento(establishmentId, "Meraki LTDA", "Adega Meraki", "12345678000195");
        entidade.setContactEmail("contato@meraki.com.br");
        entidade.setPhone("1140000000");
        entidade.setAddress("R. das Bebidas, 420");
        when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(entidade));
        when(establishmentRepository.saveAndFlush(any(EstablishmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EstabelecimentoAtualizacaoRequestDto dto = new EstabelecimentoAtualizacaoRequestDto(
                null,
                "12.345.678/0001-95",
                "   ",
                "",
                "   "
        );

        EstabelecimentoResponseDto resposta = service.atualizarEstabelecimento(dto);

        assertThat(resposta.nome()).isEqualTo("Adega Meraki");
        assertThat(resposta.cnpj()).isEqualTo("12345678000195");
        assertThat(resposta.emailContato()).isNull();
        assertThat(resposta.telefone()).isNull();
        assertThat(resposta.endereco()).isNull();
        verify(establishmentRepository, never()).existsByCnpjAndIdNot(any(), any());
    }

    @Test
    void deveValidarRegrasDeEntradaEConflitoDeCnpj() {
        assertThatThrownBy(() -> service.atualizarEstabelecimento(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Informe ao menos um campo para atualização");

        assertThatThrownBy(() -> service.atualizarEstabelecimento(
                new EstabelecimentoAtualizacaoRequestDto(null, null, null, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Informe ao menos um campo para atualização");

        EstablishmentEntity entidade = estabelecimento(establishmentId, "Meraki LTDA", "Adega Meraki", "00000000000191");
        when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(entidade));

        assertThatThrownBy(() -> service.atualizarEstabelecimento(
                new EstabelecimentoAtualizacaoRequestDto("   ", null, null, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O nome da adega não pode ficar vazio");

        assertThatThrownBy(() -> service.atualizarEstabelecimento(
                new EstabelecimentoAtualizacaoRequestDto(null, "12345", null, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O CNPJ deve conter 14 dígitos numéricos");

        assertThatThrownBy(() -> service.atualizarEstabelecimento(
                new EstabelecimentoAtualizacaoRequestDto(null, "12.345.678/0001-99", null, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CNPJ");
        assertThatThrownBy(() -> service.atualizarEstabelecimento(
                new EstabelecimentoAtualizacaoRequestDto(null, "00.000.000/0000-00", null, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CNPJ");

        when(establishmentRepository.existsByCnpjAndIdNot("98765432000198", establishmentId)).thenReturn(true);
        assertThatThrownBy(() -> service.atualizarEstabelecimento(
                new EstabelecimentoAtualizacaoRequestDto(null, "98.765.432/0001-98", null, null, null)
        )).isInstanceOf(CnpjAlreadyExistsException.class)
                .hasMessage("Já existe um estabelecimento cadastrado com este CNPJ");

        when(establishmentRepository.existsByCnpjAndIdNot("98765432000198", establishmentId)).thenReturn(false);
        when(establishmentRepository.saveAndFlush(any(EstablishmentEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq_estabelecimento_cnpj"));
        assertThatThrownBy(() -> service.atualizarEstabelecimento(
                new EstabelecimentoAtualizacaoRequestDto("Meraki", "98.765.432/0001-98", null, null, null)
        )).isInstanceOf(CnpjAlreadyExistsException.class)
                .hasMessage("Já existe um estabelecimento cadastrado com este CNPJ");
    }

    private void autenticar(String estId) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .claim("establishmentId", estId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private EstablishmentEntity estabelecimento(UUID id, String razaoSocial, String nomeFantasia, String cnpj) {
        LocalDateTime agora = LocalDateTime.now();
        return EstablishmentEntity.builder()
                .id(id)
                .corporateName(razaoSocial)
                .tradeName(nomeFantasia)
                .cnpj(cnpj)
                .active(true)
                .createdAt(agora)
                .updatedAt(agora)
                .build();
    }
}
