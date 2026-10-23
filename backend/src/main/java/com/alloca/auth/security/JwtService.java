package com.alloca.auth.security;

import com.alloca.auth.domain.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.JOSEException;
import java.text.ParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.alloca.auth.exception.InvalidTokenException;
import com.alloca.auth.exception.ExpiredTokenException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;


@Service
public class JwtService {
    private final byte[] secretKeyBytes;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlDays;

    public JwtService(
        @Value("${JWT_SECRET}") String secret,
        @Value("${JWT_ACCESS_TOKEN_TTL_SECONDS:1800}") long accessTokenTtlSeconds,
        @Value("${JWT_REFRESH_TOKEN_TTL_DAYS:7}") long refreshTokenTtlDays
    ){
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public String generateAccessToken(User user){
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtlSeconds, ChronoUnit.SECONDS);

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(user.getId().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("token_type", "access_token")
                .claim("role", user.getRole().name());
        
        if (user.getDepartment() != null){
            builder.claim("department", user.getDepartment().getId().toString());
        }

        return signToken(builder.build());
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(refreshTokenTtlDays, ChronoUnit.DAYS);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.getId().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("token_type", "refresh")
                .claim("credentials_version", user.getCredentialsVersion())
                .build();
        return signToken(claims);
    }

    public long getAccessTokenTtlSeconds(){
        return accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds(){
        return refreshTokenTtlDays * 86400L;
    }

    private String signToken(JWTClaimsSet claims){
        try{
            JWSSigner signer = new MACSigner(secretKeyBytes);
            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJWT.sign(signer);
            return signedJWT.serialize();
        }
        catch(Exception e){
            throw new IllegalStateException("Failed to sign JWT token", e);
        }
    }

    /**
     * Xác thực chữ ký, tính toàn vẹn và thời hạn của Refresh Token.
     * Trả về JWTClaimsSet nếu hợp lệ.
     */
    public JWTClaimsSet parseAndValidateRefreshToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secretKeyBytes);
            // 1. Kiểm tra chữ ký HMAC
            if (!signedJWT.verify(verifier)) {
                throw new InvalidTokenException("Invalid token signature");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // 2. Kiểm tra thời hạn
            Date expirationTime = claims.getExpirationTime();
            if (expirationTime == null || expirationTime.before(new Date())) {
                throw new ExpiredTokenException("Refresh token has expired");
            }

            // 3. Kiểm tra loại token (phải là refresh, không chấp nhận access_token)
            String tokenType = claims.getStringClaim("token_type");
            if (!"refresh".equals(tokenType)) {
                throw new InvalidTokenException("Invalid token type: expected refresh token");
            }

            return claims;
        } catch (ParseException | JOSEException e) {
            // Checked exception từ Nimbus JOSE được bọc thành Unchecked Exception
            throw new InvalidTokenException("Malformed or invalid token format");
        }
    }




    
}
