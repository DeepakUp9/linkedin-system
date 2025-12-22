package com.linkedin.user.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for updating user profile.
 * 
 * All fields are optional (nullable).
 * Only provided (non-null) fields will be updated.
 * 
 * Note: Cannot update email, password, accountType, isActive via this DTO.
 * Those require separate workflows with additional security checks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;

    @Size(max = 255, message = "Headline must not exceed 255 characters")
    private String headline;

    @Size(max = 5000, message = "Summary must not exceed 5000 characters")
    private String summary;

    @Size(max = 255, message = "Location must not exceed 255 characters")
    private String location;

    @Size(max = 512, message = "Profile photo URL must not exceed 512 characters")
    private String profilePhotoUrl;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone number must be valid")
    private String phoneNumber;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Size(max = 100, message = "Industry must not exceed 100 characters")
    private String industry;

    @Size(max = 255, message = "Job title must not exceed 255 characters")
    private String currentJobTitle;

    @Size(max = 255, message = "Company name must not exceed 255 characters")
    private String currentCompany;
}

