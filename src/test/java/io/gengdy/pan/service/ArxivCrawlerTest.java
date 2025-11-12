package io.gengdy.pan.service;


import io.gengdy.pan.ArxivSummaryMailerApplication;
import io.gengdy.pan.model.Paper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {ArxivSummaryMailerApplication.class})
@TestPropertySource(properties = {
        "pan.home=/home/gengdy/opt/arxiv_mailer/",
        "arxiv.list-url-template=https://arxiv.org/list/{category}/new",
        "arxiv.categories=cs.AI,cs.CV"
})
public class ArxivCrawlerTest
{
    @Autowired
    private ArxivCrawlerService crawlerService;

    @Test
    public void testFetchRecentPapers() {
        List<Paper> papers = crawlerService.fetchRecentPapers();

        assertNotNull(papers, "Paper list should not be null");
        assertFalse(papers.isEmpty(),
                "Crawler failed to fetch any papers. Check network or Arxiv page structure.");

        Paper firstPaper = papers.get(0);

        assertNotNull(firstPaper.getId(), "Paper ID should not be null");
        assertFalse(firstPaper.getId().isEmpty(), "Paper ID should not be empty");

        assertNotNull(firstPaper.getTitle(), "Paper title should not be null");
        assertFalse(firstPaper.getTitle().isEmpty(), "Paper title should not be empty");

        assertNotNull(firstPaper.getAuthors(), "Paper authors should not be null");
        assertFalse(firstPaper.getAuthors().isEmpty(), "Paper authors should not be empty");

        assertNotNull(firstPaper.getUrl(), "Paper URL should not be null");
        assertTrue(firstPaper.getUrl().startsWith("https://arxiv.org/abs/"),
                "Paper URL format is incorrect. Should start with https://arxiv.org/abs/");

        System.out.println("Fetched " + papers.size() + " papers. Sample paper: " + firstPaper);
    }
}
