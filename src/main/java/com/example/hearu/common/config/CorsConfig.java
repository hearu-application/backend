package com.example.hearu.common.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        // 허용 Origin (추후에 변경). allowCredentials(false)이므로 "*" 하나로 충분하다.
        configuration.setAllowedOrigins(List.of("*"));

        // 클라이언트가 보내는 메서드 허용.
        // PATCH를 빠뜨리면 @PatchMapping(닉네임/AI 설정/앱 잠금) 요청의 preflight가 차단된다.
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));

        // 클라이언트가 보내는 헤더 허용
        configuration.setAllowedHeaders(List.of("*"));

        // 자격 증명 허용 x
        configuration.setAllowCredentials(false);

        // 서버 -> 클라이언트 응답에서 클라이언트가 읽을 수 있게 허용할 헤더
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // 모든 경로에 대해 적용
        return source;
    }
}
