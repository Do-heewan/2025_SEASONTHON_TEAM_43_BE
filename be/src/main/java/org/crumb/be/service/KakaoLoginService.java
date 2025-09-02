package org.crumb.be.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class KakaoLoginService {

    private final String clientId;
    private final String clientSecret;
    private final RestClient restClientToken;
    private final RestClient restClientUser;

    private static final String KAUTH_TOKEN_URL_HOST = "https://kauth.kakao.com";
    private static final String KAUTH_USER_URL_HOST = "https://kapi.kakao.com";

    public KakaoLoginService(
            @Value("${kakao.client_id}") String clientId,
            @Value("${kakao.client_secret}") String clientSecret
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;

        // RestClient 초기화
        this.restClientToken = RestClient.builder()
                .baseUrl(KAUTH_TOKEN_URL_HOST)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();

        this.restClientUser = RestClient.builder()
                .baseUrl(KAUTH_USER_URL_HOST)
                .build();
    }

    public String getAccessTokenFromKakao(String code) {
        KakaoTokenResponseDto response = restClientToken.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth/token")
                        .queryParam("grant_type", "authorization_code")
                        .queryParam("client_id", clientId)
                        .queryParam("code", code)
                        .queryParam("client_secret", clientSecret)
                        .build())
                .retrieve()
                .body(KakaoTokenResponseDto.class);

        if (response == null || response.getAccessToken() == null) {
            throw new RuntimeException("Failed to retrieve access token from Kakao");
        }

        log.info("[Kakao Service] Access Token ------> {}", response.getAccessToken());
        return response.getAccessToken();
    }

    public KakaoUserInfoResponseDto getUserInfo(String accessToken) {
        KakaoUserInfoResponseDto userInfo = restClientUser.get()
                .uri("/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(KakaoUserInfoResponseDto.class);

        if (userInfo == null) {
            throw new RuntimeException("Failed to retrieve user info from Kakao");
        }

        return userInfo;
    }

    public String logout(String accessToken) {
        String response = restClientUser.post()
                .uri("/v1/user/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(String.class);

        if (response == null) {
            throw new RuntimeException("Failed to logout from Kakao");
        }

        return response;
    }
}