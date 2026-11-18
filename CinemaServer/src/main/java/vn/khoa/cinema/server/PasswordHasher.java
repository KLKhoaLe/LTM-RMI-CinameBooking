package vn.khoa.cinema.server;

import java.security.*;
import java.util.*;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salt riêng cho từng tài khoản; không lưu mật khẩu gốc. */
final class PasswordHasher {
    private static final int ITERATIONS = 120_000;

    static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt)
                + ":"
                + Base64.getEncoder().encodeToString(derive(password, salt));
    }

    static boolean verify(String password, String encoded) {
        String[] parts = encoded.split(":");
        return MessageDigest.isEqual(
                Base64.getDecoder().decode(parts[1]),
                derive(password, Base64.getDecoder().decode(parts[0])));
    }

    private static byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        } finally {
            spec.clearPassword();
        }
    }
}
