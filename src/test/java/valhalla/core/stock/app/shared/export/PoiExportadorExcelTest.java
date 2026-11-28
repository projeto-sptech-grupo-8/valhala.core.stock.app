package valhalla.core.stock.app.shared.export;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PoiExportadorExcelTest {

    private record LinhaTeste(
            String texto,
            Integer inteiro,
            BigDecimal valor,
            Object dataHora
    ) {
    }

    private final PoiExportadorExcel exportador = new PoiExportadorExcel();

    @Test
    void neutralizaFormulaInjectionEmTextosCabecalhosEFallbackDataHora() throws Exception {
        List<ColunaExcel<LinhaTeste>> colunas = List.of(
                new ColunaExcel<>("=CABECALHO", FormatoCelulaExcel.TEXTO, LinhaTeste::texto),
                new ColunaExcel<>("Qtd", FormatoCelulaExcel.INTEIRO, LinhaTeste::inteiro),
                new ColunaExcel<>("Valor", FormatoCelulaExcel.MOEDA, LinhaTeste::valor),
                new ColunaExcel<>("Data", FormatoCelulaExcel.DATA_HORA, LinhaTeste::dataHora)
        );

        List<LinhaTeste> linhas = List.of(
                new LinhaTeste("=CMD|' /C calc'!A0", 10, new BigDecimal("-5.50"), LocalDateTime.of(2026, 11, 20, 14, 0)),
                new LinhaTeste("+1+1", null, null, "=HYPERLINK(\"http://evil\")"),
                new LinhaTeste("-2+3", null, null, null),
                new LinhaTeste("@SUM(1,2)", null, null, null),
                new LinhaTeste("\t=1+1", null, null, null),
                new LinhaTeste("\r=1+1", null, null, null),
                new LinhaTeste("  =1+1", null, null, null),
                new LinhaTeste("Brahma Lata - NF-123", 1, new BigDecimal("12.34"), null),
                new LinhaTeste("   ", null, null, null),
                new LinhaTeste("", null, null, null),
                new LinhaTeste(null, null, null, null)
        );

        byte[] bytes = exportador.gerarPlanilha("Teste", colunas, linhas);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Teste");
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("'=CABECALHO");

            assertThat(sheet.getRow(1).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("'=CMD|' /C calc'!A0");
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(10.0);
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(-5.50);

            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("'+1+1");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("'=HYPERLINK(\"http://evil\")");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("'-2+3");
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("'@SUM(1,2)");
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("'\t=1+1");
            assertThat(sheet.getRow(6).getCell(0).getStringCellValue()).isEqualTo("'\r=1+1");
            assertThat(sheet.getRow(7).getCell(0).getStringCellValue()).isEqualTo("'  =1+1");
            assertThat(sheet.getRow(8).getCell(0).getStringCellValue()).isEqualTo("Brahma Lata - NF-123");
            assertThat(sheet.getRow(9).getCell(0).getStringCellValue()).isEqualTo("   ");
            assertThat(sheet.getRow(10).getCell(0).getStringCellValue()).isEmpty();
            assertThat(sheet.getRow(11).getCell(0).getCellType()).isEqualTo(CellType.BLANK);

            for (int i = 0; i < colunas.size(); i++) {
                assertThat(sheet.getColumnWidth(i)).isBetween(10 * 256, 12000);
            }
        }
    }

    @Test
    void sanitizarFormulaPreservaValoresNulosOuNormaisLimitaLarguraMaximaDeColuna() throws Exception {
        assertThat(PoiExportadorExcel.sanitizarFormula(null)).isNull();
        assertThat(PoiExportadorExcel.sanitizarFormula("")).isEmpty();
        assertThat(PoiExportadorExcel.sanitizarFormula("Produto Normal")).isEqualTo("Produto Normal");
        assertThat(PoiExportadorExcel.sanitizarFormula("\n=1")).isEqualTo("'\n=1");

        List<ColunaExcel<LinhaTeste>> colunas = List.of(
                new ColunaExcel<>("Descrição Longa", FormatoCelulaExcel.TEXTO, LinhaTeste::texto)
        );
        byte[] bytes = exportador.gerarPlanilha(
                "Largura",
                colunas,
                List.of(new LinhaTeste("X".repeat(120), null, null, null))
        );

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Largura");
            assertThat(sheet.getColumnWidth(0)).isEqualTo(12000);
        }
    }
}
