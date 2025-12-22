package com.linkedin.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuration for password encoding.
 * 
 * This class provides a PasswordEncoder bean that uses BCrypt hashing algorithm
 * for securely storing user passwords. BCrypt is the industry standard for
 * password hashing due to its adaptive nature and built-in salting.
 * 
 * Why Password Hashing?
 * 
 * NEVER store passwords in plain text! If database is compromised:
 * 
 * Plain Text (BAD):
 * <pre>
 * {@code
 * Database:
 * | id | email              | password      |
 * |----|--------------------| ------------- |
 * | 1  | john@example.com   | MyPass123     |  ← DISASTER!
 * | 2  | jane@example.com   | SecretPass    |  ← DISASTER!
 * 
 * Attacker gets database → All passwords exposed!
 * Users who reuse passwords → All their accounts compromised!
 * Legal liability → GDPR violations, lawsuits, reputation damage
 * }
 * </pre>
 * 
 * Hashed (GOOD):
 * <pre>
 * {@code
 * Database:
 * | id | email              | password                                                       |
 * |----|--------------------| -------------------------------------------------------------- |
 * | 1  | john@example.com   | $2a$10$N9qo8uLOickgx2ZMRZoMye.IjqV.2KlFHRwH8Z8aBmJx9XK3K7F3e  |
 * | 2  | jane@example.com   | $2a$10$8K1p/a0dL/gAG7/o/YW5a.9bQ6n8jPqK3n5yQJ5kL8lM9nO7pQ8rS  |
 * 
 * Attacker gets database → Can't reverse hashes!
 * Each password has unique salt → Rainbow tables useless!
 * }
 * </pre>
 * 
 * BCrypt Hash Structure:
 * 
 * Example: $2a$10$N9qo8uLOickgx2ZMRZoMye.IjqV.2KlFHRwH8Z8aBmJx9XK3K7F3e
 * 
 * Breaking it down:
 * - $2a$         → BCrypt version identifier
 * - 10$          → Work factor (2^10 = 1,024 rounds)
 * - N9qo8uLOickgx2ZMRZoMye → 22-character salt (random, unique per password)
 * - .IjqV.2KlFHRwH8Z8aBmJx9XK3K7F3e → 31-character hash
 * 
 * Total: 60 characters
 * 
 * Why BCrypt Over Other Algorithms?
 * 
 * MD5 / SHA-1 / SHA-256 (DON'T USE):
 * - ❌ Designed for speed (bad for passwords - easier to brute force)
 * - ❌ No built-in salting (vulnerable to rainbow tables)
 * - ❌ Fixed computational cost (can't adapt to faster hardware)
 * - ❌ MD5 and SHA-1 are cryptographically broken
 * 
 * BCrypt (GOOD):
 * - ✅ Intentionally slow (configurable work factor)
 * - ✅ Built-in salting (unique salt per password)
 * - ✅ Adaptive (increase work factor as hardware improves)
 * - ✅ Industry standard (used by major platforms)
 * 
 * Argon2 (ALSO GOOD, newer):
 * - ✅ Winner of Password Hashing Competition (2015)
 * - ✅ Memory-hard (resistant to GPU/ASIC attacks)
 * - ✅ Configurable memory, time, parallelism
 * - ✅ Future-proof design
 * - ⚠️ Less widely adopted (but growing)
 * 
 * How BCrypt Works:
 * 
 * 1. User Registration:
 * <pre>
 * {@code
 * // User submits password
 * POST /api/register
 * { "password": "MySecurePassword123" }
 * 
 * // BCrypt generates random salt
 * salt = generateRandomSalt(); // "N9qo8uLOickgx2ZMRZoMye"
 * 
 * // BCrypt hashes password with salt (2^10 = 1,024 rounds)
 * hash = bcrypt("MySecurePassword123", salt, workFactor=10);
 * // Result: "$2a$10$N9qo8uLOickgx2ZMRZoMye.IjqV.2KlFHRwH8Z8aBmJx9XK3K7F3e"
 * 
 * // Store hash in database
 * user.setPassword(hash);
 * userRepository.save(user);
 * }
 * </pre>
 * 
 * 2. User Login:
 * <pre>
 * {@code
 * // User submits password
 * POST /api/login
 * { "email": "john@example.com", "password": "MySecurePassword123" }
 * 
 * // Load user from database
 * user = userRepository.findByEmail("john@example.com");
 * storedHash = user.getPassword(); // "$2a$10$N9qo8uLOickgx2ZMRZoMye..."
 * 
 * // BCrypt extracts salt from stored hash
 * salt = extractSalt(storedHash); // "N9qo8uLOickgx2ZMRZoMye"
 * 
 * // BCrypt hashes submitted password with SAME salt
 * newHash = bcrypt("MySecurePassword123", salt, workFactor=10);
 * 
 * // Compare hashes
 * if (newHash.equals(storedHash)) {
 *     // ✅ Password correct!
 * } else {
 *     // ❌ Password wrong!
 * }
 * }
 * </pre>
 * 
 * Work Factor (Strength Parameter):
 * 
 * Work factor = n means 2^n hashing rounds
 * 
 * | Work Factor | Rounds    | Time (approx) | Security Level        |
 * |-------------|-----------|---------------|-----------------------|
 * | 4           | 16        | 0.001 sec     | Too fast (insecure)   |
 * | 8           | 256       | 0.01 sec      | Minimum acceptable    |
 * | 10          | 1,024     | 0.1 sec       | Good (default)        |
 * | 12          | 4,096     | 0.3 sec       | Better                |
 * | 14          | 16,384    | 1.2 sec       | High security         |
 * | 16          | 65,536    | 5 sec         | Very high (too slow?) |
 * 
 * Choosing Work Factor:
 * 
 * Goal: Balance security vs. user experience
 * - Too low: Vulnerable to brute force
 * - Too high: Slow login, high CPU usage, bad UX
 * 
 * Recommendations:
 * - Web applications: 10-12 (100-300ms)
 * - High security: 12-14 (300ms-1s)
 * - Enterprise: 14+ (1s+, acceptable for SSO)
 * 
 * This implementation uses 10 (default), which provides:
 * - ~100ms per hash (good UX)
 * - Strong protection against brute force
 * - Industry standard baseline
 * 
 * Attack Resistance:
 * 
 * 1. Brute Force Attack:
 *    - Attacker tries all possible passwords
 *    - 8-character password: ~6 trillion combinations
 *    - With work factor 10: ~6 trillion * 0.1 sec = 19,000 years
 * 
 * 2. Rainbow Table Attack:
 *    - Pre-computed hash tables for common passwords
 *    - BCrypt salt makes rainbow tables useless
 *    - Each password has unique salt → Must compute fresh for each
 * 
 * 3. Dictionary Attack:
 *    - Attacker tries common passwords (password123, qwerty, etc.)
 *    - BCrypt's slowness makes this impractical
 *    - 1 million common passwords * 0.1 sec = 27 hours per user
 * 
 * Best Practices:
 * 
 * 1. Never log passwords:
 * <pre>
 * {@code
 * // ❌ BAD
 * log.info("User registered with password: {}", password);
 * 
 * // ✅ GOOD
 * log.info("User registered: {}", email);
 * }
 * </pre>
 * 
 * 2. Hash passwords immediately:
 * <pre>
 * {@code
 * // ❌ BAD - password in memory too long
 * User user = new User();
 * user.setEmail(email);
 * user.setName(name);
 * // ... many lines ...
 * user.setPassword(passwordEncoder.encode(password));
 * 
 * // ✅ GOOD - hash immediately
 * String hashedPassword = passwordEncoder.encode(password);
 * User user = new User();
 * user.setPassword(hashedPassword);
 * }
 * </pre>
 * 
 * 3. Never compare passwords with ==:
 * <pre>
 * {@code
 * // ❌ BAD - timing attack vulnerability
 * if (storedHash.equals(submittedHash)) { ... }
 * 
 * // ✅ GOOD - constant-time comparison
 * if (passwordEncoder.matches(submittedPassword, storedHash)) { ... }
 * }
 * </pre>
 * 
 * 4. Clear passwords from memory:
 * <pre>
 * {@code
 * // Use char[] instead of String if possible
 * char[] password = request.getPassword().toCharArray();
 * String hash = passwordEncoder.encode(new String(password));
 * Arrays.fill(password, '\0'); // Clear password from memory
 * }
 * </pre>
 * 
 * Performance Considerations:
 * 
 * 1. Login Endpoint:
 *    - 1 hash operation per login (~100ms)
 *    - Acceptable for user experience
 *    - Consider rate limiting to prevent abuse
 * 
 * 2. Registration Endpoint:
 *    - 1 hash operation per registration (~100ms)
 *    - One-time operation, acceptable delay
 * 
 * 3. Batch Operations:
 *    - Creating 1,000 users = 100 seconds
 *    - Consider async processing for bulk operations
 *    - Or temporarily lower work factor for imports
 * 
 * 4. Password Change:
 *    - 2 hash operations (verify old + hash new)
 *    - ~200ms total, acceptable
 * 
 * Migration from Weaker Algorithms:
 * 
 * If migrating from MD5/SHA-256:
 * <pre>
 * {@code
 * // During login, detect old algorithm
 * if (storedHash.length() != 60) { // Not BCrypt
 *     // Verify with old algorithm
 *     if (verifyMD5(password, storedHash)) {
 *         // Re-hash with BCrypt
 *         String newHash = passwordEncoder.encode(password);
 *         user.setPassword(newHash);
 *         userRepository.save(user);
 *     }
 * }
 * }
 * </pre>
 * 
 * Compliance:
 * 
 * - GDPR: BCrypt meets "appropriate technical measures" requirement
 * - PCI DSS: Satisfies requirement 8.2.1 (strong cryptography)
 * - NIST: Aligns with NIST 800-63B password guidelines
 * - OWASP: Recommended in OWASP Top 10 (A02:2021 Cryptographic Failures)
 * 
 * @see BCryptPasswordEncoder
 * @see PasswordEncoder
 * @see UserFactoryImpl
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * Creates a BCrypt password encoder bean.
     * 
     * BCryptPasswordEncoder Parameters:
     * 
     * 1. No-arg constructor (used here):
     *    - Work factor: 10 (default)
     *    - Salt length: 16 bytes (random)
     *    - Version: $2a$
     * 
     * 2. BCryptPasswordEncoder(int strength):
     *    - Custom work factor
     *    - Example: new BCryptPasswordEncoder(12) → 2^12 = 4,096 rounds
     * 
     * 3. BCryptPasswordEncoder(int strength, SecureRandom random):
     *    - Custom work factor + custom RNG
     *    - For advanced security requirements
     * 
     * Default Configuration:
     * - Work factor: 10 (2^10 = 1,024 rounds)
     * - Hashing time: ~100ms (varies by hardware)
     * - Salt: Automatically generated, unique per password
     * 
     * Bean Lifecycle:
     * - Created once at application startup (singleton)
     * - Thread-safe (can be used concurrently)
     * - Reused for all password encoding operations
     * 
     * Usage Examples:
     * 
     * 1. Encoding (Registration/Password Change):
     * <pre>
     * {@code
     * @Autowired
     * private PasswordEncoder passwordEncoder;
     * 
     * public void registerUser(String password) {
     *     String hash = passwordEncoder.encode(password);
     *     // hash = "$2a$10$N9qo8uLOickgx2ZMRZoMye.IjqV.2KlFHRwH8Z8aBmJx9XK3K7F3e"
     *     user.setPassword(hash);
     *     userRepository.save(user);
     * }
     * }
     * </pre>
     * 
     * 2. Matching (Login/Authentication):
     * <pre>
     * {@code
     * @Autowired
     * private PasswordEncoder passwordEncoder;
     * 
     * public boolean authenticate(String email, String password) {
     *     User user = userRepository.findByEmail(email);
     *     if (user == null) return false;
     *     
     *     // Compare submitted password with stored hash
     *     boolean matches = passwordEncoder.matches(password, user.getPassword());
     *     return matches;
     * }
     * }
     * </pre>
     * 
     * 3. Upgrading Hash (When Increasing Work Factor):
     * <pre>
     * {@code
     * @Autowired
     * private PasswordEncoder passwordEncoder;
     * 
     * public void upgradeHashIfNeeded(User user, String password) {
     *     // Check if hash needs upgrade (old work factor)
     *     if (passwordEncoder.upgradeEncoding(user.getPassword())) {
     *         // Re-hash with new work factor
     *         String newHash = passwordEncoder.encode(password);
     *         user.setPassword(newHash);
     *         userRepository.save(user);
     *     }
     * }
     * }
     * </pre>
     * 
     * Thread Safety:
     * - BCryptPasswordEncoder is thread-safe
     * - Can be safely injected and used in singleton services
     * - No synchronization needed
     * 
     * Performance:
     * - encode(): ~100ms (blocking, CPU-intensive)
     * - matches(): ~100ms (blocking, CPU-intensive)
     * - Consider rate limiting on authentication endpoints
     * - Consider async processing for bulk operations
     * 
     * Security Notes:
     * 
     * 1. Same password, different hashes:
     * <pre>
     * {@code
     * String hash1 = encoder.encode("password123");
     * String hash2 = encoder.encode("password123");
     * // hash1 != hash2 (different salts!)
     * // But both will match "password123"
     * }
     * </pre>
     * 
     * 2. Constant-time comparison:
     *    - BCrypt.matches() uses constant-time comparison
     *    - Prevents timing attacks
     *    - Don't use String.equals() to compare hashes
     * 
     * 3. Salt storage:
     *    - Salt is embedded in the hash string
     *    - No need for separate salt column in database
     *    - Format: $version$workFactor$saltAndHash
     * 
     * Future Improvements:
     * 
     * 1. Make work factor configurable:
     * <pre>
     * {@code
     * @Value("${security.password.work-factor:10}")
     * private int workFactor;
     * 
     * @Bean
     * public PasswordEncoder passwordEncoder() {
     *     return new BCryptPasswordEncoder(workFactor);
     * }
     * }
     * </pre>
     * 
     * 2. Consider Argon2 for new projects:
     * <pre>
     * {@code
     * @Bean
     * public PasswordEncoder passwordEncoder() {
     *     return new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
     * }
     * }
     * </pre>
     * 
     * 3. Implement password strength validation:
     *    - Minimum length (8+ characters)
     *    - Require uppercase, lowercase, digit
     *    - Check against common password lists
     *    - Implement in UserFactoryImpl
     * 
     * @return BCrypt password encoder with default settings (work factor 10)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

