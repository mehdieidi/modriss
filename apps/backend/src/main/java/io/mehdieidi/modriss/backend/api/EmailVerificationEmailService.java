package io.mehdieidi.modriss.backend.api;

import io.mehdieidi.modriss.platform.identity.application.AuthService.EmailVerificationRequest;
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

/** Sends account verification links through the configured SMTP server. */
@Service
class EmailVerificationEmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailVerificationEmailService.class);

  private final JavaMailSender mailSender;
  private final String mailHost;
  private final String fromAddress;
  private final String editorUrl;

  EmailVerificationEmailService(
      ObjectProvider<JavaMailSender> mailSender,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${modriss.email-verification.from:}") String fromAddress,
      @Value("${modriss.email-verification.editor-url:http://localhost:8082}") String editorUrl) {
    this.mailSender = mailSender.getIfAvailable();
    this.mailHost = mailHost == null ? "" : mailHost.trim();
    this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
    this.editorUrl =
        editorUrl == null || editorUrl.isBlank() ? "http://localhost:8082" : editorUrl.trim();
  }

  void requireConfigured() {
    if (mailSender == null || mailHost.isBlank() || fromAddress.isBlank()) {
      throw new PlatformException(503, "Email verification delivery is not configured.");
    }
  }

  @Async("authEmailExecutor")
  public void send(EmailVerificationRequest verification) {
    try {
      String verificationUrl =
          UriComponentsBuilder.fromUriString(editorUrl)
              .replaceQueryParam("verifyToken", verification.token())
              .build()
              .encode()
              .toUriString();
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromAddress);
      message.setTo(verification.email());
      message.setSubject("Verify your MODRISS email address");
      message.setText(
          "Hello "
              + verification.displayName()
              + ",\n\nPlease verify your email address to activate your MODRISS account:\n\n"
              + verificationUrl
              + "\n\nThis link expires in 24 hours and can only be used once. "
              + "If you did not create a MODRISS account, you can ignore this email.");
      mailSender.send(message);
    } catch (RuntimeException ex) {
      log.error("Could not deliver email verification message", ex);
    }
  }
}
