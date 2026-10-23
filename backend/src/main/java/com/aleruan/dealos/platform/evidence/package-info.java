/**
 * The deal's evidence package: each module that holds documents or records of a deal contributes its
 * share (files, record rows, completeness checks and the audit entities to extract) through
 * {@link com.aleruan.dealos.platform.evidence.EvidenceContributor}; the evidence module assembles them.
 */
@org.springframework.modulith.NamedInterface("evidence")
package com.aleruan.dealos.platform.evidence;
