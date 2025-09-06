package org.crumb.be.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Bean("kakaoClient")
    public WebClient kakaoClient(
            @Value("${kakao.base-url}") String baseUrl,
            @Value("${kakao.rest-api-key}") String kakaoKey) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", kakaoKey.startsWith("KakaoAK ") ? kakaoKey : "KakaoAK " + kakaoKey)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .build();
    }

    @Bean("fastapiClient")
    public WebClient fastapiClient(@Value("${fastapi.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .build();
    }
}
