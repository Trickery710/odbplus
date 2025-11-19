package com.odbplus.app.ui;

import androidx.compose.foundation.layout.*;
import androidx.compose.foundation.text.KeyboardOptions;
import androidx.compose.material.icons.Icons;
import androidx.compose.material3.*;
import androidx.compose.runtime.*;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.text.input.ImeAction;
import com.odbplus.app.connect.ConnectViewModel;
import com.odbplus.core.transport.ConnectionState;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000*\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\u001a\u001c\u0010\u0000\u001a\u00020\u00012\u0012\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00010\u0003H\u0007\u001a\u0012\u0010\u0005\u001a\u00020\u00012\b\b\u0002\u0010\u0006\u001a\u00020\u0007H\u0007\u001a:\u0010\b\u001a\u00020\u00012\u0006\u0010\t\u001a\u00020\n2\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00010\f2\f\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u00010\f2\f\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00010\fH\u0007\u00a8\u0006\u000f"}, d2 = {"CommandInputBar", "", "onSendCommand", "Lkotlin/Function1;", "", "ConnectScreen", "viewModel", "Lcom/odbplus/app/connect/ConnectViewModel;", "ConnectScreenTopBar", "connectionState", "Lcom/odbplus/core/transport/ConnectionState;", "onConnectTcp", "Lkotlin/Function0;", "onConnectBt", "onDisconnect", "app_debug"})
public final class ConnectScreenKt {
    
    @androidx.compose.runtime.Composable()
    public static final void ConnectScreen(@org.jetbrains.annotations.NotNull()
    com.odbplus.app.connect.ConnectViewModel viewModel) {
    }
    
    @androidx.compose.runtime.Composable()
    public static final void ConnectScreenTopBar(@org.jetbrains.annotations.NotNull()
    com.odbplus.core.transport.ConnectionState connectionState, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onConnectTcp, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onConnectBt, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onDisconnect) {
    }
    
    @androidx.compose.runtime.Composable()
    public static final void CommandInputBar(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onSendCommand) {
    }
}