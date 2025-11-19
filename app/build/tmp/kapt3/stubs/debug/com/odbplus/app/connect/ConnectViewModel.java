package com.odbplus.app.connect;

import androidx.lifecycle.ViewModel;
import com.odbplus.core.transport.ConnectionState;
import com.odbplus.core.transport.TransportRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;
import kotlinx.coroutines.flow.StateFlow;
import javax.inject.Inject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0010\b\n\u0002\b\u0005\b\u0007\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\fJ\u0016\u0010\u0011\u001a\u00020\u000f2\u0006\u0010\u0012\u001a\u00020\f2\u0006\u0010\u0013\u001a\u00020\u0014J\u0006\u0010\u0015\u001a\u00020\u000fJ\b\u0010\u0016\u001a\u00020\u000fH\u0014J\u000e\u0010\u0017\u001a\u00020\u000f2\u0006\u0010\u0018\u001a\u00020\fR\u0017\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u001d\u0010\n\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\f0\u000b0\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\tR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0019"}, d2 = {"Lcom/odbplus/app/connect/ConnectViewModel;", "Landroidx/lifecycle/ViewModel;", "repo", "Lcom/odbplus/core/transport/TransportRepository;", "(Lcom/odbplus/core/transport/TransportRepository;)V", "connectionState", "Lkotlinx/coroutines/flow/StateFlow;", "Lcom/odbplus/core/transport/ConnectionState;", "getConnectionState", "()Lkotlinx/coroutines/flow/StateFlow;", "logLines", "", "", "getLogLines", "connectBluetooth", "", "macAddress", "connectTcp", "host", "port", "", "disconnect", "onCleared", "sendCustomCommand", "cmd", "app_debug"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class ConnectViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.odbplus.core.transport.TransportRepository repo = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.odbplus.core.transport.ConnectionState> connectionState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<java.lang.String>> logLines = null;
    
    @javax.inject.Inject()
    public ConnectViewModel(@org.jetbrains.annotations.NotNull()
    com.odbplus.core.transport.TransportRepository repo) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.odbplus.core.transport.ConnectionState> getConnectionState() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.util.List<java.lang.String>> getLogLines() {
        return null;
    }
    
    public final void connectTcp(@org.jetbrains.annotations.NotNull()
    java.lang.String host, int port) {
    }
    
    public final void connectBluetooth(@org.jetbrains.annotations.NotNull()
    java.lang.String macAddress) {
    }
    
    public final void sendCustomCommand(@org.jetbrains.annotations.NotNull()
    java.lang.String cmd) {
    }
    
    public final void disconnect() {
    }
    
    @java.lang.Override()
    protected void onCleared() {
    }
}