package io.gengdy.pan.config;

import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceConfig
{
    @Value("${gemini.api-key}")
    private String apiKey;

    @Bean
    public Client geminiClient()
    {
        if (apiKey == null || apiKey.isEmpty())
        {
            throw new RuntimeException("Gemini apiKey is not configured.");
        }
        return Client.builder().apiKey(apiKey).build();
    }
}
