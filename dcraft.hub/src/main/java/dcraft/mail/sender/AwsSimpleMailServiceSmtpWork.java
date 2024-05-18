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
package dcraft.mail.sender;

import dcraft.filestore.FileStoreFile;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeComposite;
import dcraft.interchange.aws.AWSSMS;
import dcraft.log.Logger;
import dcraft.struct.BaseStruct;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.IOUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.io.InputWrapper;
import dcraft.util.io.OutputWrapper;
import dcraft.web.md.MarkdownUtil;
import dcraft.xml.XElement;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Session;
import javax.mail.internet.*;
import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class AwsSimpleMailServiceSmtpWork extends StateWork {
	public StateWorkStep prepStep = null;
	public StateWorkStep attachStep = null;
	public StateWorkStep sendStep = null;
	public StateWorkStep doneStep = null;

	protected RecordStruct params = null;
	protected javax.mail.Message email = null;
	protected String to = null;
	protected InternetAddress[] toaddrs = new InternetAddress[0];
	protected InternetAddress[] dbgaddrs = new InternetAddress[0];
	protected MimeMultipart content = null;
	protected int attachcnt = 0;
	protected int currattach = 0;

	public AwsSimpleMailServiceSmtpWork() {
	}

	public AwsSimpleMailServiceSmtpWork(RecordStruct params) {
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

	        Session sess = Session.getInstance(props);

			sess.setDebug(true);

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
			}
			
	        try {				
				this.email.setFrom(fromaddr);
	        	
	        	if (rplyaddrs != null)
	        		this.email.setReplyTo(rplyaddrs);
	        	
	        	if (toaddrs != null)
	        		this.email.addRecipients(javax.mail.Message.RecipientType.TO, toaddrs);
	        	
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

					AwsSimpleMailServiceSmtpWork.this.content.addBodyPart(apart);

					AwsSimpleMailServiceSmtpWork.this.currattach++;
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
						AwsSimpleMailServiceSmtpWork.this.transition(trun, doneStep);
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

		InternetAddress[] recip = Stream.concat(Arrays.stream(this.toaddrs),
				Arrays.stream(this.dbgaddrs)).toArray(InternetAddress[]::new);

		if (! trun.hasExitErrors() && (recip.length > 0)) {
			Memory buffer = new Memory();
			OutputWrapper outputStream = new OutputWrapper(buffer);

			try {
				this.email.saveChanges();

				email.writeTo(outputStream);

				// TODO more than this - aws json needs to be in the memory

				AWSSMS.sendEmail(null, buffer, new OperationOutcomeComposite() {
					@Override
					public void callback(CompositeStruct result) throws OperatingContextException {
						System.out.println("done: " + result);

						// TODO should be MessageId
					}
				});

				// TODO wish we could get INFO: Received successful response:
				// 200, AWS Request ID: b599ca95-bc82-11e0-846a-ab5fa57d84d4
			} 
			catch (Exception x) {
				Logger.error("dcSendMail unable to send message due to service problems.  Error: " + x);
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

			for (int i = 0 ; i < this.dbgaddrs.length; i++)
				addresses.with(this.dbgaddrs[i].toUnicodeString());

			String mid = null;

			// TODO alt way to get this
//			if (StringUtil.isNotEmpty(this.extractStatusStream.getMessageId()))
//				mid = "<" + this.extractStatusStream.getMessageId() + "@email.amazonses.com>";		// TODO configure which MTA can appear here

			trun.setResult(RecordStruct.record()
					.with("Success", ! trun.hasExitErrors())
					.with("MessageId", mid)
					.with("ActualAddresses", addresses)
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
}
