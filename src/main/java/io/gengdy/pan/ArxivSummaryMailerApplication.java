package io.gengdy.pan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ArxivSummaryMailerApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(ArxivSummaryMailerApplication.class, args);
        System.out.println("ArxivSummaryMailer system has been started...");
    }
}