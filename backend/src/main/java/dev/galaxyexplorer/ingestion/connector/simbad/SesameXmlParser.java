package dev.galaxyexplorer.ingestion.connector.simbad;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import dev.galaxyexplorer.ingestion.connector.SourceQueryException;

/**
 * Le a resposta XML do Sesame. Puro.
 *
 * <p>Formato observado ao vivo (2026-10-05): raiz {@code <Sesame>}, um
 * {@code <Target>} por nome na ordem pedida, cada um com {@code <name>} (eco do
 * nome) e, se encontrado, um {@code <Resolver>} com {@code <oid>},
 * {@code <otype>}, {@code <oname>}...; se nao encontrado, so
 * {@code <INFO> *** NNNothing found *** </INFO>}.
 *
 * <p>Parser endurecido: DTD/entidades externas desabilitadas (XXE).
 */
public final class SesameXmlParser {

    private SesameXmlParser() {
    }

    public static List<SesameTarget> parse(String xml) {
        Document doc = parseSecurely(xml);
        Element root = doc.getDocumentElement();
        if (!"Sesame".equals(root.getTagName())) {
            throw new SourceQueryException("resposta do Sesame sem raiz <Sesame> (veio <" + root.getTagName() + ">)");
        }
        List<SesameTarget> targets = new ArrayList<>();
        for (Element target : children(root, "Target")) {
            String name = childText(target, "name");
            String info = childText(target, "INFO");
            List<Element> resolvers = children(target, "Resolver");
            if (resolvers.isEmpty()) {
                targets.add(new SesameTarget(name, null, null, null, info));
                continue;
            }
            Element resolver = resolvers.get(0);
            String oidText = childText(resolver, "oid");
            Long oid = null;
            if (oidText != null) {
                try {
                    oid = Long.parseLong(oidText);
                } catch (NumberFormatException e) {
                    throw new SourceQueryException("Sesame devolveu oid não numérico para '" + name + "': " + oidText);
                }
            }
            targets.add(new SesameTarget(name, oid, childText(resolver, "oname"), childText(resolver, "otype"),
                    info));
        }
        return targets;
    }

    private static Document parseSecurely(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("parser XML sem suporte as protecoes exigidas", e);
        } catch (SAXException | IOException e) {
            throw new SourceQueryException("resposta do Sesame não é XML válido: " + e.getMessage(), e);
        }
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && tag.equals(((Element) n).getTagName())) {
                out.add((Element) n);
            }
        }
        return out;
    }

    private static String childText(Element parent, String tag) {
        List<Element> found = children(parent, tag);
        if (found.isEmpty()) {
            return null;
        }
        String text = found.get(0).getTextContent();
        return text == null || text.isBlank() ? null : text.trim();
    }
}
