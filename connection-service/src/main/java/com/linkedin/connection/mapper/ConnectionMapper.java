package com.linkedin.connection.mapper;

import com.linkedin.connection.dto.ConnectionRequestDto;
import com.linkedin.connection.dto.ConnectionResponseDto;
import com.linkedin.connection.dto.PendingRequestDto;
import com.linkedin.connection.model.Connection;
import com.linkedin.user.client.UserServiceClient;
import com.linkedin.user.dto.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * MapStruct mapper for converting between Connection entities and DTOs.
 * Now with REAL user-service integration via Feign!
 * 
 * @see Connection
 * @see ConnectionResponseDto
 * @see PendingRequestDto
 * @see UserServiceClient
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.WARN,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
@Slf4j
public abstract class ConnectionMapper {

    @Autowired
    protected UserServiceClient userServiceClient;

    // =========================================================================
    // Entity → Response DTO Mappings
    // =========================================================================

    @Mapping(target = "requester", expression = "java(mapToUserSummary(connection.getRequesterId()))")
    @Mapping(target = "addressee", expression = "java(mapToUserSummary(connection.getAddresseeId()))")
    public abstract ConnectionResponseDto toResponseDto(Connection connection);

    public abstract List<ConnectionResponseDto> toResponseDtoList(List<Connection> connections);

    // =========================================================================
    // Entity → Pending Request DTO Mappings
    // =========================================================================

    public PendingRequestDto toPendingRequestDto(Connection connection, Long currentUserId) {
        if (connection == null) {
            return null;
        }

        boolean isSentByMe = connection.getRequesterId().equals(currentUserId);
        Long otherUserId = isSentByMe ? connection.getAddresseeId() : connection.getRequesterId();

        return PendingRequestDto.builder()
            .connectionId(connection.getId())
            .otherUser(mapToOtherUserSummary(otherUserId))
            .message(connection.getMessage())
            .requestedAt(connection.getRequestedAt())
            .isSentByMe(isSentByMe)
            .mutualConnections(0) // Will be populated by ConnectionService
            .build();
    }

    public List<PendingRequestDto> toPendingRequestDtoList(List<Connection> connections, Long currentUserId) {
        if (connections == null) {
            return null;
        }
        return connections.stream()
            .map(conn -> toPendingRequestDto(conn, currentUserId))
            .toList();
    }

    // =========================================================================
    // DTO → Entity Mappings
    // =========================================================================

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "requesterId", source = "requesterId")
    @Mapping(target = "addresseeId", source = "dto.addresseeId")
    @Mapping(target = "state", constant = "PENDING")
    @Mapping(target = "message", source = "dto.message")
    @Mapping(target = "requestedAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "respondedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract Connection toEntity(ConnectionRequestDto dto, Long requesterId);

    // =========================================================================
    // Helper Methods - NOW WITH REAL USER-SERVICE CALLS!
    // =========================================================================

    /**
     * Fetches user details from user-service and maps to UserSummary.
     * 
     * @param userId The user ID
     * @return UserSummary with real data from user-service
     */
    protected ConnectionResponseDto.UserSummary mapToUserSummary(Long userId) {
        if (userId == null) {
            return null;
        }

        try {
            UserResponse user = userServiceClient.getUserById(userId);
            return ConnectionResponseDto.UserSummary.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .headline(user.getHeadline())
                .profilePictureUrl(user.getProfilePictureUrl())
                .build();
        } catch (Exception ex) {
            log.warn("Failed to fetch user details for userId {}: {}", userId, ex.getMessage());
            // Fallback to minimal data if user-service is down
            return ConnectionResponseDto.UserSummary.builder()
                .userId(userId)
                .name("User " + userId)
                .email("unknown@example.com")
                .headline("Professional")
                .build();
        }
    }

    /**
     * Fetches user details from user-service and maps to OtherUserSummary.
     * 
     * @param userId The user ID
     * @return OtherUserSummary with real data from user-service
     */
    protected PendingRequestDto.OtherUserSummary mapToOtherUserSummary(Long userId) {
        if (userId == null) {
            return null;
        }

        try {
            UserResponse user = userServiceClient.getUserById(userId);
            return PendingRequestDto.OtherUserSummary.builder()
                .userId(user.getId())
                .name(user.getName())
                .headline(user.getHeadline())
                .profilePictureUrl(user.getProfilePictureUrl())
                .accountType(user.getAccountType())
                .build();
        } catch (Exception ex) {
            log.warn("Failed to fetch user details for userId {}: {}", userId, ex.getMessage());
            // Fallback to minimal data if user-service is down
            return PendingRequestDto.OtherUserSummary.builder()
                .userId(userId)
                .name("User " + userId)
                .headline("Professional")
                .accountType(com.linkedin.user.model.AccountType.BASIC)
                .build();
        }
    }
}
