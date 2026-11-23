package valhalla.core.stock.app.modules.estoque.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.MotivoPerda;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;
import valhalla.core.stock.app.shared.export.PoiExportadorExcel;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MovimentacaoExportacaoServiceTest {

    private final MovimentacaoExportacaoService service = new MovimentacaoExportacaoService(new PoiExportadorExcel());

    @Test
    void geraPlanilhaExcelComMovimentacoes() throws Exception {
        LocalDateTime agora = LocalDateTime.of(2026, 11, 14, 21, 30);
        MovimentacaoResponseDto entrada = new MovimentacaoResponseDto(
                1L, "Lucas", UUID.randomUUID(), "Brahma Lata", "un",
                TipoMovimentacaoEstoque.ENTRADA, new BigDecimal("24"),
                new BigDecimal("10"), new BigDecimal("34"),
                "Reposição (2 fardo(s))", null, "LOTE-01", "NF-123", null, null, agora, List.of()
        );
        MovimentacaoResponseDto perda = new MovimentacaoResponseDto(
                2L, "Maria", UUID.randomUUID(), "Vodka Absolut", "ML",
                TipoMovimentacaoEstoque.PERDA, new BigDecimal("100"),
                new BigDecimal("1000"), new BigDecimal("900"),
                "Quebra", MotivoPerda.QUEBRA, null, null, null, null, agora, List.of()
        );

        byte[] bytes = service.gerarPlanilhaExcel(List.of(entrada, perda));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Movimentações");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Data/Hora");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Produto");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Tipo");

            Row linha1 = sheet.getRow(1);
            assertThat(linha1.getCell(1).getStringCellValue()).isEqualTo("Brahma Lata");
            assertThat(linha1.getCell(2).getStringCellValue()).isEqualTo("Entrada");
            assertThat(linha1.getCell(3).getNumericCellValue()).isEqualTo(24.0);
            assertThat(linha1.getCell(4).getStringCellValue()).isEqualTo("un");
            assertThat(linha1.getCell(6).getNumericCellValue()).isEqualTo(34.0);
            assertThat(linha1.getCell(8).getStringCellValue()).isEqualTo("LOTE-01");
            assertThat(linha1.getCell(9).getStringCellValue()).isEqualTo("NF-123");

            Row linha2 = sheet.getRow(2);
            assertThat(linha2.getCell(2).getStringCellValue()).isEqualTo("Perda");
            assertThat(linha2.getCell(3).getNumericCellValue()).isEqualTo(100.0);
        }
    }
}
