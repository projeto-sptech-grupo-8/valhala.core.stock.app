package valhalla.core.stock.app.shared.export;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class RespostaArquivoExcel {

    public static final MediaType MEDIA_TYPE_EXCEL = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private RespostaArquivoExcel() {
    }

    public static ResponseEntity<byte[]> anexo(String prefixoArquivo, byte[] conteudo) {
        String nomeArquivo = prefixoArquivo + "-" + LocalDateTime.now().format(FORMATTER) + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MEDIA_TYPE_EXCEL);
        headers.setContentDisposition(ContentDisposition.attachment().filename(nomeArquivo).build());
        return ResponseEntity.ok().headers(headers).body(conteudo);
    }
}
