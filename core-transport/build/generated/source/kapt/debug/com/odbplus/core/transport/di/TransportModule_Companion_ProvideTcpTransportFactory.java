package com.odbplus.core.transport.di;

import com.odbplus.core.transport.ObdTransport;
import com.odbplus.core.transport.TcpTransport;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("javax.inject.Named")
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
public final class TransportModule_Companion_ProvideTcpTransportFactory implements Factory<ObdTransport> {
  private final Provider<TcpTransport> transportProvider;

  public TransportModule_Companion_ProvideTcpTransportFactory(
      Provider<TcpTransport> transportProvider) {
    this.transportProvider = transportProvider;
  }

  @Override
  public ObdTransport get() {
    return provideTcpTransport(transportProvider.get());
  }

  public static TransportModule_Companion_ProvideTcpTransportFactory create(
      Provider<TcpTransport> transportProvider) {
    return new TransportModule_Companion_ProvideTcpTransportFactory(transportProvider);
  }

  public static ObdTransport provideTcpTransport(TcpTransport transport) {
    return Preconditions.checkNotNullFromProvides(TransportModule.Companion.provideTcpTransport(transport));
  }
}
