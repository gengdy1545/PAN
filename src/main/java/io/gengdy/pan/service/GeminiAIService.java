package io.gengdy.pan.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import io.gengdy.pan.exception.GeminiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    public String generateSummary(String text) throws GeminiException
    {
        try
        {
            GenerateContentResponse response = this.geminiClient.models.generateContent(
                    modelName,
                    prompt + "\n\n" + text,
                    null
            );

            return response.text().trim();

        } catch (Exception e) {
            throw new GeminiException("Failed to generate summary using Gemini AI.", e);
        }
    }
}