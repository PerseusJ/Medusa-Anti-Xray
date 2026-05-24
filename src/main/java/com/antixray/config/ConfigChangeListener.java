package com.antixray.config;

@FunctionalInterface
public interface ConfigChangeListener {

    void onObfuscationConfigChanged();
}
