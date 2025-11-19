package com.odbplus.core.transport.di;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import kotlinx.coroutines.Dispatchers;
import javax.inject.Qualifier;
import javax.inject.Singleton;

@kotlin.annotation.Retention(value = kotlin.annotation.AnnotationRetention.BINARY)
@javax.inject.Qualifier()
@java.lang.annotation.Retention(value = java.lang.annotation.RetentionPolicy.CLASS)
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\n\n\u0002\u0018\u0002\n\u0002\u0010\u001b\n\u0000\b\u0087\u0002\u0018\u00002\u00020\u0001B\u0000\u00a8\u0006\u0002"}, d2 = {"Lcom/odbplus/core/transport/di/AppScope;", "", "core-transport_debug"})
public abstract @interface AppScope {
}