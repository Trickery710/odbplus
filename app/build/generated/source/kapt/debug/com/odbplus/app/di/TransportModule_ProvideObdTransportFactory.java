package com.odbplus.app.di;

import com.odbplus.core.transport.ObdTransport;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.coroutines.CoroutineScope;

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
public final class TransportModule_ProvideObdTransportFactory implements Factory<ObdTransport> {
  private final Provider<CoroutineScope> scopeProvider;

  public TransportModule_ProvideObdTransportFactory(Provider<CoroutineScope> scopeProvider) {
    this.scopeProvider = scopeProvider;
  }

  @Override
  public ObdTransport get() {
    return provideObdTransport(scopeProvider.get());
  }

  public static TransportModule_ProvideObdTransportFactory create(
      Provider<CoroutineScope> scopeProvider) {
    return new TransportModule_ProvideObdTransportFactory(scopeProvider);
  }

  public static ObdTransport provideObdTransport(CoroutineScope scope) {
    return Preconditions.checkNotNullFromProvides(TransportModule.INSTANCE.provideObdTransport(scope));
  }
}
