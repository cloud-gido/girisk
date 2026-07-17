package com.girisk.flink.support.util;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 轻量命令行参数解析，语义对齐 Flink {@code ParameterTool} 子集。
 *
 * <p>Application Mode 下 {@code main()} 由用户 ClassLoader 执行，集群 {@code flink-core} 不一定对
 * {@code org.apache.flink.util.ParameterTool} 可见；本类打进用户 JAR，避免启动期 NoClassDefFoundError。
 */
public final class CliParameterTool implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, String> data;

    private CliParameterTool(Map<String, String> data) {
        this.data = Collections.unmodifiableMap(new HashMap<>(data));
    }

    public static CliParameterTool fromArgs(String[] args) {
        Map<String, String> map = new HashMap<>(Math.max(16, args.length / 2));
        int i = 0;
        while (i < args.length) {
            String key = keyFromArg(args[i++]);
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Empty argument key in " + java.util.Arrays.toString(args));
            }
            if (i >= args.length) {
                map.put(key, "");
            } else if (isNextKey(args[i])) {
                map.put(key, "");
            } else {
                map.put(key, args[i++]);
            }
        }
        return fromMap(map);
    }

    public static CliParameterTool fromMap(Map<String, String> map) {
        return new CliParameterTool(map);
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    public String get(String key) {
        return data.get(key);
    }

    public String getRequired(String key) {
        String value = data.get(key);
        if (value == null) {
            throw new RuntimeException("No data for required key '" + key + "'");
        }
        return value;
    }

    public String get(String key, String defaultValue) {
        String value = data.get(key);
        return value == null ? defaultValue : value;
    }

    public int getInt(String key) {
        return Integer.parseInt(getRequired(key));
    }

    public int getInt(String key, int defaultValue) {
        String value = data.get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    public long getLong(String key) {
        return Long.parseLong(getRequired(key));
    }

    public long getLong(String key, long defaultValue) {
        String value = data.get(key);
        return value == null ? defaultValue : Long.parseLong(value);
    }

    public double getDouble(String key) {
        return Double.parseDouble(getRequired(key));
    }

    public double getDouble(String key, double defaultValue) {
        String value = data.get(key);
        return value == null ? defaultValue : Double.parseDouble(value);
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(getRequired(key));
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = data.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    public Map<String, String> toMap() {
        return data;
    }

    private static String keyFromArg(String arg) {
        if (arg.startsWith("--")) {
            return arg.substring(2);
        }
        if (arg.startsWith("-")) {
            return arg.substring(1);
        }
        throw new IllegalArgumentException("Argument does not start with '-' or '--': " + arg);
    }

    private static boolean isNextKey(String arg) {
        return arg.startsWith("--") || arg.startsWith("-");
    }
}
