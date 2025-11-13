package io.gengdy.pan.service;

import io.gengdy.pan.model.Paper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MailSenderService
{
    private final JavaMailSender mailSender;

    @Value("${mailer.sender}")
    private String sender;

    @Value("${mailer.recipients}")
    private String recipients; // comma-separated list

    public MailSenderService(JavaMailSender mailSender)
    {
        this.mailSender = mailSender;
    }

    /**
     * Send a daily digest email containing today's new papers.
     * Each paper includes title, authors, abstract, and AI-generated summary.
     */
    public void sendDailyPaperDigest(List<Paper> papers)
    {
        if (papers == null || papers.isEmpty())
        {
            System.out.println("[MailSenderService] No new papers today, skip sending email.");
            return;
        }

        try
        {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name()
            );

            // Parse multiple recipients
            List<String> recipientList = Arrays.stream(recipients.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            helper.setFrom(sender);
            helper.setTo(recipientList.toArray(new String[0]));
            helper.setSubject("[arXiv Daily Digest] " + papers.size() + " new papers today");

            // Build email content
            StringBuilder html = new StringBuilder();
            html.append("<html><body>");
            html.append("<h2>📚 arXiv Daily Papers</h2>");
            html.append("<p>Here are today's new papers from arXiv:</p>");
            html.append("<hr/>");

            for (Paper paper : papers)
            {
                html.append("<h3><a href='").append(paper.getUrl()).append("'>")
                        .append(paper.getTitle()).append("</a></h3>");
                html.append("<p><strong>Authors:</strong> ").append(paper.getAuthors()).append("</p>");
                html.append("<p><strong>Abstract:</strong> ").append(paper.getAbstractText()).append("</p>");

                if (paper.getAiSummary() != null)
                {
                    html.append("<p><strong>AI Summary:</strong> ")
                            .append(paper.getAiSummary()).append("</p>");
                }

                html.append("<hr/>");
            }

            html.append("<p>Generated automatically by arXiv crawler.</p>");
            html.append("<p>Author: Dongyang Geng</p>");
            html.append("<p>GitHub: <a href='https://github.com/gengdy1545/PAN'>gengdy1545/PAN</a></p>");
            html.append("</body></html>");

            helper.setText(html.toString(), true);
            mailSender.send(message);

            System.out.printf("[MailSenderService] Email successfully sent to %d recipients (%s)%n",
                    recipientList.size(), String.join(", ", recipientList));

        }
        catch (MessagingException e)
        {
            System.err.println("[MailSenderService] Failed to send email: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
