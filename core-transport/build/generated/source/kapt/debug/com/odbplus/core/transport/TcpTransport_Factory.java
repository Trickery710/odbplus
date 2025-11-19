package com.odbplus.core.transport;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.coroutines.CoroutineScope;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("com.odbplus.core.transport.di.AppScope")
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
public final class TcpTransport_Factory implements Factory<TcpTransport> {
  private final Provider<CoroutineScope> externalScopeProvider;

  public TcpTransport_Factory(Provider<CoroutineScope> externalScopeProvider) {
    this.externalScopeProvider = externalScopeProvider;
  }

  @Override
  public TcpTransport get() {
    return newInstance(externalScopeProvider.get());
  }

  public static TcpTransport_Factory create(Provider<CoroutineScope> externalScopeProvider) {
    return new TcpTransport_Factory(externalScopeProvider);
  }

  public static TcpTransport newInstance(CoroutineScope externalScope) {
    return new TcpTransport(externalScope);
  }
}
