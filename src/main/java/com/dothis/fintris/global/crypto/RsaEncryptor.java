package com.dothis.fintris.global.crypto;

import com.dothis.fintris.global.config.properties.CodefProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

// CODEF 공개키(X.509, Base64)로 금융기관 비밀번호 등을 RSA 암호화한다
@Component
public class RsaEncryptor {

    private final PublicKey publicKey;

    public RsaEncryptor(CodefProperties codefProperties) {
        String encoded = codefProperties.getPublicKey();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("codef.public-key 설정이 비어 있습니다.");
        }
        try {
            // PEM 머리말·줄바꿈이 섞여 있어도 읽을 수 있게 한다
            String base64 = encoded.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            this.publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("codef.public-key가 올바른 RSA 공개키가 아닙니다.", e);
        }
    }

    public String encrypt(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA 암호화에 실패했습니다.", e);
        }
    }
}
