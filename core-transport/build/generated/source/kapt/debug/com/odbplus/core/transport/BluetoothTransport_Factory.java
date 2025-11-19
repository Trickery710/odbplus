package com.odbplus.core.transport;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.coroutines.CoroutineScope;

@ScopeMetadata
@QualifierMetadata({
    "dagger.hilt.android.qualifiers.ApplicationContext",
    "com.odbplus.core.transport.di.AppScope"
})
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class BluetoothTransport_Factory implements Factory<BluetoothTransport> {
  private final Provider<Context> contextProvider;

  private final Provider<CoroutineScope> externalScopeProvider;

  public BluetoothTransport_Factory(Provider<Context> contextProvider,
      Provider<CoroutineScope> externalScopeProvider) {
    this.contextProvider = contextProvider;
    this.externalScopeProvider = externalScopeProvider;
  }

  @Override
  public BluetoothTransport get() {
    return newInstance(contextProvider.get(), externalScopeProvider.get());
  }

  public static BluetoothTransport_Factory create(Provider<Context> contextProvider,
      Provider<CoroutineScope> externalScopeProvider) {
    return new BluetoothTransport_Factory(contextProvider, externalScopeProvider);
  }

  public static BluetoothTransport newInstance(Context context, CoroutineScope externalScope) {
    return new BluetoothTransport(context, externalScope);
  }
}
