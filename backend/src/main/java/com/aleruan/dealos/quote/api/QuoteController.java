package com.aleruan.dealos.quote.api;

import com.aleruan.dealos.quote.application.QuoteService;
import com.aleruan.dealos.quote.domain.Quote;
import com.aleruan.dealos.quote.domain.QuoteLanguage;
import com.aleruan.dealos.quote.domain.QuoteSummary;
import com.aleruan.dealos.quote.domain.VatMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class QuoteController {

    record IssueBody(QuoteLanguage language, @Min(1) @Max(365) Integer validDays, VatMode vatMode,
                     @DecimalMin("0") @DecimalMax("99.99") BigDecimal vatRate, @Size(max = 4000) String terms) {
    }

    record QuoteView(UUID id, String number, int version, QuoteLanguage language, VatMode vatMode, BigDecimal vatRate,
                     String currency, BigDecimal subtotal, BigDecimal vatAmount, BigDecimal total, LocalDate issuedOn,
                     LocalDate validUntil, Instant issuedAt, String issuedBy, String pdfSha256) {

        static QuoteView of(QuoteSummary s) {
            return new QuoteView(s.id(), s.displayNumber(), s.version(), s.language(), s.vatMode(), s.vatRate(),
                    s.currency(), s.subtotal(), s.vatAmount(), s.total(), s.issuedOn(), s.validUntil(), s.issuedAt(),
                    s.issuedBy(), s.pdfSha256());
        }

        static QuoteView of(Quote q) {
            return new QuoteView(q.getId(), q.displayNumber(), q.getVersion(), q.getLanguage(), q.getVatMode(),
                    q.getVatRate(), q.getCurrency(), q.getSubtotal(), q.getVatAmount(), q.getTotal(), q.getIssuedOn(),
                    q.getValidUntil(), q.getIssuedAt(), q.getIssuedBy(), q.getPdfSha256());
        }
    }

    private final QuoteService service;

    QuoteController(QuoteService service) {
        this.service = service;
    }

    @GetMapping("/deals/{dealId}/quotes")
    List<QuoteView> list(@PathVariable UUID dealId) {
        return service.list(dealId).stream().map(QuoteView::of).toList();
    }

    @GetMapping("/deals/{dealId}/quotes/defaults")
    QuoteService.Defaults defaults(@PathVariable UUID dealId) {
        return service.defaults(dealId);
    }

    @PostMapping("/deals/{dealId}/quotes")
    QuoteView issue(@PathVariable UUID dealId, @Validated @RequestBody IssueBody body) {
        return QuoteView.of(service.issue(dealId, new QuoteService.IssueRequest(body.language(), body.validDays(),
                body.vatMode(), body.vatRate(), body.terms())));
    }

    @GetMapping("/quotes/{id}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        Quote q = service.get(id);
        String file = q.displayNumber().replace('/', '-') + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file).build().toString())
                .header(HttpHeaders.ETAG, "\"" + q.getPdfSha256() + "\"")
                .body(q.getPdf());
    }

    @GetMapping(value = "/quotes/{id}/html", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    String html(@PathVariable UUID id) {
        return service.html(id);
    }
}
