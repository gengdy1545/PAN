package io.gengdy.pan.service;

import io.gengdy.pan.ArxivSummaryMailerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = {ArxivSummaryMailerApplication.class})
@TestPropertySource(properties = {
        "pan.home=/home/gengdy/opt/arxiv_mailer/"
})
public class GeminiTest
{
    @Autowired
    private GeminiAIService service;

    @Test
    public void testGeminiAIService() throws Exception
    {
        String sampleText = "This is a sample text to be summarized by Gemini AI.";
        String summary = service.generateSummary(sampleText);
        System.out.println("Generated Summary: " + summary);
    }
}
