package org.crumb.be.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Born-To-Bread API")
                        .version("v1")
                        .description("코스(빵집 리스트) 백엔드"))
                .addServersItem(new Server().url("/"));
    }

    /** 로그인 연동 전: 임시 사용자 ID를 전역 헤더로 추가 */
    @Bean
    public OpenApiCustomizer globalHeaderCustomizer() {
        return openApi -> {
            // 🔧 null-safe: paths 없으면 패스
            if (openApi.getPaths() == null) return;

            openApi.getPaths().values().forEach(path ->
                    path.readOperations().forEach(op ->
                            op.addParametersItem(new Parameter()
                                    .name("X-User-Id")
                                    .description("임시 사용자 ID (JWT 붙이기 전)")
                                    .in("header")
                                    .required(false))));
        };
    }
}
