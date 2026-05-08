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
            log.error("지원하지 않는 OAuth Provider 입니다.");
            throw new BusinessException(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
        }
        return provider;
    }
}