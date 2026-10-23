package com.aleruan.dealos.counterparty.sanctions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Streams the EU consolidated financial sanctions list (FSF "xmlFullSanctionsList_1_1"):
 * &lt;export&gt;&lt;sanctionEntity logicalId="…"&gt;&lt;subjectType code="person|enterprise"/&gt;
 * &lt;nameAlias wholeName="…" firstName="…" lastName="…"/&gt;… Reads by local name; DTDs and
 * external entities are refused. The full file is large, so it is never loaded into a DOM.
 */
public final class SanctionsXmlParser {

    private SanctionsXmlParser() {
    }

    public static List<SanctionedName> parse(InputStream in) throws XMLStreamException {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        XMLStreamReader r = f.createXMLStreamReader(in);

        List<SanctionedName> names = new ArrayList<>();
        String logicalId = null;
        String subjectType = null;
        List<String> pending = new ArrayList<>();
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (r.getLocalName()) {
                    case "sanctionEntity" -> {
                        logicalId = attr(r, "logicalId");
                        subjectType = null;
                        pending.clear();
                    }
                    case "subjectType" -> subjectType = attr(r, "code");
                    case "nameAlias" -> {
                        String whole = attr(r, "wholeName");
                        if (whole == null || whole.isBlank()) {
                            whole = String.join(" ", nonBlank(attr(r, "firstName"), attr(r, "middleName"), attr(r, "lastName")));
                        }
                        if (!whole.isBlank()) {
                            pending.add(whole.trim());
                        }
                    }
                    default -> { }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "sanctionEntity".equals(r.getLocalName())) {
                for (String name : pending) {
                    names.add(new SanctionedName(logicalId, subjectType, name));
                }
                pending.clear();
            }
        }
        r.close();
        return names;
    }

    private static String attr(XMLStreamReader r, String name) {
        return r.getAttributeValue(null, name);
    }

    private static List<String> nonBlank(String... values) {
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(v.trim());
            }
        }
        return out;
    }
}
