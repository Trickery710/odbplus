package com.odbplus.core.transport.di;

import com.odbplus.core.transport.*;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Named;
import javax.inject.Singleton;

@dagger.Module()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\'\u0018\u0000 \u00072\u00020\u0001:\u0001\u0007B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\'\u00a8\u0006\b"}, d2 = {"Lcom/odbplus/core/transport/di/TransportModule;", "", "()V", "bindTransportRepository", "Lcom/odbplus/core/transport/TransportRepository;", "impl", "Lcom/odbplus/core/transport/TransportRepositoryImpl;", "Companion", "core-transport_debug"})
@dagger.hilt.InstallIn(value = {dagger.hilt.components.SingletonComponent.class})
public abstract class TransportModule {
    @org.jetbrains.annotations.NotNull()
    public static final com.odbplus.core.transport.di.TransportModule.Companion Companion = null;
    
    public TransportModule() {
        super();
    }
    
    @dagger.Binds()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public abstract com.odbplus.core.transport.TransportRepository bindTransportRepository(@org.jetbrains.annotations.NotNull()
    com.odbplus.core.transport.TransportRepositoryImpl impl);
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007J\u0010\u0010\u0007\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\bH\u0007\u00a8\u0006\t"}, d2 = {"Lcom/odbplus/core/transport/di/TransportModule$Companion;", "", "()V", "provideBluetoothTransport", "Lcom/odbplus/core/transport/ObdTransport;", "transport", "Lcom/odbplus/core/transport/BluetoothTransport;", "provideTcpTransport", "Lcom/odbplus/core/transport/TcpTransport;", "core-transport_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @dagger.Provides()
        @javax.inject.Singleton()
        @javax.inject.Named(value = "tcp")
        @org.jetbrains.annotations.NotNull()
        public final com.odbplus.core.transport.ObdTransport provideTcpTransport(@org.jetbrains.annotations.NotNull()
        com.odbplus.core.transport.TcpTransport transport) {
            return null;
        }
        
        @dagger.Provides()
        @javax.inject.Singleton()
        @javax.inject.Named(value = "bt")
        @org.jetbrains.annotations.NotNull()
        public final com.odbplus.core.transport.ObdTransport provideBluetoothTransport(@org.jetbrains.annotations.NotNull()
        com.odbplus.core.transport.BluetoothTransport transport) {
            return null;
        }
    }
}