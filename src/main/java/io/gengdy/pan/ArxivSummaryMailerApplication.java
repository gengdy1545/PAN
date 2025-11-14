package io.gengdy.pan;

import io.gengdy.pan.model.Paper;
import io.gengdy.pan.service.ArxivCrawlerService;
import io.gengdy.pan.service.GeminiAIService;
import io.gengdy.pan.service.MailSenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

@SpringBootApplication
@EnableScheduling
public class ArxivSummaryMailerApplication implements CommandLineRunner
{
    private static final Logger logger = LoggerFactory.getLogger(ArxivSummaryMailerApplication.class);

    private final ArxivCrawlerService crawlerService;
    private final GeminiAIService geminiAIService;
    private final MailSenderService mailSenderService;

    @Value("${pan.mode:deamon}")
    private String mode;

    public ArxivSummaryMailerApplication(ArxivCrawlerService crawlerService,
                            GeminiAIService geminiAIService,
                            MailSenderService mailSenderService)
    {
        this.crawlerService = crawlerService;
        this.geminiAIService = geminiAIService;
        this.mailSenderService = mailSenderService;
    }

    public static void main(String[] args)
    {
        SpringApplication.run(ArxivSummaryMailerApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception
    {
        logger.info("Arxiv Summary Mailer System started. Current mode: " + mode);
        if (mode.equalsIgnoreCase("oneshot"))
        {
            logger.info("[Mode: One-shot] Executing task immediately...");
            executeTask();
            logger.info("[Mode: One-shot] Task finished. Exiting system.");
            System.exit(0);
        } else
        {
            logger.info("[Mode: Daemon] System is running and waiting for scheduled trigger...");
        }
    }

    /**
     * Morning schedule (e.g. 10:00, Asia/Shanghai).
     * Cron is configured via pan.schedule.cron-morning.
     */
    @Scheduled(cron = "${pan.schedule.cron-morning}", zone = "${pan.schedule.zone:Asia/Shanghai}")
    public void morningScheduledTask()
    {
        if (mode.equalsIgnoreCase("deamon"))
        {
            logger.info("[Mode: Deamon] Morning Scheduled trigger fired.");
            executeTask();
        }
    }

    /**
     * Evening schedule (e.g. 22:00, Asia/Shanghai).
     * Cron is configured via pan.schedule.cron-evening.
     */
    @Scheduled(cron = "${pan.schedule.cron-evening}", zone = "${pan.schedule.zone:Asia/Shanghai}")
    public void eveningScheduledTask()
    {
        if (mode.equalsIgnoreCase("deamon"))
        {
            logger.info("[Mode: Deamon] Evening Scheduled trigger fired.");
            executeTask();
        }
    }

    private void executeTask()
    {
        long start = System.currentTimeMillis();
        try
        {
            logger.info(">>> 1. Starting Arxiv Crawler...");
            List<Paper> papers = crawlerService.fetchTodayPapers();
            logger.info(">>> Fetched " + papers.size() + " papers.");

            if (papers.isEmpty())
            {
                logger.info(">>> No new papers found today. Workflow ended.");
                return;
            }

            logger.info(">>> 2. Generating AI Summaries (Gemini)...");
            geminiAIService.summarizePaper(papers);

            logger.info(">>> 3. Sending Email Digest...");
            mailSenderService.sendDailyPaperDigest(papers);

            long duration = System.currentTimeMillis() - start;
            logger.info(">>> Workflow completed successfully in " + duration + " ms.");
        } catch (Exception e)
        {
            logger.error(">>> Workflow Failed!");
            e.printStackTrace();
        }
    }
}