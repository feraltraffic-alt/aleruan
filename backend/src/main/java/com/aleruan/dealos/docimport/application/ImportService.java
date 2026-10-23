package com.aleruan.dealos.docimport.application;

import com.aleruan.dealos.counterparty.CounterpartyIntake;
import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.docimport.domain.DocType;
import com.aleruan.dealos.docimport.domain.ImportDocument;
import com.aleruan.dealos.docimport.domain.ImportRepository;
import com.aleruan.dealos.docimport.domain.ImportStatus;
import com.aleruan.dealos.docimport.reader.DocumentReader;
import com.aleruan.dealos.docimport.reader.Extraction;
import com.aleruan.dealos.invoice.SupplierInvoices;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.documents.CompanyProfile;
import com.aleruan.dealos.platform.files.FileInfo;
import com.aleruan.dealos.platform.files.FileStore;
import com.aleruan.dealos.platform.security.CurrentActor;
import com.aleruan.dealos.platform.web.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Documents in, data out: upload, reading by Claude (one document at a time, by the worker), review, and
 * on confirmation the counterparty, the received invoice or the deal's contract paper. Nothing is created
 * from a document until a person has confirmed what was read.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    public static final String AUDIT_TYPE = "ImportDocument";
    public static final String FILE_OWNER = "ImportDocument";
    private static final Duration STUCK = Duration.ofMinutes(15);

    /** What the person confirmed, after correcting what was read. */
    public record Review(DocType type, String documentNumber, LocalDate issueDate, LocalDate taxableDate,
                         LocalDate dueDate, String currency, String variableSymbol, BigDecimal netAmount,
                         BigDecimal vatAmount, BigDecimal totalAmount, String vatNote, PartyInput party,
                         UUID counterpartyId, String role, UUID dealId) {
    }

    public record PartyInput(String name, String country, String companyId, String vatNumber, String address,
                             String bankAccount) {
    }

    public record Outcome(String resultType, UUID resultId, UUID counterpartyId, boolean counterpartyCreated) {
    }

    /** A document with what was read and what the reviewer should know. */
    public record Detail(ImportDocument document, Extraction read, Review confirmed, List<ImportChecks.Warning> warnings,
                         Suggestion suggestion) {
    }

    /** What the review screen proposes: the other side of the document, whether it is known already. */
    public record Suggestion(DocType type, Extraction.Party party, String role, CounterpartySummary match,
                             boolean duplicateInvoice) {
    }

    public record Upload(ImportDocument document, boolean duplicate) {
    }

    public record Queue(boolean readerConfigured, String model, long queued, long processing, long failed,
                        long toReview) {
    }

    private final ImportRepository documents;
    private final FileStore files;
    private final ObjectProvider<DocumentReader> reader;
    private final CounterpartyIntake intake;
    private final CounterpartyLookup counterparties;
    private final SupplierInvoices supplierInvoices;
    private final DealCatalog deals;
    private final CompanyProfile company;
    private final AuditService audit;
    private final CurrentActor actor;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate tx;

    ImportService(ImportRepository documents, FileStore files, ObjectProvider<DocumentReader> reader,
                  CounterpartyIntake intake, CounterpartyLookup counterparties, SupplierInvoices supplierInvoices,
                  DealCatalog deals, CompanyProfile company, AuditService audit, CurrentActor actor, ObjectMapper json,
                  Clock clock, PlatformTransactionManager transactions) {
        this.documents = documents;
        this.files = files;
        this.reader = reader;
        this.intake = intake;
        this.counterparties = counterparties;
        this.supplierInvoices = supplierInvoices;
        this.deals = deals;
        this.company = company;
        this.audit = audit;
        this.actor = actor;
        this.json = json;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    // ---- upload and queue ----

    /** Stores the file and queues it for reading; the same file uploaded twice is not read twice. */
    @Transactional
    public Upload upload(String fileName, byte[] data) {
        if (data != null && data.length > 0) {
            Optional<ImportDocument> same = documents.findFirstBySha256AndStatusNot(sha256(data), ImportStatus.REJECTED);
            if (same.isPresent()) {
                return new Upload(same.get(), true);
            }
        }
        UUID id = UUID.randomUUID();
        FileInfo f = files.store(FILE_OWNER, id, fileName, data);
        ImportDocument doc = documents.save(new ImportDocument(id, f.id(), f.fileName(), f.contentType(), f.sha256(),
                clock.instant(), actor.name()));
        audit.record(AUDIT_TYPE, id.toString(), "UPLOADED", Map.of("fileName", f.fileName(), "sha256", f.sha256()));
        return new Upload(doc, false);
    }

    @Transactional(readOnly = true)
    public List<ImportDocument> list(ImportStatus status) {
        return documents.search(status);
    }

    @Transactional(readOnly = true)
    public Queue queue() {
        DocumentReader r = reader.getIfAvailable();
        return new Queue(r != null, r == null ? null : r.model(), documents.countByStatus(ImportStatus.QUEUED),
                documents.countByStatus(ImportStatus.PROCESSING), documents.countByStatus(ImportStatus.FAILED),
                documents.countByStatus(ImportStatus.EXTRACTED));
    }

    @Transactional(readOnly = true)
    public ImportDocument get(UUID id) {
        return documents.findById(id).orElseThrow(() -> new NotFoundException("Document " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public FileStore.Content file(UUID id) {
        return files.get(get(id).getFileId());
    }

    @Transactional
    public ImportDocument retry(UUID id) {
        ImportDocument doc = get(id);
        if (doc.getStatus() == ImportStatus.PROCESSING) {
            throw new IllegalStateException("The document is being read right now");
        }
        doc.requeue();
        audit.record(AUDIT_TYPE, id.toString(), "REQUEUED", Map.of());
        return doc;
    }

    @Transactional
    public ImportDocument reject(UUID id, String reason) {
        ImportDocument doc = get(id);
        doc.reject(reason == null || reason.isBlank() ? null : reason.strip(), clock.instant(), actor.name());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", reason);
        audit.record(AUDIT_TYPE, id.toString(), "REJECTED", payload);
        return doc;
    }

    // ---- reading (worker) ----

    /** Reads the oldest queued document; false when there is nothing to read or no reader is configured. */
    public boolean processNext() {
        DocumentReader r = reader.getIfAvailable();
        if (r == null) {
            return false;
        }
        tx.executeWithoutResult(s -> documents.requeueStale(clock.instant().minus(STUCK)));
        UUID id = tx.execute(s -> documents.lockNextQueued().map(next -> {
            get(next).start(clock.instant());
            return next;
        }).orElse(null));
        if (id == null) {
            return false;
        }
        ImportDocument doc = get(id);
        try {
            DocumentReader.Result result = r.read(files.data(doc.getFileId()), doc.getContentType(), doc.getFileName());
            Extraction e = result.extraction();
            List<ImportChecks.Warning> warnings = ImportChecks.check(e, ours());
            DocType type = type(e.documentType());
            tx.executeWithoutResult(s -> {
                get(id).extracted(type, write(Map.of("read", e)), write(warnings), result.model(), result.inputTokens(),
                        result.outputTokens(), clock.instant());
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", type);
                payload.put("model", result.model());
                payload.put("inputTokens", result.inputTokens());
                payload.put("outputTokens", result.outputTokens());
                payload.put("warnings", warnings.size());
                audit.recordAs("claude", AUDIT_TYPE, id.toString(), "READ", payload);
            });
        } catch (RuntimeException ex) {
            log.warn("Reading {} failed: {}", doc.getFileName(), ex.toString());
            String message = ex instanceof DocumentReader.ReadFailed ? ex.getMessage() : "Reading failed: " + ex;
            tx.executeWithoutResult(s -> {
                ImportDocument d = get(id);
                d.failed(message, clock.instant());
                audit.recordAs("claude", AUDIT_TYPE, id.toString(), "READ_FAILED",
                        Map.of("error", message, "attempt", d.getAttempts()));
            });
        }
        return true;
    }

    // ---- review ----

    @Transactional(readOnly = true)
    public Detail detail(UUID id) {
        ImportDocument doc = get(id);
        Map<String, Object> stored = read(doc.getExtracted());
        Extraction e = stored.containsKey("read") ? json.convertValue(stored.get("read"), Extraction.class) : null;
        Review confirmed = stored.containsKey("confirmed") ? json.convertValue(stored.get("confirmed"), Review.class) : null;
        List<ImportChecks.Warning> warnings = doc.getWarnings() == null ? List.of()
                : json.convertValue(read(doc.getWarnings(), true), new TypeReference<List<ImportChecks.Warning>>() { });
        Extraction basis = e != null ? e : Extraction.blank();
        DocType type = e == null ? DocType.OTHER : type(e.documentType());
        Extraction.Party party = ImportChecks.otherSide(basis, ours());
        CounterpartySummary match = intake.match(blankToNull(party.country()), blankToNull(party.companyId()),
                blankToNull(party.vatNumber())).orElse(null);
        boolean duplicate = match != null && isInvoice(type) && supplierInvoices.exists(match.id(), basis.documentNumber());
        return new Detail(doc, e, confirmed, warnings, new Suggestion(type, party, defaultRole(type), match, duplicate));
    }

    /** What was read from the document, for lists; null when it has not been read. */
    public Extraction readOf(ImportDocument doc) {
        Map<String, Object> stored = read(doc.getExtracted());
        return stored.containsKey("read") ? json.convertValue(stored.get("read"), Extraction.class) : null;
    }

    /**
     * Creates what the reviewed document stands for. The counterparty and the record are created in one
     * transaction; registry and bank account checks run after it, outside, because registries can be slow.
     */
    public Outcome confirm(UUID id, Review r) {
        if (r == null || r.type() == null) {
            throw new IllegalArgumentException("Say what the document is");
        }
        record Created(Outcome outcome, UUID supplierInvoiceId, String bankAccount) {
        }
        Created created = tx.execute(s -> {
            ImportDocument doc = get(id);
            if (!doc.getStatus().isOpen() || doc.getStatus() == ImportStatus.PROCESSING) {
                throw new IllegalStateException("The document is " + doc.getStatus());
            }
            if (r.dealId() != null && deals.find(r.dealId()).isEmpty()) {
                throw new IllegalArgumentException("Deal " + r.dealId() + " does not exist");
            }
            String role = role(r);
            UUID partyId = null;
            boolean partyCreated = false;
            if (role != null) {
                if (r.counterpartyId() != null) {
                    CounterpartySummary c = counterparties.find(r.counterpartyId())
                            .orElseThrow(() -> new IllegalArgumentException("Counterparty " + r.counterpartyId() + " does not exist"));
                    intake.ensureRole(c.id(), role);
                    partyId = c.id();
                } else {
                    PartyInput p = r.party();
                    if (p == null || blank(p.name()) || blank(p.country())) {
                        throw new IllegalArgumentException("The counterparty needs a name and a country");
                    }
                    String country = p.country().strip().toUpperCase(Locale.ROOT);
                    Optional<CounterpartySummary> known = intake.match(country, blankToNull(p.companyId()),
                            blankToNull(p.vatNumber()));
                    if (known.isPresent()) {
                        intake.ensureRole(known.get().id(), role);
                        partyId = known.get().id();
                    } else {
                        partyId = intake.create(new CounterpartyIntake.NewParty(p.name().strip(), country,
                                blankToNull(p.companyId()), blankToNull(p.vatNumber()), blankToNull(p.address()), role)).id();
                        partyCreated = true;
                    }
                }
            }

            String resultType = "ARCHIVED";
            UUID resultId = null;
            UUID invoiceId = null;
            String bankAccount = r.party() == null ? null : blankToNull(r.party().bankAccount());
            if (registersSupplierInvoice(r, role)) {
                if (blank(r.documentNumber()) || r.totalAmount() == null || blank(r.currency())) {
                    throw new IllegalArgumentException("A received invoice needs its number, total and currency");
                }
                invoiceId = supplierInvoices.register(new SupplierInvoices.NewSupplierInvoice(partyId, r.dealId(),
                        r.documentNumber(), r.variableSymbol(), r.issueDate(), r.taxableDate(), r.dueDate(), r.currency(),
                        r.netAmount(), r.vatAmount(), r.totalAmount(), r.vatNote(), bankAccount, doc.getFileName(),
                        files.data(doc.getFileId()), id));
                resultType = "SUPPLIER_INVOICE";
                resultId = invoiceId;
            } else if (r.type() == DocType.CONTRACT && r.dealId() != null) {
                resultId = deals.attachDocument(r.dealId(), doc.getFileName(), files.data(doc.getFileId()));
                resultType = "DEAL_DOCUMENT";
            } else if (partyId != null) {
                resultType = "COUNTERPARTY";
                resultId = partyId;
            }

            Map<String, Object> stored = new LinkedHashMap<>(read(doc.getExtracted()));
            stored.put("confirmed", r);
            doc.confirm(r.type(), write(stored), r.dealId(), resultType, resultId, clock.instant(), actor.name());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", r.type());
            payload.put("result", resultType);
            payload.put("resultId", resultId);
            payload.put("counterpartyId", partyId);
            payload.put("counterpartyCreated", partyCreated);
            payload.put("dealId", r.dealId());
            audit.record(AUDIT_TYPE, id.toString(), "CONFIRMED", payload);
            return new Created(new Outcome(resultType, resultId, partyId, partyCreated), invoiceId, bankAccount);
        });

        Outcome outcome = created.outcome();
        if (outcome.counterpartyCreated()) {
            intake.runChecks(outcome.counterpartyId());
        }
        if (created.supplierInvoiceId() != null && created.bankAccount() != null) {
            String status = intake.checkAccount(outcome.counterpartyId(), created.bankAccount());
            supplierInvoices.recordAccountCheck(created.supplierInvoiceId(), status);
        }
        return outcome;
    }

    // ---- helpers ----

    private static boolean registersSupplierInvoice(Review r, String role) {
        boolean supplierSide = role != null && !"CUSTOMER".equals(role);
        return r.type() == DocType.SUPPLIER_INVOICE || r.type() == DocType.CREDIT_NOTE && supplierSide;
    }

    /** The role the other side gets; none for a document with no counterparty. */
    private static String role(Review r) {
        boolean hasParty = r.counterpartyId() != null || r.party() != null && !blank(r.party().name());
        if (!hasParty) {
            if (r.type() == DocType.SUPPLIER_INVOICE || r.type() == DocType.CUSTOMER_INVOICE
                    || r.type() == DocType.CREDIT_NOTE) {
                throw new IllegalArgumentException("An invoice needs its counterparty");
            }
            return null;
        }
        String role = blank(r.role()) ? defaultRole(r.type()) : r.role().strip().toUpperCase(Locale.ROOT);
        if (role == null) {
            throw new IllegalArgumentException("Say whether the counterparty is a customer, subcontractor, supplier or carrier");
        }
        if (!Set.of("CUSTOMER", "SUBCONTRACTOR", "SUPPLIER", "CARRIER").contains(role)) {
            throw new IllegalArgumentException("Unknown role " + role);
        }
        return role;
    }

    static String defaultRole(DocType type) {
        return switch (type) {
            case SUPPLIER_INVOICE -> "SUBCONTRACTOR";
            case CUSTOMER_INVOICE, CONTRACT -> "CUSTOMER";
            case CMR -> "CARRIER";
            case CREDIT_NOTE, OTHER -> null;
        };
    }

    private static boolean isInvoice(DocType t) {
        return t == DocType.SUPPLIER_INVOICE || t == DocType.CREDIT_NOTE;
    }

    private static DocType type(String s) {
        try {
            return s == null ? DocType.OTHER : DocType.valueOf(s.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DocType.OTHER;
        }
    }

    private Set<String> ours() {
        Set<String> ids = new LinkedHashSet<>();
        if (!blank(company.companyId())) {
            ids.add(ImportChecks.id(company.companyId()));
        }
        if (!blank(company.vatNumber())) {
            ids.add(ImportChecks.id(company.vatNumber()));
        }
        return ids;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> read(String s) {
        if (s == null || s.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(s, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored import data is unreadable", e);
        }
    }

    private Object read(String s, boolean list) {
        try {
            return json.readValue(s, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored import warnings are unreadable", e);
        }
    }

    private static String sha256(byte[] data) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return blank(s) ? null : s.strip();
    }
}
