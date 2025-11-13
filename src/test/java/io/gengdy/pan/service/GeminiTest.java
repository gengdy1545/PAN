package io.gengdy.pan.service;

import io.gengdy.pan.ArxivSummaryMailerApplication;
import io.gengdy.pan.model.Paper;
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
        String paperId = "2501.10362";
        String paperTitle = "Reviewing Uses of Regulatory Compliance Monitoring";
        String paperAuthors = "Finn Klessascheck, Luise Pufahl";
        String sampleText = "Organizations need to manage numerous business processes for delivering their services and products to customers. One important consideration thereby lies in the adherence to regulations such as laws, guidelines, or industry standards. In order to monitor adherence of their business processes to regulations -- in other words, their regulatory compliance -- organizations make use of various techniques that draw on process execution data of IT systems that support these processes. Previous research has investigated conformance checking, an operation of process mining, for the domains in which it is applied, its operationalization of regulations, the techniques being used, and the presentation of results produced. However, other techniques for regulatory compliance monitoring, which we summarize as compliance checking techniques, have not yet been investigated regarding these aspects in a structural manner. To this end, this work presents a systematic literature review on uses of regulatory compliance monitoring of business processes, thereby offering insights into the various techniques being used, their application and the results they generate. We highlight commonalities and differences between the approaches and find that various steps are performed manually; we also provide further impulses for research on compliance monitoring and its use in practice.";
        String paperUrl = "https://arxiv.org/abs/2501.10362";

        Paper paper = new Paper(paperId, paperTitle, paperAuthors, sampleText, paperUrl);
        service.summarizePaper(java.util.List.of(paper));
        System.out.println("Generated Summary: " + paper.getAiSummary());
    }
}
