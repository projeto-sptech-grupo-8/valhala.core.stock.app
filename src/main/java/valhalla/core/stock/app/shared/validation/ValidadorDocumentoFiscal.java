package valhalla.core.stock.app.shared.validation;

/**
 * Valida documentos fiscais brasileiros (CPF e CNPJ) pelo algoritmo oficial Módulo 11.
 */
public final class ValidadorDocumentoFiscal {

    private static final int[] PESOS_CNPJ_DV1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_CNPJ_DV2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private ValidadorDocumentoFiscal() {
    }

    public static boolean isCpfValido(String cpf) {
        String digitos = apenasDigitos(cpf);
        if (digitos.length() != 11 || todosDigitosIguais(digitos)) {
            return false;
        }
        int dv1 = calcularDigitoCpf(digitos, 9, 10);
        int dv2 = calcularDigitoCpf(digitos, 10, 11);
        return Character.digit(digitos.charAt(9), 10) == dv1
                && Character.digit(digitos.charAt(10), 10) == dv2;
    }

    public static boolean isCnpjValido(String cnpj) {
        String digitos = apenasDigitos(cnpj);
        if (digitos.length() != 14 || todosDigitosIguais(digitos)) {
            return false;
        }
        int dv1 = calcularDigitoCnpj(digitos, PESOS_CNPJ_DV1);
        int dv2 = calcularDigitoCnpj(digitos, PESOS_CNPJ_DV2);
        return Character.digit(digitos.charAt(12), 10) == dv1
                && Character.digit(digitos.charAt(13), 10) == dv2;
    }

    public static boolean isCpfOuCnpjValido(String documento) {
        String digitos = apenasDigitos(documento);
        if (digitos.length() == 11) {
            return isCpfValido(digitos);
        }
        if (digitos.length() == 14) {
            return isCnpjValido(digitos);
        }
        return false;
    }

    private static String apenasDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    private static boolean todosDigitosIguais(String digitos) {
        return digitos.chars().distinct().count() <= 1;
    }

    private static int calcularDigitoCpf(String digitos, int tamanhoBase, int pesoInicial) {
        int soma = 0;
        int peso = pesoInicial;
        for (int i = 0; i < tamanhoBase; i++) {
            soma += Character.digit(digitos.charAt(i), 10) * peso--;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static int calcularDigitoCnpj(String digitos, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += Character.digit(digitos.charAt(i), 10) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
