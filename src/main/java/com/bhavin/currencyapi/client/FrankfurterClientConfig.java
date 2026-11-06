package com.bhavin.currencyapi.client;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(FrankfurterProperties.class)
public class FrankfurterClientConfig {

    /** HTTP client with explicit timeouts, so a slow provider can never hang our threads. */
    @Bean
    public RestClient frankfurterRestClient(FrankfurterProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(props.readTimeout());
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
