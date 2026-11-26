package valhalla.core.stock.app.modules.relatorios.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import valhalla.core.stock.app.modules.relatorios.dto.RelatorioLinhas;
import valhalla.core.stock.app.shared.export.PoiExportadorExcel;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RelatorioExportacaoServiceTest {

    private final RelatorioExportacaoService service = new RelatorioExportacaoService(new PoiExportadorExcel());

    @Test
    void geraPlanilhaExcelParaCurvaAbc() throws Exception {
        RelatorioLinhas.CurvaAbc classeA = new RelatorioLinhas.CurvaAbc(
                UUID.randomUUID(), "Heineken 600ml", "Cerveja",
                new BigDecimal("20"), new BigDecimal("298.005"),
                1, new BigDecimal("74.504"), new BigDecimal("74.504"), "A"
        );
        RelatorioLinhas.CurvaAbc semTexto = new RelatorioLinhas.CurvaAbc(
                UUID.randomUUID(), null, null,
                new BigDecimal("2"), null,
                2, null, null, null
        );

        byte[] bytes = service.gerarPlanilhaCurvaAbc(List.of(classeA, semTexto));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Curva ABC");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Posição");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Produto");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Faturamento");
            assertThat(header.getCell(7).getStringCellValue()).isEqualTo("Classe");

            Row linha1 = sheet.getRow(1);
            assertThat(linha1.getCell(0).getNumericCellValue()).isEqualTo(1.0);
            assertThat(linha1.getCell(1).getStringCellValue()).isEqualTo("Heineken 600ml");
            assertThat(linha1.getCell(2).getStringCellValue()).isEqualTo("Cerveja");
            assertThat(linha1.getCell(3).getNumericCellValue()).isEqualTo(20.0);
            assertThat(linha1.getCell(4).getNumericCellValue()).isEqualTo(298.01);
            assertThat(linha1.getCell(5).getNumericCellValue()).isEqualTo(74.5);
            assertThat(linha1.getCell(7).getStringCellValue()).isEqualTo("A");

            Row linha2 = sheet.getRow(2);
            assertThat(linha2.getCell(1).getStringCellValue()).isEmpty();
            assertThat(linha2.getCell(7).getStringCellValue()).isEmpty();
        }
    }

    @Test
    void geraPlanilhaExcelParaFaltaDeEstoqueComClassificacaoDeSituacao() throws Exception {
        RelatorioLinhas.Ruptura recorrente = new RelatorioLinhas.Ruptura(
                UUID.randomUUID(), "Stella Artois", "Cerveja",
                8, 1, new BigDecimal("6"), new BigDecimal("59.404"), new BigDecimal("26.67")
        );
        RelatorioLinhas.Ruptura pontual = new RelatorioLinhas.Ruptura(
                UUID.randomUUID(), "Espumante Brut", "Vinho",
                2, 1, new BigDecimal("1"), new BigDecimal("98.00"), new BigDecimal("6.67")
        );
        RelatorioLinhas.Ruptura semRegistro = new RelatorioLinhas.Ruptura(
                UUID.randomUUID(), "Vodka Absolut", "Destilados",
                0, 0, BigDecimal.ZERO, BigDecimal.ZERO, null
        );

        byte[] bytes = service.gerarPlanilhaRuptura(List.of(recorrente, pontual, semRegistro));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Falta de Estoque");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(4);

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Produto");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Dias sem estoque");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("Venda perdida");
            assertThat(header.getCell(7).getStringCellValue()).isEqualTo("Situação");

            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo("Recorrente");
            assertThat(sheet.getRow(1).getCell(6).getNumericCellValue()).isEqualTo(59.4);
            assertThat(sheet.getRow(2).getCell(7).getStringCellValue()).isEqualTo("Pontual");
            assertThat(sheet.getRow(3).getCell(7).getStringCellValue()).isEqualTo("Sem registro");
        }
    }
}
