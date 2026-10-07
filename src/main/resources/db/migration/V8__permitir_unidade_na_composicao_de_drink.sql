-- Ingredientes fracionados são consumidos em ML; ingredientes unitários, em UN.
ALTER TABLE composicao_drink
    DROP CONSTRAINT IF EXISTS ck_composicao_drink_unidade;

ALTER TABLE composicao_drink
    ADD CONSTRAINT ck_composicao_drink_unidade
    CHECK (unidade_consumo IN ('ML', 'UN'));
