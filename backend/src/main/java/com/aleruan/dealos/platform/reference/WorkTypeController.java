package com.aleruan.dealos.platform.reference;

import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.web.ConflictException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reference/work-types")
class WorkTypeController {

    record CreateWorkType(
            @NotBlank @Pattern(regexp = "[A-Z0-9_]{2,40}") String code,
            @NotBlank String nameCs, @NotBlank String namePl, @NotBlank String nameEn, @NotBlank String nameRu,
            @DecimalMin("0") BigDecimal defaultMarkupPct) {
    }

    private final WorkTypeRepository repository;
    private final AuditService audit;

    WorkTypeController(WorkTypeRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @GetMapping
    List<WorkType> list() {
        return repository.findByActiveTrueOrderByCode();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    WorkType create(@Valid @RequestBody CreateWorkType request) {
        if (repository.existsByCode(request.code())) {
            throw new ConflictException("Work type " + request.code() + " already exists");
        }
        WorkType saved = repository.save(new WorkType(request.code(), request.nameCs(), request.namePl(),
                request.nameEn(), request.nameRu(), request.defaultMarkupPct()));
        audit.record("WorkType", saved.getId().toString(), "CREATED", request);
        return saved;
    }
}
