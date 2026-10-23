package com.aleruan.dealos.evidence.application;

import com.aleruan.dealos.counterparty.CheckRecord;
import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.deal.DealCatalog.DealSummary;
import com.aleruan.dealos.evidence.summary.EvidenceLayout;
import com.aleruan.dealos.evidence.summary.SummaryHtml;
import com.aleruan.dealos.evidence.summary.SummaryModel;
import com.aleruan.dealos.platform.audit.AuditRecord;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.audit.ChainVerification;
import com.aleruan.dealos.platform.documents.CompanyProfile;
import com.aleruan.dealos.platform.documents.DocLanguage;
import com.aleruan.dealos.platform.documents.PdfRenderer;
import com.aleruan.dealos.platform.evidence.EvidenceCheck;
import com.aleruan.dealos.platform.evidence.EvidenceCollector;
import com.aleruan.dealos.platform.evidence.EvidenceContributor;
import com.aleruan.dealos.platform.evidence.EvidenceEntry;
import com.aleruan.dealos.platform.evidence.EvidenceFile;
import com.aleruan.dealos.platform.evidence.EvidenceSection;
import com.aleruan.dealos.platform.security.CurrentActor;
import com.aleruan.dealos.platform.web.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Collects a deal's evidence from every module, judges its completeness and exports it as one ZIP:
 * the files under section folders, a summary PDF in English and in the customer's language,
 * manifest.json and SHA256SUMS. The hash of SHA256SUMS goes into the audit log, which anchors the package.
 */
@Service
public class EvidenceService implements com.aleruan.dealos.evidence.EvidenceCatalog {

    public static final String EXPORTED = "EVIDENCE_EXPORTED";
    private static final String DEAL_AUDIT_TYPE = "Deal";
    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");

    /** What the deal page shows: files are listed with their archive paths, nothing is loaded. */
    public record Overview(DealInfo deal, List<Item> items, List<EvidenceCheck> checks, Score score,
                           LastExport lastExport) {
    }

    public record DealInfo(UUID id, String number, String title, String customer, DocLanguage language,
                           BigDecimal contractValue, String currency) {
    }

    public record Item(EvidenceSection section, LocalDate date, String by, String title, String status,
                       BigDecimal amount, String currency, String reference, String text, List<FileRef> files) {
    }

    public record FileRef(String path, String sha256, boolean generated) {
    }

    public record Score(int ok, int warn, int missing) {
        public int total() {
            return ok + warn + missing;
        }
    }

    public record LastExport(Instant at, String by, String sha256sums, Integer files) {
    }

    /** The finished archive in a temporary file; the caller streams and deletes it. */
    public record Export(Path file, String fileName, String sha256sums, int files) {
    }

    /** One deal's assembled evidence before export. */
    record Assembled(DealSummary deal, CounterpartySummary customer, List<EvidenceEntry> entries,
                     List<EvidenceCheck> checks, List<AuditRecord> audit, ChainVerification chain) {
    }

    private final List<EvidenceContributor> contributors;
    private final DealCatalog deals;
    private final CounterpartyLookup counterparties;
    private final AuditService audit;
    private final PdfRenderer pdf;
    private final CompanyProfile company;
    private final CurrentActor actor;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate readOnly;

    EvidenceService(List<EvidenceContributor> contributors, DealCatalog deals, CounterpartyLookup counterparties,
                    AuditService audit, PdfRenderer pdf, CompanyProfile company, CurrentActor actor, ObjectMapper json,
                    Clock clock, PlatformTransactionManager transactions) {
        this.contributors = contributors;
        this.deals = deals;
        this.counterparties = counterparties;
        this.audit = audit;
        this.pdf = pdf;
        this.company = company;
        this.actor = actor;
        this.json = json.copy().enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.clock = clock;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID dealId) {
        Assembled a = assemble(dealId, true);
        Map<EvidenceFile, String> paths = EvidenceLayout.paths(a.entries());
        List<Item> items = a.entries().stream().map(e -> new Item(e.section(), e.date(), e.by(), e.title(), e.status(),
                e.amount(), e.currency(), e.reference(), e.text(),
                e.files().stream().map(f -> new FileRef(paths.get(f), f.sha256(), f.generated())).toList())).toList();
        return new Overview(info(a), items, a.checks(), score(a.checks()), lastExport(dealId));
    }

    /**
     * Not one transaction: the deal is assembled in a short read-only one, each file is read in its own,
     * and the export is recorded at the end. So no entities pile up while hundreds of files are packed
     * and no database connection is held while PDFs render.
     */
    public Export export(UUID dealId) {
        Assembled a = readOnly.execute(status -> assemble(dealId, true));
        Path tmp = null;
        try {
            tmp = Files.createTempFile("evidence-", ".zip");
            List<SummaryModel.PackedFile> packed = new ArrayList<>();
            List<SummaryModel.Item> items = new ArrayList<>();
            List<EvidenceCheck> checks = new ArrayList<>(a.checks());
            Map<EvidenceFile, String> paths = EvidenceLayout.paths(a.entries());
            Instant now = clock.instant();

            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp))) {
                int verified = 0;
                for (EvidenceEntry e : a.entries()) {
                    List<SummaryModel.PackedFile> files = new ArrayList<>();
                    for (EvidenceFile f : e.files()) {
                        String path = paths.get(f);
                        byte[] bytes = Objects.requireNonNull(f.content().get(), path);
                        String sha = sha256(bytes);
                        if (!f.generated() && f.sha256() != null) { // stored without a hash: reported on assembly
                            if (sha.equalsIgnoreCase(f.sha256())) {
                                verified++;
                            } else {
                                checks.add(EvidenceCheck.missing("FILE_INTEGRITY", Map.of("path", path)));
                            }
                        }
                        put(zip, path, bytes, now);
                        SummaryModel.PackedFile p = new SummaryModel.PackedFile(path, sha, bytes.length, f.generated());
                        files.add(p);
                        packed.add(p);
                    }
                    items.add(new SummaryModel.Item(e, files));
                }
                if (verified > 0 && checks.stream().noneMatch(c -> c.code().equals("FILE_INTEGRITY"))) {
                    checks.add(EvidenceCheck.ok("FILES_INTACT", Map.of("count", Integer.toString(verified))));
                }
                checks = sorted(checks);

                SummaryModel model = summaryModel(a, now, checks, items, packed);
                Set<DocLanguage> languages = new LinkedHashSet<>(List.of(DocLanguage.EN, language(a)));
                for (DocLanguage lang : languages) {
                    String path = "summary-" + lang.name().toLowerCase(Locale.ROOT) + ".pdf";
                    byte[] bytes = pdf.render(SummaryHtml.render(model, lang, pdf.logo(), PdfRenderer.FONT));
                    put(zip, path, bytes, now);
                    packed.add(new SummaryModel.PackedFile(path, sha256(bytes), bytes.length, true));
                }

                byte[] manifest = json.writeValueAsBytes(manifest(a, now, checks, items, packed));
                put(zip, "manifest.json", manifest, now);
                packed.add(new SummaryModel.PackedFile("manifest.json", sha256(manifest), manifest.length, true));

                byte[] sums = sha256sums(packed).getBytes(StandardCharsets.UTF_8);
                put(zip, "SHA256SUMS", sums, now);
                String sumsHash = sha256(sums);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("sha256sums", sumsHash);
                payload.put("files", packed.size());
                payload.put("score", score(checks));
                audit.record(DEAL_AUDIT_TYPE, dealId.toString(), EXPORTED, payload);

                String day = LocalDate.ofInstant(now, PRAGUE).toString().replace("-", "");
                return new Export(tmp, "evidence-" + a.deal().number() + "-" + day + ".zip", sumsHash, packed.size());
            }
        } catch (IOException | RuntimeException e) {
            deleteQuietly(tmp);
            if (e instanceof IOException io) {
                throw new UncheckedIOException("Could not build the evidence package", io);
            }
            throw (RuntimeException) e;
        }
    }

    // ---- assembly ----

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceCheck> gaps(UUID dealId) {
        return assemble(dealId, false).checks();
    }

    /** {@code withAudit}: also extract the audit records and verify the whole chain (skipped for gap lists). */
    Assembled assemble(UUID dealId, boolean withAudit) {
        DealSummary deal = deals.find(dealId).orElseThrow(() -> new NotFoundException("Deal " + dealId + " not found"));
        CounterpartySummary customer = counterparties.find(deal.customerId()).orElse(null);
        EvidenceCollector out = new EvidenceCollector();
        for (EvidenceContributor c : contributors) {
            c.contribute(dealId, out);
        }
        parties(deal, customer, out);

        List<AuditRecord> records = !withAudit ? List.of() : out.auditRefs().stream()
                .flatMap(r -> audit.history(r.entityType(), r.entityId()).stream())
                .collect(Collectors.toMap(AuditRecord::seq, r -> r, (x, y) -> x, java.util.TreeMap::new))
                .values().stream().toList();
        ChainVerification chain = withAudit ? audit.verifyChain() : null;
        if (chain == null) {
            // gap lists for analytics: the chain is checked once on the audit page, not per deal
        } else if (chain.valid()) {
            out.ok("AUDIT_CHAIN", Map.of("verified", Long.toString(chain.verifiedRecords())));
        } else {
            out.missing("AUDIT_CHAIN", Map.of("seq", String.valueOf(chain.brokenAtSeq())));
        }
        if (!records.isEmpty()) {
            List<AuditRecord> extract = records;
            out.entry(EvidenceEntry.of(EvidenceSection.AUDIT, "audit-log.json")
                    .reference(extract.get(0).seq() + "–" + extract.get(extract.size() - 1).seq())
                    .file(EvidenceFile.generated("audit-log.json", "application/json", () -> toJson(AuditExtract.of(extract))))
                    .build());
        }

        List<EvidenceEntry> sortedEntries = out.entries().stream()
                .sorted(Comparator.comparingInt(e -> e.section().ordinal())) // stable: keeps each module's order
                .toList();
        Map<EvidenceFile, String> paths = EvidenceLayout.paths(sortedEntries);
        sortedEntries.stream().flatMap(e -> e.files().stream()).filter(f -> !f.generated() && f.sha256() == null)
                .forEach(f -> out.warn("FILE_UNHASHED", Map.of("path", paths.get(f))));
        List<EvidenceEntry> entries = sortedEntries;
        return new Assembled(deal, customer, entries, sorted(out.checks()), records, chain);
    }

    /** Registry checks of the customer and of every subcontractor on the deal's lines. */
    private void parties(DealSummary deal, CounterpartySummary customer, EvidenceCollector out) {
        Map<UUID, String> parties = new LinkedHashMap<>();
        parties.put(deal.customerId(), "CUSTOMER");
        deals.lines(deal.id()).stream().map(DealCatalog.LineSummary::subcontractorId).filter(Objects::nonNull)
                .forEach(id -> parties.putIfAbsent(id, "SUBCONTRACTOR"));

        for (Map.Entry<UUID, String> p : parties.entrySet()) {
            Optional<CounterpartySummary> c = p.getKey().equals(deal.customerId()) && customer != null
                    ? Optional.of(customer) : counterparties.find(p.getKey());
            if (c.isEmpty()) {
                continue;
            }
            CounterpartySummary cp = c.get();
            out.audit("Counterparty", cp.id());
            List<CheckRecord> history = counterparties.checks(cp.id());
            Map<String, String> params = new LinkedHashMap<>();
            params.put("name", cp.legalName());
            params.put("role", p.getValue());
            if (history.isEmpty()) {
                out.missing("COUNTERPARTY_UNCHECKED", params);
                continue;
            }
            // the latest result of each source; the JSON keeps the whole history
            Map<String, CheckRecord> latest = new LinkedHashMap<>();
            history.forEach(r -> latest.putIfAbsent(r.source() + "|" + Objects.toString(r.checkedAccount(), ""), r));
            String worst = latest.values().stream().map(CheckRecord::status)
                    .max(Comparator.comparingInt(EvidenceService::severity)).orElse("GREEN");
            Instant newest = history.get(0).checkedAt();
            params.put("status", worst);
            params.put("date", newest.atZone(PRAGUE).toLocalDate().toString());
            if ("GREEN".equals(worst)) {
                out.ok("COUNTERPARTY_CHECKED", params);
            } else {
                out.warn("COUNTERPARTY_FLAGGED", params);
            }

            String file = cp.legalName() + "-checks.json";
            boolean first = true;
            for (CheckRecord r : latest.values()) {
                String title = cp.legalName() + " — " + r.source()
                        + (r.checkedAccount() == null ? "" : " " + r.checkedAccount());
                EvidenceEntry.Builder b = EvidenceEntry.of(EvidenceSection.CHECKS, title)
                        .date(r.checkedAt().atZone(PRAGUE).toLocalDate())
                        .status(r.status())
                        .reference(r.requestId())
                        .text(text(r));
                if (first) {
                    b.file(EvidenceFile.generated(file, "application/json", () -> toJson(history)));
                    first = false;
                }
                out.entry(b.build());
            }
        }
    }

    private static String text(CheckRecord r) {
        List<String> parts = new ArrayList<>(r.findings());
        if (r.unavailableReason() != null) {
            parts.add(r.unavailableReason());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static int severity(String status) {
        return switch (status) {
            case "RED" -> 2;
            case "YELLOW" -> 1;
            default -> 0;
        };
    }

    // ---- outputs ----

    private SummaryModel summaryModel(Assembled a, Instant now, List<EvidenceCheck> checks,
                                      List<SummaryModel.Item> items, List<SummaryModel.PackedFile> packed) {
        List<AuditRecord> r = a.audit();
        SummaryModel.Audit auditInfo = new SummaryModel.Audit(r.size(),
                r.isEmpty() ? null : r.get(0).seq(), r.isEmpty() ? null : r.get(r.size() - 1).seq(),
                r.isEmpty() ? null : r.get(r.size() - 1).hash(), a.chain().valid(), a.chain().verifiedRecords(),
                a.chain().brokenAtSeq());
        return new SummaryModel(a.deal().number(), a.deal().title(),
                a.customer() == null ? "" : a.customer().legalName(), company.name(),
                deals.contractValue(a.deal().id()), a.deal().currency(), LocalDateTime.ofInstant(now, PRAGUE),
                actor.name(), checks, items, auditInfo, List.copyOf(packed));
    }

    private Map<String, Object> manifest(Assembled a, Instant now, List<EvidenceCheck> checks,
                                         List<SummaryModel.Item> items, List<SummaryModel.PackedFile> packed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", "aleruan-evidence/1");
        m.put("deal", Map.of("id", a.deal().id(), "number", a.deal().number(), "title", a.deal().title(),
                "customer", a.customer() == null ? "" : a.customer().legalName()));
        m.put("generatedAt", now.toString());
        m.put("generatedBy", actor.name());
        m.put("checks", checks);
        List<Map<String, Object>> documents = new ArrayList<>();
        for (SummaryModel.Item i : items) {
            EvidenceEntry e = i.entry();
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("section", e.section());
            d.put("date", e.date());
            d.put("title", e.title());
            d.put("by", e.by());
            d.put("status", e.status());
            d.put("amount", e.amount());
            d.put("currency", e.currency());
            d.put("reference", e.reference());
            d.put("files", i.files().stream().map(SummaryModel.PackedFile::path).toList());
            documents.add(d);
        }
        m.put("documents", documents);
        m.put("files", packed);
        List<AuditRecord> r = a.audit();
        Map<String, Object> au = new LinkedHashMap<>();
        au.put("records", r.size());
        au.put("lastSeq", r.isEmpty() ? null : r.get(r.size() - 1).seq());
        au.put("lastHash", r.isEmpty() ? null : r.get(r.size() - 1).hash());
        au.put("chainValid", a.chain().valid());
        m.put("audit", au);
        return m;
    }

    /** "hash  path" lines, the format "sha256sum -c" reads. */
    static String sha256sums(List<SummaryModel.PackedFile> files) {
        return files.stream().sorted(Comparator.comparing(SummaryModel.PackedFile::path))
                .map(f -> f.sha256() + "  " + f.path() + "\n").collect(Collectors.joining());
    }

    private DealInfo info(Assembled a) {
        return new DealInfo(a.deal().id(), a.deal().number(), a.deal().title(),
                a.customer() == null ? null : a.customer().legalName(), language(a),
                deals.contractValue(a.deal().id()), a.deal().currency());
    }

    private static DocLanguage language(Assembled a) {
        return DocLanguage.forCountry(a.customer() == null ? null : a.customer().country());
    }

    private LastExport lastExport(UUID dealId) {
        List<AuditRecord> history = audit.history(DEAL_AUDIT_TYPE, dealId.toString());
        for (int i = history.size() - 1; i >= 0; i--) {
            AuditRecord r = history.get(i);
            if (EXPORTED.equals(r.action())) {
                String sums = null;
                Integer files = null;
                try {
                    var node = json.readTree(r.payload());
                    sums = node.path("sha256sums").asText(null);
                    files = node.has("files") ? node.get("files").asInt() : null;
                } catch (JsonProcessingException | IllegalArgumentException e) {
                    // an unreadable payload still tells when and by whom
                }
                return new LastExport(r.occurredAt(), r.actor(), sums, files);
            }
        }
        return null;
    }

    static Score score(List<EvidenceCheck> checks) {
        int ok = 0;
        int warn = 0;
        int missing = 0;
        for (EvidenceCheck c : checks) {
            switch (c.level()) {
                case OK -> ok++;
                case WARN -> warn++;
                case MISSING -> missing++;
            }
        }
        return new Score(ok, warn, missing);
    }

    /** Gaps first, then things to check, then what is in order; stable within a level. */
    static List<EvidenceCheck> sorted(List<EvidenceCheck> checks) {
        return checks.stream().sorted(Comparator.comparingInt(c -> -c.level().ordinal())).toList();
    }

    private byte[] toJson(Object value) {
        try {
            return json.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write JSON for the evidence package", e);
        }
    }

    private static void put(ZipOutputStream zip, String path, byte[] bytes, Instant at) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(at.toEpochMilli());
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteQuietly(Path p) {
        if (p == null) {
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // the OS cleans its temp folder
        }
    }
}
