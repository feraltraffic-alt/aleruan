package com.aleruan.dealos.docimport.domain;

/** What a document is, from Aleruan's point of view. */
public enum DocType {
    /** An invoice a subcontractor or supplier sent to Aleruan. */
    SUPPLIER_INVOICE,
    /** An invoice Aleruan issued to a customer (history from before the system). */
    CUSTOMER_INVOICE,
    /** A credit note, either direction. */
    CREDIT_NOTE,
    /** A consignment note (CMR) of a delivery. */
    CMR,
    /** A contract, order or annex. */
    CONTRACT,
    OTHER
}
