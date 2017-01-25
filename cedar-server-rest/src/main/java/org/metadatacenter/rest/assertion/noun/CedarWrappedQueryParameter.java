package org.metadatacenter.rest.assertion.noun;

import org.metadatacenter.rest.context.CedarParameterSource;

import java.util.Optional;

public class CedarWrappedQueryParameter extends CedarParameterNoun {

  private final String name;
  private final CedarParameterSource source;
  private final Optional<? extends Object> wrapped;

  public CedarWrappedQueryParameter(String name, Optional<? extends Object> wrapped) {
    this.name = name;
    this.source = CedarParameterSource.QueryString;
    this.wrapped = wrapped;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public CedarParameterSource getSource() {
    return source;
  }

  @Override
  public String stringValue() {
    if (wrapped != null && wrapped.isPresent() && wrapped.get() != null) {
      return wrapped.get().toString();
    } else {
      return null;
    }
  }

  public boolean isNull() {
    return isMissing() || wrapped.get() == null;
  }

  public boolean isPresentAndNull() {
    return wrapped != null && wrapped.isPresent() && wrapped.get() == null;
  }

  public boolean isMissing() {
    return wrapped == null || !wrapped.isPresent();
  }

}