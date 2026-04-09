package com.taskflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.jdbc.DataSourceBuilder;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
@Profile("prod")
public class DatabaseConfig {

    @Value("${DATABASE_URL:#{null}}")
    private String databaseUrl;

    @Bean
    @Primary
    public DataSource dataSource() {
        if (databaseUrl != null && databaseUrl.startsWith("postgresql://")) {
            // Handle Supabase connection pooler URL without relying on URI parsing
            try {
                String raw = databaseUrl.substring("postgresql://".length());
                int atIndex = raw.lastIndexOf('@');
                if (atIndex < 0) {
                    throw new IllegalArgumentException("Invalid pooler URL, missing '@' separator");
                }

                String userInfo = raw.substring(0, atIndex);
                String hostPart = raw.substring(atIndex + 1);
                String username = null;
                String password = null;

                int colonIndex = userInfo.indexOf(':');
                if (colonIndex >= 0) {
                    username = userInfo.substring(0, colonIndex);
                    password = userInfo.substring(colonIndex + 1);
                }

                int slashIndex = hostPart.indexOf('/');
                if (slashIndex < 0) {
                    throw new IllegalArgumentException("Invalid pooler URL, missing '/' after host");
                }

                String hostPort = hostPart.substring(0, slashIndex);
                String pathAndQuery = hostPart.substring(slashIndex + 1);
                String host;
                int port = 5432;

                int hostColon = hostPort.lastIndexOf(':');
                if (hostColon >= 0) {
                    host = hostPort.substring(0, hostColon);
                    String portText = hostPort.substring(hostColon + 1);
                    if (!portText.isEmpty()) {
                        port = Integer.parseInt(portText);
                    }
                } else {
                    host = hostPort;
                }

                String path = pathAndQuery;
                String query = null;
                int queryIndex = pathAndQuery.indexOf('?');
                if (queryIndex >= 0) {
                    path = pathAndQuery.substring(0, queryIndex);
                    query = pathAndQuery.substring(queryIndex + 1);
                }

                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + path;
                if (query != null && !query.isEmpty()) {
                    jdbcUrl += "?" + query;
                }

                return DataSourceBuilder.create()
                    .url(jdbcUrl)
                    .username(username)
                    .password(password)
                    .driverClassName("org.postgresql.Driver")
                    .build();

            } catch (Exception e) {
                System.err.println("Failed to parse DATABASE_URL: " + databaseUrl);
                e.printStackTrace();
                throw new RuntimeException("Failed to parse DATABASE_URL: " + databaseUrl, e);
            }
        } else {
            // Handle regular JDBC URL
            return DataSourceBuilder.create()
                .url(databaseUrl)
                .username(System.getenv("DATABASE_USERNAME"))
                .password(System.getenv("DATABASE_PASSWORD"))
                .driverClassName("org.postgresql.Driver")
                .build();
        }
    }
}