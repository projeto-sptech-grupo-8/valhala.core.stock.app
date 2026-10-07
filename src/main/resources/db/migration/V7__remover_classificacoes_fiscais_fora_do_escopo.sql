-- NCM e CEST pertencem ao escopo fiscal e não serão usados pelo módulo atual.
-- Esta migration remove permanentemente os dados já existentes nessas colunas.
ALTER TABLE produto
    DROP COLUMN IF EXISTS ncm,
    DROP COLUMN IF EXISTS cest;

ALTER TABLE item_nota_fiscal
    DROP COLUMN IF EXISTS ncm;
