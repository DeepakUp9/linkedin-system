package com.linkedin.post.model;

import lombok.Getter;

/**
 * Enum representing different types of posts.
 * 
 * Purpose:
 * Categorizes posts by their primary content type.
 * Helps with rendering and interaction logic on frontend.
 * 
 * Post Types:
 * 
 * 1. TEXT:
 *    - Pure text content
 *    - May include hashtags and mentions
 *    - Most common type
 *    - Example: Status updates, thoughts, announcements
 * 
 * 2. IMAGE:
 *    - Text + one or more images
 *    - Images stored in S3 (or local storage)
 *    - Supports captions
 *    - Example: Photos from events, infographics
 * 
 * 3. ARTICLE:
 *    - Long-form content (like LinkedIn articles)
 *    - May have cover image
 *    - Supports rich formatting
 *    - Example: Thought leadership, tutorials
 * 
 * 4. VIDEO:
 *    - Text + video content
 *    - Video stored externally (YouTube link or S3)
 *    - Thumbnail generated
 *    - Example: Presentations, demos
 * 
 * 5. POLL:
 *    - Text + poll options
 *    - Users can vote
 *    - Shows results after voting
 *    - Example: Opinion surveys, feedback requests
 * 
 * Usage in Code:
 * <pre>
 * {@code
 * Post post = Post.builder()
 *     .content("Check out this infographic!")
 *     .type(PostType.IMAGE)
 *     .imageUrls(List.of("https://..."))
 *     .build();
 * 
 * // Frontend logic
 * if (post.getType() == PostType.IMAGE) {
 *     renderImageGallery(post.getImageUrls());
 * }
 * }
 * </pre>
 * 
 * @see Post
 */
@Getter
public enum PostType {
    
    /**
     * Text-only post.
     */
    TEXT("Text", "Text post", true),
    
    /**
     * Post with images.
     */
    IMAGE("Image", "Post with images", true),
    
    /**
     * Long-form article.
     */
    ARTICLE("Article", "Long-form article", true),
    
    /**
     * Video post.
     */
    VIDEO("Video", "Post with video", false), // Future implementation
    
    /**
     * Poll/survey post.
     */
    POLL("Poll", "Poll or survey", false); // Future implementation

    private final String displayName;
    private final String description;
    private final boolean implemented;

    PostType(String displayName, String description, boolean implemented) {
        this.displayName = displayName;
        this.description = description;
        this.implemented = implemented;
    }

    /**
     * Check if this post type is currently supported.
     * 
     * @return true if implemented
     */
    public boolean isImplemented() {
        return implemented;
    }

    /**
     * Check if this type supports multiple media items.
     * 
     * @return true if IMAGE or VIDEO
     */
    public boolean supportsMultipleMedia() {
        return this == IMAGE || this == VIDEO;
    }

    /**
     * Check if this type requires media upload.
     * 
     * @return true if IMAGE or VIDEO
     */
    public boolean requiresMedia() {
        return this == IMAGE || this == VIDEO;
    }

    /**
     * Check if this is a text-based post.
     * 
     * @return true if TEXT or ARTICLE
     */
    public boolean isTextBased() {
        return this == TEXT || this == ARTICLE;
    }
}

