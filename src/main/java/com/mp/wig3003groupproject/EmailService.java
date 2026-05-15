package com.mp.wig3003groupproject;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.io.File;
import java.util.Properties;

public class EmailService {

    public static void sendEmailWithAttachment(String recipient, String subject, String body, File attachment) throws Exception {
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
        Transport.send(message);
    }
}
