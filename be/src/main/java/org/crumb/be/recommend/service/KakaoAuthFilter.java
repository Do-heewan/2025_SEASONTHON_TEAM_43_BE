package org.crumb.be.recommend.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.crumb.be.service.KakaoLoginService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class KakaoAuthFilter extends OncePerRequestFilter {

    private final KakaoLoginService kakaoLoginService;
    private final AntPathMatcher matcher = new AntPathMatcher();

    // 인증 제외(로그인/문서/헬스체크 등)
    private static final Set<String> EXCLUDE = Set.of(
            "/api/kakao/**",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/actuator/health", "/health"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : EXCLUDE) {
            if (matcher.match(pattern, path)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain
    ) throws ServletException, IOException {

        String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.getWriter().write("{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"Missing Bearer token\"}");
            return;
        }
        String kakaoAccessToken = auth.substring("Bearer ".length()).trim();
        try {
            // Kakao 토큰 → 유저 정보 조회
            var userInfo = kakaoLoginService.getUserInfo(kakaoAccessToken);
            Long userId = userInfo.getId();

            // 컨트롤러에서 꺼내 쓰도록 request attribute에 저장
            req.setAttribute("userId", userId);
            chain.doFilter(req, res);
        } catch (Exception e) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.getWriter().write("{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"Invalid Kakao token\"}");
        }
    }
}
