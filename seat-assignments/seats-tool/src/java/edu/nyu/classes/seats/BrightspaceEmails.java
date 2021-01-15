package edu.nyu.classes.seats;

import com.sun.mail.smtp.SMTPTransport;
import edu.nyu.classes.seats.brightspace.BrightspaceSectionInfo;
import edu.nyu.classes.seats.models.SeatGroup;
import edu.nyu.classes.seats.storage.SeatsStorage;
import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class BrightspaceEmails {

    private static final Logger LOG = LoggerFactory.getLogger(BrightspaceEmails.class);

    public static class EmailAddress {
        private String displayName;
        private String email;

        public EmailAddress(String displayName, String email) {
            this.displayName = displayName;
            this.email = email;
        }

        public InternetAddress toInternetAddress() {
            try {
                return new InternetAddress(this.email, this.displayName);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }

        }
    }

    public static EmailAddress makeEmailAddress(String displayName, String email) {
        return new EmailAddress(displayName, email);
    }

    private static boolean testMode() {
        return "true".equals(HotReloadConfigurationService.getString("testMode@org.sakaiproject.email.api.EmailService", "true"));
    }

    private static Properties loadSmtpProperties() {
        Properties result = new Properties();

        result.put("mail.smtp.host", HotReloadConfigurationService.getString("smtp@org.sakaiproject.email.api.EmailService", "localhost"));
        result.put("mail.smtp.auth", "false");
        result.put("mail.smtp.port", HotReloadConfigurationService.getString("smtpPort@org.sakaiproject.email.api.EmailService", "25"));

        return result;
    }

    public static void sendPlaintextEmail(List<EmailAddress> toAddresses,
                                          List<EmailAddress> ccAddresses,
                                          List<EmailAddress> bccAddresses,
                                          String subject,
                                          String body) {

        Properties smtpProperties = loadSmtpProperties();

        Session session = Session.getInstance(smtpProperties, null);
        Message msg = new MimeMessage(session);

        try {
            msg.setFrom(new InternetAddress("do-not-reply@nyu.edu", "NYU LMS (Brightspace)"));

            msg.setRecipients(Message.RecipientType.TO, toAddresses.stream().map(EmailAddress::toInternetAddress).collect(Collectors.toList()).toArray(new InternetAddress[] {}));
            msg.setRecipients(Message.RecipientType.CC, ccAddresses.stream().map(EmailAddress::toInternetAddress).collect(Collectors.toList()).toArray(new InternetAddress[] {}));
            msg.setRecipients(Message.RecipientType.BCC, bccAddresses.stream().map(EmailAddress::toInternetAddress).collect(Collectors.toList()).toArray(new InternetAddress[] {}));

            msg.setSubject(subject);
            msg.setText(body);
            msg.setSentDate(new Date());

            sendEmail(msg, session);
        } catch (IOException | MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    private static void sendEmail(Message msg, Session session) throws IOException, MessagingException {
        if (testMode()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            msg.writeTo(bos);

            LOG.info("DEBUG EMAIL FOLLOWS:");
            LOG.info(new String(bos.toByteArray(), "UTF-8"));
        } else {
            SMTPTransport t = (SMTPTransport)session.getTransport("smtp");

            t.connect();
            t.sendMessage(msg, msg.getAllRecipients());
            t.close();
        }
    }

    public static void sendUserAddedEmail(EmailAddress studentEmail, List<EmailAddress> ccEmails, SeatGroup group, String siteTitle, BrightspaceSectionInfo sectionInfo) {
        Properties smtpProperties = loadSmtpProperties();
        Session session = Session.getInstance(smtpProperties, null);
        Message msg = new MimeMessage(session);

        String subject = String.format("You've been added to a cohort for %s", siteTitle);

        String body = String.format("<p>Dear %s,</p>" +
                        "<p>You've been added to %s for %s. Please contact your instructor for information on when you will be meeting in-person for your course.</p>" +
                        "<p>Note: you will be required to record your seating assignment for the duration of the semester in the Seating Assignments tool in NYU LMS (Brightspace). " +
                        "For more information, see the <a href=\"%s\">Seating Assignments knowledgebase article</a>.</p>",
                studentEmail.displayName,
                group.name,
                siteTitle,
                "https://www.nyu.edu/servicelink/KB0018304"
        );

        try {
            msg.setFrom(new InternetAddress("do-not-reply@nyu.edu", "NYU LMS (Brightspace)"));
            msg.setSubject(subject);

            Multipart multipart = new MimeMultipart();
            MimeBodyPart bodyPart = new MimeBodyPart();
            bodyPart.setContent(body, "text/html; charset=utf-8");
            multipart.addBodyPart(bodyPart);
            msg.setContent(multipart);

            msg.setRecipients(Message.RecipientType.TO, new InternetAddress[] {studentEmail.toInternetAddress()});
            msg.setRecipients(Message.RecipientType.CC, ccEmails.stream().map(EmailAddress::toInternetAddress).collect(Collectors.toList()).toArray(new InternetAddress[] {}));

            sendEmail(msg, session);
        } catch (IOException | MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
