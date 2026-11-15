package valhalla.core.stock.app.modules.estoque.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ProdutoExportacaoService {

    private static final String[] COLUNAS = {
            "SKU",
            "Nome",
            "Tipo",
            "Categoria",
            "Unidade",
            "Fardo",
            "Unid. por Fardo",
            "Fracionado",
            "Volume Embalagem (ml)",
            "Quantidade em Estoque",
            "Estoque Mínimo",
            "Preço de Custo",
            "Preço de Venda",
            "Margem (%)",
            "Situação",
            "Código de Barras"
    };

    public byte[] gerarPlanilhaExcel(List<ProdutoResponseDto> produtos) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Produtos");

            CellStyle headerStyle = criarEstiloCabecalho(workbook);
            DataFormat dataFormat = workbook.createDataFormat();

            CellStyle moedaStyle = workbook.createCellStyle();
            moedaStyle.setDataFormat(dataFormat.getFormat("R$ #,##0.00"));

            CellStyle decimalStyle = workbook.createCellStyle();
            decimalStyle.setDataFormat(dataFormat.getFormat("#,##0.###"));

            CellStyle inteiroStyle = workbook.createCellStyle();
            inteiroStyle.setDataFormat(dataFormat.getFormat("#,##0"));

            Row header = sheet.createRow(0);
            for (int i = 0; i < COLUNAS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(COLUNAS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (ProdutoResponseDto produto : produtos) {
                Row row = sheet.createRow(rowIdx++);
                preencherLinha(row, produto, moedaStyle, decimalStyle, inteiroStyle);
            }

            for (int i = 0; i < COLUNAS.length; i++) {
                sheet.autoSizeColumn(i);
                int largura = Math.min(sheet.getColumnWidth(i) + 512, 12000);
                sheet.setColumnWidth(i, largura);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException excecao) {
            throw new IllegalStateException("Não foi possível gerar a planilha Excel de produtos", excecao);
        }
    }

    private void preencherLinha(
            Row row,
            ProdutoResponseDto produto,
            CellStyle moedaStyle,
            CellStyle decimalStyle,
            CellStyle inteiroStyle
    ) {
        boolean isDrink = produto.tipo() == ProdutoTipo.DRINK;
        BigDecimal estoque = converterParaUnidadeExibicao(produto, produto.quantidadeEstoque());
        BigDecimal estoqueMinimo = converterParaUnidadeExibicao(produto, produto.estoqueMinimo());

        row.createCell(0).setCellValue(textoOuVazio(produto.sku()));
        row.createCell(1).setCellValue(textoOuVazio(produto.nome()));
        row.createCell(2).setCellValue(isDrink ? "Drink" : "Padrão");
        row.createCell(3).setCellValue(textoOuVazio(produto.categoriaNome()));
        row.createCell(4).setCellValue(textoOuVazio(produto.unidadeMedida()));
        row.createCell(5).setCellValue(Boolean.TRUE.equals(produto.fardo()) ? "Sim" : "Não");
        escreverInteiro(row.createCell(6), produto.unidadesPorFardo(), inteiroStyle);
        row.createCell(7).setCellValue(Boolean.TRUE.equals(produto.fracionado()) ? "Sim" : "Não");
        escreverDecimal(row.createCell(8), produto.volumeEmbalagemMl(), decimalStyle);
        escreverDecimal(row.createCell(9), isDrink ? null : estoque, decimalStyle);
        escreverDecimal(row.createCell(10), isDrink ? null : estoqueMinimo, decimalStyle);
        escreverDecimal(row.createCell(11), isDrink ? null : produto.precoCusto(), moedaStyle);
        escreverDecimal(row.createCell(12), produto.precoVenda(), moedaStyle);
        escreverDecimal(row.createCell(13), produto.margemPercentual(), decimalStyle);
        row.createCell(14).setCellValue(Boolean.TRUE.equals(produto.ativo()) ? "Ativo" : "Inativo");
        row.createCell(15).setCellValue(textoOuVazio(produto.codigoBarras()));
    }

    private BigDecimal converterParaUnidadeExibicao(ProdutoResponseDto produto, BigDecimal valorBase) {
        if (valorBase == null) {
            return null;
        }
        if (Boolean.TRUE.equals(produto.fracionado())
                && produto.volumeEmbalagemMl() != null
                && produto.volumeEmbalagemMl().signum() > 0) {
            return valorBase.divide(produto.volumeEmbalagemMl(), 3, RoundingMode.HALF_UP);
        }
        return valorBase;
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

    private void escreverDecimal(Cell cell, BigDecimal valor, CellStyle style) {
        if (valor == null) {
            return;
        }
        cell.setCellValue(valor.doubleValue());
        cell.setCellStyle(style);
    }

    private void escreverInteiro(Cell cell, Integer valor, CellStyle style) {
        if (valor == null) {
            return;
        }
        cell.setCellValue(valor);
        cell.setCellStyle(style);
    }

    private String textoOuVazio(String valor) {
        return valor == null ? "" : valor;
    }
}
