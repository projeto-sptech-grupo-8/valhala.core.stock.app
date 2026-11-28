package valhalla.core.stock.app.support;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.CaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.CanalPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusDescontoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PedidoRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigracaoV30PostgresIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PagamentoPedidoRepository pagamentoPedidoRepository;

    @Test
    void todasCheckConstraintsValidadasEIndicesDaV30Criados() {
        Integer naoValidadas = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_constraint c
                JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = 'public'
                  AND c.contype = 'c'
                  AND NOT c.convalidated
                """, Integer.class);
        assertThat(naoValidadas).isZero();

        List<String> indices = jdbcTemplate.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'pagamento_pedido'
                  AND indexname IN (
                      'uq_pagamento_chave_idempotencia',
                      'idx_pagamento_order_externa_id',
                      'idx_pagamento_terminal_id_status'
                  )
                """, String.class);
        assertThat(indices).containsExactlyInAnyOrder(
                "uq_pagamento_chave_idempotencia",
                "idx_pagamento_order_externa_id",
                "idx_pagamento_terminal_id_status"
        );
    }

    @Test
    void chaveIdempotenciaPermiteMesmaChaveEmPedidosDistintosEBloqueiaNoMesmoPedido() throws Exception {
        Cookie gerente = autenticarGerente("v30.idemp@meraki.com");
        assertThat(gerente).isNotNull();
        UserEntity usuario = userRepository.findByEmailIgnoreCase("v30.idemp@meraki.com").orElseThrow();

        CaixaEntity caixa = new CaixaEntity();
        caixa.setEstabelecimentoId(estabelecimento.getId());
        caixa.setNome("Caixa V30");
        caixa.setAtivo(true);
        caixa = caixaRepository.saveAndFlush(caixa);

        PedidoEntity pedido1 = criarPedido(usuario.getId(), 700_001L);
        PedidoEntity pedido2 = criarPedido(usuario.getId(), 700_002L);

        PagamentoPedidoEntity pag1 = novoPagamento(pedido1.getId(), "chave-compartilhada");
        pagamentoPedidoRepository.saveAndFlush(pag1);

        // Mesmo valor de chave_idempotencia em pedido diferente deve ser aceito após V30
        PagamentoPedidoEntity pag2 = novoPagamento(pedido2.getId(), "chave-compartilhada");
        pagamentoPedidoRepository.saveAndFlush(pag2);

        // Mesma chave no mesmo pedido deve ser rejeitada pelo índice único uq_pagamento_chave_idempotencia
        PagamentoPedidoEntity pagDuplicadoMesmoPedido = novoPagamento(pedido1.getId(), "chave-compartilhada");
        assertThatThrownBy(() -> pagamentoPedidoRepository.saveAndFlush(pagDuplicadoMesmoPedido))
                .isInstanceOf(Exception.class);
    }

    private PedidoEntity criarPedido(UUID usuarioId, long numero) {
        PedidoEntity pedido = new PedidoEntity();
        pedido.setEstabelecimentoId(estabelecimento.getId());
        pedido.setOperadorUsuarioId(usuarioId);
        pedido.setNumero(numero);
        pedido.setTipo("VENDA_PDV");
        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_PAGAMENTO);
        pedido.setCanal(CanalPedidoCaixa.BALCAO);
        pedido.setSubtotal(new BigDecimal("20.00"));
        pedido.setDesconto(BigDecimal.ZERO);
        pedido.setTotal(new BigDecimal("20.00"));
        pedido.setStatusDesconto(StatusDescontoPedido.NENHUM);
        return pedidoRepository.saveAndFlush(pedido);
    }

    private PagamentoPedidoEntity novoPagamento(UUID pedidoId, String chaveIdempotencia) {
        PagamentoPedidoEntity pagamento = new PagamentoPedidoEntity();
        pagamento.setId(UUID.randomUUID());
        pagamento.setPedidoId(pedidoId);
        pagamento.setForma(FormaPagamentoCaixa.PIX);
        pagamento.setStatus(StatusPagamentoPedido.PENDENTE);
        pagamento.setProvedor("MERCADO_PAGO");
        pagamento.setValor(new BigDecimal("20.00"));
        pagamento.setValorTroco(BigDecimal.ZERO);
        pagamento.setValorEstornado(BigDecimal.ZERO);
        pagamento.setParcelas(1);
        pagamento.setChaveIdempotencia(chaveIdempotencia);
        return pagamento;
    }
}
