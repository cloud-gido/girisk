package com.girisk.flink.support.kafka;

import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 让 JAAS / Kafka 客户端从用户作业 JAR 的 ClassLoader 加载，避免落到集群 lib 残缺的 {@code org.apache.kafka.*}。
 */
public final class KafkaClientClassLoaders {

    private static final String SCRAM_SERVER_FIRST_MESSAGE =
            "org.apache.kafka.common.security.scram.internals.ScramMessages$ServerFirstMessage";

    private static final String SCRAM_SASL_CLIENT =
            "org.apache.kafka.common.security.scram.internals.ScramSaslClient";

    private KafkaClientClassLoaders() {}

    public static void useUserCodeClassLoader(Class<?> anchor) {
        ClassLoader cl = anchor.getClassLoader();
        if (cl != null) {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    /**
     * 在独立线程中执行 Kafka Admin 操作，线程 ContextClassLoader 固定为用户 JAR，避免 AdminClient 后台线程落到父
     * ClassLoader。
     */
    public static <T> T callWithUserCodeClassLoader(Class<?> anchor, Callable<T> action, long timeoutSec)
            throws Exception {
        ClassLoader userCl = anchor.getClassLoader();
        ExecutorService executor =
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r, "kafka-user-cl-isolated");
                            if (userCl != null) {
                                t.setContextClassLoader(userCl);
                            }
                            return t;
                        });
        try {
            Future<T> future = executor.submit(action);
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 启动时打印 SCRAM 类与集群 lib 冲突提示（仅日志，不阻断）。 */
    public static void logScramClasspathDiagnostics(Class<?> anchor) {
        ClassLoader userCl = anchor.getClassLoader();
        if (userCl == null) {
            System.err.println("[KafkaClientClassLoaders] 无法获取用户 ClassLoader");
            return;
        }
        boolean userHasScramMessage = canLoad(SCRAM_SERVER_FIRST_MESSAGE, userCl);
        boolean userHasLoginModule = canLoad(KafkaClientConfigs.SCRAM_LOGIN_MODULE, userCl);
        System.out.printf(
                Locale.ROOT,
                "[KafkaClientClassLoaders] userJar=%s ScramMessages=%s ScramLoginModule=%s%n",
                userCl,
                userHasScramMessage ? "OK" : "MISSING",
                userHasLoginModule ? "OK" : "MISSING");

        ClassLoader parent = userCl.getParent();
        if (parent != null && canLoad(SCRAM_SASL_CLIENT, parent)) {
            System.err.println(
                    "[KafkaClientClassLoaders] 警告：集群父 ClassLoader 中存在 org.apache.kafka.ScramSaslClient，"
                            + "极易导致 ScramMessages NoClassDefFoundError。"
                            + " 请删除 $FLINK_HOME/lib/kafka-clients*.jar 后重启 Flink 再提交作业。");
        }
    }

    private static boolean canLoad(String className, ClassLoader cl) {
        try {
            Class.forName(className, false, cl);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
