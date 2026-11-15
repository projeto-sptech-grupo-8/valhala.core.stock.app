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
    private static final int LARGURA_MAXIMA_COLUNA = 12000;

    @Override
    public <T> byte[] gerarPlanilha(String nomeAba, List<ColunaExcel<T>> colunas, List<T> registros) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(JANELA_STREAMING);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            SXSSFSheet sheet = workbook.createSheet(nomeAba);
            sheet.trackAllColumnsForAutoSizing();

            CellStyle headerStyle = criarEstiloCabecalho(workbook);
            Map<FormatoCelulaExcel, CellStyle> estilos = criarEstilos(workbook);

            Row header = sheet.createRow(0);
            for (int i = 0; i < colunas.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(colunas.get(i).cabecalho());
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (T registro : registros) {
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < colunas.size(); i++) {
                    ColunaExcel<T> coluna = colunas.get(i);
                    Object valor = coluna.extrator().apply(registro);
                    escreverCelula(row.createCell(i), valor, coluna.formato(), estilos);
                }
            }

            for (int i = 0; i < colunas.size(); i++) {
                sheet.autoSizeColumn(i);
                int largura = Math.min(sheet.getColumnWidth(i) + 512, LARGURA_MAXIMA_COLUNA);
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

    private void escreverCelula(
            Cell cell,
            Object valor,
            FormatoCelulaExcel formato,
            Map<FormatoCelulaExcel, CellStyle> estilos
    ) {
        if (valor == null) {
            return;
        }
        switch (formato) {
            case INTEIRO -> {
                if (valor instanceof Number numero) {
                    cell.setCellValue(numero.longValue());
                    cell.setCellStyle(estilos.get(FormatoCelulaExcel.INTEIRO));
                }
            }
            case DECIMAL, MOEDA -> {
                if (valor instanceof Number numero) {
                    cell.setCellValue(numero.doubleValue());
                    cell.setCellStyle(estilos.get(formato));
                }
            }
            case DATA_HORA -> {
                if (valor instanceof LocalDateTime dataHora) {
                    cell.setCellValue(dataHora);
                    cell.setCellStyle(estilos.get(FormatoCelulaExcel.DATA_HORA));
                } else {
                    cell.setCellValue(valor.toString());
                }
            }
            case TEXTO -> cell.setCellValue(valor.toString());
        }
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
