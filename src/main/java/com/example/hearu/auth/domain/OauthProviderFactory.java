package com.example.hearu.auth.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.example.hearu.auth.domain.error.AuthErrorCode;
import com.example.hearu.common.util.exception.BusinessException;

@Slf4j
@Component
public class OauthProviderFactory {

    private final Map<ProviderType, OauthProvider> providerMap = new EnumMap<>(ProviderType.class);

    public OauthProviderFactory(List<OauthProvider> providers) {
        for (OauthProvider provider : providers) {
            providerMap.put(provider.getProviderType(), provider);
        }
    }

    public OauthProvider getProvider(ProviderType type) {
        OauthProvider provider = providerMap.get(type);
        if (provider == null) {
            // 클라이언트가 잘못된 값을 보낸 경우이므로 서버 장애(ERROR)가 아닌 WARN이 적절하다.
            log.warn("지원하지 않는 OAuth Provider 입니다. 요청 provider={}, 지원 목록={}",
                type, providerMap.keySet());
            throw new BusinessException(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
        }
        log.debug("OAuth Provider 선택. provider={}", type);
        return provider;
    }
}