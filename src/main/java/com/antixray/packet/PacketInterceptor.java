package com.antixray.packet;

public interface PacketInterceptor {

    void register();

    void unregister();

    boolean isAvailable();

    InterceptionMode getMode();
}
