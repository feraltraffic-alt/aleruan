package com.aleruan.dealos.platform.audit;

/** Result of an audit chain check. {@code brokenAtSeq} is null when the chain is intact. */
public record ChainVerification(boolean valid, long verifiedRecords, Long brokenAtSeq) {

    public static ChainVerification ok(long verifiedRecords) {
        return new ChainVerification(true, verifiedRecords, null);
    }

    public static ChainVerification broken(long verifiedRecords, long brokenAtSeq) {
        return new ChainVerification(false, verifiedRecords, brokenAtSeq);
    }
}
