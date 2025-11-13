package io.gengdy.pan.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import io.gengdy.pan.model.Paper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeminiAIService
{
    @Value("${gemini.model-name}")
    private String modelName;

    @Value("${gemini.prompt}")
    private String prompt;

    private Client geminiClient;

    public GeminiAIService(Client geminiClient)
    {
        this.geminiClient = geminiClient;
    }

    public void summarizePaper(List<Paper> papers)
    {
        for (Paper paper : papers)
        {
            GenerateContentResponse response  = this.geminiClient.models.generateContent(
                    modelName,
                    prompt + "\n\nAbstract:\n" + paper.getAbstractText(),
                    null
            );

            String summary = response.text().trim();
            if (summary.isEmpty())
            {
                continue;
            }
            paper.setAiSummary(summary);

            try
            {
                Thread.sleep(6000); // Sleep for 6 seconds to avoid rate limiting
            } catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }
}