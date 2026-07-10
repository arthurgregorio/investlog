package br.com.investlog.server.shared.exceptions

/**
 * Deliberately not mapped in GlobalExceptionHandler — this is thrown and caught entirely inside
 * GoogleLoginSuccessHandler, an AuthenticationSuccessHandler that runs outside the normal
 * @RestControllerAdvice pipeline, never as a REST controller's response.
 */
class GoogleAccountEmailInUseException(message: String) : RuntimeException(message)
