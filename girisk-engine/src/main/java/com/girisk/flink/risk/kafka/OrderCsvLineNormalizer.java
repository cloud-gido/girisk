package com.girisk.flink.risk.kafka;

/** CSV 行级归一：逗号、空白、BOM 等。 */
public final class OrderCsvLineNormalizer {

    private OrderCsvLineNormalizer() {}

    public static String normalizeLine(String line) {
        if (line == null) {
            return "";
        }
        String s = line.trim();
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1).trim();
        }
        return s.replace('，', ',');
    }
}
