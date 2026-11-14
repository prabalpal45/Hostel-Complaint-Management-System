package util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * PasswordUtil
 *
 * Provides secure password hashing using BCrypt and supports transparent
 * migration verification for legacy plaintext passwords.
 */
public class PasswordUtil {

    private static final int BCRYPT_LOG_ROUNDS = 12;

    /**
     * Hashes a plaintext password using BCrypt.
     * If the password appears to already be a valid BCrypt hash, it is returned untouched.
     *
     * @param plainPassword Plaintext password
     * @return 60-character BCrypt hash string
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }
        if (isBCryptHash(plainPassword)) {
            return plainPassword;
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_LOG_ROUNDS));
    }

    /**
     * Verifies a candidate plaintext password against a stored password hash or legacy plaintext.
     *
     * @param candidatePlainPassword The plaintext password entered by the user
     * @param storedHashOrPlaintext The stored password from the database/file
     * @return true if credentials match, false otherwise
     */
    public static boolean checkPassword(String candidatePlainPassword, String storedHashOrPlaintext) {
        if (candidatePlainPassword == null || storedHashOrPlaintext == null) {
            return false;
        }

        if (isBCryptHash(storedHashOrPlaintext)) {
            try {
                return BCrypt.checkpw(candidatePlainPassword, storedHashOrPlaintext);
            } catch (Exception e) {
                return false;
            }
        }

        // Migration fallback: legacy plaintext comparison
        return candidatePlainPassword.equals(storedHashOrPlaintext);
    }

    /**
     * Checks if a string is already a BCrypt hash.
     */
    public static boolean isBCryptHash(String password) {
        if (password == null || password.length() != 60) {
            return false;
        }
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }
}
