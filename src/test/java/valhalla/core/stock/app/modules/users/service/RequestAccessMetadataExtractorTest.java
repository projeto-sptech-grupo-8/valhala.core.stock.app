package valhalla.core.stock.app.modules.users.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class RequestAccessMetadataExtractorTest {

    private final RequestAccessMetadataExtractor extractor = new RequestAccessMetadataExtractor();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void deveRetornarFallbackQuandoNaoHouverRequisicaoNoContexto() {
        RequestContextHolder.resetRequestAttributes();

        RequestAccessMetadataExtractor.AccessMetadata metadata = extractor.extractCurrent();

        assertThat(metadata.sourceIp()).isNull();
        assertThat(metadata.device()).isEqualTo("Desconhecido");
        assertThat(metadata.userAgent()).isNull();
    }

    @Test
    void deveRetornarFallbackQuandoRequisicaoForNula() {
        RequestAccessMetadataExtractor.AccessMetadata metadata = extractor.extractFrom(null);

        assertThat(metadata.sourceIp()).isNull();
        assertThat(metadata.device()).isEqualTo("Desconhecido");
        assertThat(metadata.userAgent()).isNull();
    }

    @Test
    void deveExtrairPrimeiroIpDeXForwardedForEDispositivoWindowsChrome() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.24, 192.168.0.1");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestAccessMetadataExtractor.AccessMetadata metadata = extractor.extractCurrent();

        assertThat(metadata.sourceIp()).isEqualTo("10.0.0.24");
        assertThat(metadata.device()).isEqualTo("Windows · Chrome");
        assertThat(metadata.userAgent()).contains("Chrome/130.0.0.0");
    }

    @Test
    void deveNormalizarLoopbackIpv6ParaIpv4() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("0:0:0:0:0:0:0:1");

        RequestAccessMetadataExtractor.AccessMetadata ipv6Long = extractor.extractFrom(request);
        assertThat(ipv6Long.sourceIp()).isEqualTo("127.0.0.1");

        request.setRemoteAddr("::1");
        RequestAccessMetadataExtractor.AccessMetadata ipv6Short = extractor.extractFrom(request);
        assertThat(ipv6Short.sourceIp()).isEqualTo("127.0.0.1");
    }

    @Test
    void deveIdentificarSistemasOperacionaisENavegadoresComuns() {
        assertThat(extractor.summarizeDevice(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"))
                .isEqualTo("Windows · Edge");
        assertThat(extractor.summarizeDevice(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36 OPR/114.0.0.0"))
                .isEqualTo("Windows · Opera");
        assertThat(extractor.summarizeDevice(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1"))
                .isEqualTo("iPhone · Safari");
        assertThat(extractor.summarizeDevice(
                "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1"))
                .isEqualTo("iPad · Safari");
        assertThat(extractor.summarizeDevice(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36"))
                .isEqualTo("Android · Chrome");
        assertThat(extractor.summarizeDevice(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 Safari/605.1.15"))
                .isEqualTo("macOS · Safari");
        assertThat(extractor.summarizeDevice(
                "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0"))
                .isEqualTo("Linux · Firefox");
        assertThat(extractor.summarizeDevice("curl/8.7.1")).isEqualTo("Desconhecido");
        assertThat(extractor.summarizeDevice("   ")).isEqualTo("Desconhecido");
        assertThat(extractor.summarizeDevice("CustomClient (Linux)")).isEqualTo("Linux");
        assertThat(extractor.summarizeDevice("Chrome/130.0.0.0")).isEqualTo("Chrome");
    }

    @Test
    void deveTruncarUserAgentEIpMuitoLongos() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1".repeat(80));
        request.addHeader("User-Agent", "A".repeat(600));

        RequestAccessMetadataExtractor.AccessMetadata metadata = extractor.extractFrom(request);

        assertThat(metadata.sourceIp()).hasSize(45);
        assertThat(metadata.userAgent()).hasSize(512);
    }
}
