package com.aleruan.dealos.counterparty.registry;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Czech VAT register web service (ADIS "rozhraniCRPDPH", SOAP, namespace http://adis.mfcr.cz/rozhraniCRPDPH/).
 * Response: &lt;status statusCode="0" statusText="OK"/&gt; and per DIČ
 * &lt;statusPlatceDPH dic="…" nespolehlivyPlatce="ANO|NE|NENALEZEN" cisloFu="…"&gt; with
 * &lt;zverejneneUcty&gt;&lt;ucet&gt;&lt;standardniUcet predcisli cislo kodBanky/&gt; | &lt;nestandardniUcet cislo/&gt;,
 * plus &lt;nazevSubjektu&gt; and &lt;adresa&gt; in the extended operation.
 * Parsed by local names so the namespace prefix does not matter. XXE is disabled.
 */
public final class RegistrDphResultMapper {

    public static final String NAMESPACE = "http://adis.mfcr.cz/rozhraniCRPDPH/";

    private RegistrDphResultMapper() {
    }

    /** SOAP request for one DIČ (digits only, without the CZ prefix). */
    public static String request(String dicDigits) {
        if (!dicDigits.matches("\\d{8,10}")) {
            throw new IllegalArgumentException("DIČ must be 8–10 digits: " + dicDigits);
        }
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:roz="%s">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <roz:StatusNespolehlivyPlatceRozsirenyRequest>
                      <roz:dic>%s</roz:dic>
                    </roz:StatusNespolehlivyPlatceRozsirenyRequest>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(NAMESPACE, dicDigits);
    }

    public static RegistrySnapshot map(String xml, Instant at) {
        Document doc;
        try {
            doc = parse(xml);
        } catch (Exception e) {
            return RegistrySnapshot.unavailable(CheckSource.REGISTR_DPH, at, "Unreadable XML: " + e.getMessage(), xml);
        }
        Element fault = first(doc.getDocumentElement(), "Fault");
        if (fault != null) {
            return RegistrySnapshot.unavailable(CheckSource.REGISTR_DPH, at, "SOAP fault: " + fault.getTextContent().trim(), xml);
        }
        Element status = first(doc.getDocumentElement(), "status");
        if (status != null && !"0".equals(status.getAttribute("statusCode"))) {
            return RegistrySnapshot.unavailable(CheckSource.REGISTR_DPH, at,
                    status.getAttribute("statusCode") + ": " + status.getAttribute("statusText"), xml);
        }
        Element payer = first(doc.getDocumentElement(), "statusPlatceDPH");
        if (payer == null) {
            payer = first(doc.getDocumentElement(), "statusSubjektu");
        }
        if (payer == null) {
            return RegistrySnapshot.unavailable(CheckSource.REGISTR_DPH, at, "No statusPlatceDPH in response", xml);
        }
        String reliability = payer.getAttribute("nespolehlivyPlatce");
        boolean notFound = "NENALEZEN".equalsIgnoreCase(reliability);
        Element name = first(payer, "nazevSubjektu");
        Element address = first(payer, "adresa");
        String dic = payer.getAttribute("dic");
        return RegistrySnapshot.builder(CheckSource.REGISTR_DPH, at)
                .found(true)
                .vatStatus(notFound ? "NENALEZEN" : "PLATCE")
                .unreliableVatPayer(notFound ? null : "ANO".equalsIgnoreCase(reliability))
                .vatNumber(dic.isEmpty() ? null : "CZ" + dic)
                .legalName(name == null ? null : name.getTextContent().trim())
                .address(address == null ? null : joinChildren(address))
                .publishedAccounts(accounts(payer))
                .rawResponse(xml)
                .build();
    }

    private static List<String> accounts(Element payer) {
        List<String> result = new ArrayList<>();
        NodeList all = payer.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            String local = local(e);
            if ("standardniUcet".equals(local)) {
                String prefix = e.getAttribute("predcisli");
                result.add((prefix.isEmpty() ? "" : prefix + "-") + e.getAttribute("cislo") + "/" + e.getAttribute("kodBanky"));
            } else if ("nestandardniUcet".equals(local)) {
                result.add(e.getAttribute("cislo"));
            }
        }
        return result;
    }

    private static String joinChildren(Element parent) {
        List<String> parts = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && !n.getTextContent().isBlank()) {
                parts.add(n.getTextContent().trim());
            }
        }
        return parts.isEmpty() ? parent.getTextContent().trim() : String.join(", ", parts);
    }

    private static Element first(Element root, String localName) {
        NodeList all = root.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (localName.equals(local(e))) {
                return e;
            }
        }
        return localName.equals(local(root)) ? root : null;
    }

    private static String local(Element e) {
        return e.getLocalName() != null ? e.getLocalName() : e.getTagName().replaceFirst("^.*:", "");
    }

    static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder builder = f.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler()); // throw, don't print to stderr
        return builder.parse(new InputSource(new StringReader(xml)));
    }
}
