package com.linkedin.user.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security Configuration for JWT-based authentication.
 * 
 * This configuration sets up:
 * 1. **Stateless Session Management**: No server-side sessions; all authentication is via JWT.
 * 2. **JWT Authentication Filter**: Intercepts requests to validate JWT tokens.
 * 3. **Public Endpoints**: Defines which URLs can be accessed without authentication.
 * 4. **Protected Endpoints**: All other endpoints require authentication.
 * 5. **CORS Configuration**: Allows cross-origin requests from specified origins.
 * 6. **Authentication Provider**: Uses {@link CustomUserDetailsService} and {@link PasswordEncoder}.
 * 7. **JWT Entry Point**: Handles unauthorized access attempts (401 errors).
 *
 * Design Pattern: Configuration/Strategy Pattern
 * - Spring Security uses a chain of filters (Chain of Responsibility pattern).
 * - We inject our custom JWT filter into this chain.
 * - The {@link DaoAuthenticationProvider} uses Strategy pattern to delegate
 *   user loading to {@link CustomUserDetailsService} and password verification
 *   to {@link PasswordEncoder}.
 *
 * Security Flow:
 * 1. Client sends request with "Authorization: Bearer <JWT>" header
 * 2. {@link JwtAuthenticationFilter} intercepts and validates the token
 * 3. If valid, user is authenticated and request proceeds
 * 4. If invalid or missing, {@link JwtAuthenticationEntryPoint} returns 401
 * 5. Controllers can access authenticated user via {@code @AuthenticationPrincipal}
 *
 * @see JwtAuthenticationFilter
 * @see JwtAuthenticationEntryPoint
 * @see CustomUserDetailsService
 * @see JwtTokenProvider
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomUserDetailsService customUserDetailsService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Configures the main security filter chain.
     * This is the core of Spring Security configuration.
     *
     * @param http The {@link HttpSecurity} to configure.
     * @return The configured {@link SecurityFilterChain}.
     * @throws Exception if configuration fails.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Spring Security filter chain for JWT authentication...");

        http
            // Disable CSRF protection (not needed for stateless JWT APIs)
            .csrf(AbstractHttpConfigurer::disable)

            // Configure CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Configure exception handling for unauthorized access
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )

            // Stateless session management (no server-side sessions)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Configure endpoint access rules
            .authorizeHttpRequests(authorize -> authorize
                // Public endpoints (no authentication required)
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/refresh",
                    "/actuator/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/swagger-ui.html",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()

                // Health check endpoint (public for monitoring)
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // All other endpoints require authentication
                .anyRequest().authenticated()
            )

            // Set custom authentication provider
            .authenticationProvider(authenticationProvider())

            // Add JWT authentication filter before Spring's default UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        log.info("Spring Security filter chain configured successfully.");
        return http.build();
    }

    /**
     * Configures the authentication provider.
     * Uses {@link DaoAuthenticationProvider} which delegates to:
     * - {@link CustomUserDetailsService} for loading user details
     * - {@link PasswordEncoder} for password verification
     *
     * @return The configured {@link AuthenticationProvider}.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        log.debug("Creating DaoAuthenticationProvider with CustomUserDetailsService and BCrypt PasswordEncoder");
        
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        
        return authProvider;
    }

    /**
     * Exposes the {@link AuthenticationManager} as a bean.
     * This is required for programmatic authentication in controllers/services
     * (e.g., in {@link com.linkedin.user.service.AuthenticationService#login}).
     *
     * @param config The {@link AuthenticationConfiguration} provided by Spring.
     * @return The {@link AuthenticationManager} instance.
     * @throws Exception if the authentication manager cannot be retrieved.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        log.debug("Exposing AuthenticationManager bean for programmatic authentication");
        return config.getAuthenticationManager();
    }

    /**
     * Configures CORS (Cross-Origin Resource Sharing) settings.
     * Allows the frontend application (running on a different origin) to make API calls.
     *
     * In production, you should:
     * 1. Specify exact allowed origins (not "*")
     * 2. Consider using environment variables for allowed origins
     * 3. Restrict allowed methods and headers to only what's needed
     *
     * @return The {@link CorsConfigurationSource} with configured CORS settings.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        log.debug("Configuring CORS settings");
        
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allowed origins (update for production with actual frontend URLs)
        configuration.setAllowedOrigins(List.of(
            "http://localhost:3000",  // React development server
            "http://localhost:4200",  // Angular development server
            "http://localhost:8080",  // Local testing
            "http://localhost:8081"   // API Gateway (if different port)
        ));
        
        // Allowed HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        
        // Allowed headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers"
        ));
        
        // Exposed headers (headers that the browser can access)
        configuration.setExposedHeaders(Arrays.asList(
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials",
            "Authorization"
        ));
        
        // Allow credentials (cookies, authorization headers, etc.)
        configuration.setAllowCredentials(true);
        
        // Max age for preflight requests (how long browsers can cache CORS settings)
        configuration.setMaxAge(3600L); // 1 hour
        
        // Apply CORS configuration to all endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}

