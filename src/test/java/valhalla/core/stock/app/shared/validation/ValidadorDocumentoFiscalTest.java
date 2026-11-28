package valhalla.core.stock.app.shared.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorDocumentoFiscalTest {

    @Test
    void validaCpfComESemMascaraERejeitaDigitosInvalidosOuRepetidos() {
        assertThat(ValidadorDocumentoFiscal.isCpfValido("12345678909")).isTrue();
        assertThat(ValidadorDocumentoFiscal.isCpfValido("123.456.789-09")).isTrue();
        assertThat(ValidadorDocumentoFiscal.isCpfValido("98765432100")).isTrue();

        assertThat(ValidadorDocumentoFiscal.isCpfValido("123.456.789-01")).isFalse();
        assertThat(ValidadorDocumentoFiscal.isCpfValido("11111111111")).isFalse();
        assertThat(ValidadorDocumentoFiscal.isCpfValido("1234567890")).isFalse();
        assertThat(ValidadorDocumentoFiscal.isCpfValido(null)).isFalse();
    }

    @Test
    void validaCnpjComESemMascaraERejeitaDigitosInvalidosOuRepetidos() {
        assertThat(ValidadorDocumentoFiscal.isCnpjValido("00000000000191")).isTrue();
        assertThat(ValidadorDocumentoFiscal.isCnpjValido("12.345.678/0001-95")).isTrue();
        assertThat(ValidadorDocumentoFiscal.isCnpjValido("98.765.432/0001-98")).isTrue();
        assertThat(ValidadorDocumentoFiscal.isCnpjValido("11.222.333/0001-81")).isTrue();

        assertThat(ValidadorDocumentoFiscal.isCnpjValido("12.345.678/0001-99")).isFalse();
        assertThat(ValidadorDocumentoFiscal.isCnpjValido("00.000.000/0000-00")).isFalse();
        assertThat(ValidadorDocumentoFiscal.isCnpjValido("1234567800019")).isFalse();
        assertThat(ValidadorDocumentoFiscal.isCnpjValido(null)).isFalse();
    }

    @Test
    void validaDocumentoGenericoAceitandoCpfOuCnpjValidos() {
        assertThat(ValidadorDocumentoFiscal.isCpfOuCnpjValido("123.456.789-09")).isTrue();
        assertThat(ValidadorDocumentoFiscal.isCpfOuCnpjValido("12.345.678/0001-95")).isTrue();
        assertThat(ValidadorDocumentoFiscal.isCpfOuCnpjValido("12.345.678/0001-99")).isFalse();
        assertThat(ValidadorDocumentoFiscal.isCpfOuCnpjValido("12345")).isFalse();
    }
}
