package com.linkedin.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard success response wrapper for all API responses.
 * 
 * This generic DTO ensures consistent response formatting across all microservices,
 * making it easier for frontend applications to handle responses uniformly.
 * 
 * The generic type T represents the actual data being returned.
 * 
 * Example JSON for ApiResponse<UserDTO>:
 * {
 *   "success": true,
 *   "message": "User retrieved successfully",
 *   "data": {
 *     "id": 123,
 *     "name": "John Doe",
 *     "email": "john@example.com"
 *   },
 *   "timestamp": "2025-12-20T10:50:00",
 *   "metadata": {
 *     "page": 1,
 *     "size": 20,
 *     "totalElements": 100
 *   }
 * }
 * 
 * @param <T> The type of data being returned (UserDTO, PostDTO, List<JobDTO>, etc.)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)  // Exclude null fields from JSON
public class ApiResponse<T> {

    /**
     * Indicates whether the request was successful
     * Always true for this class (errors use ErrorResponse)
     */
    private boolean success = true;

    /**
     * Human-readable success message
     * Examples: "User created successfully", "Post updated"
     */
    private String message;

    /**
     * The actual data payload
     * Can be a single object (UserDTO), list (List<PostDTO>), or any type
     */
    private T data;

    /**
     * When the response was generated
     * Format: ISO-8601 (2025-12-20T10:50:00)
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Optional metadata about the response
     * Commonly used for pagination information:
     * - page: Current page number
     * - size: Items per page
     * - totalElements: Total number of items
     * - totalPages: Total number of pages
     */
    private Map<String, Object> metadata;

    /**
     * Convenience method to create a success response with data
     * 
     * @param data    The data to return
     * @param message Success message
     * @param <T>     Type of data
     * @return ApiResponse containing the data
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.message = message;
        response.data = data;
        response.timestamp = LocalDateTime.now();
        return response;
    }

    /**
     * Convenience method to create a success response with data and metadata
     * 
     * @param data     The data to return
     * @param message  Success message
     * @param metadata Additional metadata (e.g., pagination info)
     * @param <T>      Type of data
     * @return ApiResponse containing the data and metadata
     */
    public static <T> ApiResponse<T> success(T data, String message, Map<String, Object> metadata) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.message = message;
        response.data = data;
        response.metadata = metadata;
        response.timestamp = LocalDateTime.now();
        return response;
    }

    /**
     * Convenience method to create a success response with only a message
     * Used for operations that don't return data (e.g., DELETE)
     * 
     * @param message Success message
     * @param <T>     Type parameter (can be Void)
     * @return ApiResponse with message only
     */
    public static <T> ApiResponse<T> success(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.message = message;
        response.timestamp = LocalDateTime.now();
        return response;
    }
}

