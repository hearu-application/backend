package com.example.hearu.common.cert;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cert")
public class CertProperties {

    private String sniHost;
    private String targetHost;
    private int targetPort;
    private int warnThresholdDays;
    private Duration timeout;
}
