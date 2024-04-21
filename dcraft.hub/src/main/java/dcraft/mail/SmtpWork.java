/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.mail;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.sun.mail.smtp.SMTPMessage;
import com.sun.mail.util.LogOutputStream;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.log.DebugLevel;
import dcraft.log.Logger;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.HexUtil;
import dcraft.util.IOUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.io.InputWrapper;
import dcraft.util.io.OutputWrapper;
import dcraft.web.md.MarkdownUtil;
import dcraft.web.ui.UIUtil;
import dcraft.xml.XElement;

public class SmtpWork extends StateWork {
	public StateWorkStep prepStep = null;
	public StateWorkStep attachStep = null;
	public StateWorkStep sendStep = null;
	public StateWorkStep doneStep = null;

	protected RecordStruct params = null;
	protected javax.mail.Message email = null;
	protected String to = null;
	protected String cc = null;
	protected String bcc = null;
	protected InternetAddress[] toaddrs = new InternetAddress[0];
	protected InternetAddress[] ccaddrs = new InternetAddress[0];
	protected InternetAddress[] bccaddrs = new InternetAddress[0];
	protected InternetAddress[] dbgaddrs = new InternetAddress[0];
	protected MimeMultipart content = null;
	protected int attachcnt = 0;
	protected int currattach = 0;
	protected ExtractPrintStream extractStatusStream = new ExtractPrintStream();

	public SmtpWork() {
	}

	public SmtpWork(RecordStruct params) {
		this.params = params;
	}

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
			.withStep(prepStep = StateWorkStep.of("Prep Email Session", this::prepEmail))
			.withStep(attachStep = StateWorkStep.of("Add Attachments", this::addAttach))
			.withStep(sendStep = StateWorkStep.of("Send Email", this::sendEmail))
			.withStep(doneStep = StateWorkStep.of("Done", this::done));
	}

	public StateWorkStep prepEmail(TaskContext trun) throws OperatingContextException {
		if (this.params == null)
			this.params = (RecordStruct) trun.getParams();

		XElement settings = ApplicationHub.getCatalogSettings("Email-Send");
		
		if (settings == null) {
			Logger.error("Missing email settings");
			return this.doneStep;
		}
		
		boolean smtpAuth = settings.getAttributeAsBooleanOrFalse("SmtpAuth");
		boolean smtpDebug = settings.getAttributeAsBooleanOrFalse("SmtpDebug");
		
		String debugBCC = settings.getAttribute("BccDebug");
		String skipto = settings.getAttribute("SkipToAddress");
		
		try {
			String from = this.params.getFieldAsString("From");
			String reply = this.params.getFieldAsString("ReplyTo");
			
			if (StringUtil.isEmpty(from))
				from = settings.getAttribute("DefaultFrom");
			
			if (StringUtil.isEmpty(reply))
				reply = settings.getAttribute("DefaultReplyTo");
			
			this.to = this.params.getFieldAsString("To");
			this.cc = this.params.getFieldAsString("Cc");
			this.bcc = this.params.getFieldAsString("Bcc");
			String subject = this.params.getFieldAsString("Subject");
			String body = this.params.getFieldAsString("Html");
			String textbody = this.params.getFieldAsString("Text");

			if (StringUtil.isEmpty(body) && StringUtil.isNotEmpty(textbody)) {
				XElement root = MarkdownUtil.process(textbody, true);

				if (root == null) {
					Logger.error("inline md error: ");
					return this.doneStep;
				}

				body = root.toPrettyString();
			}

			Logger.info("Sending email from: " + from);
			Logger.info("Sending email to: " + to);
			
			Properties props = new Properties();

			//props.put("mail.mime.allowutf8", "true");
			
			if (smtpAuth) {
				props.put("mail.smtp.auth", "true");
				
				// TODO put this back in for Java8 - until then we have issues with Could not generate DH keypair
				// see http://stackoverflow.com/questions/12743846/unable-to-send-an-email-using-smtp-getting-javax-mail-messagingexception-could
				props.put("mail.smtp.starttls.enable", "true");
			}

			if (smtpDebug) {
				props.put("mail.debug", "true");
			}

	        Session sess = Session.getInstance(props);

			sess.setDebug(true);
			sess.setDebugOut(extractStatusStream);

	        // do debug on task with trace level
	        if (smtpDebug || (trun.getDebugLevel() == DebugLevel.Trace)) {
				extractStatusStream.setOut(true);
	        }
	        
	        // Create a new Message
	    	this.email = new MimeMessage(sess);

			InternetAddress fromaddr = StringUtil.isEmpty(from) ? null : InternetAddress.parse(from.replace(';', ','))[0];
			InternetAddress[] rplyaddrs = null;
			
			try {
				rplyaddrs = StringUtil.isEmpty(reply) ? null : InternetAddress.parse(reply.replace(';', ','));
			}
			catch (Exception x) {
				// TODO reply to can be blank
			}

			if (StringUtil.isNotEmpty(to))
				this.toaddrs = InternetAddress.parse(to.replace(';', ','));

			if (StringUtil.isNotEmpty(cc))
				this.ccaddrs = InternetAddress.parse(cc.replace(';', ','));

			if (StringUtil.isNotEmpty(bcc))
				this.bccaddrs = InternetAddress.parse(bcc.replace(';', ','));

			if (StringUtil.isNotEmpty(debugBCC))
				this.dbgaddrs = InternetAddress.parse(debugBCC.replace(';', ','));
			
			if (StringUtil.isNotEmpty(skipto)) {
				List<InternetAddress> passed = new ArrayList<>();
				
				for (int i = 0; i < toaddrs.length; i++) {
					InternetAddress toa = toaddrs[i];
					
					if (!toa.getAddress().contains(skipto))
						passed.add(toa);
				}
				
				toaddrs = passed.stream().toArray(InternetAddress[]::new);

				// cc

				List<InternetAddress> passedcc = new ArrayList<>();

				for (int i = 0; i < ccaddrs.length; i++) {
					InternetAddress toa = ccaddrs[i];

					if (!toa.getAddress().contains(skipto))
						passedcc.add(toa);
				}

				ccaddrs = passedcc.stream().toArray(InternetAddress[]::new);
			}
			
	        try {				
				this.email.setFrom(fromaddr);
	        	
	        	if (rplyaddrs != null)
	        		this.email.setReplyTo(rplyaddrs);
	        	
	        	if (toaddrs != null)
	        		this.email.addRecipients(javax.mail.Message.RecipientType.TO, toaddrs);

	        	if (ccaddrs != null)
	        		this.email.addRecipients(Message.RecipientType.CC, ccaddrs);

	        	if (bccaddrs != null)
	        		this.email.addRecipients(Message.RecipientType.BCC, bccaddrs);

	        	if (dbgaddrs != null)
	        		this.email.addRecipients(javax.mail.Message.RecipientType.BCC, dbgaddrs);
	        	
	        	this.email.setSubject(subject);

	        	if (this.params.isNotFieldEmpty("InReplyTo"))
		        	this.email.addHeader("In-Reply-To", this.params.getFieldAsString("InReplyTo"));

	        	if (this.params.isNotFieldEmpty("Unsubscribe")) {
					this.email.addHeader("List-Unsubscribe", this.params.getFieldAsString("Unsubscribe"));
					this.email.addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
				}

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
	            
	            ListStruct attachments = this.params.getFieldAsList("Attachments");
	            
	            this.attachcnt = (attachments != null) ? attachments.getSize() : 0;
	            
	            if (attachcnt > 0) {
	            	// hints - https://mlyly.wordpress.com/2011/05/13/hello-world/
		            // COVER WRAP
		            MimeBodyPart wrap = new MimeBodyPart();
		            wrap.setContent(cover);
		            
		            this.content = new MimeMultipart("related");
		            this.content.addBodyPart(wrap);	        	
	            	
	            	this.email.setContent(this.content);
	            }
	            else {
	            	this.email.setContent(cover);
	            }
	            
	        } 
	        catch (Exception x) {
	        	Logger.error("dcSendMail unable to send message due to invalid fields.");
	        }
		}
        catch (AddressException x) {
        	Logger.error("dcSendMail unable to send message due to addressing problems.  Error: " + x);
        }
		
		return StateWorkStep.NEXT;
	}

	public StateWorkStep addAttach(TaskContext trun) throws OperatingContextException {
		if (this.currattach >= this.attachcnt)
			return StateWorkStep.NEXT;

		ListStruct attachments = this.params.getFieldAsList("Attachments");

		RecordStruct attachment = attachments.getItemAsRecord(this.currattach);

		// add the attachment parts, if any
		String name = attachment.getFieldAsString("Name");
		String mime = attachment.getFieldAsString("Mime");
		
		if (StringUtil.isEmpty(mime))
			mime = ResourceHub.getResources().getMime().getMimeTypeForName(name).getMimeType();
		
		String fmime = mime;
		
		Consumer<Memory> addAttach = new Consumer<Memory>() {
			@Override
			public void accept(Memory mem) {
				mem.setPosition(0);

				MimeBodyPart apart = new MimeBodyPart();

				DataSource source = new DataSource() {
					@Override
					public OutputStream getOutputStream() throws IOException {
						return new OutputWrapper(mem);
					}

					@Override
					public String getName() {
						return name;
					}

					@Override
					public InputStream getInputStream() throws IOException {
						return new InputWrapper(mem);
					}

					@Override
					public String getContentType() {
						return fmime;
					}
				};

				try {
					apart.setDataHandler(new DataHandler(source));
					apart.setFileName(name);

					SmtpWork.this.content.addBodyPart(apart);

					SmtpWork.this.currattach++;
				} 
				catch (Exception x) {
					Logger.error("dcSendMail unable to send message due to invalid fields.");
				}

				// try next attachment
				trun.resume();
			}
		};

		Memory smem = attachment.getFieldAsBinary("Content");
		BaseStruct fobj = attachment.getField("File");

		if (smem != null) {
			smem.setPosition(0);
			addAttach.accept(smem);

			return StateWorkStep.WAIT;
		}
		else if (fobj instanceof StringStruct) {
			addAttach.accept(IOUtil.readEntireFileToMemory(Paths.get(((StringStruct) fobj).getValueAsString())));

			return StateWorkStep.WAIT;
		}
		else if (fobj instanceof FileStoreFile) {
			((FileStoreFile) fobj).readAllBinary(new OperationOutcome<Memory>() {
				@Override
				public void callback(Memory result) throws OperatingContextException {
					if (this.hasErrors())
						SmtpWork.this.transition(trun, doneStep);
					else
						addAttach.accept(this.getResult());
				}
			});

			return StateWorkStep.WAIT;
		}

		this.currattach++;

		return StateWorkStep.REPEAT;
	}

	public StateWorkStep sendEmail(TaskContext trun) throws OperatingContextException {
		XElement settings = ApplicationHub.getCatalogSettings("Email-Send");

		if (settings == null) {
			Logger.error("Missing email settings");
			return this.doneStep;
		}

		String smtpHost = settings.getAttribute("SmtpHost");
		int smtpPort = (int) StringUtil.parseInt(
				settings.getAttribute("SmtpPort"), 587);
		String smtpUsername = settings.getAttribute("SmtpUsername");
		String smtpPassword = settings.hasAttribute("SmtpPassword") 
				? ApplicationHub.getClock().getObfuscator().decryptHexToString(settings.getAttribute("SmtpPassword"))
				: null;

		ArrayList<InternetAddress> recips = new ArrayList<>();
		recips.addAll(Arrays.asList(this.toaddrs));
		recips.addAll(Arrays.asList(this.ccaddrs));
		recips.addAll(Arrays.asList(this.bccaddrs));
		recips.addAll(Arrays.asList(this.dbgaddrs));

		InternetAddress[] recip = recips.toArray(InternetAddress[]::new);

		if (! trun.hasExitErrors() && (recip.length > 0)) {
			Transport t = null;

			try {
				this.email.saveChanges();

				t = this.email.getSession().getTransport("smtp");

				t.connect(smtpHost, smtpPort, smtpUsername, smtpPassword);

				t.sendMessage(email, recip);

				t.close();

				// TODO wish we could get INFO: Received successful response:
				// 200, AWS Request ID: b599ca95-bc82-11e0-846a-ab5fa57d84d4
			} 
			catch (Exception x) {
				Logger.error("dcSendMail unable to send message due to service problems.  Error: " + x);
			}

			if (t != null) {
				if (t.isConnected()) {
					try {
						t.close();
					} 
					catch (MessagingException e) {
					}
				}
			}
		}

		if (trun.hasExitErrors())
			Logger.warn("Unable to send email to: " + this.to);
		else
			Logger.info("Email sent to: " + this.to);

		if ((this.params != null) && (this.params.getFieldAsBooleanOrFalse("Feedback"))) {
			ListStruct addresses = ListStruct.list();

			for (int i = 0 ; i < this.toaddrs.length; i++)
				addresses.with(this.toaddrs[i].toUnicodeString());

			for (int i = 0 ; i < this.ccaddrs.length; i++)
				addresses.with(this.ccaddrs[i].toUnicodeString());

			for (int i = 0 ; i < this.bccaddrs.length; i++)
				addresses.with(this.bccaddrs[i].toUnicodeString());

			for (int i = 0 ; i < this.dbgaddrs.length; i++)
				addresses.with(this.dbgaddrs[i].toUnicodeString());

			String mid = null;

			if (StringUtil.isNotEmpty(this.extractStatusStream.getMessageId()))
				mid = "<" + this.extractStatusStream.getMessageId() + "@email.amazonses.com>";		// TODO configure which MTA can appear here

			trun.setResult(RecordStruct.record()
					.with("Transport", RecordStruct.record()
						.with("Success", ! trun.hasExitErrors())
						.with("MessageId", mid)
						.with("ActualAddresses", addresses)
					)
			);
		}

		return StateWorkStep.NEXT;
	}

	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		return StateWorkStep.NEXT;
	}

	@Override
	protected void finalize() throws Throwable {
		this.content = null;
		this.email = null;
		
		super.finalize();
	}

	public class ExtractPrintStream extends PrintStream {
		protected boolean toOut = false;
		protected boolean dataSent = false;
		protected String messageid = null;

		public void setOut(boolean v) {
			this.toOut = v;
		}

		public String getMessageId() {
			return this.messageid;
		}

		public ExtractPrintStream() {
			super(LogOutputStream.nullOutputStream());
		}

		@Override
		public void println(String msg) {
			// TODO do we need to `use` context?
			//Logger.trace(msg);

			if ("DATA".equals(msg)) {
				this.dataSent = true;
			}
			else if (this.dataSent && StringUtil.isNotEmpty(msg)) {
				if (msg.startsWith("250 Ok")) {
					this.messageid = msg.substring(7);
				}
			}

			if (this.toOut)
				System.out.println("----- " + msg);
		}
	}
}
