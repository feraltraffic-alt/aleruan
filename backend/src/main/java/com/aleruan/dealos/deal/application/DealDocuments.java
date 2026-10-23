package com.aleruan.dealos.deal.application;

import com.aleruan.dealos.platform.files.FileInfo;
import com.aleruan.dealos.platform.files.FileStore;
import com.aleruan.dealos.platform.web.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The signed contract, its annexes and other papers of a deal, as scans or PDFs. */
@Service
public class DealDocuments {

    public static final String OWNER = "Deal";

    private final DealService deals;
    private final FileStore files;

    DealDocuments(DealService deals, FileStore files) {
        this.deals = deals;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public List<FileInfo> list(UUID dealId) {
        deals.get(dealId);
        return files.list(OWNER, List.of(dealId));
    }

    @Transactional
    public FileInfo attach(UUID dealId, String fileName, byte[] data) {
        deals.get(dealId);
        return files.store(OWNER, dealId, fileName, data);
    }

    @Transactional
    public void remove(UUID dealId, UUID fileId) {
        deals.get(dealId);
        files.delete(OWNER, dealId, fileId);
    }

    /** A document of this deal; another deal's file id is not found here. */
    @Transactional(readOnly = true)
    public FileStore.Content get(UUID dealId, UUID fileId) {
        FileStore.Content c = files.get(fileId);
        if (!OWNER.equals(c.info().ownerType()) || !dealId.equals(c.info().ownerId())) {
            throw new NotFoundException("File " + fileId + " not found");
        }
        return c;
    }
}
