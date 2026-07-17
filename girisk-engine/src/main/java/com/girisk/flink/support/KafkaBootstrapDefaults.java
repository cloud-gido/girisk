package com.girisk.flink.support;

/** Default Kafka bootstrap for local/dev when --bootstrap is omitted. */
public final class KafkaBootstrapDefaults {

    public static final String DEV = "localhost:9092";

    private KafkaBootstrapDefaults() {}
}
