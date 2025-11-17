package com.odbplus.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
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
public final class TransportModule_ProvideExternalScopeFactory implements Factory<CoroutineScope> {
  @Override
  public CoroutineScope get() {
    return provideExternalScope();
  }

  public static TransportModule_ProvideExternalScopeFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CoroutineScope provideExternalScope() {
    return Preconditions.checkNotNullFromProvides(TransportModule.INSTANCE.provideExternalScope());
  }

  private static final class InstanceHolder {
    private static final TransportModule_ProvideExternalScopeFactory INSTANCE = new TransportModule_ProvideExternalScopeFactory();
  }
}
