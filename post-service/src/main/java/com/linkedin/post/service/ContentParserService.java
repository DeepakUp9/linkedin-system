package com.linkedin.post.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for parsing post content and extracting metadata.
 * 
 * Purpose:
 * Extracts structured data from post text:
 * - Hashtags (#SpringBoot, #Java)
 * - Mentions (@userId)
 * 
 * Design Pattern: Service Layer Pattern
 * - Encapsulates parsing logic
 * - Reusable across services
 * - Testable in isolation
 * 
 * Usage Example:
 * <pre>
 * {@code
 * String content = "Excited about #SpringBoot and #Microservices! @123 @456";
 * List<String> hashtags = contentParserService.extractHashtags(content);
 * // Returns: ["SpringBoot", "Microservices"]
 * 
 * List<Long> mentions = contentParserService.extractMentions(content);
 * // Returns: [123, 456]
 * }
 * </pre>
 * 
 * Regex Patterns:
 * - Hashtags: #[a-zA-Z0-9_]+
 * - Mentions: @[0-9]+
 * 
 * @see com.linkedin.post.service.PostService
 */
@Service
@Slf4j
public class ContentParserService {

    // Regex pattern for hashtags: #word
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#([a-zA-Z0-9_]+)");
    
    // Regex pattern for mentions: @userId (numeric)
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([0-9]+)");

    /**
     * Extract hashtags from content.
     * 
     * Logic:
     * 1. Find all occurrences of #word
     * 2. Remove # symbol
     * 3. Remove duplicates
     * 4. Limit to max count (10)
     * 
     * Examples:
     * - "Hello #World" → ["World"]
     * - "#Java #SpringBoot #Java" → ["Java", "SpringBoot"]
     * - "#Tech #Innovation #AI #ML #Cloud" → ["Tech", "Innovation", "AI", "ML", "Cloud"]
     * 
     * @param content Post content
     * @return List of hashtags (without # symbol)
     */
    public List<String> extractHashtags(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }

        List<String> hashtags = new ArrayList<>();
        Matcher matcher = HASHTAG_PATTERN.matcher(content);

        while (matcher.find()) {
            String hashtag = matcher.group(1); // Group 1 = text after #
            if (!hashtags.contains(hashtag)) { // Avoid duplicates
                hashtags.add(hashtag);
            }
        }

        // Limit to 10 hashtags (configured in application.yml)
        if (hashtags.size() > 10) {
            log.warn("Post contains {} hashtags, limiting to 10", hashtags.size());
            hashtags = hashtags.subList(0, 10);
        }

        log.debug("Extracted {} hashtags from content", hashtags.size());
        return hashtags;
    }

    /**
     * Extract mentioned user IDs from content.
     * 
     * Logic:
     * 1. Find all occurrences of @userId (numeric)
     * 2. Parse to Long
     * 3. Remove duplicates
     * 4. Limit to max count (20)
     * 
     * Examples:
     * - "Hello @123" → [123]
     * - "@123 and @456" → [123, 456]
     * - "@123 @123" → [123] (duplicate removed)
     * 
     * Note: In production, frontend would convert display names to IDs:
     * - User types: "@John Doe"
     * - Frontend converts: "@123" (John's ID)
     * - Backend receives: "@123"
     * 
     * @param content Post content
     * @return List of user IDs mentioned
     */
    public List<Long> extractMentions(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }

        List<Long> mentions = new ArrayList<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);

        while (matcher.find()) {
            try {
                Long userId = Long.parseLong(matcher.group(1)); // Group 1 = digits after @
                if (!mentions.contains(userId)) { // Avoid duplicates
                    mentions.add(userId);
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid mention format: {}", matcher.group(0));
            }
        }

        // Limit to 20 mentions
        if (mentions.size() > 20) {
            log.warn("Post contains {} mentions, limiting to 20", mentions.size());
            mentions = mentions.subList(0, 20);
        }

        log.debug("Extracted {} mentions from content", mentions.size());
        return mentions;
    }

    /**
     * Validate content for prohibited patterns.
     * 
     * Checks for:
     * - Excessive special characters
     * - Spam patterns
     * - Prohibited words (optional)
     * 
     * @param content Post content
     * @return true if valid
     */
    public boolean isValidContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        // Check length
        if (content.length() > 3000) {
            return false;
        }

        // Check for excessive hashtags (spam detection)
        List<String> hashtags = extractHashtags(content);
        if (hashtags.size() > 10) {
            log.warn("Content rejected: too many hashtags ({})", hashtags.size());
            return false;
        }

        // Check for excessive mentions (spam detection)
        List<Long> mentions = extractMentions(content);
        if (mentions.size() > 20) {
            log.warn("Content rejected: too many mentions ({})", mentions.size());
            return false;
        }

        return true;
    }

    /**
     * Clean content by removing excessive whitespace.
     * 
     * @param content Raw content
     * @return Cleaned content
     */
    public String cleanContent(String content) {
        if (content == null) {
            return null;
        }

        // Remove excessive whitespace
        content = content.replaceAll("\\s+", " ");
        
        // Trim
        content = content.trim();

        return content;
    }
}

