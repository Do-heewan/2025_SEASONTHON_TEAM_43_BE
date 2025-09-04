package org.crumb.be.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.crumb.be.service.JwtService;
import org.crumb.be.service.KakaoLoginService;
import org.crumb.be.service.KakaoUserInfoResponseDto;
import org.springframework.http.HttpStatus;
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
    final JwtService jwtService;

    @GetMapping("/login")
    public ResponseEntity<String> login(@RequestParam("code") String code) {
        String token = kakaoLoginService.getAccessTokenFromKakao(code);

        KakaoUserInfoResponseDto userInfo = kakaoLoginService.getUserInfo(token); // token으로 user Info 불러오기
//        Long userId = userInfo.getId();
//        String username = userInfo.getKakaoAccount().getProfile().getNickName();
//        String userEmail = userInfo.getKakaoAccount().getEmail();
//
//        Map<String, Object> response = new HashMap<>(); // id, email, name, token 반환
//        response.put("accessToken", token);
//        response.put("userId", userId);
//        response.put("userName", username);
//        response.put("userEmail", userEmail);

        return ResponseEntity.ok(token);
    }

    @PostMapping("/login-bread")
    public ResponseEntity<Map<String, String>> redirect(@RequestHeader("Authorization") String accessToken) {

        // "Bearer {token}" 형식에서 토큰 추출
        String kakaoAccessToken = accessToken.replace("Bearer ", "");
        System.out.println("token: " + kakaoAccessToken);

        // 1. 카카오 API로 사용자 정보 조회
        KakaoUserInfoResponseDto kakaoUser = kakaoLoginService.getUserInfo(kakaoAccessToken);
        if (kakaoUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        System.out.println("User Info: " + kakaoUser.getKakaoAccount().getProfile().getNickName());

//        // 2. 사용자 DB 저장 또는 조회
//        User user = userService.findOrCreateUser(kakaoUser);

        // 3. 백엔드 JWT 발급
        String backendToken = jwtService.generateToken(kakaoUser.getKakaoAccount().getEmail());

        Map<String, String> response = new HashMap<>();
        response.put("accessToken", backendToken);

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
