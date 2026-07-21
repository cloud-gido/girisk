package com.girisk.config;

/**
 * 解析 Redis 连接串（支持 rediss + 密码特殊字符），不依赖 {@link java.net.URI}。
 * 对齐 GISO {@code RedisConnections}，供 ElastiCache / Doppler {@code rediss://:token@host/0} 使用。
 */
public final class RedisUrlParser {

    public record Info(String scheme, String host, int port, String username, String password, int database) {
        public boolean ssl() {
            return "rediss".equalsIgnoreCase(scheme);
        }
    }

    private RedisUrlParser() {
    }

    public static boolean isRedisUri(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.trim().toLowerCase();
        return lower.startsWith("redis://") || lower.startsWith("rediss://");
    }

    public static Info parse(String url, String overridePassword) {
        String trimmed = url == null ? "" : url.trim();
        if (!isRedisUri(trimmed)) {
            throw new IllegalArgumentException("invalid redis url: " + trimmed);
        }
        int at = trimmed.lastIndexOf('@');
        String scheme;
        String auth;
        String tail;
        if (at < 0) {
            scheme = trimmed.toLowerCase().startsWith("rediss://") ? "rediss" : "redis";
            tail = trimmed.substring(trimmed.indexOf("://") + 3);
            auth = "";
        } else {
            String head = trimmed.substring(0, at);
            tail = trimmed.substring(at + 1);
            scheme = head.toLowerCase().startsWith("rediss://") ? "rediss" : "redis";
            auth = head.substring(head.indexOf("://") + 3);
        }

        String username = "";
        String password = "";
        boolean hasEmbeddedPassword = false;
        if (!auth.isEmpty()) {
            if (auth.startsWith(":")) {
                password = auth.substring(1);
                hasEmbeddedPassword = !password.isBlank();
            } else {
                int colon = auth.indexOf(':');
                if (colon > 0) {
                    username = auth.substring(0, colon);
                    password = auth.substring(colon + 1);
                    hasEmbeddedPassword = !password.isBlank();
                } else {
                    password = auth;
                    hasEmbeddedPassword = !password.isBlank();
                }
            }
        }
        password = percentDecode(password == null ? "" : password.trim());
        username = percentDecode(username == null ? "" : username.trim());
        if (!hasEmbeddedPassword && overridePassword != null && !overridePassword.isBlank()) {
            password = overridePassword.trim();
        }

        String hostPort;
        int database = 0;
        int slash = tail.indexOf('/');
        if (slash >= 0) {
            hostPort = tail.substring(0, slash);
            String dbPart = tail.substring(slash + 1);
            if (!dbPart.isBlank()) {
                database = Integer.parseInt(dbPart.trim());
            }
        } else {
            hostPort = tail;
        }

        int port = 6379;
        String host;
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0) {
            host = hostPort.substring(0, colon);
            port = Integer.parseInt(hostPort.substring(colon + 1));
        } else {
            host = hostPort;
        }

        // ElastiCache：强制 TLS + db0，且 AUTH 只传密码
        if (host != null && host.contains(".amazonaws.com")) {
            return new Info("rediss", host, port, "", password, 0);
        }
        return new Info(scheme, host, port, username, password, database);
    }

    public static String redact(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        int at = url.lastIndexOf('@');
        if (at < 0) {
            return url.trim();
        }
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return url.trim();
        }
        return url.substring(0, schemeEnd + 3) + "***@" + url.substring(at + 1);
    }

    private static String percentDecode(String value) {
        if (value == null || value.indexOf('%') < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '%' && i + 2 < value.length()) {
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.append((char) ((hi << 4) + lo));
                    i += 2;
                    continue;
                }
            }
            out.append(ch);
        }
        return out.toString();
    }
}
