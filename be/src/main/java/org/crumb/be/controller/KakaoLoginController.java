package org.crumb.be.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.crumb.be.client.User;
import org.crumb.be.service.JwtService;
import org.crumb.be.service.KakaoLoginService;
import org.crumb.be.service.KakaoUserInfoResponseDto;
import org.crumb.be.service.UserService;
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
    final UserService userService;

    @GetMapping("/login")
    public ResponseEntity<String> login(@RequestParam("code") String code) {
        String token = kakaoLoginService.getAccessTokenFromKakao(code);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/login-bread")
    public ResponseEntity<Map<String, String>> redirect(@RequestHeader("Authorization") String accessToken) {

        // "Bearer {token}" 형식에서 토큰 추출
        String kakaoAccessToken = accessToken.replace("Bearer ", "");
        System.out.println("token: " + kakaoAccessToken);

        // 카카오 API로 사용자 정보 조회
        KakaoUserInfoResponseDto kakaoUser = kakaoLoginService.getUserInfo(kakaoAccessToken);
        if (kakaoUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = kakaoUser.getKakaoAccount().getEmail();
        String nickname = kakaoUser.getKakaoAccount().getProfile().getNickName();

        log.info("User Info: email={}, nickname={}", email, nickname);

        // DB에서 기존 사용자 조회
        User user = userService.findByEmail(email);
        if (user == null) {
            // 신규 사용자 생성 후 저장
            user = new User();
            user.setEmail(email);
            user.setNickname(nickname);
            userService.save(user);
            log.info("신규 사용자 저장 완료: {}", email);
        } else {
            log.info("기존 사용자 로그인: {}", email);
        }

        // 백엔드 JWT 발급
        String backendToken = jwtService.generateToken(email);

        Map<String, String> response = new HashMap<>();
        response.put("accessToken", backendToken);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String authorizationHeader) {

        String accessToken = authorizationHeader.substring("Bearer ".length()).trim();
        String logoutResponse = kakaoLoginService.logout(accessToken);

        return ResponseEntity.ok(logoutResponse);
    }
}
