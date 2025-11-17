package com.odbplus.app.connect;

import com.odbplus.core.protocol.TransportRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class ConnectViewModel_Factory implements Factory<ConnectViewModel> {
  private final Provider<TransportRepository> repoProvider;

  public ConnectViewModel_Factory(Provider<TransportRepository> repoProvider) {
    this.repoProvider = repoProvider;
  }

  @Override
  public ConnectViewModel get() {
    return newInstance(repoProvider.get());
  }

  public static ConnectViewModel_Factory create(Provider<TransportRepository> repoProvider) {
    return new ConnectViewModel_Factory(repoProvider);
  }

  public static ConnectViewModel newInstance(TransportRepository repo) {
    return new ConnectViewModel(repo);
  }
}
