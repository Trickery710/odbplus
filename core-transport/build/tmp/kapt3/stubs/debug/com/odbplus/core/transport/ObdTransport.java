package com.odbplus.core.transport;

import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.StateFlow;

/**
 * Defines the contract for a low-level OBD transport layer (e.g., TCP, Bluetooth).
 * This interface is abstract and does not know about Hilt or any specific implementation.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\u0005\bf\u0018\u00002\u00020\u0001J\u000e\u0010\u000b\u001a\u00020\fH\u00a6@\u00a2\u0006\u0002\u0010\rJ\u001e\u0010\u000e\u001a\u00020\f2\u0006\u0010\u000f\u001a\u00020\u00042\u0006\u0010\u0010\u001a\u00020\u0011H\u00a6@\u00a2\u0006\u0002\u0010\u0012J\u0018\u0010\u0013\u001a\u00020\u00042\b\b\u0002\u0010\u0014\u001a\u00020\u0015H\u00a6@\u00a2\u0006\u0002\u0010\u0016J\u0016\u0010\u0017\u001a\u00020\f2\u0006\u0010\u0018\u001a\u00020\u0004H\u00a6@\u00a2\u0006\u0002\u0010\u0019R\u0018\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006R\u0018\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0007\u0010\n\u00a8\u0006\u001a"}, d2 = {"Lcom/odbplus/core/transport/ObdTransport;", "", "inbound", "Lkotlinx/coroutines/flow/Flow;", "", "getInbound", "()Lkotlinx/coroutines/flow/Flow;", "isConnected", "Lkotlinx/coroutines/flow/StateFlow;", "", "()Lkotlinx/coroutines/flow/StateFlow;", "close", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "connect", "host", "port", "", "(Ljava/lang/String;ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "readUntilPrompt", "timeoutMs", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "writeLine", "line", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "core-transport_debug"})
public abstract interface ObdTransport {
    
    /**
     * A flow that emits every raw line of data received from the transport.
     */
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.lang.String> getInbound();
    
    /**
     * A flow that emits the current connection status (true if connected).
     */
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isConnected();
    
    /**
     * Initiates a connection to the configured endpoint.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object connect(@org.jetbrains.annotations.NotNull()
    java.lang.String host, int port, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    /**
     * Closes the active connection and releases resources.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object close(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    /**
     * Writes a single line of text to the transport, appending the required carriage return.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object writeLine(@org.jetbrains.annotations.NotNull()
    java.lang.String line, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    /**
     * Reads from the inbound flow until a prompt character ('>') is received or a timeout occurs.
     * Returns the aggregated response.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object readUntilPrompt(long timeoutMs, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.String> $completion);
    
    /**
     * Defines the contract for a low-level OBD transport layer (e.g., TCP, Bluetooth).
     * This interface is abstract and does not know about Hilt or any specific implementation.
     */
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
    }
}