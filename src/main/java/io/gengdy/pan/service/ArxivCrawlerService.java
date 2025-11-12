package io.gengdy.pan.service;

import io.gengdy.pan.model.Paper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ArxivCrawlerService
{
    private static final Logger logger = LoggerFactory.getLogger(ArxivCrawlerService.class);

    @Value("${arxiv.list-url-template}")
    private String urlTemplate;

    @Value("${arxiv.categories}")
    private List<String> targetCategories;

    public List<Paper> fetchRecentPapers()
    {
        Set<Paper> papers = new HashSet<>();

        for (String category : targetCategories)
        {
            String url = urlTemplate.replace("{category}", category.trim());

            try
            {
                Document doc = Jsoup.connect(url)
                        .userAgent("pan-arxiv-crawler/1.0")
                        .get();

                Elements dtElements = doc.select("dl dt");
                for (Element dt: dtElements)
                {
                    Element dd = dt.nextElementSibling();
                    if (dd == null)
                        continue;

                    try
                    {
                        Element absLink = dt.selectFirst("a[title=Abstract]");
                        if (absLink == null)
                            continue;

                        String paperUrl = absLink.attr("abs:href");
                        String paperId = paperUrl.substring(paperUrl.lastIndexOf('/') + 1);

                        String title = dd.selectFirst("div.list-title").text().replace("Title: ", "").trim();
                        String authors = dd.selectFirst("div.list-authors").text().replace("Authors: ", "").trim();
                        String abstractText = dd.selectFirst("p.mathjax").text().trim();

                        Paper paper = new Paper(paperId, title, authors, abstractText, paperUrl);
                        papers.add(paper);
                    } catch (Exception e)
                    {
                        logger.warn("Failed to parse paper details from document: {}", e.getMessage());
                    }
                }
            } catch (IOException e)
            {
                logger.error("Failed to fetch arxiv category {} recent papers.", category, e);
            }
        }

        return new ArrayList<>(papers);
    }
}