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
            // Handle Supabase connection pooler URL
            try {
                // URL decode the database URL to handle special characters in password
                String decodedUrl = java.net.URLDecoder.decode(databaseUrl, "UTF-8");
                URI uri = new URI(decodedUrl);
                String host = uri.getHost();
                int port = uri.getPort();
                String path = uri.getPath();
                if (path.startsWith("/")) {
                    path = path.substring(1); // Remove leading slash
                }
                String userInfo = uri.getUserInfo();
                String query = uri.getQuery();

                String username = null;
                String password = null;

                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":");
                    username = parts[0];
                    // Re-encode the password to handle special characters
                    password = java.net.URLDecoder.decode(parts[1], "UTF-8");
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