package com.aleruan.dealos.docimport.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reads queued documents in the background, a few per tick, one at a time. */
@Component
@ConditionalOnProperty(name = "dealos.import.worker-enabled", havingValue = "true", matchIfMissing = true)
class ImportWorker {

    private static final int PER_TICK = 5;

    private final ImportService service;

    ImportWorker(ImportService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${dealos.import.poll:PT10S}", initialDelayString = "PT30S")
    void tick() {
        for (int i = 0; i < PER_TICK && service.processNext(); i++) {
            // keep going while there is work
        }
    }
}
