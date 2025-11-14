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
        "pan.home=/home/gengdy/opt/arxiv_mailer/"
})
public class ArxivCrawlerTest
{
    @Autowired
    private ArxivCrawlerService crawlerService;

    @Test
    public void testFetchRecentPapers() throws Exception
    {
        List<Paper> papers = crawlerService.fetchTodayPapers();

        assertNotNull(papers, "Paper list should not be null");

        if (papers.isEmpty())
        {
            System.out.println("No papers fetched for today.");
            return;
        }

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
