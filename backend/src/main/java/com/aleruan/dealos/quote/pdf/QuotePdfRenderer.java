package com.aleruan.dealos.quote.pdf;

import com.aleruan.dealos.quote.domain.QuoteDocument;
import com.aleruan.dealos.quote.domain.QuoteHtml;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Turns a quote into a PDF with the brand font (IBM Plex Sans, embedded and subset) and logo. */
@Component
public class QuotePdfRenderer {

    static final String FONT = "Plex";

    private final byte[] regular = resource("pdf/fonts/IBMPlexSans-Regular.ttf");
    private final byte[] semiBold = resource("pdf/fonts/IBMPlexSans-SemiBold.ttf");
    private final String logo = "data:image/png;base64," + Base64.getEncoder().encodeToString(resource("pdf/logo.png"));

    public String html(QuoteDocument document) {
        return QuoteHtml.render(document, logo, FONT);
    }

    public byte[] render(QuoteDocument document) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> new ByteArrayInputStream(regular), FONT, 400, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> new ByteArrayInputStream(semiBold), FONT, 600, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.withHtmlContent(html(document), null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render quote " + document.displayNumber(), e);
        }
    }

    private static byte[] resource(String path) {
        try (InputStream in = QuotePdfRenderer.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
