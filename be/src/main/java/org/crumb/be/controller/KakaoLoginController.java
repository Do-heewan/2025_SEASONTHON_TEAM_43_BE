package org.crumb.be.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.crumb.be.service.KakaoLoginService;
import org.crumb.be.service.KakaoUserInfoResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kakao")
public class KakaoLoginController {
    final KakaoLoginService kakaoLoginService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> redirect(@RequestParam("code") String code) {
        String token = kakaoLoginService.getAccessTokenFromKakao(code); // code로 token 발급

        KakaoUserInfoResponseDto userInfo = kakaoLoginService.getUserInfo(token); // token으로 user Info 불러오기

        Long userId = userInfo.getId();
        String username = userInfo.getKakaoAccount().getProfile().getNickName();
        String userEmail = userInfo.getKakaoAccount().getEmail();

        Map<String, Object> response = new HashMap<>(); // id, email, name, token 반환
        response.put("accessToken", token);
        response.put("userId", userId);
        response.put("userName", username);
        response.put("userEmail", userEmail);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String authorizationHeader) {

        String accessToken = authorizationHeader.substring("Bearer ".length()).trim();
        String logoutResponse = kakaoLoginService.logout(accessToken);

        System.out.println(logoutResponse);

        return ResponseEntity.ok(logoutResponse);
    }
}
