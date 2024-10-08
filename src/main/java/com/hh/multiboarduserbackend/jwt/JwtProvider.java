package com.hh.multiboarduserbackend.jwt;

import com.hh.multiboarduserbackend.exception.CustomException;
import com.hh.multiboarduserbackend.exception.JwtErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtProvider {

    private static final long ACCESS_TOKEN_TIME = 1000 * 60 * 60 * 24 * 30 * 1000; // 한달

    private final JwtProperties jwtProperties;
    private final SecretKey key;
    private final MacAlgorithm alg;

    public JwtProvider(JwtProperties jwtProperties, MacAlgorithm macAlgorithm) {
        this.jwtProperties = jwtProperties;
        this.alg = macAlgorithm;

        var keyBytes = Decoders.BASE64.decode(jwtProperties.secret());
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }


    /**
     * jwt 토큰 생성 메서드
     * @param memberId - auth value
     * @return - jwt token
     */
    public JwtToken generateToken(Long memberId) {

        long now = (new Date(System.currentTimeMillis())).getTime();

        // Access Token 생성
        Date expiration = new Date(now + ACCESS_TOKEN_TIME);
        String accessToken = Jwts.builder()
                .issuer(jwtProperties.issuer())
                .expiration(expiration)
                .claim("auth", memberId)
                .signWith(key, alg)
                .compact();

        return JwtToken.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(null)
                .build();

    }

    /**
     * 토큰 파싱해서 memberId 리턴하는 메서드
     * @param accessToken - token
     * @return - memberId
     */
    public Long getAuthentication(@NotNull String accessToken) {
        Claims claims = parseClaims(accessToken);

        if(claims.get("auth") == null) {
            throw JwtErrorCode.NOT_AUTH_TOKEN_ERROR.defaultException();
        }

        return Long.parseLong(claims.get("auth").toString());
    }

    /**
     * 토큰 정보 검증 메서드
     * @param token - jwt token
     * @return - boolean
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key).build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            throw JwtErrorCode.EXPIRED_ACCESS_TOKEN.defaultException(e);
        } catch (JwtException | IllegalArgumentException e) {
            throw JwtErrorCode.WRONG_ACCESS_TOKEN.defaultException(e);
        }
    }

    /**
     * 토큰 받아서 클레임으로 변환하는 메서드
     * @param accessToken - jwt token
     * @return - claims
     */
    private Claims parseClaims(final String accessToken) {
        try {
            return Jwts.parser()
                    .verifyWith(key).build()
                    .parseSignedClaims(accessToken).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw JwtErrorCode.RESOLVE_TOKEN_ERROR.defaultException(e);
        }

    }
}
