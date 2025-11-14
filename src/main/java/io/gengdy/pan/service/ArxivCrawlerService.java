package io.gengdy.pan.service;

import io.gengdy.pan.model.Paper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ArxivCrawlerService
 * <p>
 * Fetches papers from arXiv using the export API:
 * https://export.arxiv.org/api/query
 * <p>
 * Time window logic (configurable zone, typically Asia/Shanghai):
 * <p>
 * - Morning job (around anchor-hour, e.g. 09:00 local time):
 * Fetch papers submitted in [yesterday anchor-hour, today anchor-hour]
 * <p>
 * - Evening job (around evening-end-hour, e.g. 21:00 local time):
 * Fetch papers submitted in [today anchor-hour, today evening-end-hour]
 * <p>
 * Which window is used is determined by the current local time:
 * - If now < evening-end-hour  -> morning window
 * - Else                       -> evening window
 * <p>
 * NOTE: In practice, this service is triggered by two cron jobs:
 * pan.schedule.cron-morning (at anchor-hour)
 * pan.schedule.cron-evening (at evening-end-hour)
 * so the above logic aligns naturally with the cron configuration.
 */
@Service
public class ArxivCrawlerService
{

    /**
     * Base URL for the arXiv export API.
     * Example: https://export.arxiv.org/api/query
     */
    @Value("${arxiv.export-url}")
    private String exportUrl;

    /**
     * Comma-separated category list, e.g. "cs.AI,cs.CL".
     */
    @Value("${arxiv.categories}")
    private String categoriesCsv;

    /**
     * Time zone ID string, e.g. "Asia/Shanghai".
     * Shared with scheduling configuration.
     */
    @Value("${pan.schedule.zone}")
    private String zoneIdString;

    /**
     * Anchor hour for the daily window (0-23, local time).
     * Morning window: [yesterday anchor-hour, today anchor-hour]
     */
    @Value("${pan.window.anchor-hour:9}")
    private int windowAnchorHour;

    /**
     * Evening window end hour (0-23, local time).
     * Evening window: [today anchor-hour, today evening-end-hour]
     */
    @Value("${pan.window.evening-end-hour:21}")
    private int windowEveningEndHour;

    /**
     * Parsed ZoneId, lazily initialized from configuration.
     */
    private ZoneId zoneId;

    /**
     * Shared HTTP client for all requests.
     */
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Lazily resolve and cache the ZoneId from configuration.
     */
    private ZoneId getZoneId()
    {
        if (zoneId == null)
        {
            zoneId = ZoneId.of(zoneIdString);
        }
        return zoneId;
    }

    /**
     * Entry point used by scheduled tasks.
     * <p>
     * Window selection:
     * <p>
     * - If current local time < evening-end-hour:
     * "morning window": [yesterday anchor-hour, today anchor-hour]
     * <p>
     * - Else:
     * "evening window": [today anchor-hour, today evening-end-hour]
     * <p>
     * Local time is computed in the configured zone (e.g. Asia/Shanghai),
     * and the window is converted to UTC for querying arXiv.
     *
     * @return list of papers within the selected time window.
     */
    public List<Paper> fetchTodayPapers() throws Exception
    {
        ZoneId zone = getZoneId();
        ZonedDateTime nowLocal = ZonedDateTime.now(zone);
        LocalDate today = nowLocal.toLocalDate();

        // Configured anchor and evening-end times in local zone
        LocalTime anchorTime = LocalTime.of(windowAnchorHour, 0);
        LocalTime eveningEndTime = LocalTime.of(windowEveningEndHour, 0);

        ZonedDateTime todayAnchor = today.atTime(anchorTime).atZone(zone);
        ZonedDateTime todayEveningEnd = today.atTime(eveningEndTime).atZone(zone);

        ZonedDateTime startLocal;
        ZonedDateTime endLocal;

        // If current time is before the evening-end-hour, treat as "morning window"
        if (nowLocal.toLocalTime().isBefore(eveningEndTime))
        {
            // Morning job:
            //   window = [yesterday evening-end-hour, today anchor-hour]
            startLocal = todayEveningEnd.minusDays(1);
            endLocal = todayAnchor;
        } else
        {
            // Evening job:
            //   window = [today anchor-hour, today evening-end-hour]
            startLocal = todayAnchor;
            endLocal = todayEveningEnd;
        }

        return fetchPapersBySubmittedDateRange(startLocal, endLocal);
    }

    /**
     * Fetch papers submitted within a given time range.
     * <p>
     * Input start/end timestamps are in the configured local time zone.
     * They are converted to UTC (GMT) and then used to build a
     * "submittedDate" range filter for the arXiv export API:
     * <p>
     * submittedDate:[YYYYMMDDHHmm TO YYYYMMDDHHmm]
     *
     * @param startLocal start of the window in local time (inclusive)
     * @param endLocal   end of the window in local time (inclusive)
     * @return list of deduplicated papers across all configured categories
     */
    private List<Paper> fetchPapersBySubmittedDateRange(ZonedDateTime startLocal,
                                                        ZonedDateTime endLocal) throws Exception
    {

        // 1) Convert local time to UTC (GMT)
        ZonedDateTime startUtc = startLocal.withZoneSameInstant(ZoneOffset.UTC);
        ZonedDateTime endUtc = endLocal.withZoneSameInstant(ZoneOffset.UTC);

        // 2) Format to yyyyMMddHHmm as required by "submittedDate" filter
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        String fromStr = startUtc.format(fmt);
        String toStr = endUtc.format(fmt);

        // 3) Resolve category list from configuration
        List<String> categoryList = Arrays.stream(
                        Optional.ofNullable(categoriesCsv).orElse("cs.DB").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        Map<String, Paper> merged = new LinkedHashMap<>();

        for (String cat : categoryList)
        {
            int startIndex = 0;
            int pageSize = 200;  // Number of results per page (tunable)

            while (true)
            {
                URI uri = buildExportApiUri(cat, fromStr, toStr, startIndex, pageSize);
                String xml = httpGet(uri);

                List<ArxivEntry> entries = parseArxivAtom(xml);
                if (entries.isEmpty())
                {
                    break;
                }

                for (ArxivEntry e : entries)
                {
                    String authors = String.join(", ", e.authors);
                    Paper p = new Paper(
                            e.idNoVersion,
                            e.title,
                            authors,
                            e.abstractText,
                            "https://arxiv.org/abs/" + e.idNoVersion
                    );
                    // Deduplicate across categories by id (no version)
                    merged.putIfAbsent(e.idNoVersion, p);
                }

                if (entries.size() < pageSize)
                {
                    // Last page for this category
                    break;
                }
                startIndex += pageSize;
            }
        }

        return new ArrayList<>(merged.values());
    }

    /**
     * Build URI for the arXiv export API query.
     * <p>
     * Example search_query:
     * (cat:cs.DB) AND submittedDate:[202511130900 TO 202511140900]
     */
    private URI buildExportApiUri(String category,
                                  String submittedFrom,
                                  String submittedTo,
                                  int start,
                                  int maxResults)
    {

        String searchQuery = String.format(
                "(cat:%s) AND submittedDate:[%s TO %s]",
                category, submittedFrom, submittedTo
        );

        String encoded = urlEncode(searchQuery);

        String url = String.format(
                "%s?search_query=%s&start=%d&max_results=%d&sortBy=submittedDate&sortOrder=ascending",
                exportUrl,
                encoded,
                start,
                maxResults
        );

        return URI.create(url);
    }

    /**
     * Simple HTTP GET with basic retry for transient connection issues.
     */
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
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                return resp.body();
            } catch (java.net.ConnectException |
                     java.net.http.HttpTimeoutException e)
            {
                if (attempts >= 3)
                {
                    throw new RuntimeException("Connect failed after " + attempts + " attempts. URI=" + uri, e);
                }
                Thread.sleep(60000L); // Wait 60 seconds before retrying
            }
        }
    }

    /**
     * Parse arXiv Atom XML returned by the export API into a list of entries.
     */
    private static List<ArxivEntry> parseArxivAtom(String xml) throws Exception
    {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        doc.getDocumentElement().normalize();

        List<ArxivEntry> out = new ArrayList<>();
        NodeList entries = doc.getElementsByTagName("entry");

        for (int i = 0; i < entries.getLength(); i++)
        {
            Element entry = (Element) entries.item(i);

            // <id> e.g. http://arxiv.org/abs/2501.01234v1
            String idText = nullToEmpty(text(first(entry, "id")));
            String arxivIdWithVersion = extractArxivIdFromAtomId(idText);
            if (arxivIdWithVersion == null || arxivIdWithVersion.isBlank())
            {
                continue;
            }

            String idNoVersion = stripVersion(arxivIdWithVersion);
            String title = nullToEmpty(text(first(entry, "title")));
            String abs = nullToEmpty(text(first(entry, "summary")));

            // Authors
            List<String> authors = new ArrayList<>();
            NodeList authorNodes = entry.getElementsByTagName("author");
            for (int j = 0; j < authorNodes.getLength(); j++)
            {
                Element a = (Element) authorNodes.item(j);
                String name = text(first(a, "name"));
                if (name != null && !name.isBlank())
                {
                    authors.add(name.trim());
                }
            }

            ArxivEntry e = new ArxivEntry();
            e.idNoVersion = idNoVersion;
            e.idWithVersion = arxivIdWithVersion;
            e.title = title;
            e.abstractText = abs;
            e.authors = authors;

            out.add(e);
        }

        return out;
    }

    /**
     * Extract arXiv ID (with version) from Atom <id> value.
     * <p>
     * Example:
     * http://arxiv.org/abs/2501.01234v1 -> 2501.01234v1
     */
    private static String extractArxivIdFromAtomId(String s)
    {
        if (s == null) return null;
        s = s.trim();
        int idx = s.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < s.length())
        {
            return s.substring(idx + 1).trim();
        }
        return s;
    }

    /**
     * Remove version suffix from an arXiv ID.
     * <p>
     * Example:
     * 2501.01234v2 -> 2501.01234
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
            return URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
        } catch (Exception e)
        {
            return s;
        }
    }

    private static Element first(Element parent, String tag)
    {
        if (parent == null) return null;
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() > 0 ? (Element) nl.item(0) : null;
    }

    private static String text(Node node)
    {
        return (node == null) ? null : node.getTextContent();
    }

    private static String nullToEmpty(String s)
    {
        return (s == null) ? "" : s.trim();
    }

    /**
     * Internal structure representing one arXiv entry from the Atom feed.
     */
    private static class ArxivEntry
    {
        String idNoVersion;
        String idWithVersion;
        String title;
        String abstractText;
        List<String> authors;
    }
}
