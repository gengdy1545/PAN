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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ArxivCrawlerService
 * <p>
 * Fetches daily papers from arXiv using the official OAI-PMH API:
 * https://oaipmh.arxiv.org/oai
 * <p>
 * --- Date & Timezone ---
 * arXiv releases new papers nightly according to US Eastern Time (ET).
 * Therefore, "today" is defined using the fixed timezone America/New_York
 * to align with arXiv’s publication schedule.
 * <p>
 * Configurable properties:
 * - arxiv.oai-url       (default: https://oaipmh.arxiv.org/oai)
 * - arxiv.categories    (default: cs.AI, comma-separated list)
 * <p>
 * Output model: io.gengdy.pan.model.Paper
 */
@Service
public class ArxivCrawlerService
{

    @Value("${arxiv.oai-url}")
    private String oaiUrl;

    @Value("${arxiv.categories}")
    private String categoriesCsv;

    /**
     * Fixed timezone to align with arXiv’s nightly publication batch.
     */
    private static final ZoneId ET = ZoneId.of("America/New_York");

    /**
     * Reusable HTTP client
     */
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(java.net.ProxySelector.getDefault())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Fetch papers published "today" according to Eastern Time.
     */
    public List<Paper> fetchTodayPapers() throws Exception
    {
        LocalDate todayET = LocalDate.now(ET);
        return fetchPapersByDate(todayET);
    }

    /**
     * Fetch all papers for a specific date (based on Eastern Time).
     * Uses OAI-PMH ListRecords between from=until=YYYY-MM-DD.
     */
    public List<Paper> fetchPapersByDate(LocalDate dateET) throws Exception
    {
        String from = dateET.format(DateTimeFormatter.ISO_DATE);
        String until = from;

        List<String> categoryList = Arrays.stream(
                        Optional.ofNullable(categoriesCsv).orElse("cs.AI").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());

        Map<String, Paper> merged = new LinkedHashMap<>();

        for (String cat : categoryList)
        {
            String set = toOaiSet(cat); // e.g., cs.AI -> cs:cs:AI
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

                for (ArxivItem it : pr.records)
                {
                    String authors = String.join(", ", it.authors);
                    Paper p = new Paper(
                            it.idNoVersion,
                            it.title,
                            authors,
                            it.abstractText,
                            "https://arxiv.org/abs/" + it.idNoVersion
                    );
                    merged.putIfAbsent(it.idNoVersion, p); // deduplicate across categories
                }
            } while (token != null && !token.isBlank());
        }

        return new ArrayList<>(merged.values());
    }

    // ---------------- HTTP & URI ----------------

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
        return URI.create(String.format("%s?verb=ListRecords&resumptionToken=%s",
                base, urlEncode(token)));
    }

    private String httpGet(URI uri) throws Exception
    {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(java.time.Duration.ofSeconds(20))
                .GET()
                .build();

        int attempts = 0;
        while (true)
        {
            attempts++;
            try
            {
                return http.send(req, BodyHandlers.ofString()).body();
            } catch (java.net.ConnectException | java.net.http.HttpTimeoutException e)
            {
                if (attempts >= 3)
                {
                    throw new RuntimeException("Connect failed after " + attempts + " attempts. URI=" + uri, e);
                }
                Thread.sleep(400L * attempts);
            }
        }
    }

    // ---------------- OAI-PMH XML Parsing ----------------

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

        List<ArxivItem> out = new ArrayList<>();
        NodeList recs = doc.getElementsByTagName("record");

        for (int i = 0; i < recs.getLength(); i++)
        {
            Element rec = (Element) recs.item(i);

            // Skip deleted records
            Element header = first(rec, "header");
            if (header != null)
            {
                String status = header.getAttribute("status");
                if ("deleted".equalsIgnoreCase(status))
                {
                    continue;
                }
            }

            // Parse arXiv identifier (e.g., oai:arXiv:2501.01234v1)
            String identifier = text(first(rec, "header", "identifier"));
            String arxivIdWithVersion = extractArxivIdFromOaiIdentifier(identifier);
            if (arxivIdWithVersion == null || arxivIdWithVersion.isBlank()) continue;
            String idNoVersion = stripVersion(arxivIdWithVersion);

            // Metadata section
            Element md = first(rec, "metadata");
            if (md == null) continue;
            Element arxiv = first(md, "arXiv");
            if (arxiv == null) continue;

            String title = nullToEmpty(text(first(arxiv, "title")));
            String abs = nullToEmpty(text(first(arxiv, "abstract")));
            String created = text(first(arxiv, "created"));

            // Parse authors
            List<String> authors = new ArrayList<>();
            Element authorsEl = first(arxiv, "authors");
            if (authorsEl != null)
            {
                NodeList aNodes = authorsEl.getElementsByTagName("author");
                for (int j = 0; j < aNodes.getLength(); j++)
                {
                    Element a = (Element) aNodes.item(j);
                    String keyname = text(first(a, "keyname"));
                    String forenames = text(first(a, "forenames"));
                    String full = (forenames == null || forenames.isBlank())
                            ? safe(keyname)
                            : (forenames + " " + safe(keyname)).trim();
                    if (!full.isBlank()) authors.add(full);
                }
            }

            ArxivItem item = new ArxivItem();
            item.idNoVersion = idNoVersion;
            item.idWithVersion = arxivIdWithVersion;
            item.title = title;
            item.abstractText = abs;
            item.authors = authors;
            item.created = parseInstantDate(created);
            out.add(item);
        }

        ParseResult pr = new ParseResult();
        pr.records = out;
        pr.resumptionToken = (token != null && !token.isBlank()) ? token : null;
        return pr;
    }

    // ---------------- Utilities ----------------

    /**
     * Converts cs.AI -> cs:cs:AI (OAI set name format).
     */
    private static String toOaiSet(String cat)
    {
        if (cat == null || !cat.contains(".")) return null;
        String[] p = cat.split("\\.");
        return p[0] + ":" + p[0] + ":" + p[1];
    }

    /**
     * Extracts arXiv ID (with version) from oai:arXiv:XXXXvY safely.
     */
    private static String extractArxivIdFromOaiIdentifier(String s)
    {
        if (s == null) return null;
        s = s.trim();
        Matcher m = Pattern.compile("^oai:arXiv:(\\S+)$").matcher(s);
        if (m.find()) return m.group(1);
        int idx = s.lastIndexOf(':');
        if (idx >= 0 && idx + 1 < s.length())
        {
            return s.substring(idx + 1).trim();
        }
        return s;
    }

    /**
     * Removes version suffix (2501.01234v2 -> 2501.01234).
     */
    private static String stripVersion(String arxivId)
    {
        if (arxivId == null) return null;
        int v = arxivId.indexOf('v');
        return (v > 0) ? arxivId.substring(0, v) : arxivId;
    }

    private static String urlEncode(String s)
    {
        try
        {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e)
        {
            return s;
        }
    }

    private static Instant parseInstantDate(String yyyyMmDd)
    {
        if (yyyyMmDd == null || yyyyMmDd.isBlank()) return null;
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

    private static String nullToEmpty(String s)
    {
        return (s == null) ? "" : s.trim();
    }

    private static String safe(String s)
    {
        return (s == null) ? "" : s;
    }

    // ---------------- Internal Structures ----------------

    private static class ArxivItem
    {
        String idNoVersion;     // e.g., 2501.01234
        String idWithVersion;   // e.g., 2501.01234v1
        String title;
        String abstractText;
        List<String> authors;
        Instant created;
    }

    private static class ParseResult
    {
        List<ArxivItem> records;
        String resumptionToken;
    }
}
