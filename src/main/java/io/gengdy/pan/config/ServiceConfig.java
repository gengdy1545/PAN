package io.gengdy.pan.config;

import com.google.genai.Client;
import io.gengdy.pan.exception.GeminiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceConfig
{
    private static final Logger logger = LoggerFactory.getLogger(ServiceConfig.class);

    @Value("${gemini.api-key}")
    private String apiKey;

    @Bean
    public Client geminiClient() throws GeminiException
    {
        if (apiKey == null || apiKey.isEmpty())
        {
            logger.error("Gemini apiKey is not configured.");
            throw new GeminiException("Gemini apiKey is not configured.");
        }
        return Client.builder().apiKey(apiKey).build();
    }
}
