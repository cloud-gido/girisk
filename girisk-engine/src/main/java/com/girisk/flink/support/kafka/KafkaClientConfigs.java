package com.girisk.flink.support.kafka;

import com.girisk.flink.support.KafkaBootstrapDefaults;
import com.girisk.flink.support.util.CliParameterTool;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Kafka 客户端公共配置：bootstrap、SASL_SSL（MSK SCRAM）等。
 *
 * <p>优先级：程序参数 {@code --kafka.*} / {@code --bootstrap} &gt; 环境变量 &gt;
 * 配置文件 &gt; 代码默认。
 *
 * <p>平台无法配参数时：复制 {@code kafka-client.properties.example} 为
 * {@code src/main/resources/kafka-client.properties}，填好后 {@code mvn package} 打进 JAR（该文件已
 * gitignore，勿提交密码）。
 */
public final class KafkaClientConfigs {

    public static final String SCRAM_LOGIN_MODULE =
            "org.apache.kafka.common.security.scram.ScramLoginModule";

    private static final String SCRAM_LOGIN_MODULE_SHADED_LEGACY =
            "org.apache.kafka.common.security.scram.ScramLoginModule";

    private static final String CLASSPATH_CONFIG = "kafka-client.properties";
    private static final String DEFAULT_FS_CONFIG = "/opt/flink/conf/kafka-client.properties";

    private static volatile Properties fileConfig;
    private static volatile String fileConfigSource;

    private KafkaClientConfigs() {}

    /** {@code --bootstrap} → env → 配置文件 → {@link KafkaBootstrapDefaults#DEV} */
    public static String resolveBootstrap(CliParameterTool t) {
        if (t.has("bootstrap")) {
            return t.get("bootstrap").trim();
        }
        String env = System.getenv("INFRA_KAFKA_BOOTSTRAP_SERVERS");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String fromFile = fileProperty("bootstrap.servers", "bootstrap");
        if (!fromFile.isEmpty()) {
            return fromFile;
        }
        return KafkaBootstrapDefaults.DEV;
    }

    /** Flink Kafka Source/Sink 附加属性（不含 bootstrap；含 SASL 与文件中的其它 client 项）。 */
    public static Properties clientProperties(CliParameterTool t) {
        Properties props = securityProperties(t);
        mergeFileClientProperties(props, t);
        applyMskDefaultsIfSecured(props, t);
        return props;
    }

    /** AdminClient / 原生 KafkaProducer 用（含 {@code bootstrap.servers}）。 */
    public static Properties adminClientProperties(CliParameterTool t) {
        Properties props = clientProperties(t);
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, resolveBootstrap(t));
        props.putIfAbsent(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "60000");
        props.putIfAbsent(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "120000");
        return props;
    }

    /** 原生 {@link org.apache.kafka.clients.producer.KafkaProducer} 用（含 bootstrap）。 */
    public static Properties producerProperties(CliParameterTool t) {
        Properties props = securityProperties(t);
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, resolveBootstrap(t));
        return props;
    }

    public static boolean isSecured(CliParameterTool t) {
        String protocol = resolveSecurityProtocol(t);
        return protocol != null
                && !protocol.isEmpty()
                && !"PLAINTEXT".equalsIgnoreCase(protocol);
    }

    public static void logSecuritySummary(CliParameterTool t) {
        ensureFileConfigLoaded(t);
        if (fileConfigSource != null) {
            System.out.printf("[KafkaClientConfigs] 配置文件: %s%n", fileConfigSource);
        }
        String protocol = resolveSecurityProtocol(t);
        if (protocol == null || protocol.isEmpty() || "PLAINTEXT".equalsIgnoreCase(protocol)) {
            System.out.println("[KafkaClientConfigs] security.protocol=PLAINTEXT（未启用 SASL/SSL）");
            return;
        }
        String jaas = resolveJaasConfig(t);
        String loginModuleFlag =
                jaas.contains(SCRAM_LOGIN_MODULE_SHADED_LEGACY)
                        ? "NO(旧shaded-JAR-请重打未relocate包)"
                        : (jaas.contains(SCRAM_LOGIN_MODULE) || jaas.isEmpty() ? "yes" : "custom");
        System.out.printf(
                Locale.ROOT,
                "[KafkaClientConfigs] bootstrap=%s security.protocol=%s sasl.mechanism=%s jaas=%s shadedLoginModule=%s truststore=%s%n",
                resolveBootstrap(t),
                protocol,
                resolveMechanism(t),
                jaasConfigured(t) ? "yes" : "no",
                loginModuleFlag,
                resolveOptional(t, "kafka.ssl.truststore.location", "KAFKA_SSL_TRUSTSTORE_LOCATION")
                        .or(() -> {
                            String f = fileProperty("ssl.truststore.location");
                            return f.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(f);
                        })
                        .orElse("(default JVM CA)"));
    }

    private static Properties securityProperties(CliParameterTool t) {
        Properties props = new Properties();
        String protocol = resolveSecurityProtocol(t);
        if (protocol == null || protocol.isEmpty() || "PLAINTEXT".equalsIgnoreCase(protocol)) {
            return props;
        }
        props.put("security.protocol", protocol);

        String mechanism = resolveMechanism(t);
        if (!mechanism.isEmpty()) {
            props.put("sasl.mechanism", mechanism);
        }

        String jaas = resolveJaasConfig(t);
        if (!jaas.isEmpty()) {
            props.put("sasl.jaas.config", jaas);
        }

        putOptional(props, "ssl.truststore.location", t, "kafka.ssl.truststore.location", "KAFKA_SSL_TRUSTSTORE_LOCATION");
        putOptional(props, "ssl.truststore.password", t, "kafka.ssl.truststore.password", "KAFKA_SSL_TRUSTSTORE_PASSWORD");
        putOptional(
                props,
                "ssl.endpoint.identification.algorithm",
                t,
                "kafka.ssl.endpoint.identification.algorithm",
                "KAFKA_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM");

        putFileOptional(props, "ssl.truststore.location");
        putFileOptional(props, "ssl.truststore.password");
        putFileOptional(props, "ssl.endpoint.identification.algorithm");
        return props;
    }

    /** 将 kafka-client.properties 中其余 Kafka client 项并入（不含 bootstrap / 明文账号）。 */
    private static void mergeFileClientProperties(Properties props, CliParameterTool t) {
        if (t.has("kafka.security.protocol")
                && "PLAINTEXT".equalsIgnoreCase(t.get("kafka.security.protocol").trim())) {
            return;
        }
        ensureFileConfigLoaded(t);
        if (fileConfig == null || fileConfig.isEmpty()) {
            return;
        }
        for (String key : fileConfig.stringPropertyNames()) {
            if (isNonPassThroughFileKey(key)) {
                continue;
            }
            String value = fileConfig.getProperty(key);
            if (value != null && !value.trim().isEmpty()) {
                props.putIfAbsent(key.trim(), value.trim());
            }
        }
    }

    private static boolean isNonPassThroughFileKey(String key) {
        return "bootstrap".equals(key)
                || "bootstrap.servers".equals(key)
                || "sasl.username".equals(key)
                || "sasl.password".equals(key);
    }

    /** MSK + 跨 VPC 场景下 Source Enumerator 的 AdminClient 常用缺省项。 */
    private static void applyMskDefaultsIfSecured(Properties props, CliParameterTool t) {
        if (!isSecured(t)) {
            return;
        }
        props.putIfAbsent("client.dns.lookup", "use_all_dns_ips");
        props.putIfAbsent("request.timeout.ms", "60000");
        props.putIfAbsent("default.api.timeout.ms", "120000");
        props.putIfAbsent("socket.connection.setup.timeout.ms", "30000");
        props.putIfAbsent("metadata.max.age.ms", "300000");
        props.putIfAbsent("reconnect.backoff.ms", "1000");
        props.putIfAbsent("reconnect.backoff.max.ms", "10000");
    }

    private static String resolveSecurityProtocol(CliParameterTool t) {
        return resolve(
                t,
                "kafka.security.protocol",
                "KAFKA_SECURITY_PROTOCOL",
                "security.protocol",
                "");
    }

    private static String resolveMechanism(CliParameterTool t) {
        return resolve(
                t,
                "kafka.sasl.mechanism",
                "KAFKA_SASL_MECHANISM",
                "sasl.mechanism",
                "SCRAM-SHA-512");
    }

    private static String resolveJaasConfig(CliParameterTool t) {
        String jaas = resolve(t, "kafka.sasl.jaas.config", "KAFKA_SASL_JAAS_CONFIG", "sasl.jaas.config", "");
        if (!jaas.isEmpty()) {
            return normalizeJaasModule(jaas);
        }
        String username =
                resolve(
                        t,
                        "kafka.sasl.username",
                        "INFRA_KAFKA_SASL_USERNAME",
                        "sasl.username",
                        resolve(t, "kafka.sasl.username", "KAFKA_SASL_USERNAME", "", ""));
        String password =
                resolve(
                        t,
                        "kafka.sasl.password",
                        "INFRA_KAFKA_SASL_PASSWORD",
                        "sasl.password",
                        resolve(t, "kafka.sasl.password", "KAFKA_SASL_PASSWORD", "", ""));
        if (username.isEmpty() || password.isEmpty()) {
            return "";
        }
        return formatScramJaas(username, password);
    }

    private static boolean jaasConfigured(CliParameterTool t) {
        return !resolveJaasConfig(t).isEmpty();
    }

    private static String formatScramJaas(String username, String password) {
        return String.format(
                Locale.ROOT,
                "%s required username=\"%s\" password=\"%s\";",
                SCRAM_LOGIN_MODULE,
                escapeJaas(username),
                escapeJaas(password));
    }

    /** 将旧版 shaded LoginModule 配置改回标准 {@code org.apache.kafka}（与 fat JAR 内 kafka-clients 一致）。 */
    static String normalizeJaasModule(String jaas) {
        if (jaas == null || jaas.isEmpty()) {
            return jaas;
        }
        if (jaas.contains(SCRAM_LOGIN_MODULE_SHADED_LEGACY)) {
            return jaas.replace(SCRAM_LOGIN_MODULE_SHADED_LEGACY, SCRAM_LOGIN_MODULE);
        }
        return jaas;
    }

    private static String escapeJaas(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String resolve(
            CliParameterTool t,
            String argKey,
            String envKey,
            String fileKey,
            String defaultValue) {
        if (t.has(argKey)) {
            return t.get(argKey).trim();
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String fromFile = fileProperty(fileKey);
        if (!fromFile.isEmpty()) {
            return fromFile;
        }
        return defaultValue == null ? "" : defaultValue;
    }

    private static java.util.Optional<String> resolveOptional(CliParameterTool t, String argKey, String envKey) {
        if (t.has(argKey)) {
            return java.util.Optional.of(t.get(argKey).trim());
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return java.util.Optional.of(env.trim());
        }
        return java.util.Optional.empty();
    }

    private static void putOptional(
            Properties props, String kafkaKey, CliParameterTool t, String argKey, String envKey) {
        resolveOptional(t, argKey, envKey).ifPresent(v -> props.put(kafkaKey, v));
    }

    private static void putFileOptional(Properties props, String kafkaKey) {
        String v = fileProperty(kafkaKey);
        if (!v.isEmpty() && !props.containsKey(kafkaKey)) {
            props.put(kafkaKey, v);
        }
    }

    private static String fileProperty(String... keys) {
        ensureFileConfigLoaded(CliParameterTool.fromArgs(new String[0]));
        if (fileConfig == null) {
            return "";
        }
        for (String key : keys) {
            String v = fileConfig.getProperty(key);
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return "";
    }

    private static void ensureFileConfigLoaded(CliParameterTool t) {
        if (fileConfig != null) {
            return;
        }
        synchronized (KafkaClientConfigs.class) {
            if (fileConfig != null) {
                return;
            }
            if (t.has("kafka.client.config")) {
                Path path = Path.of(t.get("kafka.client.config").trim());
                if (loadFromPath(path)) {
                    return;
                }
                System.err.printf("[KafkaClientConfigs] 无法读取 --kafka.client.config: %s%n", path);
            }
            if (loadFromClasspath()) {
                return;
            }
            loadFromPath(Path.of(DEFAULT_FS_CONFIG));
        }
    }

    private static boolean loadFromClasspath() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = KafkaClientConfigs.class.getClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(CLASSPATH_CONFIG)) {
            if (in == null) {
                return false;
            }
            Properties p = new Properties();
            p.load(in);
            fileConfig = p;
            fileConfigSource = "classpath:" + CLASSPATH_CONFIG;
            return true;
        } catch (IOException e) {
            System.err.printf("[KafkaClientConfigs] 读取 classpath 配置失败: %s%n", e.getMessage());
            fileConfig = new Properties();
            return false;
        }
    }

    private static boolean loadFromPath(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try (InputStream in = Files.newInputStream(path)) {
            Properties p = new Properties();
            p.load(in);
            fileConfig = p;
            fileConfigSource = path.toString();
            return true;
        } catch (IOException e) {
            System.err.printf("[KafkaClientConfigs] 读取配置失败 %s: %s%n", path, e.getMessage());
            fileConfig = new Properties();
            return false;
        }
    }

    /** 供测试：consumer 属性名常量暴露。 */
    static String consumerBootstrapKey() {
        return ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG;
    }
}
