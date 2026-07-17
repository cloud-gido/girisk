package com.girisk.flink.risk.kafka;

import org.apache.flink.api.common.serialization.SerializationSchema;

import java.nio.charset.StandardCharsets;

/** {@link KafkaOrderMessage} 的 Kafka key/value 序列化（显式类，避免 Flink 无法推断方法引用）。 */
public final class KafkaOrderMessageSerializationSchemas {

    private KafkaOrderMessageSerializationSchemas() {}

    public static final class KeySchema implements SerializationSchema<KafkaOrderMessage> {
        private static final long serialVersionUID = 1L;

        @Override
        public byte[] serialize(KafkaOrderMessage element) {
            if (element == null || element.orderId == null) {
                return null;
            }
            return element.orderId.getBytes(StandardCharsets.UTF_8);
        }
    }

    public static final class ValueSchema implements SerializationSchema<KafkaOrderMessage> {
        private static final long serialVersionUID = 1L;

        @Override
        public byte[] serialize(KafkaOrderMessage element) {
            if (element == null || element.payload == null) {
                return null;
            }
            return element.payload.getBytes(StandardCharsets.UTF_8);
        }
    }
}
