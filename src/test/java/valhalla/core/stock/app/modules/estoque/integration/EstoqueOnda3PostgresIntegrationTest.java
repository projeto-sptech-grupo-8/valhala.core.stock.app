package valhalla.core.stock.app.modules.estoque.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.repository.LoteEstoqueRepository;
import valhalla.core.stock.app.support.PostgresIntegrationTestSupport;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EstoqueOnda3PostgresIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private LoteEstoqueRepository loteEstoqueRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migracaoV29ReconciliaDivergenciaPositivaENegativaDeLotesNoPostgres() throws Exception {
        CategoriaEntity categoria = categoria("Cervejas");
        ProdutoEntity produtoPositivo = produtoPadrao("Brahma Lata", categoria, false, "15");
        ProdutoEntity produtoNegativo = produtoPadrao("Heineken 600ml", categoria, false, "5");

        EstoqueEntity estoquePositivo = estoqueRepository.findByProdutoId(produtoPositivo.getId()).orElseThrow();
        EstoqueEntity estoqueNegativo = estoqueRepository.findByProdutoId(produtoNegativo.getId()).orElseThrow();

        // Positivo: soma dos lotes (3) < estoque (15) => diferença +12 deve ir para SEM-LOTE
        jdbcTemplate.update(
                "INSERT INTO lote_estoque (estoque_id, codigo, validade, quantidade_atual) VALUES (?, 'L-POS', ?, 3)",
                estoquePositivo.getId(), Date.valueOf(LocalDate.now().plusDays(30))
        );

        // Negativo: soma dos lotes (4 + 6 = 10) > estoque (5) => excesso 5 deve sair em ordem FEFO (4 do primeiro, 1 do segundo)
        jdbcTemplate.update(
                "INSERT INTO lote_estoque (estoque_id, codigo, validade, quantidade_atual) VALUES (?, 'FEFO-1', ?, 4)",
                estoqueNegativo.getId(), Date.valueOf(LocalDate.now().plusDays(5))
        );
        jdbcTemplate.update(
                "INSERT INTO lote_estoque (estoque_id, codigo, validade, quantidade_atual) VALUES (?, 'FEFO-2', ?, 6)",
                estoqueNegativo.getId(), Date.valueOf(LocalDate.now().plusDays(40))
        );
        entityManager.flush();
        entityManager.clear();

        String sqlV29 = new ClassPathResource("db/migration/V29__endurecer_estoque_versao_e_reconciliar_lotes.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        for (String instrucao : sqlV29.split(";")) {
            String limpa = instrucao.replaceAll("(?m)^--.*$", "").trim();
            if (!limpa.isBlank()) {
                jdbcTemplate.execute(limpa);
            }
        }
        entityManager.clear();

        List<LoteEstoqueEntity> lotesPositivos = loteEstoqueRepository.findAllByEstoqueId(estoquePositivo.getId());
        assertThat(lotesPositivos).anySatisfy(l -> {
            assertThat(l.getCodigo()).isEqualTo("SEM-LOTE");
            assertThat(l.getQuantidadeAtual()).isEqualByComparingTo("12");
        });

        List<LoteEstoqueEntity> lotesNegativos = loteEstoqueRepository.findAllByEstoqueId(estoqueNegativo.getId());
        assertThat(lotesNegativos).anySatisfy(l -> {
            assertThat(l.getCodigo()).isEqualTo("FEFO-1");
            assertThat(l.getQuantidadeAtual()).isEqualByComparingTo("0");
        });
        assertThat(lotesNegativos).anySatisfy(l -> {
            assertThat(l.getCodigo()).isEqualTo("FEFO-2");
            assertThat(l.getQuantidadeAtual()).isEqualByComparingTo("5");
        });
    }

    @Test
    void resumoDeValidadeExecutaAgregacaoSqlComFracionadoESemValidadeNoPostgres() throws Exception {
        CategoriaEntity categoria = categoria("Destilados");
        ProdutoEntity cerveja = produtoPadrao("Stella 330ml", categoria, false, "0");
        ProdutoEntity vodka = produtoPadrao("Vodka 1L", categoria, true, "0");
        ProdutoEntity inativo = produtoPadrao("Produto Inativo", categoria, false, "0");

        Cookie operador = autenticarComPermissoes("pg-lotes@meraki.com", "MOVIMENTAR_ESTOQUE", "VISUALIZAR_ESTOQUE");
        LocalDate hoje = LocalDate.now();

        mockMvc.perform(post("/movimentacoes-estoque").cookie(operador).with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":\"%s\",\"tipo\":\"ENTRADA\",\"quantidade\":2,\"lote\":\"L-VENC\",\"validade\":\"%s\"}"
                                .formatted(cerveja.getId(), hoje.minusDays(2))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/movimentacoes-estoque").cookie(operador).with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":\"%s\",\"tipo\":\"ENTRADA\",\"quantidade\":1,\"lote\":\"L-7D\",\"validade\":\"%s\"}"
                                .formatted(cerveja.getId(), hoje.plusDays(5))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/movimentacoes-estoque").cookie(operador).with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":\"%s\",\"tipo\":\"ENTRADA\",\"quantidade\":3,\"lote\":\"L-30D\",\"validade\":\"%s\"}"
                                .formatted(cerveja.getId(), hoje.plusDays(20))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/movimentacoes-estoque").cookie(operador).with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":\"%s\",\"tipo\":\"ENTRADA\",\"quantidade\":4}"
                                .formatted(cerveja.getId())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/movimentacoes-estoque").cookie(operador).with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":\"%s\",\"tipo\":\"ENTRADA\",\"quantidade\":500,\"lote\":\"V-VENC\",\"validade\":\"%s\"}"
                                .formatted(vodka.getId(), hoje.minusDays(1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/movimentacoes-estoque").cookie(operador).with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":\"%s\",\"tipo\":\"ENTRADA\",\"quantidade\":5,\"lote\":\"INAT\",\"validade\":\"%s\"}"
                                .formatted(inativo.getId(), hoje.minusDays(1))))
                .andExpect(status().isCreated());
        inativo.setAtivo(false);
        produtoRepository.saveAndFlush(inativo);

        mockMvc.perform(get("/lotes/resumo").cookie(operador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotesComSaldo").value(5))
                .andExpect(jsonPath("$.vencidos").value(2))
                .andExpect(jsonPath("$.vencemEm7Dias").value(1))
                .andExpect(jsonPath("$.vencemEm30Dias").value(1))
                .andExpect(jsonPath("$.semValidade").value(1))
                .andExpect(jsonPath("$.valorVencido").value(25.0))
                .andExpect(jsonPath("$.valorEmRisco").value(65.0));
    }

    @Test
    void controleDeVersaoOtimistaDetectaConcorrenciaEmEstoqueELoteNoPostgres() {
        CategoriaEntity categoria = categoria("Concorrência");
        ProdutoEntity produto = produtoPadrao("Gin 1L", categoria, false, "10");

        EstoqueEntity estoqueOriginal = estoqueRepository.findByProdutoId(produto.getId()).orElseThrow();
        assertThat(estoqueOriginal.getVersao()).isEqualTo(0L);
        entityManager.detach(estoqueOriginal);

        EstoqueEntity estoqueAtualizado = estoqueRepository.findByProdutoId(produto.getId()).orElseThrow();
        estoqueAtualizado.setEstoqueMinimo(new BigDecimal("4"));
        estoqueAtualizado = estoqueRepository.saveAndFlush(estoqueAtualizado);
        assertThat(estoqueAtualizado.getVersao()).isEqualTo(1L);
        entityManager.clear();

        estoqueOriginal.setEstoqueMinimo(new BigDecimal("2"));
        assertThatThrownBy(() -> estoqueRepository.saveAndFlush(estoqueOriginal))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        entityManager.clear();

        LoteEstoqueEntity novaEntidade = new LoteEstoqueEntity();
        novaEntidade.setEstoque(estoqueAtualizado);
        novaEntidade.setCodigo("L-OPT-1");
        novaEntidade.setQuantidadeAtual(new BigDecimal("10"));
        LoteEstoqueEntity loteObsoleto = loteEstoqueRepository.saveAndFlush(novaEntidade);
        assertThat(loteObsoleto.getVersao()).isEqualTo(0L);
        entityManager.detach(loteObsoleto);

        LoteEstoqueEntity loteAtualizado = loteEstoqueRepository.findById(loteObsoleto.getId()).orElseThrow();
        loteAtualizado.setQuantidadeAtual(new BigDecimal("8"));
        loteAtualizado = loteEstoqueRepository.saveAndFlush(loteAtualizado);
        assertThat(loteAtualizado.getVersao()).isEqualTo(1L);
        entityManager.clear();

        loteObsoleto.setQuantidadeAtual(new BigDecimal("7"));
        assertThatThrownBy(() -> loteEstoqueRepository.saveAndFlush(loteObsoleto))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void movimentacaoQueInvadeReservaRetorna409AntesDaConstraintDoPostgres() throws Exception {
        CategoriaEntity categoria = categoria("Reservas");
        ProdutoEntity produto = produtoPadrao("Red Bull", categoria, false, "10");
        EstoqueEntity estoque = estoqueRepository.findByProdutoId(produto.getId()).orElseThrow();
        estoque.setQuantidadeReservada(new BigDecimal("6"));
        estoqueRepository.saveAndFlush(estoque);

        Cookie operador = autenticarComPermissoes("pg-reserva@meraki.com", "MOVIMENTAR_ESTOQUE");

        mockMvc.perform(post("/movimentacoes-estoque").cookie(operador).with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":\"%s\",\"tipo\":\"SAIDA\",\"quantidade\":5,\"motivo\":\"Balcão\"}"
                                .formatted(produto.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.estoque").exists());
    }
}
