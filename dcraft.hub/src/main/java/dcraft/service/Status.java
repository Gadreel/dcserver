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
package dcraft.service;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.app.HubState;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.stream.ReturnOption;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.stream.record.TransformRecordStream;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.task.run.WorkHub;
import dcraft.util.chars.TranslateLang;
import dcraft.xml.XElement;

public class Status extends BaseService {
	protected String version = null;
	protected String app = null;
	
	@Override
	public void init(String name, XElement config, ResourceTier tier) {
		super.init(name, config, tier);
		
		/* TODO
		OperationContext.useNewRoot();
		
		RecordStruct ldres = Updater.loadDeployed();
		
		if (ldres == null) {
			Logger.error("Error reading deployed.json file: " + ldres.getMessage());
			return;
		}
		
		RecordStruct deployed = ldres.getResult();
		
		this.version = deployed.getFieldAsString("Version");
		this.app = deployed.getFieldAsString("PackagePrefix");
		*/
	}
	
	@Override
	public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		if ("Test".equals(request.getFeature())) {
			if ("Echo".equals(request.getOp())) {
				Logger.info("Echo got: " + request.getData());
				callback.returnValue(request.getData());
				return true;
			}
			
			if ("EchoReverse".equals(request.getOp())) {
				Logger.info("Echo Reverse got: " + request.getData());
				callback.returnValue(Struct.objectToStruct(new StringBuilder(request.getData().toString()).reverse().toString()));
				return true;
			}
			
			if ("Tickle".equals(request.getOp())) {
				Logger.info("Tickle got: " + request.getData());
				callback.returnEmpty();
				return true;
			}			
		}
		else if ("Translate".equals(request.getFeature())) {
			// TODO support From and To - for now it is en to x-pig-latin
			RecordStruct rec = request.getDataAsRecord();
			
			String from = rec.getFieldAsString("From");
			String to = rec.getFieldAsString("To");
			
			if ("Text".equals(request.getOp())) {
				String original = rec.getFieldAsString("Text");
				
				callback.returnValue(Struct.objectToStruct(TranslateLang.translateText(original, from, to)));
				return true;
			}
			
			if ("TextStream".equals(request.getOp())) {
				StreamFragment ss = request.getRequestStream();
				StreamFragment sd = request.getResponseStream();
				
				StreamWork strm = StreamWork.of(ss)
						.with(new TransformRecordStream() {
							@Override
							public ReturnOption handle(RecordStruct slice) throws OperatingContextException {
								if (slice != null) {
									String text = slice.getFieldAsString("Text");
									
									Logger.info("Translating: " + text);
									
									slice.with("Text", TranslateLang.translateText(text, from, to));
								}
								
								return this.consumer.handle(slice);
							}
						})
						.with(sd);
				
				Task task = Task
						.ofSubtask("Text translation streamer " + from + " to " + to, "TextStream")
						.withWork(strm);
				
				TaskHub.submit(task, new TaskObserver() {
					@Override
					public void callback(TaskContext subtask) {
						// indicate that we are done
						callback.returnEmpty();
					}
				});
				
				return true;
			}
			
			/*
			else if ("Xml".equals(op)) {
				return MessageUtil.successAlt(TranslateLang.translateXml(original, from, to));
			}
			*/
		}
		else if ("Info".equals(request.getFeature())) {
			if ("Test".equals(request.getOp())) {
				OperationContext tc = OperationContext.getOrThrow();
				
				callback.setResult(RecordStruct.record().with("UserId", tc.getUserContext().getUserId()));

				return true;
			}
			else if ("StatusReport".equals(request.getOp())) {
				RecordStruct report = request.getDataAsRecord();

				System.out.println("Got status report from: " + report.getFieldAsString("Deployment") + " - "
						+ report.getFieldAsString("Node") + " - " + report.getFieldAsString("At"));

				callback.returnEmpty();

				return true;
			}
			else if ("HubStatus".equals(request.getOp())) {
				RecordStruct rec = new RecordStruct();

				Logger.info("Status check");
				
				rec.with("ServerIdled", ApplicationHub.isIdled());
				
				rec.with("WorkPool", WorkHub.toStatusReport());
				
				/* TODO
				ListStruct sessions = new ListStruct();
				
				for (Session sess : SessionHub.list()) 
					sessions.withItem(sess.toStatusReport());
				
				rec.withField("Sessions", sessions);
				
				rec.withField("WorkQueue", TaskQueueHub.list());
				*/
				
				callback.setResult(rec);
				callback.returnResult();
				return true;
			}			
			else if ("TaskStatus".equals(request.getOp())) {
				ListStruct results = new ListStruct();
				
				/* TODO
				ListStruct requests = (ListStruct) data;
				
				for (Struct struct : requests.items()) {
					RecordStruct req = (RecordStruct) struct;
					
					// try to get the info locally
					RecordStruct trec = TaskHub.status(
							req.getFieldAsString("TaskId"), 
							req.getFieldAsString("WorkId")
					);
					
					if ((trec != null) && trec.isEmpty())
						System.out.println("empty from pool");
					
					// else look for it in database
					if (trec == null)
						trec = TaskQueueHub.status(
								req.getFieldAsString("TaskId"), 
								req.getFieldAsString("WorkId")
						);
					
					if ((trec != null) && trec.isEmpty())
						System.out.println("empty from queue");
					
					if (trec != null)
						results.withItem(trec);
				}
						*/
				
				callback.setResult(results);
				callback.returnResult();
				return true;
			}			
			else if ("Version".equals(request.getOp())) { 
				callback.setResult(RecordStruct.record()
						.with("Version", this.version)
						.with("App", this.app)
				);
				callback.returnResult();
				return true;
			}
		}
		else if ("Management".equals(request.getFeature())) {
			if ("Idle".equals(request.getOp())) {
				RecordStruct rec = request.getDataAsRecord();
				boolean idle = rec.getFieldAsBoolean("Idle");
				
				if (idle)
					ApplicationHub.setState(HubState.Idle);
				else
					ApplicationHub.setState(HubState.Running);
				
				callback.returnResult();
				return true;
			}			
		}
		
		return false;
	}
}
