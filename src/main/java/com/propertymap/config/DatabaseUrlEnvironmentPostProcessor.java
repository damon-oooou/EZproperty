package com.propertymap.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * v0.6 阶段 C:把 Railway 注入的 DATABASE_URL(postgresql://user:pass@host:port/db)
 * 映射为 Spring 需要的 jdbc:postgresql:// URL + 独立的用户名/密码属性。
 *
 * 通过 META-INF/spring.factories 注册,在配置装载早期运行。
 * dev 下没有 DATABASE_URL 环境变量,本类是 no-op,application.yml 的 localhost 配置照常生效。
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        String raw = environment.getProperty("DATABASE_URL");
        if (raw == null || raw.isBlank()) {
            return; // dev:无此变量,不做任何事
        }

        Map<String, Object> props = new HashMap<>();
        if (raw.startsWith("jdbc:")) {
            // 已经是 jdbc 形式,直接透传
            props.put("spring.datasource.url", raw);
        } else {
            // postgresql://user:pass@host:port/db?params
            URI uri = URI.create(raw);
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String path = uri.getPath() == null ? "" : uri.getPath();
            String query = uri.getQuery();
            props.put("spring.datasource.url",
                    "jdbc:postgresql://" + host + ":" + port + path
                            + (query == null ? "" : "?" + query));

            String userInfo = uri.getUserInfo();
            if (userInfo != null) {
                int colon = userInfo.indexOf(':');
                String user = colon >= 0 ? userInfo.substring(0, colon) : userInfo;
                String pass = colon >= 0 ? userInfo.substring(colon + 1) : "";
                props.put("spring.datasource.username",
                        URLDecoder.decode(user, StandardCharsets.UTF_8));
                props.put("spring.datasource.password",
                        URLDecoder.decode(pass, StandardCharsets.UTF_8));
            }
        }
        // addFirst:优先级高于 application*.yml,确保覆盖 dev 的 localhost 配置
        environment.getPropertySources().addFirst(
                new MapPropertySource("databaseUrlMapping", props));
    }
}
