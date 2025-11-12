package io.gengdy.pan.service;

import io.gengdy.pan.model.Paper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ArxivCrawlerService {

    @Value("${arxiv.oai-url:https://oaipmh.arxiv.org/oai}")
    private String oaiUrl;

    @Value("${arxiv.categories:cs.AI}")
    private String categories;

    @Value("${arxiv.timezone:America/New_York}")
    private String timezone;

    private final HttpClient http = HttpClient.newHttpClient();

    public List<Paper> fetchTodayPapers() throws Exception
    {
        ZoneId zone = ZoneId.of(timezone);
        LocalDate today = LocalDate.now(zone);
        String from = today.toString();
        String until = today.toString();

        List<String> categoryList = Arrays.stream(categories.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        Map<String, Paper> all = new LinkedHashMap<>();

        for (String cat : categoryList)
        {
            String set = toOaiSet(cat);
            if (set == null) continue;

            String token = null;
            do
            {
                URI uri = (token == null)
                        ? buildListRecordsUri(oaiUrl, from, until, set)
                        : buildListRecordsWithTokenUri(oaiUrl, token);

                String xml = httpGet(uri);
                ParseResult pr = parseOaiListRecords(xml);
                token = pr.resumptionToken;

                for (ArxivItem item : pr.records)
                {
                    String authors = String.join(", ", item.authors);
                    Paper p = new Paper(
                            item.id,
                            item.title,
                            authors,
                            item.abstractText,
                            item.absUrl
                    );
                    all.putIfAbsent(item.id, p);
                }
            } while (token != null && !token.isBlank());
        }

        return new ArrayList<>(all.values());
    }

    // ---------- HTTP & XML ----------

    private static URI buildListRecordsUri(String base, String from, String until, String set)
    {
        String url = String.format(
                "%s?verb=ListRecords&metadataPrefix=arXiv&from=%s&until=%s&set=%s",
                base, from, until, urlEncode(set)
        );
        return URI.create(url);
    }

    private static URI buildListRecordsWithTokenUri(String base, String token)
    {
        return URI.create(String.format("%s?verb=ListRecords&resumptionToken=%s", base, urlEncode(token)));
    }

    private String httpGet(URI uri) throws Exception
    {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("User-Agent", "ArxivCrawlerService/1.0 (mailto:your@email)")
                .GET().build();
        return http.send(req, BodyHandlers.ofString()).body();
    }

    private static ParseResult parseOaiListRecords(String xml) throws Exception
    {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        doc.getDocumentElement().normalize();

        String token = null;
        NodeList tokenNodes = doc.getElementsByTagName("resumptionToken");
        if (tokenNodes.getLength() > 0)
        {
            token = text(tokenNodes.item(0));
            if (token != null) token = token.trim();
        }

        List<ArxivItem> list = new ArrayList<>();
        NodeList recs = doc.getElementsByTagName("record");

        for (int i = 0; i < recs.getLength(); i++)
        {
            Element rec = (Element) recs.item(i);
            String identifier = text(first(rec, "header", "identifier"));
            String arxivId = extractArxivIdFromOaiIdentifier(identifier);
            Element md = first(rec, "metadata");
            if (md == null) continue;
            Element arxiv = first(md, "arXiv");
            if (arxiv == null) continue;

            String title = text(first(arxiv, "title"));
            String abs = text(first(arxiv, "abstract"));
            String created = text(first(arxiv, "created"));

            List<String> authors = new ArrayList<>();
            Element authorsEl = first(arxiv, "authors");
            if (authorsEl != null)
            {
                NodeList aNodes = authorsEl.getElementsByTagName("author");
                for (int j = 0; j < aNodes.getLength(); j++) {
                    Element a = (Element) aNodes.item(j);
                    String keyname = text(first(a, "keyname"));
                    String forenames = text(first(a, "forenames"));
                    String full = (forenames == null || forenames.isBlank())
                            ? safe(keyname)
                            : (forenames + " " + safe(keyname)).trim();
                    if (!full.isBlank()) authors.add(full);
                }
            }

            String idNoVer = stripVersion(arxivId);
            String absUrl = "https://arxiv.org/abs/" + idNoVer;

            ArxivItem item = new ArxivItem(idNoVer, title, abs, authors, absUrl);
            item.created = parseInstant(created);
            list.add(item);
        }

        ParseResult pr = new ParseResult();
        pr.records = list;
        pr.resumptionToken = (token != null && !token.isBlank()) ? token : null;
        return pr;
    }

    // ---------- helper method  ----------
    private static String toOaiSet(String cat)
    {
        if (cat == null || !cat.contains(".")) return null;
        String[] p = cat.split("\\.");
        return p[0] + ":" + p[0] + ":" + p[1];
    }

    private static String extractArxivIdFromOaiIdentifier(String oaiId)
    {
        if (oaiId == null) return null;
        int idx = oaiId.indexOf("oai:arXiv:");
        return idx >= 0 ? oaiId.substring(idx + "oai:arXiv:".length()) : oaiId;
    }

    private static String stripVersion(String arxivId)
    {
        if (arxivId == null) return null;
        int v = arxivId.indexOf('v');
        return (v > 0) ? arxivId.substring(0, v) : arxivId;
    }

    private static Instant parseInstant(String yyyyMmDd)
    {
        try
        {
            LocalDate d = LocalDate.parse(yyyyMmDd, DateTimeFormatter.ISO_DATE);
            return d.atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e)
        {
            return null;
        }
    }

    private static Element first(Element parent, String tag)
    {
        if (parent == null) return null;
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() > 0 ? (Element) nl.item(0) : null;
    }

    private static Element first(Element parent, String tag1, String tag2)
    {
        Element mid = first(parent, tag1);
        return first(mid, tag2);
    }

    private static String text(Node node)
    {
        return (node == null) ? null : node.getTextContent();
    }

    private static String safe(String s)
    {
        return s == null ? "" : s;
    }
    private static String urlEncode(String s)
    {
        try
        {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            return s;
        }
    }

    // ---------- Internal Data Structure ----------
    private static class ArxivItem
    {
        String id;
        String title;
        String abstractText;
        List<String> authors;
        String absUrl;
        Instant created;

        public ArxivItem(String id, String title, String abstractText,
                         List<String> authors, String absUrl)
        {
            this.id = id;
            this.title = title;
            this.abstractText = abstractText;
            this.authors = authors;
            this.absUrl = absUrl;
        }
    }

    private static class ParseResult
    {
        List<ArxivItem> records;
        String resumptionToken;
    }
}
