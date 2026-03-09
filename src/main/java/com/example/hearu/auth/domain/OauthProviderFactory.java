package com.example.hearu.auth.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class OauthProviderFactory {

    private final Map<ProviderType, OauthProvider> providerMap = new EnumMap<>(ProviderType.class);

    public OauthProviderFactory(List<OauthProvider> providers) {
        for (OauthProvider provider : providers) {
            providerMap.put(provider.getProviderType(), provider);
        }
    }

    public OauthProvider getProvider(ProviderType type) {
        return providerMap.get(type);
    }
}