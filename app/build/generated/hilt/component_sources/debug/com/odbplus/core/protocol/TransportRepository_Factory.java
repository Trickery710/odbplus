package com.odbplus.core.protocol;

import com.odbplus.core.transport.ObdTransport;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
    "cast",
    "deprecation"
})
public final class TransportRepository_Factory implements Factory<TransportRepository> {
  private final Provider<ObdTransport> transportProvider;

  public TransportRepository_Factory(Provider<ObdTransport> transportProvider) {
    this.transportProvider = transportProvider;
  }

  @Override
  public TransportRepository get() {
    return newInstance(transportProvider.get());
  }

  public static TransportRepository_Factory create(Provider<ObdTransport> transportProvider) {
    return new TransportRepository_Factory(transportProvider);
  }

  public static TransportRepository newInstance(ObdTransport transport) {
    return new TransportRepository(transport);
  }
}
