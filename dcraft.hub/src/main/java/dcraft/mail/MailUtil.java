package dcraft.mail;

import dcraft.filestore.CommonPath;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.struct.*;
import dcraft.task.IParentAwareWork;
import dcraft.task.IWork;
import dcraft.tenant.Site;
import dcraft.tenant.WebFindResult;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.web.md.MarkdownUtil;
import dcraft.web.ui.inst.Html;
import dcraft.xml.XElement;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailUtil {
    static public final Pattern SSI_VIRTUAL_PATTERN = Pattern.compile("<!--#include virtual=\"(.*)\" -->");

    static public IEmailOutputWork emailFindFile(Site site, CommonPath path, String view) throws OperatingContextException {
        // =====================================================
        //  if request has an extension do specific file lookup
        // =====================================================

        if (Logger.isDebug())
            Logger.debug("find file before ext check: " + path + " - " + view);

        // if we have an extension then we don't have to do the search below
        // never go up a level past a file (or folder) with an extension
        if (path.hasFileExtension()) {
            WebFindResult wpath = MailUtil.emailFindFilePath(site, path, view);

            if (wpath != null)
                return MailUtil.emailPathToAdapter(site, view, wpath);

            // let caller decide if error - Logger.errorTr(150007);
            return null;
        }

        // =====================================================
        //  if request does not have an extension look for files
        //  that might match this path or one of its parents
        //  using the special extensions
        // =====================================================

        if (Logger.isDebug())
            Logger.debug("find dyn file: " + path + " - " + view);

        WebFindResult wpath = MailUtil.emailFindFilePath(site, path, view);

        if (wpath == null) {
            // let caller decide if error - Logger.errorTr(150007);
            return null;
        }

        if (Logger.isDebug())
            Logger.debug("find file path: " + wpath + " - " + path + " - " + view);

        return MailUtil.emailPathToAdapter(site, view, wpath);
    }

    static public IEmailOutputWork emailPathToAdapter(Site site, String view, WebFindResult wpath) throws OperatingContextException {
        String filename = wpath.file.getFileName().toString();

        int ldot = filename.indexOf('.');
        String ext = (ldot >= 0) ? filename.substring(ldot) : null;

        String classname = site.getEmailBuilder(ext);

        if (StringUtil.isNotEmpty(classname)) {
            try {
                IEmailOutputWork outputWork = (IEmailOutputWork) site.getResources().getClassLoader().getInstance(classname);

                outputWork.init(wpath.file, wpath.path, view);

                return outputWork;
            }
            catch (Exception x) {
                Logger.error("Bad Dynamic Email Adapter: " + x);
            }
        }

        return null;
    }

    static public WebFindResult emailFindFilePath(Site site, CommonPath path, String view) {
        // figure out which section we are looking in
        String sect = "email";

        if (Logger.isDebug())
            Logger.debug("find file path: " + path + " in " + sect);

        // =====================================================
        //  if request has an extension do specific file lookup
        // =====================================================

        // if we have an extension then we don't have to do the search below
        // never go up a level past a file (or folder) with an extension
        if (path.hasFileExtension()) {
            Path spath = site.findSectionFile(sect, path.toString(), view);

            if (spath == null)
                return null;

            return WebFindResult.of(spath, path);
        }

        // =====================================================
        //  if request does not have an extension look for files
        //  that might match this path or one of its parents
        //  using the special extensions
        // =====================================================

        if (Logger.isDebug())
            Logger.debug("find file path dyn: " + path + " in " + sect);

        // we get here if we have no extension - thus we need to look for path match with specials
        int pdepth = path.getNameCount();

        Set<String> specialExtensions = site.getEmailExtensions();

        // check file system
        while (pdepth > 0) {
            CommonPath ppath = path.subpath(0, pdepth);

            // we want to check all extensions at folder level then go up the path
            for (String ext : specialExtensions) {
                Path cfile = site.findSectionFile(sect, ppath.toString() + ext, view);

                if (cfile != null)
                    return WebFindResult.of(cfile, ppath);
            }

            pdepth--;
        }

        // let caller decide if error
        return null;
    }

    static public CharSequence processSSIIncludes(CharSequence content, String view) throws OperatingContextException {
        if (StringUtil.isEmpty(content))
            return null;

        OperationContext ctx = OperationContext.getOrThrow();

        boolean checkmatches = true;

        while (checkmatches) {
            checkmatches = false;
            Matcher m = MailUtil.SSI_VIRTUAL_PATTERN.matcher(content);

            while (m.find()) {
                String grp = m.group();

                String vfilename = grp.substring(1, grp.length() - 1);

                vfilename = vfilename.substring(vfilename.indexOf('"') + 1);
                vfilename = vfilename.substring(0, vfilename.indexOf('"'));

                //System.out.println("include v file: " + vfilename);

                Path sf = ctx.getSite().findSectionFile("email", vfilename, view);

                if (sf == null)
                    continue;

                CharSequence val = IOUtil.readEntireFile(sf);

                if (val == null)
                    val = "";

                content = content.toString().replace(grp, val);
                checkmatches = true;
            }
        }

        return content;
    }

    static public MDParseResult mdToDynamic(IParentAwareWork scope, Path file) {
        CharSequence md = IOUtil.readEntireFile(file);

        if (md.length() == 0)
            return null;

        // TODO md = this.processIncludes(wctx, md);

        MDParseResult result = new MDParseResult();

        try {
            BufferedReader bufReader = new BufferedReader(new StringReader(md.toString()));

            String line = bufReader.readLine();

            // TODO enhance to become https://www.npmjs.com/package/front-matter compatible

            // start with $ for non-locale fields
            while (StringUtil.isNotEmpty(line)) {
                int pos = line.indexOf(':');

                if (pos == -1)
                    break;

                String field = line.substring(0, pos);

                String value = line.substring(pos + 1).trim();

                result.fields.with(field, value);

                line = bufReader.readLine();
            }

            // String locale = LocaleUtil.normalizeCode(fields.getFieldAsString("Locale", "eng"));  // should be a way to override, but be careful because 3rd party might depend on being "en", sorry something has to be default


            // see if there is more - the body
            if (line != null) {
                StringBuilder sb = new StringBuilder();

                line = bufReader.readLine();

                while (line != null) {
                    sb.append(line);
                    sb.append("\n");

                    line = bufReader.readLine();
                }

                String content = StackUtil.resolveValueToString(scope, sb.toString());

                result.markdown = content;

                XElement root = MarkdownUtil.process(content, true);

                if (root != null) {
                    // TODO check for Skeleton field - if present then load that and make content into a variable

                    Html html = Html.tag();     // TODO switch to dcCerberus Html tag

                    result.script = Script.of(html, content);

                    return result;
                }
            }
        }
        catch (Exception x) {
            System.out.println("md parse issue");
        }

        return null;
    }

    static public class MDParseResult {
        public RecordStruct fields = RecordStruct.record();
        public String markdown = null;
        public Script script = null;
    }

    static public IWork buildSendEmailWork(RecordStruct params) throws OperatingContextException {
        if (MailUtil.isSimpleSMSEmail(params)) {
            RecordStruct simpleParams = MailUtil.createSimpleSMSParams(params);
            return new SimpleSmsWork(simpleParams);
        }

        return new SmtpWork(params);
    }

    static public boolean isSimpleSMSEmail(RecordStruct params) {
        // if there are headers we cannot use simple
        if (params.isNotFieldEmpty("InReplyTo") || params.isNotFieldEmpty("Unsubscribe"))
            return false;

        // if there are attachments we cannot use simple
        if (params.isNotFieldEmpty("Attachments"))
            return false;

        return true;
    }

    static public RecordStruct createSimpleSMSParams(RecordStruct standardParams) throws OperatingContextException {
        XElement settings = ApplicationHub.getCatalogSettings("Email-Send");

        if (settings == null) {
            Logger.error("Missing email settings");
            return null;
        }

        // supports only 1 domain/address
        String skipto = settings.getAttribute("SkipToAddress", "");

        String from = standardParams.getFieldAsString("From");
        String reply = standardParams.getFieldAsString("ReplyTo");

        if (StringUtil.isEmpty(from))
            from = settings.getAttribute("DefaultFrom");

        if (StringUtil.isEmpty(reply))
            reply = settings.getAttribute("DefaultReplyTo");

        String to = standardParams.getFieldAsString("To");
        String cc = standardParams.getFieldAsString("Cc");
        String bcc = standardParams.getFieldAsString("Bcc");
        String subject = standardParams.getFieldAsString("Subject");
        String body = standardParams.getFieldAsString("Html");
        String textbody = standardParams.getFieldAsString("Text");

        if (StringUtil.isEmpty(body) && StringUtil.isNotEmpty(textbody)) {
            XElement root = MarkdownUtil.process(textbody, true);

            if (root == null) {
                Logger.error("inline md error: ");
                return null;
            }

            body = root.toPrettyString();
        }

        Logger.info("Sending email from: " + from);
        Logger.info("Sending email to: " + to);

        String fromfinal = MailUtil.normalizeEmailAddress(from);

        if (StringUtil.isEmpty(fromfinal) && settings.hasNotEmptyAttribute("DefaultFrom")) {
            from = settings.getAttribute("DefaultFrom");
            fromfinal = MailUtil.normalizeEmailAddress(from);
        }

        if (StringUtil.isEmpty(fromfinal)) {
            Logger.error("Missing or invalid from field for email");
            return null;
        }

        ListStruct tolist = ListStruct.list();
        ListStruct cclist = ListStruct.list();
        ListStruct bcclist = ListStruct.list();
        ListStruct actuallist = ListStruct.list();
        ListStruct replylist = ListStruct.list();

        if (StringUtil.isNotEmpty(to)) {
            List<EmailAddress> parsed = EmailAddress.parseList(to);

            if (parsed != null) {
                for (EmailAddress address : parsed) {
                    if (!address.containsDomain(skipto)) {
                        tolist.with(address.toStringForTransport("aws"));       // TODO don't hard code, configure
                        actuallist.with(address.toStringNormalized());
                    }
                }
            }
        }

        if (tolist.isEmpty()) {
            Logger.error("Missing 'to' field for email");
            return null;
        }

        if (StringUtil.isNotEmpty(cc)) {
            List<EmailAddress> parsed = EmailAddress.parseList(cc);

            if (parsed != null) {
                for (EmailAddress address : parsed) {
                    if (!address.containsDomain(skipto)) {
                        cclist.with(address.toStringForTransport("aws"));       // TODO don't hard code, configure
                        actuallist.with(address.toStringNormalized());
                    }
                }
            }
        }

        if (StringUtil.isNotEmpty(bcc)) {
            List<EmailAddress> parsed = EmailAddress.parseList(bcc);

            if (parsed != null) {
                for (EmailAddress address : parsed) {
                    if (!address.containsDomain(skipto)) {
                        bcclist.with(address.toStringForTransport("aws"));       // TODO don't hard code, configure
                        actuallist.with(address.toStringNormalized());
                    }
                }
            }
        }

        if (StringUtil.isNotEmpty(reply)) {
            List<EmailAddress> parsed = EmailAddress.parseList(reply);

            if ((parsed == null) || (parsed.size() == 0)) {
                if (settings.hasNotEmptyAttribute("DefaultReplyTo")) {
                    reply = settings.getAttribute("DefaultReplyTo");
                    parsed = EmailAddress.parseList(reply);
                }
            }

            if (parsed != null) {
                for (EmailAddress address : parsed) {
                    if (!address.containsDomain(skipto)) {
                        replylist.with(address.toStringForTransport("aws"));       // TODO don't hard code, configure
                    }
                }
            }
        }

        return RecordStruct.record()
                .with("ActualAddresses", actuallist)
                .with("Sms", RecordStruct.record()
                        .with("FromEmailAddress", fromfinal)
                        .with("Destination", RecordStruct.record()
                                .with("ToAddresses", tolist)
                                .withConditional("CcAddresses", cclist)
                                .withConditional("BccAddresses", bcclist)
                        )
                        .with("Content", RecordStruct.record()
                                .with("Simple", RecordStruct.record()
                                        .with("Subject", RecordStruct.record()
                                                .with("Charset", "UTF-8")
                                                .with("Data", subject)
                                        )
                                        .with("Body", RecordStruct.record()
                                                .withConditional(StringUtil.isNotEmpty(textbody), "Text", RecordStruct.record()
                                                        .with("Charset", "UTF-8")
                                                        .with("Data", textbody)
                                                )
                                                .withConditional(StringUtil.isNotEmpty(body), "Html", RecordStruct.record()
                                                        .with("Charset", "UTF-8")
                                                        .with("Data", body)
                                                )
                                        )
                                )
                        )
                        .withConditional("ReplyToAddresses", replylist)
                );
    }

    // for indexing, not for sending, storage or display
    //
    // assumes a single address to parse
    // lowercase 7 bit ascii and idn domains, lower case the local (account)
    //
    // indexing cannot handle values greater than 1000
    // for all valid email addresses (which should be under 80 chars) this is not a problem
    // but to be sure we use a hash if the value is too large
    static public String indexableEmailAddress(String address) {
        List<EmailAddress> parsed = EmailAddress.parseList(address);

        if ((parsed == null) || (parsed.size() == 0))
            return null;

        return parsed.get(0).toStringForIndex();
    }

    // for sending, not for storage or display
    // assumes single address
    //
    // lowercase 7 bit ascii and idn domains
    static public String normalizeEmailAddress(String address) {
        List<EmailAddress> parsed = EmailAddress.parseList(address);

        if ((parsed == null) || (parsed.size() == 0))
            return null;

        return parsed.get(0).toStringNormalized();
    }
}