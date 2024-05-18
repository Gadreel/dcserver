package dcraft.mail;

import dcraft.db.BasicRequestContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.mail.sender.AwsSimpleMailServiceHttpWork;
import dcraft.mail.sender.AwsSimpleMailServiceSmtpWork;
import dcraft.mail.sender.AwsUtil;
import dcraft.mail.sender.SmtpWork;
import dcraft.schema.SchemaResource;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.service.ServiceRequest;
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
import dcraft.xml.XmlReader;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailUtil {
    static public final Pattern SSI_PATH_PATTERN = Pattern.compile("<!--#include path=\"(.*)\" -->");

    // add .v to end of comm name
    static protected CommonPath toCommFolder(CommonPath path) {
        if (path != null)
            path = path.getParent().resolve(path.getFileName() + ".v");

        return path;
    }

    static public IWork buildSendEmailWork(RecordStruct params) throws OperatingContextException {
        // TODO configure email services someday, right we assume AWS
        return AwsUtil.buildSendEmailWork(params);
    }

    // full path support only
    // include sections like so:  /dcc/skeletons/tx.v/code-email-[section].[locale].html
    static public CharSequence processSSIIncludes(CharSequence content) throws OperatingContextException {
        if (StringUtil.isEmpty(content))
            return null;

        OperationContext ctx = OperationContext.getOrThrow();

        // allow for nested includes

        boolean checkmatches = true;

        while (checkmatches) {
            checkmatches = false;
            Matcher m = MailUtil.SSI_PATH_PATTERN.matcher(content);

            while (m.find()) {
                String grp = m.group();

                String vfilename = grp.substring(1, grp.length() - 1);

                vfilename = vfilename.substring(vfilename.indexOf('"') + 1);
                vfilename = vfilename.substring(0, vfilename.indexOf('"'));

                Path sf = ctx.getSite().findSectionFile("communicate", vfilename, null);

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
