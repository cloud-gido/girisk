package com.girisk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "girisk.demo")
public class DemoBootstrapProperties {

    /** When true, empty Redis top:worstloss is filled from classpath demo assets on startup. */
    private boolean autoSeed = false;

    private String classpathPrefix = "demo/germany-paraguay";

    public boolean isAutoSeed() {
        return autoSeed;
    }

    public void setAutoSeed(boolean autoSeed) {
        this.autoSeed = autoSeed;
    }

    public String getClasspathPrefix() {
        return classpathPrefix;
    }

    public void setClasspathPrefix(String classpathPrefix) {
        this.classpathPrefix = classpathPrefix;
    }
}
