package com.qctv1.ai.chat.support;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class ChatRequestUserResolver {

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";

    private final SecretKey secretKey;
    private final String issuer;

    public ChatRequestUserResolver(
            @Value("${qctv1.chat.auth.jwt-secret:${QCTV1_JWT_SECRET:Qctv1JwtSecretKeyForDevOnlyPleaseChange1234567890}}") String jwtSecret,
            @Value("${qctv1.chat.auth.issuer:${QCTV1_JWT_ISSUER:qctv1-iam}}") String issuer
    ) {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    public Long resolveUserId(String userIdHeader, String authorizationHeader) {
        if (StringUtils.hasText(userIdHeader)) {
            return Long.parseLong(userIdHeader.trim());
        }

        String token = normalizeToken(authorizationHeader);
        if (!StringUtils.hasText(token)) {
            return null;
        }

        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token);
            Claims claims = jws.getPayload();
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            if (!TOKEN_TYPE_ACCESS.equals(tokenType)) {
                return null;
            }
            return Long.parseLong(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }
}
