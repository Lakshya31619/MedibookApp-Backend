package com.medibook.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    public void sendVerificationCode(String toEmail, String fullName, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("MediBook – Verify your email address");

            String html = buildVerificationEmail(fullName, code);
            helper.setText(html, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send verification email: " + e.getMessage());
        }
    }

    private String buildVerificationEmail(String fullName, String code) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8"/>
              <style>
                body { margin:0; padding:0; background:#f4f7fb; font-family:'Helvetica Neue',Arial,sans-serif; }
                .wrapper { max-width:520px; margin:40px auto; background:#ffffff; border-radius:12px;
                           box-shadow:0 4px 24px rgba(0,0,0,0.08); overflow:hidden; }
                .header  { background:#1e3a5f; padding:32px 40px; text-align:center; }
                .header h1 { color:#ffffff; margin:0; font-size:24px; letter-spacing:-0.5px; }
                .header span { color:#90b8d8; font-size:13px; }
                .body    { padding:36px 40px; }
                .body p  { color:#374151; font-size:15px; line-height:1.7; margin:0 0 16px; }
                .code-box { background:#f0f5ff; border:2px dashed #3b6db5; border-radius:10px;
                            padding:20px; text-align:center; margin:24px 0; }
                .code-box .code { font-size:40px; font-weight:800; letter-spacing:12px;
                                  color:#1e3a5f; font-family:monospace; }
                .note    { color:#6b7280; font-size:13px; }
                .footer  { background:#f9fafb; border-top:1px solid #e5e7eb;
                           padding:20px 40px; text-align:center; }
                .footer p { color:#9ca3af; font-size:12px; margin:0; }
              </style>
            </head>
            <body>
              <div class="wrapper">
                <div class="header">
                  <h1>🏥 MediBook</h1>
                  <span>Healthcare at your fingertips</span>
                </div>
                <div class="body">
                  <p>Hi <strong>%s</strong>,</p>
                  <p>Thanks for signing up! Use the 6-digit code below to verify your email address and activate your account.</p>
                  <div class="code-box">
                    <div class="code">%s</div>
                  </div>
                  <p class="note">⏱ This code expires in <strong>10 minutes</strong>. Do not share it with anyone.</p>
                  <p class="note">If you didn't create a MediBook account, you can safely ignore this email.</p>
                </div>
                <div class="footer">
                  <p>© MediBook · All rights reserved</p>
                </div>
              </div>
            </body>
            </html>
            """.formatted(fullName, code);
    }
}