package com.odbplus.core.transport.di;

import com.odbplus.core.transport.BluetoothTransport;
import com.odbplus.core.transport.ObdTransport;
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
public final class TransportModule_Companion_ProvideBluetoothTransportFactory implements Factory<ObdTransport> {
  private final Provider<BluetoothTransport> transportProvider;

  public TransportModule_Companion_ProvideBluetoothTransportFactory(
      Provider<BluetoothTransport> transportProvider) {
    this.transportProvider = transportProvider;
  }

  @Override
  public ObdTransport get() {
    return provideBluetoothTransport(transportProvider.get());
  }

  public static TransportModule_Companion_ProvideBluetoothTransportFactory create(
      Provider<BluetoothTransport> transportProvider) {
    return new TransportModule_Companion_ProvideBluetoothTransportFactory(transportProvider);
  }

  public static ObdTransport provideBluetoothTransport(BluetoothTransport transport) {
    return Preconditions.checkNotNullFromProvides(TransportModule.Companion.provideBluetoothTransport(transport));
  }
}
