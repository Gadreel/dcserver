package dcraft.mail.sender;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.mail.EmailAddress;
import dcraft.mail.MailUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.util.StringUtil;
import dcraft.web.md.MarkdownUtil;
import dcraft.xml.XElement;

import java.util.List;

public class AwsUtil {
	// TODO work in progress
	static public IWork buildSendEmailWork(RecordStruct params) throws OperatingContextException {
		if (AwsUtil.canUseSimpleMailServiceHttp(params)) {
			RecordStruct simpleParams = AwsUtil.createSimpleMailServiceHttpParams(params);
			return new AwsSimpleMailServiceHttpWork(simpleParams);
		}

		Logger.error("Smtp not supported on new sender system yet!");
		return null;

		// TODO test this more - does it also need S
		//return new AwsSimpleMailServiceSmtpWork(params);
	}

	static public boolean canUseSimpleMailServiceHttp(RecordStruct params) {
		// if there are headers we cannot use simple
		if (params.isNotFieldEmpty("InReplyTo") || params.isNotFieldEmpty("Unsubscribe"))
			return false;

		// if there are attachments we cannot use simple
		if (params.isNotFieldEmpty("Attachments"))
			return false;

		return true;
	}

	/*
		From:
		ReplyTo:  [optional]
		To:
		Cc:  [optional]
		Bcc:  [optional]
		Subject:
		Html:  [optional]
		Text:
	 */
	static public RecordStruct createSimpleMailServiceHttpParams(RecordStruct standardParams) throws OperatingContextException {
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

		/* assume we already processed
		if (StringUtil.isEmpty(body) && StringUtil.isNotEmpty(textbody)) {
			XElement root = MarkdownUtil.process(textbody, true);

			if (root == null) {
				Logger.error("inline md error: ");
				return null;
			}

			body = root.toPrettyString();
		}
		 */

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
					if (! address.containsDomain(skipto)) {
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
				.with("AwsMailHttp", RecordStruct.record()
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

}
