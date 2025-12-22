package com.linkedin.connection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for sending a connection request.
 * Used as the request body for POST /api/connections/requests endpoint.
 * 
 * Design Considerations:
 * - Only includes fields the client needs to provide
 * - Validation ensures data integrity before reaching business logic
 * - Message is optional (empty string allowed)
 * - Requester ID comes from authenticated user (not in DTO)
 * 
 * Example Usage (JSON Request Body):
 * <pre>
 * {@code
 * {
 *   "addresseeId": 456,
 *   "message": "I saw your talk at SpringOne and would love to connect!"
 * }
 * }
 * </pre>
 * 
 * @see com.linkedin.connection.controller.ConnectionController#sendConnectionRequest
 * @see com.linkedin.connection.service.ConnectionService#sendRequest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for sending a connection request")
public class ConnectionRequestDto {

    /**
     * ID of the user to connect with (addressee).
     * Must be a positive number and cannot be null.
     * 
     * Business Rule: Cannot be the same as the authenticated user's ID
     * (validated in service layer, not here).
     */
    @NotNull(message = "Addressee ID cannot be null")
    @Positive(message = "Addressee ID must be a positive number")
    @Schema(
        description = "ID of the user to send connection request to",
        example = "456",
        required = true
    )
    private Long addresseeId;

    /**
     * Optional message explaining why you want to connect.
     * Maximum 500 characters.
     * 
     * Examples:
     * - "I saw your talk at XYZ conference"
     * - "We both work in the same industry"
     * - "" (empty is allowed)
     */
    @Size(max = 500, message = "Message must not exceed 500 characters")
    @Schema(
        description = "Optional message to include with the connection request",
        example = "I saw your talk at SpringOne and would love to connect!",
        maxLength = 500
    )
    private String message;
}

