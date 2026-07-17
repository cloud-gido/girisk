package com.girisk.flink.risk.runtime;

import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.StateBackendOptions;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import com.girisk.flink.support.util.CliParameterTool;

import java.util.Locale;

/**
 * 生产环境 Flink 运行时：RocksDB 状态后端 + 周期性 checkpoint（Flink 2.0 Configuration API）。
 *
 * <p>集群 {@code flink-conf.yaml} 已配置 checkpoint 目录时，可不传 {@code --checkpoint.dir}；否则建议在
 * Program Args 或集群默认里指定持久化路径（HDFS / S3 / 共享 NFS）。
 */
public final class FlinkProductionRuntime {

    private FlinkProductionRuntime() {}

    /** 将 checkpoint、状态后端写入 {@link StreamExecutionEnvironment}。 */
    public static void apply(StreamExecutionEnvironment env, CliParameterTool t) {
        boolean checkpointEnabled = t.getBoolean("checkpoint.enabled", true);
        if (!checkpointEnabled) {
            System.err.println("[FlinkProductionRuntime] checkpoint.enabled=false，仅适合本地调试");
            return;
        }

        Configuration conf = new Configuration();
        String checkpointDir = resolveCheckpointDir(t);
        if (!checkpointDir.isEmpty()) {
            conf.set(CheckpointingOptions.CHECKPOINT_STORAGE, "filesystem");
            conf.set(CheckpointingOptions.CHECKPOINTS_DIRECTORY, checkpointDir);
        }

        String backend = t.get("state.backend", "rocksdb").trim().toLowerCase(Locale.ROOT);
        boolean incremental = t.getBoolean("state.backend.incremental", true);
        if ("hashmap".equals(backend) || "heap".equals(backend)) {
            conf.set(StateBackendOptions.STATE_BACKEND, "hashmap");
            System.err.println("[FlinkProductionRuntime] state.backend=hashmap（小状态/本地调试）");
        } else {
            conf.set(StateBackendOptions.STATE_BACKEND, "rocksdb");
            if (incremental) {
                conf.set(CheckpointingOptions.INCREMENTAL_CHECKPOINTS, true);
            }
        }

        env.configure(conf);

        long intervalMs = t.getLong("checkpoint.intervalMs", 60_000L);
        env.enableCheckpointing(intervalMs);

        CheckpointConfig cc = env.getCheckpointConfig();
        cc.setCheckpointingMode(
                t.getBoolean("checkpoint.exactlyOnce", true)
                        ? CheckpointingMode.EXACTLY_ONCE
                        : CheckpointingMode.AT_LEAST_ONCE);
        cc.setMinPauseBetweenCheckpoints(t.getLong("checkpoint.minPauseMs", 30_000L));
        cc.setCheckpointTimeout(t.getLong("checkpoint.timeoutMs", 600_000L));
        cc.setMaxConcurrentCheckpoints(t.getInt("checkpoint.maxConcurrent", 1));
        cc.setTolerableCheckpointFailureNumber(t.getInt("checkpoint.tolerableFailures", 3));

        if (checkpointDir.isEmpty()) {
            System.err.println(
                    "[FlinkProductionRuntime] 未设置 --checkpoint.dir，使用集群 flink-conf 中的"
                            + " execution.checkpointing.dir");
        }
        System.err.printf(
                Locale.ROOT,
                "[FlinkProductionRuntime] backend=%s checkpointIntervalMs=%d incremental=%s%n",
                backend,
                intervalMs,
                incremental);
    }

    private static String resolveCheckpointDir(CliParameterTool t) {
        if (t.has("checkpoint.dir")) {
            return t.get("checkpoint.dir").trim();
        }
        if (t.has("checkpoint.storage")) {
            return t.get("checkpoint.storage").trim();
        }
        return "";
    }
}
