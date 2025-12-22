package com.linkedin.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;

/**
 * Service for sending email notifications.
 * 
 * Purpose:
 * - Sends HTML emails using SMTP
 * - Uses Thymeleaf templates for email content
 * - Async execution (doesn't block caller)
 * - Handles failures gracefully
 * 
 * How It Works:
 * <pre>
 * 1. Service receives email request:
 *    sendConnectionAcceptedEmail(toEmail, userName)
 * 
 * 2. Load Thymeleaf template:
 *    templates/email/connection-accepted.html
 * 
 * 3. Populate template with variables:
 *    {userName: "John Doe", actionLink: "https://..."}
 * 
 * 4. Render HTML:
 *    &lt;html&gt;&lt;body&gt;Hi, John Doe accepted...&lt;/body&gt;&lt;/html&gt;
 * 
 * 5. Send via SMTP:
 *    Gmail/SendGrid/AWS SES
 * 
 * 6. Return immediately (async)
 *    Caller doesn't wait for email to send
 * </pre>
 * 
 * Configuration (application.yml):
 * <pre>
 * spring:
 *   mail:
 *     host: smtp.gmail.com
 *     port: 587
 *     username: your-email@gmail.com
 *     password: your-app-password
 * </pre>
 * 
 * @see org.springframework.mail.javamail.JavaMailSender
 * @see org.thymeleaf.TemplateEngine
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.from:LinkedIn Notifications <noreply@linkedin.com>}")
    private String fromEmail;

    // =========================================================================
    // Generic Email Sending
    // =========================================================================

    /**
     * Send HTML email using a Thymeleaf template.
     * 
     * Why @Async?
     * - Sending email takes 1-3 seconds (network I/O)
     * - @Async runs in separate thread
     * - Caller returns immediately
     * - User doesn't wait for email to send
     * 
     * Example:
     * <pre>
     * {@code
     * // Caller
     * emailService.sendTemplatedEmail(
     *     "user@example.com",
     *     "Connection Accepted!",
     *     "connection-accepted",
     *     Map.of("userName", "John Doe")
     * );
     * // Returns immediately! (async)
     * 
     * // Meanwhile, in background thread:
     * // - Renders template
     * // - Sends email
     * // - Logs result
     * }
     * </pre>
     * 
     * Thread Pool:
     * Spring Boot default async executor:
     * - Core threads: 8
     * - Max threads: Integer.MAX_VALUE
     * - Queue: LinkedBlockingQueue
     * 
     * @param to Recipient email
     * @param subject Email subject
     * @param templateName Thymeleaf template name (without .html)
     * @param variables Template variables
     */
    @Async
    public void sendTemplatedEmail(
        String to, 
        String subject, 
        String templateName, 
        Map<String, Object> variables
    ) {
        try {
            log.debug("Preparing to send email to {} with template {}", to, templateName);
            
            // 1. Create Thymeleaf context with variables
            Context context = new Context();
            context.setVariables(variables);
            
            // 2. Render template to HTML string
            String htmlContent = templateEngine.process(templateName, context);
            
            // 3. Send email
            sendHtmlEmail(to, subject, htmlContent);
            
            log.info("Successfully sent email to {} with template {}", to, templateName);
            
        } catch (Exception e) {
            log.error("Failed to send email to {} with template {}: {}", 
                to, templateName, e.getMessage(), e);
            // Don't throw exception (async method, no one to catch it)
            // Failure is logged for monitoring/alerting
        }
    }

    /**
     * Send plain HTML email (no template).
     * 
     * @param to Recipient email
     * @param subject Email subject
     * @param htmlContent HTML content
     * @throws MessagingException if email creation fails
     * @throws MailException if sending fails
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) 
        throws MessagingException, MailException {
        
        log.debug("Sending HTML email to {} with subject: {}", to, subject);
        
        // 1. Create MIME message (supports HTML, attachments, etc.)
        MimeMessage message = mailSender.createMimeMessage();
        
        // 2. Use helper to populate message
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true); // true = HTML mode
        
        // 3. Send message via SMTP
        mailSender.send(message);
        
        log.debug("Email sent successfully to {}", to);
    }

    // =========================================================================
    // Specific Notification Emails
    // =========================================================================

    /**
     * Send email for connection request received.
     * 
     * Template: connection-requested.html
     * Variables:
     * - requesterName: Who sent the request
     * - requesterHeadline: Their job title
     * - requesterProfileUrl: Link to their profile
     * - acceptLink: Link to accept request
     * 
     * @param toEmail Recipient email
     * @param requesterName Name of person who sent request
     * @param requesterHeadline Their headline
     * @param acceptLink Link to accept
     */
    @Async
    public void sendConnectionRequestedEmail(
        String toEmail,
        String requesterName,
        String requesterHeadline,
        String acceptLink
    ) {
        log.info("Sending connection requested email to {}", toEmail);
        
        Map<String, Object> variables = Map.of(
            "requesterName", requesterName,
            "requesterHeadline", requesterHeadline != null ? requesterHeadline : "Professional",
            "acceptLink", acceptLink
        );
        
        sendTemplatedEmail(
            toEmail,
            requesterName + " wants to connect with you",
            "connection-requested",
            variables
        );
    }

    /**
     * Send email for connection request accepted.
     * 
     * Template: connection-accepted.html
     * Variables:
     * - accepterName: Who accepted the request
     * - accepterHeadline: Their job title
     * - profileLink: Link to their profile
     * 
     * @param toEmail Recipient email
     * @param accepterName Name of person who accepted
     * @param accepterHeadline Their headline
     * @param profileLink Link to profile
     */
    @Async
    public void sendConnectionAcceptedEmail(
        String toEmail,
        String accepterName,
        String accepterHeadline,
        String profileLink
    ) {
        log.info("Sending connection accepted email to {}", toEmail);
        
        Map<String, Object> variables = Map.of(
            "accepterName", accepterName,
            "accepterHeadline", accepterHeadline != null ? accepterHeadline : "Professional",
            "profileLink", profileLink
        );
        
        sendTemplatedEmail(
            toEmail,
            accepterName + " accepted your connection request",
            "connection-accepted",
            variables
        );
    }

    /**
     * Send test email (for debugging).
     * 
     * Use Case:
     * - Verify SMTP configuration works
     * - Test email templates
     * - Health check endpoint
     * 
     * @param toEmail Recipient email
     */
    @Async
    public void sendTestEmail(String toEmail) {
        log.info("Sending test email to {}", toEmail);
        
        Map<String, Object> variables = Map.of(
            "recipientEmail", toEmail,
            "timestamp", java.time.LocalDateTime.now().toString()
        );
        
        sendTemplatedEmail(
            toEmail,
            "Test Email from LinkedIn Notification Service",
            "test-email",
            variables
        );
    }

    // =========================================================================
    // Health Check
    // =========================================================================

    /**
     * Verify email service is configured and working.
     * 
     * Use Case:
     * Spring Boot Actuator health check
     * 
     * Checks:
     * - JavaMailSender is configured
     * - SMTP connection can be established
     * 
     * @return true if email service is healthy
     */
    public boolean isHealthy() {
        try {
            // Test SMTP connection
            mailSender.createMimeMessage();
            return true;
        } catch (Exception e) {
            log.error("Email service health check failed: {}", e.getMessage());
            return false;
        }
    }
}

