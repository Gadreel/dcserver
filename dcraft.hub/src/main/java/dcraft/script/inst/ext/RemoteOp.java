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
package dcraft.script.inst.ext;

import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.*;
import dcraft.struct.scalar.BinaryStruct;
import dcraft.struct.scalar.DateStruct;
import dcraft.task.TaskContext;
import dcraft.tool.certs.CertUtil;
import dcraft.util.KeyUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.net.*;
import dcraft.xml.XElement;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.function.BiConsumer;

public class RemoteOp extends Instruction {
	static public RemoteOp tag() {
		RemoteOp el = new RemoteOp();
		el.setName("dcs.RemoteOp");
		return el;
	}

	@Override
	public XElement newNode() {
		return RemoteOp.tag();
	}

	@Override
	public ReturnOption run(InstructionWork stack) throws OperatingContextException {
		if (stack.getState() == ExecuteState.READY) {
			String url = StackUtil.stringFromSource(stack, "Url");
			String method = StackUtil.stringFromSource(stack, "Method", "POST");

			if (StringUtil.isEmpty(url)) {
				Logger.warn("Missing url");
				return ReturnOption.CONTINUE;
			}

			HttpRequest.Builder builder = HttpRequest.newBuilder()
					.header("User-Agent", "dcServer/2021.1 (Language=Java/11)")
					.uri(URI.create(url));

			String contentType = StackUtil.stringFromSource(stack, "ContentType", "application/json");

			RecordStruct headers = Struct.objectToRecord(StackUtil.refFromSource(stack,"Headers"));

			if (headers != null) {
				for (FieldStruct fld : headers.getFields()) {
					if (fld.getValue() != null) {
						builder.header(fld.getName(), fld.getValue().toString());

						if ("Content-Type".equals(fld.getName()))
							contentType = fld.getValue().toString();
					}
				}
			}

			builder.header("Content-Type", contentType);

			System.out.println("Endpoint: " + url);

			BaseStruct body = StackUtil.refFromSource(stack,"Content");

			if (body != null) {
				if (body instanceof BinaryStruct) {
					Memory mem = Struct.objectToBinary(body);

					// review - does it work? is it effecient?
					if ("PUT".equals(method))
						builder.PUT(HttpRequest.BodyPublishers.ofByteArray(mem.toArray()));
					else
						builder.POST(HttpRequest.BodyPublishers.ofByteArray(mem.toArray()));
				}
				else if (body instanceof FileStoreFile) {
					// TODO
				}
				else if ("application/x-www-form-urlencoded".equals(contentType) && (body instanceof RecordStruct)) {
					RecordStruct values = Struct.objectToRecord(body);

					String paramstr = "";

					for (FieldStruct fld : values.getFields()) {
						if (paramstr.length() > 0)
							paramstr += "&";

						try {
							paramstr += fld.getName() + "=" + URLEncoder.encode(fld.getValue().toString(), "UTF-8");
						}
						catch (UnsupportedEncodingException x) {
							// nonsense
						}
					}

					if ("PUT".equals(method))
						builder.PUT(HttpRequest.BodyPublishers.ofString(paramstr));
					else
						builder.POST(HttpRequest.BodyPublishers.ofString(paramstr));
				}
				else {
					String strbody = Struct.objectToString(body);

					if (stack != null)
						strbody = StackUtil.resolveValueToString(stack, strbody, true);

					if ("PUT".equals(method))
						builder.PUT(HttpRequest.BodyPublishers.ofString(strbody));
					else
						builder.POST(HttpRequest.BodyPublishers.ofString(strbody));
				}
			}
			else if ("DELETE".equals(method)) {
				builder.DELETE();
			}
			else {
				builder.GET();
			}

			String resultType = StackUtil.stringFromSource(stack, "ResultType", "application/json");

			HttpResponse.BodyHandler<? extends BaseStruct> resultHandler = new StringStructSubscriber();

			if ("application/json".equals(resultType) || "json".equals(resultType)) {
				resultHandler = new JSONSubscriber();
			}
			else if ("text/xml".equals(resultType) || "xml".equals(resultType)) {
				resultHandler = new XmlSubscriber();
			}
			else if ("application/octet-stream".equals(resultType) || "binary".equals(resultType)) {
				resultHandler = new BinaryStructSubscriber();
			}

			String result = StackUtil.stringFromSource(stack, "Result");
			String responseResult = StackUtil.stringFromSource(stack, "ResponseResult");
			String certResult = StackUtil.stringFromSource(stack, "CertResult");

			TaskContext ctx = OperationContext.getAsTaskOrThrow();

			HttpClient.newHttpClient().sendAsync(builder.build(), resultHandler).whenComplete(new BiConsumer<HttpResponse<? extends BaseStruct>, Throwable>() {
				@Override
				public void accept(HttpResponse<? extends BaseStruct> response, Throwable throwable) {
					OperationContext.set(ctx);

					if (throwable != null) {
						Logger.error("Error processing, unable to connect or read from url. Error: " + throwable);
					}

					if (response != null) {
						try {
							if (StringUtil.isNotEmpty(certResult)) {
								RecordStruct certResultRecord = RecordStruct.record()
										.with("Status", "Not Present");

								if (response.sslSession().isPresent()) {
									try {
										Certificate[] certificates = response.sslSession().get().getPeerCertificates();

										// the first should the server's
										if (certificates.length > 0) {
											certResultRecord.with("Status", "Server Cert Not Found");

											Certificate certificate = certificates[0];

											//try {
												/*
												CertificateFactory cf = CertificateFactory.getInstance("X.509");
												ByteArrayInputStream bais = new ByteArrayInputStream(certificate.getEncoded());
												X509Certificate x509 = (X509Certificate) cf.generateCertificate(bais);
												 */

												if (certificate instanceof X509Certificate) {
													X509Certificate x509 = (X509Certificate) certificate;

													///x509.getKeyUsage()

													//List<String> usage = x509.getExtendedKeyUsage();

													Date after = x509.getNotAfter();

													String subject = x509.getSubjectDN().toString();
													String thumbprint = KeyUtil.getCertThumbprint(x509);

													//System.out.println("Shared Cert - Subject: " + subject + " - Thumbprint: " + thumbprint
													//		+ " - Date: " + after);
													//System.out.println("   usage: " + usage);

													certResultRecord
															.with("Status", "Checked")
															.with("Subject", subject)
															.with("AlternativeNames", ListStruct.list(CertUtil.getSubjectAlternativeNames(x509)))
															.with("Thumbprint", thumbprint)
															.with("Expires", LocalDate.ofInstant(after.toInstant(), ZoneId.of("UTC")))
													;
												}
												else {
													certResultRecord.with("Status", "Wrong Cert Format");
												}

												/*
											}
											catch (CertificateException x) {
												System.out.println("bad cert format");
											}

												 */
										}
									}
									catch (SSLPeerUnverifiedException x) {
										//Logger.error("Peer certificate unverified. Error: " + x);
										certResultRecord.with("Status", "Unverified");
									}
								}

								StackUtil.addVariable(stack, certResult, certResultRecord);
							}

							if ((response.body() != null) && StringUtil.isNotEmpty(result))
								StackUtil.addVariable(stack, result, response.body());

							if (StringUtil.isNotEmpty(responseResult)) {
								RecordStruct responseRec = RecordStruct.record();

								int responseCode = response.statusCode();

								responseRec.with("Code", responseCode);

								RecordStruct respheaders = RecordStruct.record();

								for (String hdrs : response.headers().map().keySet()) {
									respheaders.with(hdrs, response.headers().firstValue(hdrs).get());
								}

								responseRec.with("Headers", respheaders);

								StackUtil.addVariable(stack, responseResult, responseRec);
							}
						}
						catch (OperatingContextException x) {
							// NA, we just set the context
						}
					}

					stack.setState(ExecuteState.RESUME);

					try {
						ctx.resume();
					}
					catch (Exception x) {
						Logger.error("Unable to resume after RemoteOp inst: " + x);
					}
				}
			});

			return ReturnOption.AWAIT;
		}

		return ReturnOption.CONTINUE;
	}
}
