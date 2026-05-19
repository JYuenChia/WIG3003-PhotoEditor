package com.mp.wig3003groupproject;

import java.io.File;
import java.util.Properties;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

public class EmailService {

    public static void sendEmailWithAttachment(String recipient, String subject, String body, File attachment) throws Exception {
        try {
            // Debug: Log configuration
            System.out.println("[EMAIL DEBUG] Sender: " + EmailConfig.SENDER_EMAIL);
            System.out.println("[EMAIL DEBUG] SMTP Host: " + EmailConfig.SMTP_HOST);
            System.out.println("[EMAIL DEBUG] SMTP Port: " + EmailConfig.SMTP_PORT);
            System.out.println("[EMAIL DEBUG] Recipient: " + recipient);
            
            // Set SMTP properties
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", EmailConfig.SMTP_HOST);
            props.put("mail.smtp.port", EmailConfig.SMTP_PORT);
            props.put("mail.smtp.ssl.trust", EmailConfig.SMTP_HOST);

            // Create session with authentication
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(EmailConfig.SENDER_EMAIL, EmailConfig.APP_PASSWORD);
                }
            });

            // Construct the message
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EmailConfig.SENDER_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setSubject(subject);

            // Create the message part
            MimeMultipart multipart = new MimeMultipart();

            if (body != null && !body.isBlank()) {
                MimeBodyPart messageBodyPart = new MimeBodyPart();
                messageBodyPart.setText(body);
                multipart.addBodyPart(messageBodyPart);
            }

            // Create the attachment part
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(attachment);
            multipart.addBodyPart(attachmentPart);

            // Set the content
            message.setContent(multipart);

            // Send the email
            System.out.println("[EMAIL DEBUG] Attempting to send email...");
            Transport.send(message);
            System.out.println("[EMAIL DEBUG] Email sent successfully!");
        } catch (AuthenticationFailedException e) {
            System.err.println("[EMAIL ERROR] Authentication failed. Check SENDER_EMAIL and APP_PASSWORD in email_configuration.example");
            throw e;
        } catch (MessagingException e) {
            System.err.println("[EMAIL ERROR] " + e.getMessage());
            throw e;
        }
    }
}
