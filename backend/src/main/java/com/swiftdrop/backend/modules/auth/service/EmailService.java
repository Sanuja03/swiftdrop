package com.swiftdrop.backend.modules.auth.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    /**
     * Sends an HTML email verification message with the 6-digit code.
     */
    public boolean sendEmailVerification(String toEmail, String recipientName, String verificationCode) {
        if (senderEmail == null || senderEmail.isBlank()) {
            log.warn("EmailService: SPRING_MAIL_USERNAME not set. Email verification to [{}] skipped. Code: [{}]", toEmail, verificationCode);
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail, "SwiftDrop Delivery Services");
            helper.setTo(toEmail);
            helper.setSubject("SwiftDrop - Verify Your Email Address: " + verificationCode);

            String htmlBody = buildEmailVerificationHtml(recipientName, toEmail, verificationCode);
            helper.setText(htmlBody, true);

            attachInlineLogo(helper);

            mailSender.send(message);
            log.info("EmailService: Verification email successfully dispatched to [{}]", toEmail);
            return true;
        } catch (Exception e) {
            log.error("EmailService: Failed to send verification email to [{}]: {}", toEmail, e.getMessage());
            return false;
        }
    }

    private void attachInlineLogo(MimeMessageHelper helper) {
        try {
            ClassPathResource logoResource = new ClassPathResource("static/swiftdrop-avatar.png");
            if (logoResource.exists()) {
                helper.addInline("swiftdropLogo", logoResource, "image/png");
            }
        } catch (Exception ex) {
            log.debug("EmailService: Could not attach inline logo: {}", ex.getMessage());
        }
    }

    private String buildEmailVerificationHtml(String recipientName, String toEmail, String verificationCode) {
        String name = (recipientName != null && !recipientName.isBlank()) ? recipientName : "Valued Customer";
        String directLink = "http://localhost:5173/verify-email?email=" + toEmail + "&code=" + verificationCode;

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #F5F7FA; margin: 0; padding: 20px; color: #1E293B; }
                .email-container { max-width: 560px; margin: 0 auto; background: #FFFFFF; border-radius: 14px; overflow: hidden; border: 1px solid #E2E8F0; box-shadow: 0 4px 16px rgba(0,0,0,0.06); }
                .email-header { background-color: #081F38; padding: 24px 30px; text-align: center; }
                .email-body { padding: 32px 30px; }
                .greeting { font-size: 18px; font-weight: 700; color: #102A4C; margin-bottom: 12px; }
                .message-text { font-size: 14px; line-height: 1.6; color: #475569; margin: 0 0 20px 0; }
                .code-box { background-color: #F8FAFC; border: 2px dashed #16A34A; border-radius: 10px; padding: 18px; text-align: center; margin: 24px 0; }
                .code-label { font-size: 11px; text-transform: uppercase; letter-spacing: 1px; color: #15803D; font-weight: 700; margin-bottom: 6px; }
                .code-digits { font-family: 'Courier New', monospace; font-size: 32px; font-weight: 800; letter-spacing: 8px; color: #102A4C; }
                .btn-verify { display: inline-block; background-color: #F58220; color: #FFFFFF !important; font-size: 14px; font-weight: 600; text-decoration: none; padding: 12px 26px; border-radius: 8px; margin: 12px 0 20px 0; text-align: center; }
                .security-note { background-color: #EFF6FF; border-left: 4px solid #3B82F6; padding: 12px 14px; border-radius: 0 8px 8px 0; font-size: 12.5px; color: #1E40AF; line-height: 1.5; margin-bottom: 24px; }
                .email-footer { background-color: #F1F5F9; padding: 18px 30px; text-align: center; font-size: 12px; color: #64748B; border-top: 1px solid #E2E8F0; }
              </style>
            </head>
            <body>
              <div class="email-container">
                <div class="email-header">
                  <table role="presentation" border="0" cellpadding="0" cellspacing="0" style="margin: 0 auto;">
                    <tr>
                      <td style="vertical-align: middle; padding-right: 12px;">
                        <img src="cid:swiftdropLogo" width="46" height="46" alt="SwiftDrop Logo" style="display: block; border-radius: 10px; border: 0;" />
                      </td>
                      <td style="vertical-align: middle; text-align: left;">
                        <div style="color: #FFFFFF; font-size: 22px; font-weight: 800; line-height: 1.1; letter-spacing: -0.5px;">Swift<span style="color: #F58220;">Drop</span></div>
                        <div style="color: #F58220; font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 1px; margin-top: 3px;">Courier & Parcel Delivery</div>
                      </td>
                    </tr>
                  </table>
                </div>
                <div class="email-body">
                  <div class="greeting">Welcome to SwiftDrop, %s!</div>
                  <p class="message-text">
                    Thank you for signing up. To activate your account and start sending and tracking parcels, please verify your email address using the 6-digit code below:
                  </p>
                  
                  <div class="code-box">
                    <div class="code-label">Email Verification Code</div>
                    <div class="code-digits">%s</div>
                  </div>

                  <div style="text-align: center;">
                    <a href="%s" class="btn-verify" style="color: #ffffff;">Verify Email Address</a>
                  </div>

                  <div class="security-note">
                    <strong>⏱️ Code Expiry:</strong> This code is valid for <strong>24 hours</strong>. If you did not create a SwiftDrop account, please ignore this email.
                  </div>

                  <p class="message-text" style="margin-bottom: 0;">
                    Warm regards,<br>
                    <strong>The SwiftDrop Team</strong>
                  </p>
                </div>
                <div class="email-footer">
                  © 2026 SwiftDrop Parcel Delivery & Courier Management System.<br>
                  Enterprise Application Development (IN3101) — University of Moratuwa.
                </div>
              </div>
            </body>
            </html>
            """.formatted(name, verificationCode, directLink);
    }

    /**
     * Sends an HTML password reset email with the 6-digit verification code.
     */
    public boolean sendPasswordResetEmail(String toEmail, String recipientName, String resetCode) {
        if (senderEmail == null || senderEmail.isBlank()) {
            log.warn("EmailService: SPRING_MAIL_USERNAME not set. Email to [{}] skipped. Code: [{}]", toEmail, resetCode);
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail, "SwiftDrop Delivery Services");
            helper.setTo(toEmail);
            helper.setSubject("SwiftDrop - Your Password Reset Verification Code: " + resetCode);

            String htmlBody = buildPasswordResetHtml(recipientName, resetCode);
            helper.setText(htmlBody, true);

            attachInlineLogo(helper);

            mailSender.send(message);
            log.info("EmailService: Password reset email successfully dispatched to [{}]", toEmail);
            return true;
        } catch (Exception e) {
            log.error("EmailService: Failed to send password reset email to [{}]: {}", toEmail, e.getMessage());
            return false;
        }
    }

    private String buildPasswordResetHtml(String recipientName, String resetCode) {
        String name = (recipientName != null && !recipientName.isBlank()) ? recipientName : "Valued Customer";

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #F5F7FA; margin: 0; padding: 20px; color: #1E293B; }
                .email-container { max-width: 560px; margin: 0 auto; background: #FFFFFF; border-radius: 14px; overflow: hidden; border: 1px solid #E2E8F0; box-shadow: 0 4px 16px rgba(0,0,0,0.06); }
                .email-header { background-color: #081F38; padding: 24px 30px; text-align: center; }
                .email-body { padding: 32px 30px; }
                .greeting { font-size: 18px; font-weight: 700; color: #102A4C; margin-bottom: 12px; }
                .message-text { font-size: 14px; line-height: 1.6; color: #475569; margin: 0 0 20px 0; }
                .code-box { background-color: #F8FAFC; border: 2px dashed #F58220; border-radius: 10px; padding: 18px; text-align: center; margin: 24px 0; }
                .code-label { font-size: 11px; text-transform: uppercase; letter-spacing: 1px; color: #64748B; font-weight: 700; margin-bottom: 6px; }
                .code-digits { font-family: 'Courier New', monospace; font-size: 32px; font-weight: 800; letter-spacing: 8px; color: #102A4C; }
                .security-note { background-color: #FFF7ED; border-left: 4px solid #F58220; padding: 12px 14px; border-radius: 0 8px 8px 0; font-size: 12.5px; color: #9A3412; line-height: 1.5; margin-bottom: 24px; }
                .email-footer { background-color: #F1F5F9; padding: 18px 30px; text-align: center; font-size: 12px; color: #64748B; border-top: 1px solid #E2E8F0; }
              </style>
            </head>
            <body>
              <div class="email-container">
                <div class="email-header">
                  <table role="presentation" border="0" cellpadding="0" cellspacing="0" style="margin: 0 auto;">
                    <tr>
                      <td style="vertical-align: middle; padding-right: 12px;">
                        <img src="cid:swiftdropLogo" width="46" height="46" alt="SwiftDrop Logo" style="display: block; border-radius: 10px; border: 0;" />
                      </td>
                      <td style="vertical-align: middle; text-align: left;">
                        <div style="color: #FFFFFF; font-size: 22px; font-weight: 800; line-height: 1.1; letter-spacing: -0.5px;">Swift<span style="color: #F58220;">Drop</span></div>
                        <div style="color: #F58220; font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 1px; margin-top: 3px;">Courier & Parcel Delivery</div>
                      </td>
                    </tr>
                  </table>
                </div>
                <div class="email-body">
                  <div class="greeting">Hello %s,</div>
                  <p class="message-text">
                    We received a request to reset your password for your SwiftDrop account. Please use the 6-digit verification code below to reset your password:
                  </p>
                  
                  <div class="code-box">
                    <div class="code-label">Password Reset Code</div>
                    <div class="code-digits">%s</div>
                  </div>

                  <div class="security-note">
                    <strong>⏱️ Code Expiration:</strong> This code is valid for <strong>15 minutes</strong>. If you did not request this password reset, please ignore this email or review your account security.
                  </div>

                  <p class="message-text" style="margin-bottom: 0;">
                    Best regards,<br>
                    <strong>The SwiftDrop Security Team</strong>
                  </p>
                </div>
                <div class="email-footer">
                  © 2026 SwiftDrop Parcel Delivery & Courier Management System.<br>
                  Enterprise Application Development (IN3101) — University of Moratuwa.
                </div>
              </div>
            </body>
            </html>
            """.formatted(name, resetCode);
    }
}

