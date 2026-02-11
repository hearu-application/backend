package com.example.hearu.auth.domain;

import com.example.hearu.auth.dto.request.OauthRequest;
import com.example.hearu.auth.dto.response.OauthUserInfo;

public interface OauthProvider {
    ProviderType getProviderType();
    OauthUserInfo getUserInfoFromOauthServer(OauthRequest request);
}
