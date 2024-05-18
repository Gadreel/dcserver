package dcraft.interchange.smtp;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.DebugLevel;
import dcraft.log.Logger;
import dcraft.util.HexUtil;
import dcraft.util.StringUtil;
import dcraft.web.md.MarkdownUtil;
import dcraft.xml.XElement;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.*;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

public class SimpleSMTPUtil {
    static public boolean sendEmail(String to, String subject, String body, String textbody, String from, String reply) throws OperatingContextException {
        XElement settings = ApplicationHub.getCatalogSettings("Email-Send");

        if (settings == null) {
            Logger.error("Missing email settings");
            return false;
        }

        boolean smtpAuth = settings.getAttributeAsBooleanOrFalse("SmtpAuth");
        boolean smtpDebug = settings.getAttributeAsBooleanOrFalse("SmtpDebug");

        String debugBCC = settings.getAttribute("BccDebug");
        String skipto = settings.getAttribute("SkipToAddress");

        try {
            if (StringUtil.isEmpty(from))
                from = settings.getAttribute("DefaultFrom");

            if (StringUtil.isEmpty(reply))
                reply = settings.getAttribute("DefaultReplyTo");

            if (StringUtil.isEmpty(body) && StringUtil.isNotEmpty(textbody)) {
                XElement root = MarkdownUtil.process(textbody, true);

                if (root == null) {
                    Logger.error("inline md error: ");
                    return false;
                }

                body = root.toPrettyString();
            }

            Logger.info("Sending email from: " + from);
            Logger.info("Sending email to: " + to);

            Properties props = new Properties();

            if (smtpAuth) {
                props.put("mail.smtp.auth", "true");

                // TODO put this back in for Java8 - until then we have issues with Could not generate DH keypair
                // see http://stackoverflow.com/questions/12743846/unable-to-send-an-email-using-smtp-getting-javax-mail-messagingexception-could
                props.put("mail.smtp.starttls.enable", "true");
            }

            Session sess = Session.getInstance(props);

            // do debug on task with trace level
            if (smtpDebug || (OperationContext.getOrThrow().getDebugLevel() == DebugLevel.Trace)) {
                sess.setDebugOut(new SimpleSMTPUtil.DebugPrintStream(OperationContext.getOrThrow()));
                sess.setDebug(true);
            }

            // Create a new Message
            Message email = new MimeMessage(sess);

            InternetAddress fromaddr = StringUtil.isEmpty(from) ? null : InternetAddress.parse(from.replace(';', ','))[0];
            InternetAddress[] rplyaddrs = null;

            try {
                rplyaddrs = StringUtil.isEmpty(reply) ? null : InternetAddress.parse(reply.replace(';', ','));
            }
            catch (Exception x) {
                // TODO reply to can be blank
            }

            InternetAddress[] toaddrs = new InternetAddress[0];
            InternetAddress[] dbgaddrs = new InternetAddress[0];

            if (StringUtil.isNotEmpty(to))
                toaddrs = InternetAddress.parse(to.replace(';', ','));

            if (StringUtil.isNotEmpty(debugBCC))
                dbgaddrs = InternetAddress.parse(debugBCC.replace(';', ','));

            if (StringUtil.isNotEmpty(skipto)) {
                List<InternetAddress> passed = new ArrayList<InternetAddress>();

                for (int i = 0; i < toaddrs.length; i++) {
                    InternetAddress toa = toaddrs[i];

                    if (!toa.getAddress().contains(skipto))
                        passed.add(toa);
                }

                toaddrs = passed.stream().toArray(InternetAddress[]::new);
            }

            try {
                email.setFrom(fromaddr);

                if (rplyaddrs != null)
                    email.setReplyTo(rplyaddrs);

                if (toaddrs != null)
                    email.addRecipients(javax.mail.Message.RecipientType.TO, toaddrs);

                if (dbgaddrs != null)
                    email.addRecipients(javax.mail.Message.RecipientType.BCC, dbgaddrs);

                email.setSubject(subject);

                // ALTERNATIVE TEXT/HTML CONTENT
                MimeMultipart cover = new MimeMultipart(StringUtil.isNotEmpty(textbody) ? "alternative" : "mixed");

                if (StringUtil.isNotEmpty(textbody)) {
                    MimeBodyPart txt = new MimeBodyPart();
                    txt.setText(textbody);
                    cover.addBodyPart(txt);
                }

                // add the message part
                if (StringUtil.isNotEmpty(body)) {
                    MimeBodyPart html = new MimeBodyPart();
                    html.setContent(body, "text/html");
                    cover.addBodyPart(html);
                }

                email.setContent(cover);
            }
            catch (Exception x) {
                Logger.error("dcSendMail unable to send message due to invalid fields.");
            }

            String smtpHost = settings.getAttribute("SmtpHost");
            int smtpPort = (int) StringUtil.parseInt(
                    settings.getAttribute("SmtpPort"), 587);
            String smtpUsername = settings.getAttribute("SmtpUsername");
            String smtpPassword = settings.hasAttribute("SmtpPassword")
                    ? ApplicationHub.getClock().getObfuscator().decryptHexToString(settings.getAttribute("SmtpPassword"))
                    : null;

            InternetAddress[] recip = Stream.concat(Arrays.stream(toaddrs), Arrays.stream(dbgaddrs)).toArray(InternetAddress[]::new);

            if (recip.length > 0) {
                email.saveChanges();

                try (Transport t = email.getSession().getTransport("smtp")) {
                    t.connect(smtpHost, smtpPort, smtpUsername, smtpPassword);

                    t.sendMessage(email, recip);

                    // TODO wish we could get INFO: Received successful response:
                    // 200, AWS Request ID: b599ca95-bc82-11e0-846a-ab5fa57d84d4

                    return true;
                }
            }
        }
        catch (AddressException x) {
            Logger.error("dcSendMail unable to send message due to addressing problems.  Error: " + x);
        }
        catch (MessagingException x) {
            Logger.error("dcSendMail unable to send message due to transport problems.  Error: " + x);
        }

        return false;
    }

    static public class DebugPrintStream extends PrintStream {
        //protected OperationContext or = null;

        public DebugPrintStream(OperationContext or) {
            super(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    if (b == 13)
                        System.out.println();
                    else
                        System.out.print(HexUtil.charToHex(b));
                }
            });

            //this.or = or;
        }

        @Override
        public void println(String msg) {
            // TODO do we need to `use` context?
            Logger.trace(msg);
        }
    }
}
