package io.mehdieidi.modriss.backend.api;

import io.mehdieidi.modriss.platform.identity.application.AuthService.PasswordResetRequest;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/** Sends password reset links through the configured SMTP server. */
@Service
class PasswordResetEmailService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailService.class);

  private final JavaMailSender mailSender;
  private final String mailHost;
  private final String fromAddress;
  private final String editorUrl;

  PasswordResetEmailService(
      ObjectProvider<JavaMailSender> mailSender,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${modriss.password-reset.from:}") String fromAddress,
      @Value("${modriss.password-reset.editor-url:http://localhost:8082}") String editorUrl) {
    this.mailSender = mailSender.getIfAvailable();
    this.mailHost = mailHost == null ? "" : mailHost.trim();
    this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
    this.editorUrl =
        editorUrl == null || editorUrl.isBlank() ? "http://localhost:8082" : editorUrl.trim();
  }

  void requireConfigured() {
    if (mailSender == null
        || mailHost == null
        || mailHost.isBlank()
        || fromAddress == null
        || fromAddress.isBlank()) {
      throw new PlatformException(503, "Password reset email delivery is not configured.");
    }
  }

  @Async("passwordResetMailExecutor")
  public void send(PasswordResetRequest reset) {
    try {
      String resetUrl =
          UriComponentsBuilder.fromUriString(editorUrl)
              .replaceQueryParam("resetToken", reset.token())
              .build()
              .encode()
              .toUriString();
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromAddress);
      message.setTo(reset.email());
      message.setSubject("Reset your MODRISS password");
      message.setText(
          "Hello "
              + reset.displayName()
              + ",\n\nUse this link to choose a new MODRISS password:\n\n"
              + resetUrl
              + "\n\nThis link expires in 30 minutes and can only be used once. "
              + "If you did not request a password reset, you can ignore this email.");
      mailSender.send(message);
    } catch (RuntimeException ex) {
      log.error("Could not deliver password reset email", ex);
    }
  }
}
