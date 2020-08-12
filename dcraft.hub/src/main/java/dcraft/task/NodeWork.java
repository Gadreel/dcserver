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
package dcraft.task;

import dcraft.filestore.CommonPath;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.FileUtil;
import dcraft.util.StringUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NodeWork implements IWork {
	static public NodeWork work() {
		return new NodeWork();
	}

	static public NodeWork of(CommonPath path) {
		NodeWork work = new NodeWork();
		work.op = path;
		return work;
	}

	// members
	protected CommonPath op = null;

	protected Process proc = null;
	protected ScheduledFuture<?> sfuture = null;
	protected boolean beforestart = true;
	
	@Override
	public void run(TaskContext trun) throws OperatingContextException {
		CompositeStruct params = Struct.objectToComposite(trun.getTask().getParams());

		Path script = OperationContext.getOrThrow().getResources().getNodes().findScript(this.op);

		if (script == null) {
			// TODO error code and set last
			trun.complete();
			return;
		}

		StringBuilder nodepath = new StringBuilder();
		nodepath.append("export NODE_PATH=$(/usr/bin/npm root --quiet -g)");

		OperationContext.getOrThrow().getResources().getNodes().buildClassPath(nodepath);

		//System.out.println(nodepath);

		List<String> oparams = new ArrayList<>();

		oparams.add("sh");
		oparams.add("-c");

		// TODO make node path configurable?
		oparams.add(nodepath + " && /usr/bin/node ./" + script);

		ProcessBuilder pb = new ProcessBuilder(oparams);
		pb.redirectErrorStream(true);
		pb.directory(Path.of(".").toFile());

		this.sfuture = ApplicationHub.getClock().schedulePeriodicInternal(new Runnable() {
			@Override
			public void run() {
				if (NodeWork.this.beforestart)
					return;
				
				Process p = NodeWork.this.proc;
				
				System.out.println(ZonedDateTime.now() + ": Keep alive check shell proc: " + trun.getTask().getTitle());

				// if started and alive just keep the context alive
				if ((p != null) && p.isAlive()) {
					System.out.println(ZonedDateTime.now() + ": Keep alive signaled shell proc: " + trun.getTask().getTitle());
					trun.touch();
					return;
				}
				
				// otherwise if started and not alive/present the clear out this schedule
				System.out.println(ZonedDateTime.now() + ": Keep alive failed shell proc: " + trun.getTask().getTitle());
				
				ScheduledFuture<?> future = NodeWork.this.sfuture;
				
				if (future != null)
					future.cancel(true);
				
				NodeWork.this.sfuture = null;
			}
		}, 55);
		
		// must keep in same thread so we obey the rules of the pool we are running in
		try {
			// TODO use "params" as JSON input - if not null

			this.proc = pb.start();
			
			this.beforestart = false;

			BufferedReader input = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			
			String line = null;
			StringBuilder respone = new StringBuilder();

			while ((line = input.readLine()) != null) {
				trun.touch();

				respone.append(line);
				respone.append('\n');
			}
			
			input.close();

			trun.setResult(Struct.objectToComposite(respone));

			try {
				proc.waitFor(58, TimeUnit.SECONDS);
			}
			catch (InterruptedException x) {
			}

			long ecode = (long) proc.exitValue();
			
			if (ecode > 0)
				Logger.infoTr(ecode, "Node exited with code", "Exit");
			else
				Logger.info("Node exited with no error", "Exit");
		}
		catch (IOException x) {
			Logger.error("Node IO Error " + x);
		}
		finally {
			this.proc = null;
			
			ScheduledFuture<?> future = NodeWork.this.sfuture;
			
			if (future != null)
				future.cancel(true);
			
			NodeWork.this.sfuture = null;
		}
		
		trun.complete();
	}
}
