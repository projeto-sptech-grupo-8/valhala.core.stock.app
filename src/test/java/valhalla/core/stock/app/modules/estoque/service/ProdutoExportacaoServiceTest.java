package valhalla.core.stock.app.modules.estoque.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.shared.export.PoiExportadorExcel;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProdutoExportacaoServiceTest {

    private final ProdutoExportacaoService service = new ProdutoExportacaoService(new PoiExportadorExcel());

    @Test
    void geraPlanilhaExcelComProdutosPadraoFracionadoEDrink() throws Exception {
        LocalDateTime agora = LocalDateTime.now();
        ProdutoResponseDto lata = new ProdutoResponseDto(
                UUID.randomUUID(), "PRD-0001", "Brahma Lata", ProdutoTipo.PADRAO,
                1, "Cervejas", "un", new BigDecimal("2.50"), new BigDecimal("5.00"),
                new BigDecimal("50.00"), false, null, true, 12, true,
                new BigDecimal("24"), new BigDecimal("12"), agora, agora
        );
        ProdutoResponseDto vodka = new ProdutoResponseDto(
                UUID.randomUUID(), "PRD-0002", "Vodka Absolut", ProdutoTipo.PADRAO,
                2, "Destilados", "garrafa", new BigDecimal("80.00"), new BigDecimal("120.00"),
                new BigDecimal("33.33"), true, new BigDecimal("1000"), false, null, true,
                new BigDecimal("2500"), new BigDecimal("1000"), agora, agora
        );
        ProdutoResponseDto drink = new ProdutoResponseDto(
                UUID.randomUUID(), "PRD-0003", "Gin Tônica", ProdutoTipo.DRINK,
                3, "Drinks", "copo", BigDecimal.ZERO, new BigDecimal("28.00"),
                null, false, null, false, null, true,
                null, null, agora, agora
        );

        byte[] bytes = service.gerarPlanilhaExcel(List.of(lata, vodka, drink));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Produtos");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(4);

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("SKU");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("Fardo");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("Unid. por Fardo");

            Row linhaLata = sheet.getRow(1);
            assertThat(linhaLata.getCell(1).getStringCellValue()).isEqualTo("Brahma Lata");
            assertThat(linhaLata.getCell(5).getStringCellValue()).isEqualTo("Sim");
            assertThat(linhaLata.getCell(6).getNumericCellValue()).isEqualTo(12.0);
            assertThat(linhaLata.getCell(9).getNumericCellValue()).isEqualTo(24.0);

            Row linhaVodka = sheet.getRow(2);
            assertThat(linhaVodka.getCell(7).getStringCellValue()).isEqualTo("Sim");
            assertThat(linhaVodka.getCell(9).getNumericCellValue()).isEqualTo(2.5);
            assertThat(linhaVodka.getCell(10).getNumericCellValue()).isEqualTo(1.0);

            Row linhaDrink = sheet.getRow(3);
            assertThat(linhaDrink.getCell(2).getStringCellValue()).isEqualTo("Drink");
            assertThat(linhaDrink.getCell(9).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            assertThat(linhaDrink.getCell(11).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
        }
    }
}
