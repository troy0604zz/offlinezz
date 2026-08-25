package com.example.aibi.domain;

import com.example.aibi.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class DatasourceSecretCipher {
    private static final int IV_BYTES = 12;
    private final String passphrase;
    private final SecureRandom random = new SecureRandom();

    public DatasourceSecretCipher(@Value("${app.security.datasource-key:}") String passphrase) {
        this.passphrase = passphrase == null ? "" : passphrase;
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) return null;
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("数据源密码加密失败", ex);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) return "";
        requireKey();
        try {
            byte[] bytes = Base64.getDecoder().decode(encryptedText.replaceFirst("^v1:", ""));
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("DATASOURCE_PASSWORD_DECRYPT_FAILED", "数据源密码无法解密，请检查 DATASOURCE_ENCRYPTION_KEY", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private SecretKeySpec key() throws Exception {
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(passphrase.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    private void requireKey() {
        if (passphrase.length() < 16) {
            throw new BusinessException("DATASOURCE_KEY_MISSING", "保存独立数据源密码前，请配置至少 16 位的 DATASOURCE_ENCRYPTION_KEY", HttpStatus.BAD_REQUEST);
        }
    }
}
