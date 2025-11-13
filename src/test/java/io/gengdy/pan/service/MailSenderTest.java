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
public class MailSenderTest
{
    @Autowired
    private MailSenderService mailSenderService;

    @Test
    public void testSendMail() throws Exception
    {
        Paper paper1 = new Paper("id", "tile", "authors", "abstract", "url");
        Paper paper2 = new Paper("id", "tile", "authors", "abstract", "url");
        List<Paper> papers = List.of(paper1, paper2);

        mailSenderService.sendDailyPaperDigest(papers);
    }
}