package com.odbplus.core.transport;

import kotlinx.coroutines.flow.StateFlow;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0007\n\u0002\u0010\t\n\u0002\b\u0002\b\u0007\u0018\u00002\u00020\u0001B\u001b\b\u0007\u0012\b\b\u0001\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0001\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0005J\u0010\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u000bH\u0002J&\u0010\u0016\u001a\u00020\u00142\u0006\u0010\u0017\u001a\u00020\u000b2\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u001bH\u0096@\u00a2\u0006\u0002\u0010\u001cJ\u000e\u0010\u001d\u001a\u00020\u0014H\u0096@\u00a2\u0006\u0002\u0010\u001eJ\u000e\u0010\u001f\u001a\u00020\u0014H\u0082@\u00a2\u0006\u0002\u0010\u001eJ\u001e\u0010 \u001a\u00020\u00142\u0006\u0010!\u001a\u00020\u000b2\u0006\u0010\"\u001a\u00020#H\u0096@\u00a2\u0006\u0002\u0010$R\u0014\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\t\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000b0\n0\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\f\u001a\u0004\u0018\u00010\u0003X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\r\u001a\b\u0012\u0004\u0012\u00020\b0\u000eX\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R \u0010\u0011\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000b0\n0\u000eX\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0010R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006%"}, d2 = {"Lcom/odbplus/core/transport/TransportRepositoryImpl;", "Lcom/odbplus/core/transport/TransportRepository;", "tcpTransport", "Lcom/odbplus/core/transport/ObdTransport;", "bluetoothTransport", "(Lcom/odbplus/core/transport/ObdTransport;Lcom/odbplus/core/transport/ObdTransport;)V", "_connectionState", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/odbplus/core/transport/ConnectionState;", "_logLines", "", "", "activeTransport", "connectionState", "Lkotlinx/coroutines/flow/StateFlow;", "getConnectionState", "()Lkotlinx/coroutines/flow/StateFlow;", "logLines", "getLogLines", "addLog", "", "line", "connect", "address", "port", "", "isBluetooth", "", "(Ljava/lang/String;IZLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "disconnect", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "initElmSession", "sendAndAwait", "cmd", "timeoutMs", "", "(Ljava/lang/String;JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "core-transport_debug"})
public final class TransportRepositoryImpl implements com.odbplus.core.transport.TransportRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.odbplus.core.transport.ObdTransport tcpTransport = null;
    @org.jetbrains.annotations.NotNull()
    private final com.odbplus.core.transport.ObdTransport bluetoothTransport = null;
    @org.jetbrains.annotations.Nullable()
    private com.odbplus.core.transport.ObdTransport activeTransport;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.odbplus.core.transport.ConnectionState> _connectionState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.odbplus.core.transport.ConnectionState> connectionState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.util.List<java.lang.String>> _logLines = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<java.lang.String>> logLines = null;
    
    @javax.inject.Inject()
    public TransportRepositoryImpl(@javax.inject.Named(value = "tcp")
    @org.jetbrains.annotations.NotNull()
    com.odbplus.core.transport.ObdTransport tcpTransport, @javax.inject.Named(value = "bt")
    @org.jetbrains.annotations.NotNull()
    com.odbplus.core.transport.ObdTransport bluetoothTransport) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.StateFlow<com.odbplus.core.transport.ConnectionState> getConnectionState() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.StateFlow<java.util.List<java.lang.String>> getLogLines() {
        return null;
    }
    
    private final void addLog(java.lang.String line) {
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object connect(@org.jetbrains.annotations.NotNull()
    java.lang.String address, int port, boolean isBluetooth, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object disconnect(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final java.lang.Object initElmSession(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object sendAndAwait(@org.jetbrains.annotations.NotNull()
    java.lang.String cmd, long timeoutMs, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
}