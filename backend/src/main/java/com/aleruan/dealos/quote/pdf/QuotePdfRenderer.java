package com.aleruan.dealos.quote.pdf;

import com.aleruan.dealos.platform.documents.PdfRenderer;
import com.aleruan.dealos.quote.domain.QuoteDocument;
import com.aleruan.dealos.quote.domain.QuoteHtml;
import org.springframework.stereotype.Component;

/** Quotes and change order sheets as HTML (preview) and PDF. */
@Component
public class QuotePdfRenderer {

    private final PdfRenderer pdf;

    QuotePdfRenderer(PdfRenderer pdf) {
        this.pdf = pdf;
    }

    public String html(QuoteDocument document) {
        return QuoteHtml.render(document, pdf.logo(), PdfRenderer.FONT);
    }

    public byte[] render(QuoteDocument document) {
        return pdf.render(html(document));
    }
}
