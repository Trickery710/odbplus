package com.odbplus.core.transport;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class TransportRepositoryImpl_Factory implements Factory<TransportRepositoryImpl> {
  private final Provider<ObdTransport> tcpTransportProvider;

  private final Provider<ObdTransport> bluetoothTransportProvider;

  public TransportRepositoryImpl_Factory(Provider<ObdTransport> tcpTransportProvider,
      Provider<ObdTransport> bluetoothTransportProvider) {
    this.tcpTransportProvider = tcpTransportProvider;
    this.bluetoothTransportProvider = bluetoothTransportProvider;
  }

  @Override
  public TransportRepositoryImpl get() {
    return newInstance(tcpTransportProvider.get(), bluetoothTransportProvider.get());
  }

  public static TransportRepositoryImpl_Factory create(Provider<ObdTransport> tcpTransportProvider,
      Provider<ObdTransport> bluetoothTransportProvider) {
    return new TransportRepositoryImpl_Factory(tcpTransportProvider, bluetoothTransportProvider);
  }

  public static TransportRepositoryImpl newInstance(ObdTransport tcpTransport,
      ObdTransport bluetoothTransport) {
    return new TransportRepositoryImpl(tcpTransport, bluetoothTransport);
  }
}
