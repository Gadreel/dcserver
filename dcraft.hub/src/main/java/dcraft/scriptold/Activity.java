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
package dcraft.scriptold;

import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

import dcraft.hub.op.IOperationObserver;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationObserver;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.DateTimeStruct;
import dcraft.struct.scalar.NullStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.task.run.WorkHub;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

// TODO global variables need to be pushed by Collab to all activities, those vars get stored in Activity
// level variables (so be sure to use a good naming convention for globals - all start with "gbl"

// note that Activity, not Main is the root function block, little different but means exit codes are with Activity
public class Activity implements IWork, IInstructionCallback {
    protected TaskContext opcontext = null;
    
    protected boolean debugmode = false;
    protected boolean inDebugger = false;
    protected boolean exitFlag = false;
    protected IDebugger debugger = null;
    
    // error handler
    protected ErrorMode errorMode = ErrorMode.Resume;
    protected long errorCode = 0;
    protected String errorMessage = null;
    
	protected Script script = null;
	protected StackFunctionEntry stack = null;
	protected Instruction inst = null;
	protected long starttime = 0;
	protected long runtime = 0;
	protected AtomicLong runCount = new AtomicLong();		// useful flag to let us know that another instruction has completed
	protected AtomicLong varnames = new AtomicLong();
	
	protected Map<String, Struct> globals = new HashMap<String, Struct>();
	
	protected IOperationObserver taskObserver = null;		// TODO rework so it is not circular ref
	protected boolean hasErrored = false;

    public void setContext(TaskContext v) {
    	this.opcontext = v;
    }

    public ExecuteState getState() {
        return (this.stack != null) ? this.stack.getState() : ExecuteState.Ready;
    }
    
    public void setState(ExecuteState v) {
    	if (this.stack != null) 
    		this.stack.setState(v);
    }
    
    public boolean hasErrored() {
		return this.hasErrored;
	}
    
    public void clearErrored() {
		this.hasErrored = false;
	}
    
    public Long getExitCode() {
        return (this.stack != null) ? this.stack.getLastCode() : 0;
    }
    
    public Struct getExitResult() {
        return (this.stack != null) ? this.stack.getLastResult() : null;
    }

    public void setExitFlag(boolean v) {
		this.exitFlag = v;
	}
    
    public boolean isExitFlag() {
		return this.exitFlag;
	}
    
    public void setDebugMode(boolean v) {
		this.debugmode = v;
	}
    
    /*
     * has the code signaled that it wants to debug?
     */
    public boolean isDebugMode() {
		return this.debugmode;
	}
    
    public void setInDebugger(boolean v) {
		this.inDebugger = v;
	}
    
    public void setDebugger(IDebugger v) {
		this.debugger = v;
	}
    
    public IDebugger getDebugger() {
		return this.debugger;
	}
    
    public void setErrorMode(ErrorMode errorMode, long errorCode, String errorMessage) {
		this.errorMode = errorMode;
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
	}
    
    /*
     * is the code already managed by a debugger
     */
    public boolean isInDebugger() {
		return this.inDebugger;
	}
    
	public String getTitle() {
		if (this.script == null)
			return null;
		
		return this.script.getTitle(); 
	}
	
	public long getRuntime() {
		return this.runtime;
	}

	public long getRunCount() {
		return this.runCount.get();
	}
    
	@Override
	public void run(TaskContext scriptrun) throws OperatingContextException {
		if (this.opcontext == null) {
			this.opcontext = scriptrun;
			
			this.taskObserver = new OperationObserver() {
				// lock is too expensive, this is a flag just too keep us from calling ourself again, not for thread safety
				// worse case is we get a few more log messages than we wanted, should be very rare (task killed same time scriptold makes error)
				protected boolean inHandler = false;
				
				@Override
				public void completed(OperationContext ctx) {
					if (scriptrun.hasExitErrors()) {
						Logger.error("scriptold task run canceled");
						
						// TODO review - I don't know that cancel should be any different from complete for stack, we just mean cleanup your resources
						if (Activity.this.stack != null)
							Activity.this.stack.cancel();
						
						System.out.println("activity canceled");
					}
					
					Activity.this.runtime = (System.currentTimeMillis() - Activity.this.starttime);
				}
				
				@Override
				public void log(OperationContext ctx, RecordStruct entry) {
					if ("Error".equals(entry.getFieldAsString("Level"))) {
						if (this.inHandler)
							return;
						
						this.inHandler = true;
						
						try {
							Activity.this.hasErrored = true;
							
							long lcode = entry.getFieldAsInteger("Code", 1);
							
							if (lcode > 0) {
								StackEntry se = Activity.this.stack.getExecutingStack();
								
								if (se != null)
									se.setLastCode(lcode);
							}
							
							if (StringUtil.isNotEmpty(Activity.this.errorMessage) && (Activity.this.errorCode > 0))  
								((TaskContext) ctx).setExitCode(Activity.this.errorCode, Activity.this.errorMessage);
							else if (StringUtil.isNotEmpty(Activity.this.errorMessage)) 
								((TaskContext) ctx).setExitCode(1, Activity.this.errorMessage);
							else if (Activity.this.errorCode > 0) 
								((TaskContext) ctx).setExitCodeTr(Activity.this.errorCode);
							
							if (Activity.this.errorMode == ErrorMode.Debug) 
								Activity.this.engageDebugger();							
							else if (Activity.this.errorMode == ErrorMode.Exit) 
								Activity.this.setExitFlag(true);
							
							// else if resume do nothing
						}
						finally {						
							this.inHandler = false;
						}
					}					
				}
			};
			
			this.opcontext.getController().addObserver(this.taskObserver);
		}
		
    	if (this.inst == null) { 
			this.exitFlag = true;
    	}
    	else if (this.stack == null) {
			this.starttime = System.currentTimeMillis();
       		this.stack = (StackFunctionEntry)this.inst.createStack(this, null);
       		this.stack.setParameter(scriptrun.getTask().getParams());
    	}
		
		if (this.exitFlag) {
			scriptrun.complete();
			return;
		}
		
       	this.stack.run(this);
	}
	
	@Override
	public void resume() {
		if (this.opcontext == null) {
			Logger.error("Resume Activity with no run!!!");
			return;
		}
		
		if (this.exitFlag) 
			this.opcontext.complete();
		else if (this.debugmode) {
			IDebugger d = this.debugger;
			
			if (d != null)
				d.stepped();
		}
		else {
			WorkHub.submit(this.opcontext);
		}
		
		this.runCount.incrementAndGet();
	}

	public void engageDebugger() {		
		Logger.debug("Debugger requested");
		
		this.debugmode = true;
		
		if (this.inDebugger) 
			return;

		// need a task run to do debugging
		if (this.opcontext != null) {
        	/* cleaning up ---
			IDebuggerHandler debugger = ScriptHub.getDebugger();
			
			if (debugger == null) {
				Logger.error("Unable to debug scriptold, no debugger registered.");
				this.opcontext.complete();
			}
			else {
				// so debugging don't timeout
				this.opcontext.getTask().withTimeout(0).withDeadline(0);
			
				debugger.startDebugger(this.opcontext);
			}
			*/
		}
	}

    public Struct createStruct(String type) {
        	/* cleaning up ---
    	if (this.opcontext != null)
    		return ScriptHub.createVariable(type);
    		*/
    	
    	return NullStruct.instance;
    }

    public RecordStruct getDebugInfo() {
    	RecordStruct info = new RecordStruct();		// TODO type this
    	
        /* TODO
    	if (this.opcontext != null)
    		info.with("Log", this.opcontext.getMessages());
    		*/
    	
    	ListStruct list = new ListStruct();

    	// global level
    	RecordStruct dumpRec = new RecordStruct();
    	list.withItem(dumpRec);
    	
    	dumpRec.with("Line", 1);
    	dumpRec.with("Column", 1);
    	dumpRec.with("Command", ">Global<");

    	RecordStruct dumpVariables = new RecordStruct();
    	dumpRec.with("Variables", dumpVariables);
        
        for (Entry<String, Struct> var : this.globals.entrySet()) 
            dumpVariables.with(var.getKey(), var.getValue());
        
        dumpVariables.with("_Errored", BooleanStruct.of(this.hasErrored));
        
        /* TODO
    	if (this.opcontext != null)
    		dumpVariables.with("_ExitCode", new IntegerStruct(this.opcontext.getCode()));
    		*/

        // add the rest of the stack
    	if (this.stack != null)
    		this.stack.debugStack(list);
    	
        info.with("Stack", list);
        
        return info;
    }

    public boolean compile(String source) {
		boolean checkmatches = true;
		Set<String> includeonce = new HashSet<>();

		while (checkmatches) {
			checkmatches = false;
	    	
			Matcher m = Script.includepattern.matcher(source);
			
			while (m.find()) {
				String grp = m.group();
				String path = grp.trim();

				path = path.substring(10, path.length() - 3);

				CharSequence lib = "\n";
				
				if (! includeonce.contains(path)) {
					System.out.println("Including: " + path);
					
					// set lib from file content
					lib = IOUtil.readEntireFile(Paths.get("." + path));
					
					if (StringUtil.isEmpty(lib))
						lib = "\n";
					else
						lib = "\n" + lib;
					
					includeonce.add(path);
				}
				
				source = source.replace(grp, lib);
				checkmatches = true;
			}
		}
    	
		XElement xres = XmlReader.parse(source, true, true);
		
		if (xres == null) {
			Logger.error("Unable to parse scriptold");
			return false;
		}
    	
        this.script = new Script();        
        
        if (! this.script.compile(xres, source)) {
			Logger.error("Unable to compile scriptold");
	        return false;
        }
        
       	this.inst = this.script.getMain();        
        return true;
    }

    public boolean compile(XElement source) {
        this.script = new Script();        
        
        if (! this.script.compile(source, source.toString(true))) {
			Logger.error("Unable to compile scriptold");
	        return false;
        }
        
       	this.inst = this.script.getMain();        
        return true;
    }
    
    public Script getScript() {
		return this.script;
	}

    // global variables
    public Struct queryVariable(String name) {
		if (StringUtil.isEmpty(name))
			return null;
        
        if ("_Errored".equals(name)) 
        	return BooleanStruct.of(this.hasErrored);
        
        /* TODO
        if ("_ExitCode".equals(name)) 
        	return new IntegerStruct(this.opcontext.getCode());
        
        if ("_Log".equals(name)) 
        	return this.opcontext.getMessages();
        	*/

        if ("_Now".equals(name))
        	return DateTimeStruct.of(ZonedDateTime.now(ZoneId.of("UTC")));

        // do not call super - that would expose vars outside of the function
        int dotpos = name.indexOf(".");

        if (dotpos > -1) {
            String oname = name.substring(0, dotpos);

            Struct ov = this.globals.containsKey(oname) ? this.globals.get(oname) : null;

            if (ov == null) {
            	Logger.errorTr(507, oname);
            	return null;
            }
            
            if (!(ov instanceof CompositeStruct)){
            	Logger.errorTr(508, oname);
            	return null;
            }
            
            Struct sres = ((CompositeStruct)ov).select(name.substring(dotpos + 1)); 
            
            return sres;
        }
        else if (this.globals.containsKey(name)) {
            return this.globals.get(name);
        }
        
        return null;
    }

	public Instruction queryFunction(String name) {
		return this.script.getFunction(name);
	}

	public void addVariable(String name, Struct var) {
    	this.globals.put(name, var);
	}

	public String tempVarName() {
		return this.varnames.incrementAndGet() + "";
	}
}
