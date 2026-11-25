package com.kazforge.jsonapi.spring.webmvc.internal;

import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.ErrorSource;
import java.io.Serial;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Safe client-input diagnostics from typed query binding. */
public final class QueryBindingFailure extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;
  private final List<ErrorObject> errors;

  public QueryBindingFailure(List<ErrorObject> errors) {
    super("Invalid query parameters");
    this.errors = List.copyOf(errors);
  }

  public static ErrorObject error(ProtocolError reason, @Nullable String parameter) {
    return reason.error(parameter == null ? null : ErrorSource.ofParameter(parameter));
  }

  public List<ErrorObject> errors() {
    return errors;
  }
}
