package com.propertymap.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 验证前端送来的 Google ID token(签名、有效期、audience 必须是本应用的 Client ID)。
 * 验签用的 Google 公钥由库自动拉取并缓存。
 */
@Service
public class GoogleTokenVerifier {

    /** 拿到就是可信信息:email 已经过 Google 验证。 */
    public record GoogleUser(String email, String name) {}

    private final GoogleIdTokenVerifier verifier; // 未配置 client-id 时为 null

    public GoogleTokenVerifier(@Value("${google.client-id:}") String clientId) {
        this.verifier = (clientId == null || clientId.isBlank())
                ? null
                : new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                        .setAudience(List.of(clientId))
                        .build();
    }

    public GoogleUser verify(String idTokenString) {
        if (verifier == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google sign-in is not configured on this server");
        }
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email not verified");
            }
            String name = (String) payload.get("name");
            return new GoogleUser(payload.getEmail(),
                    name != null && !name.isBlank() ? name : payload.getEmail());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token");
        }
    }
}
