package com.ailabs.registration;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Hash de credenciales (contraseña y OTP) con PBKDF2 y sal aleatoria, formato {@code iteraciones$sal$hash}.
 * Los tokens de sesión, que ya son aleatorios de 256 bits, se guardan con SHA-256 ({@link #sha256}).
 */
@ApplicationScoped
public class CredentialHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final SecureRandom random = new SecureRandom();

    @ConfigProperty(name = "registration.hash.iterations")
    int iterations;

    private volatile String dummyHash;

    public String hash(String secret) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] derived = derive(secret, salt, iterations);
        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        return iterations + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derived);
    }

    /** Comparación en tiempo constante; un hash malformado o un secreto nulo nunca coinciden. */
    public boolean matches(String secret, String stored) {
        if (secret == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 3) {
            return false;
        }
        try {
            int storedIterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            return MessageDigest.isEqual(expected, derive(secret, salt, storedIterations));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Hash válido para comparar cuando el cliente no existe y así igualar el tiempo de respuesta. */
    public String dummyHash() {
        String current = dummyHash;
        if (current == null) {
            current = hash("dummy-credential");
            dummyHash = current;
        }
        return current;
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static byte[] derive(String secret, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 no disponible", e);
        } finally {
            spec.clearPassword();
        }
    }
}
