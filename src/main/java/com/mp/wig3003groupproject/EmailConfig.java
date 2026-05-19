package com.mp.wig3003groupproject;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class EmailConfig {
    private static final String CONFIG_FILE = "email_configuration.example";
    private static final Properties CONFIG = loadConfig();

    public static final String SENDER_EMAIL = getConfigValue("SENDER_EMAIL");
    public static final String APP_PASSWORD = getConfigValue("APP_PASSWORD");
    public static final String SMTP_HOST = getConfigValue("SMTP_HOST", "smtp.gmail.com");
    public static final String SMTP_PORT = getConfigValue("SMTP_PORT", "587");

    private static Properties loadConfig() {
        Properties properties = new Properties();

        try (InputStream inputStream = new FileInputStream(CONFIG_FILE)) {
            properties.load(inputStream);
            return properties;
        } catch (IOException ignored) {
            // Fall back to environment variables when the local file is absent.
        }

        setIfPresent(properties, "SENDER_EMAIL", System.getenv("SENDER_EMAIL"));
        setIfPresent(properties, "APP_PASSWORD", System.getenv("APP_PASSWORD"));
        setIfPresent(properties, "SMTP_HOST", System.getenv("SMTP_HOST"));
        setIfPresent(properties, "SMTP_PORT", System.getenv("SMTP_PORT"));
        return properties;
    }

    private static void setIfPresent(Properties properties, String key, String value) {
        if (value != null && !value.isBlank()) {
            properties.setProperty(key, value);
        }
    }

    private static String getConfigValue(String key) {
        String value = CONFIG.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing email config value: " + key);
        }
        return value;
    }

    private static String getConfigValue(String key, String defaultValue) {
        String value = CONFIG.getProperty(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
