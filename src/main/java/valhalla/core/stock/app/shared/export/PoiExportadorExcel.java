package valhalla.core.stock.app.shared.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PoiExportadorExcel implements ExportadorExcel {

    private static final int JANELA_STREAMING = 100;
    private static final int LARGURA_MINIMA_COLUNA = 10 * 256;
    private static final int MARGEM_CARACTERES_COLUNA = 4;
    private static final int LARGURA_MAXIMA_COLUNA = 12000;
    private static final int COMPRIMENTO_MAXIMO_CARACTERES = LARGURA_MAXIMA_COLUNA / 256;

    @Override
    public <T> byte[] gerarPlanilha(String nomeAba, List<ColunaExcel<T>> colunas, List<T> registros) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(JANELA_STREAMING);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            SXSSFSheet sheet = workbook.createSheet(nomeAba);

            CellStyle headerStyle = criarEstiloCabecalho(workbook);
            Map<FormatoCelulaExcel, CellStyle> estilos = criarEstilos(workbook);
            int[] larguraMaximaCaracteres = new int[colunas.size()];

            Row header = sheet.createRow(0);
            for (int i = 0; i < colunas.size(); i++) {
                String cabecalho = sanitizarFormula(colunas.get(i).cabecalho());
                Cell cell = header.createCell(i);
                cell.setCellValue(cabecalho);
                cell.setCellStyle(headerStyle);
                if (cabecalho != null) {
                    larguraMaximaCaracteres[i] = Math.min(cabecalho.length(), COMPRIMENTO_MAXIMO_CARACTERES);
                }
            }

            int rowIdx = 1;
            for (T registro : registros) {
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < colunas.size(); i++) {
                    ColunaExcel<T> coluna = colunas.get(i);
                    Object valor = coluna.extrator().apply(registro);
                    int comprimento = escreverCelula(row.createCell(i), valor, coluna.formato(), estilos);
                    if (comprimento > larguraMaximaCaracteres[i]) {
                        larguraMaximaCaracteres[i] = Math.min(comprimento, COMPRIMENTO_MAXIMO_CARACTERES);
                    }
                }
            }

            for (int i = 0; i < colunas.size(); i++) {
                int larguraCalculada = (larguraMaximaCaracteres[i] + MARGEM_CARACTERES_COLUNA) * 256;
                int largura = Math.max(LARGURA_MINIMA_COLUNA, Math.min(larguraCalculada, LARGURA_MAXIMA_COLUNA));
                sheet.setColumnWidth(i, largura);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException excecao) {
            throw new IllegalStateException("Não foi possível gerar a planilha Excel", excecao);
        } finally {
            workbook.dispose();
            try {
                workbook.close();
            } catch (IOException ignored) {
                // fechamento best-effort após dispose
            }
        }
    }

    private int escreverCelula(
            Cell cell,
            Object valor,
            FormatoCelulaExcel formato,
            Map<FormatoCelulaExcel, CellStyle> estilos
    ) {
        if (valor == null) {
            return 0;
        }
        return switch (formato) {
            case INTEIRO -> {
                if (valor instanceof Number numero) {
                    long inteiro = numero.longValue();
                    cell.setCellValue(inteiro);
                    cell.setCellStyle(estilos.get(FormatoCelulaExcel.INTEIRO));
                    yield Long.toString(inteiro).length() + 3;
                }
                yield 0;
            }
            case DECIMAL, MOEDA -> {
                if (valor instanceof Number numero) {
                    double decimal = numero.doubleValue();
                    cell.setCellValue(decimal);
                    cell.setCellStyle(estilos.get(formato));
                    yield String.valueOf(decimal).length() + (formato == FormatoCelulaExcel.MOEDA ? 6 : 3);
                }
                yield 0;
            }
            case DATA_HORA -> {
                if (valor instanceof LocalDateTime dataHora) {
                    cell.setCellValue(dataHora);
                    cell.setCellStyle(estilos.get(FormatoCelulaExcel.DATA_HORA));
                    yield 16;
                }
                String texto = sanitizarFormula(valor.toString());
                cell.setCellValue(texto);
                yield texto.length();
            }
            case TEXTO -> {
                String texto = sanitizarFormula(valor.toString());
                cell.setCellValue(texto);
                yield texto.length();
            }
        };
    }

    static String sanitizarFormula(String valor) {
        if (valor == null || valor.isEmpty()) {
            return valor;
        }
        if (ehPrefixoFormula(valor.charAt(0))) {
            return "'" + valor;
        }
        for (int i = 0; i < valor.length(); i++) {
            char caractere = valor.charAt(i);
            if (!Character.isWhitespace(caractere)) {
                return ehPrefixoFormula(caractere) ? "'" + valor : valor;
            }
        }
        return valor;
    }

    private static boolean ehPrefixoFormula(char caractere) {
        return caractere == '='
                || caractere == '+'
                || caractere == '-'
                || caractere == '@'
                || caractere == '\t'
                || caractere == '\r'
                || caractere == '\n';
    }

    private Map<FormatoCelulaExcel, CellStyle> criarEstilos(Workbook workbook) {
        DataFormat dataFormat = workbook.createDataFormat();
        Map<FormatoCelulaExcel, CellStyle> estilos = new EnumMap<>(FormatoCelulaExcel.class);

        CellStyle moeda = workbook.createCellStyle();
        moeda.setDataFormat(dataFormat.getFormat("R$ #,##0.00"));
        estilos.put(FormatoCelulaExcel.MOEDA, moeda);

        CellStyle decimal = workbook.createCellStyle();
        decimal.setDataFormat(dataFormat.getFormat("#,##0.###"));
        estilos.put(FormatoCelulaExcel.DECIMAL, decimal);

        CellStyle inteiro = workbook.createCellStyle();
        inteiro.setDataFormat(dataFormat.getFormat("#,##0"));
        estilos.put(FormatoCelulaExcel.INTEIRO, inteiro);

        CellStyle dataHora = workbook.createCellStyle();
        dataHora.setDataFormat(dataFormat.getFormat("dd/mm/yyyy hh:mm"));
        estilos.put(FormatoCelulaExcel.DATA_HORA, dataHora);

        return estilos;
    }

    private CellStyle criarEstiloCabecalho(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
