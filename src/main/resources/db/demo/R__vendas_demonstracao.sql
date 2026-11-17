-- Dados de demonstração para os relatórios: vendas dos últimos 90 dias, perdas e rupturas de estoque.
-- Só é aplicado com o perfil `dev` (ver application-dev.properties); nunca em produção.
-- Idempotente: cada bloco só insere se ainda não houver dados de demonstração. Os dados são sintéticos
-- e relativos à data de execução (a janela de 90 dias acompanha o dia em que o seed rodou).
--
-- As vendas não baixam estoque: servem apenas aos relatórios. As perdas já estão refletidas no saldo atual
-- (saldo_anterior = saldo atual + perda). As rupturas são pares saída-até-zero / reposição com efeito líquido
-- nulo no saldo atual. Movimentações de demonstração levam o marcador '[demo]' no motivo.

-- ─── Vendas (pedidos, itens e pagamentos) ───
DO $$
DECLARE
    v_estab UUID;
    v_user  UUID;
BEGIN
    SELECT id INTO v_estab FROM estabelecimento WHERE cnpj = '00000000000191';
    SELECT id INTO v_user FROM usuario WHERE LOWER(email) = 'dev@meraki.com';
    IF v_estab IS NULL OR v_user IS NULL OR EXISTS (SELECT 1 FROM pedido WHERE estabelecimento_id = v_estab) THEN
        RETURN;
    END IF;

    PERFORM setseed(0.42); -- mesma massa de dados a cada carga

    -- vendas por dia: mais movimento de sexta a domingo; horário concentrado no fim da tarde e à noite
    CREATE TEMP TABLE tmp_pedido ON COMMIT DROP AS
    SELECT row_number() OVER (ORDER BY momento) AS seq, momento,
           CASE WHEN r < 0.50 THEN 'BALCAO' WHEN r < 0.85 THEN 'MESA' ELSE 'DELIVERY' END AS tipo,
           CASE WHEN r2 < 0.03 THEN 'CANCELADO' ELSE 'CONCLUIDO' END AS status,
           1 + floor(random() * 4)::int AS n_itens,
           random() AS r_pgto
    FROM (
        SELECT (d::date + make_interval(hours => 10 + floor(14 * sqrt(random()))::int,
                                         mins => floor(random() * 60)::int)) AS momento,
               random() AS r, random() AS r2
        FROM generate_series(current_date - 89, current_date, interval '1 day') d
        CROSS JOIN LATERAL generate_series(1,
            CASE EXTRACT(ISODOW FROM d) WHEN 5 THEN 16 WHEN 6 THEN 20 WHEN 7 THEN 12 ELSE 7 END
            + floor(random() * 4)::int) n
    ) t
    WHERE momento <= now();

    INSERT INTO pedido (estabelecimento_id, operador_usuario_id, numero, tipo, status, subtotal, desconto, total,
                        criado_em, concluido_em, cancelado_em)
    SELECT v_estab, v_user, seq, tipo, status, 0, 0, 0, momento,
           CASE WHEN status = 'CONCLUIDO' THEN momento END,
           CASE WHEN status = 'CANCELADO' THEN momento END
    FROM tmp_pedido;

    -- produtos vendáveis ordenados por SKU; o expoente concentra as vendas em poucos produtos (curva ABC)
    CREATE TEMP TABLE tmp_vendavel ON COMMIT DROP AS
    SELECT row_number() OVER (ORDER BY sku) AS idx, id, nome, preco_venda
    FROM produto WHERE estabelecimento_id = v_estab AND ativo;

    CREATE TEMP TABLE tmp_item ON COMMIT DROP AS
    SELECT pe.id AS pedido_id, 1 + floor(power(random(), 1.8) * (SELECT COUNT(*) FROM tmp_vendavel))::int AS idx,
           1 + floor(random() * 3)::int AS quantidade
    FROM tmp_pedido t
    JOIN pedido pe ON pe.estabelecimento_id = v_estab AND pe.numero = t.seq
    CROSS JOIN LATERAL generate_series(1, t.n_itens) n;

    INSERT INTO item_pedido (pedido_id, produto_id, descricao_produto, quantidade, preco_unitario, desconto, subtotal,
                             custo_unitario)
    SELECT i.pedido_id, v.id, v.nome, i.quantidade, v.preco_venda, 0, i.quantidade * v.preco_venda,
           COALESCE(cp.custo_unitario, 0)
    FROM tmp_item i
    JOIN tmp_vendavel v ON v.idx = i.idx
    LEFT JOIN vw_custo_produto cp ON cp.produto_id = v.id;

    UPDATE pedido pe
    SET subtotal = s.total, total = s.total
    FROM (SELECT pedido_id, SUM(subtotal) AS total FROM item_pedido GROUP BY pedido_id) s
    WHERE s.pedido_id = pe.id AND pe.estabelecimento_id = v_estab;

    INSERT INTO pagamento_pedido (pedido_id, forma, status, valor, pago_em)
    SELECT pe.id,
           CASE WHEN t.r_pgto < 0.45 THEN 'PIX' WHEN t.r_pgto < 0.75 THEN 'CARTAO_CREDITO'
                WHEN t.r_pgto < 0.90 THEN 'CARTAO_DEBITO' ELSE 'DINHEIRO' END,
           'PAGO', pe.total, pe.concluido_em
    FROM tmp_pedido t
    JOIN pedido pe ON pe.estabelecimento_id = v_estab AND pe.numero = t.seq
    WHERE pe.status = 'CONCLUIDO' AND pe.total > 0;
END $$;

-- ─── Perdas (quebra, vencimento e consumo interno) ───
DO $$
DECLARE
    v_estab UUID;
    v_user  UUID;
BEGIN
    SELECT id INTO v_estab FROM estabelecimento WHERE cnpj = '00000000000191';
    SELECT id INTO v_user FROM usuario WHERE LOWER(email) = 'dev@meraki.com';
    IF v_estab IS NULL OR v_user IS NULL
       OR EXISTS (SELECT 1 FROM movimentacao_estoque WHERE tipo = 'PERDA' AND motivo LIKE '%[demo]%') THEN
        RETURN;
    END IF;

    PERFORM setseed(0.43);

    CREATE TEMP TABLE tmp_estocavel ON COMMIT DROP AS
    SELECT row_number() OVER (ORDER BY p.sku) AS idx, e.id AS estoque_id, e.quantidade_atual,
           CASE WHEN p.fracionado THEN p.volume_embalagem_ml ELSE 1 END AS embalagem
    FROM produto p JOIN estoque e ON e.produto_id = p.id
    WHERE p.estabelecimento_id = v_estab AND p.tipo = 'PADRAO';

    CREATE TEMP TABLE tmp_perda ON COMMIT DROP AS
    SELECT 1 + floor(random() * (SELECT COUNT(*) FROM tmp_estocavel))::int AS idx,
           1 + floor(random() * 2)::int AS embalagens,
           random() AS r,
           (current_date - floor(random() * 88)::int) + make_interval(hours => 10 + floor(random() * 13)::int) AS momento
    FROM generate_series(1, 55);

    INSERT INTO movimentacao_estoque (estoque_id, usuario_id, tipo, quantidade, saldo_anterior, saldo_posterior,
                                      motivo, motivo_perda, ocorrido_em)
    SELECT t.estoque_id, v_user, 'PERDA', s.embalagens * t.embalagem, t.quantidade_atual + s.embalagens * t.embalagem,
           t.quantidade_atual,
           CASE WHEN s.r < 0.45 THEN 'Perda / quebra – garrafa quebrada [demo]'
                WHEN s.r < 0.75 THEN 'Vencimento – lote vencido [demo]'
                ELSE 'Consumo interno – degustação [demo]' END,
           CASE WHEN s.r < 0.45 THEN 'QUEBRA' WHEN s.r < 0.75 THEN 'VENCIMENTO' ELSE 'CONSUMO_INTERNO' END,
           s.momento
    FROM tmp_perda s
    JOIN tmp_estocavel t ON t.idx = s.idx
    WHERE s.momento <= now();
END $$;

-- ─── Rupturas (saldo zerado por alguns dias, depois reposição) ───
DO $$
DECLARE
    v_estab UUID;
    v_user  UUID;
BEGIN
    SELECT id INTO v_estab FROM estabelecimento WHERE cnpj = '00000000000191';
    SELECT id INTO v_user FROM usuario WHERE LOWER(email) = 'dev@meraki.com';
    IF v_estab IS NULL OR v_user IS NULL
       OR EXISTS (SELECT 1 FROM movimentacao_estoque WHERE motivo LIKE 'Ruptura%[demo]%') THEN
        RETURN;
    END IF;

    CREATE TEMP TABLE tmp_ruptura ON COMMIT DROP AS
    SELECT e.id AS estoque_id, e.quantidade_atual, r.atras, r.duracao
    FROM (VALUES
        ('BEB-008', 84, 3), ('BEB-008', 60, 2), ('BEB-008', 33, 4), ('BEB-008', 14, 3), ('BEB-008', 5, 1),
        ('BEB-011', 70, 5), ('BEB-011', 41, 2), ('BEB-011', 20, 3), ('BEB-011', 8, 2),
        ('BEB-002', 75, 2), ('BEB-002', 47, 3), ('BEB-002', 26, 2), ('BEB-002', 3, 1),
        ('BEB-007', 58, 2), ('BEB-007', 36, 2), ('BEB-007', 18, 3),
        ('BEB-012', 22, 2), ('BEB-012', 10, 1)
    ) AS r(sku, atras, duracao)
    JOIN produto p ON p.estabelecimento_id = v_estab AND p.sku = r.sku
    JOIN estoque e ON e.produto_id = p.id
    WHERE e.quantidade_atual > 0;

    INSERT INTO movimentacao_estoque (estoque_id, usuario_id, tipo, quantidade, saldo_anterior, saldo_posterior,
                                      motivo, ocorrido_em)
    SELECT estoque_id, v_user, 'SAIDA', quantidade_atual, quantidade_atual, 0,
           'Ruptura – último item vendido [demo]', (current_date - atras) + interval '14 hours'
    FROM tmp_ruptura
    UNION ALL
    SELECT estoque_id, v_user, 'ENTRADA', quantidade_atual, 0, quantidade_atual,
           'Ruptura – reposição do fornecedor [demo]',
           (current_date - atras) + make_interval(days => duracao) + interval '9 hours'
    FROM tmp_ruptura;
END $$;
