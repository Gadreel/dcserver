package dcraft.hub.op;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import dcraft.log.DebugLevel;
import dcraft.log.HubLog;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;

/*
 * Controller needs to be able to be shared by multiple contexts, even those with
 * different tenants/sites - tasks launched to do work external to our context.
 * 
 * It is especially important that their work activity keeps the master task
 * alive by sharing the controller (though a task observer also works).
 */
public class OperationController extends RecordStruct implements IVariableProvider {
	// ======================================================
	// these fields are local only
	// ======================================================
    
    protected long firstactivity = System.currentTimeMillis();
    protected long lastactivity = System.currentTimeMillis();
    protected long lastdeepactivity = 0;
	
    //protected String id = null;			// uniquely identify accross all nodes - nodeid_runid_seqnum
	//protected String seqnum = null;		// last part of the id, the seqnum
	protected DebugLevel level = HubLog.getGlobalLevel();
    
	protected List<IOperationObserver> observers = new CopyOnWriteArrayList<>();
    protected IOperationLogger logger = null;
	
	//protected List<RecordStruct> messages = new ArrayList<>();

	//protected Map<String, Struct> variables = new HashMap<>();

	protected OperationController() {
	}

	public OperationController(String opid) {
		this
				.with("Id", opid)
				.with("SequenceNumber", opid.substring(26))
				.with("Variables", RecordStruct.record())
				.with("Messages", ListStruct.list());
	}
	
	public void addObserver(IOperationObserver oo) {
		// the idea is that we want to unwind the callbacks in LIFO order
		this.observers.add(0, oo);
			
		if ((oo instanceof IOperationLogger) && (this.logger == null))
			this.logger = (IOperationLogger) oo;
	}
	
	public int countObservers() {
		return this.observers.size();
	}
	
	public void removeObserver(IOperationObserver o) {
		this.observers.remove(o);
	}	

    public void fireEvent(OperationContext target, OperationEvent event, Object detail) {
		OperationContext curr = OperationContext.getOrNull();
		
		// do not use locks here or in addObserver - too expensive - officially 
		// we only support listeners to COMPLETE, START, PREP, START that
		// are added before we start - so we may never want to add any more support
		// here, however, if we do, do not include a lock here...that is too expensive
		// relative to benefits
		
		try {
			List<IOperationObserver> removelist = null;
			
			for (IOperationObserver ob : this.observers) {
		    	try {
		    		 ObserverState st = ob.fireEvent(target, event, detail);
		    		 
		    		 if (st == ObserverState.Done) {
		    			 if (removelist == null)
		    				 removelist = new ArrayList<>();		// create an object only if it is needed
		    			 
		    			 removelist.add(ob);
		    		 }
		    	}
		    	catch (Exception x) {
		    		// cannot log, that could trigger a recursive issue
		    		HubLog.log(this.getOpId(), DebugLevel.Error, 1, "Unhealthy Observer " + ob.getClass().getName() + " - " + x);
		    	}
			}
			
			if (removelist != null) {
				for (IOperationObserver ob : removelist)
					this.observers.remove(ob);
			}
		}
		finally {
	    	OperationContext.set(curr);
		}
    }
    
    public long getFirstActivity() {
		return this.firstactivity;
	}
    
    public long getLastActivity() {
		return this.lastactivity;
	}
    
	public String getOpId() {
		return this.getFieldAsString("Id");
	}
	
	public String getSeqNumber() {
		return this.getFieldAsString("SequenceNumber");
	}

	@Override
	public RecordStruct variables() {
		return this.getFieldAsRecord("Variables");
	}

	@Override
	public void addVariable(String name, BaseStruct var) throws OperatingContextException {
		this.getFieldAsRecord("Variables").with(name, var);

		if (var instanceof AutoCloseable) {
			OperationContext run = OperationContext.getOrThrow();

			if (run != null) {
				run.getController().addObserver(new OperationObserver() {
					@Override
					public void completed(OperationContext ctx) {
						try {
							((AutoCloseable) var).close();
						}
						catch (Exception x) {
							Logger.warn("Script could not close and autoclosable var: " + x);
						}
					}
				});
			}
		}
	}

	@Override
	public void clearVariables() {
		this.getFieldAsRecord("Variables").clear();
	}

	@Override
	public BaseStruct queryVariable(String name) {
		if (StringUtil.isEmpty(name))
			return null;

		return this.getFieldAsRecord("Variables").getField(name);
	}

	public ListStruct getMessages() {
		return this.getFieldAsList("Messages");
	}

	public ListStruct getMessages(int msgStart, int msgEnd) {
		ListStruct messages = this.getFieldAsList("Messages");

		if (msgEnd == -1)
			msgEnd = messages.size();
			
		if (msgStart < 0)
			msgStart = 0;
		
		if (msgEnd < 0)
			msgEnd = 0;
		
		if (msgEnd > messages.size())
			msgEnd = messages.size();
		
		if (msgStart > msgEnd)
			msgStart = msgEnd;

		ListStruct ret = ListStruct.list();

		for (int i = msgStart; i < msgEnd; i++)
			ret.with(messages.getItem(i));

		return ret;

		//return ListStruct.list(this.messages.subList(msgStart, msgEnd).toArray());
	}

	/**
	 * @param code to search for
	 * @return true if an error code is present
	 */
	public boolean hasCode(long code) {
		return this.hasCode(code, 0, -1);
	}

	public boolean hasCode(long code, int msgStart, int msgEnd) {
		ListStruct messages = this.getFieldAsList("Messages");

		if (msgEnd == -1)
			msgEnd = messages.size();
			
		if (msgStart < 0)
			msgStart = 0;
		
		if (msgEnd < 0)
			msgEnd = 0;
		
		if (msgEnd > messages.size())
			msgEnd = messages.size();
		
		if (msgStart > msgEnd)
			msgStart = msgEnd;
		
		for (int i = msgStart; i < msgEnd; i++) {
			RecordStruct msg =  messages.getItemAsRecord(i);
		
			if (msg.getFieldAsInteger("Code") == code)
				return true;
		}
		
		return false;
	}

	public boolean hasBoundary(String tag, int msgStart, int msgEnd) {
		ListStruct messages = this.getFieldAsList("Messages");

		if (msgEnd == -1)
			msgEnd = messages.size();

		if (msgStart < 0)
			msgStart = 0;

		if (msgEnd < 0)
			msgEnd = 0;

		if (msgEnd > messages.size())
			msgEnd = messages.size();

		if (msgStart > msgEnd)
			msgStart = msgEnd;

		StringStruct stag = StringStruct.of(tag);

		for (int i = msgStart; i < msgEnd; i++) {
			RecordStruct msg =  messages.getItemAsRecord(i);

			if (msg.isNotFieldEmpty("Tags")) {
				ListStruct mtags = msg.getFieldAsList("Tags");

				if (mtags.contains(stag))
					return true;
			}
		}

		return false;
	}

	public boolean hasCodeRange(long from, long to) {
		return this.hasCodeRange(from, to, 0, -1);
	}

	public boolean hasCodeRange(long from, long to, int msgStart, int msgEnd) {
		ListStruct messages = this.getFieldAsList("Messages");

		if (msgEnd == -1)
			msgEnd = messages.size();
			
		if (msgStart < 0)
			msgStart = 0;
		
		if (msgEnd < 0)
			msgEnd = 0;
		
		if (msgEnd > messages.size())
			msgEnd = messages.size();
		
		if (msgStart > msgEnd)
			msgStart = msgEnd;
		
		for (int i = msgStart; i < msgEnd; i++) {
			RecordStruct msg = messages.getItemAsRecord(i);
	
			long code = msg.getFieldAsInteger("Code", 0);
			
			if ((code >= from) && (code <= to))
				return true;
		}
		
		return false;
	}

	// search backward through log to find an error, if we hit a message with an Exit tag then
	// stop, as Exit resets Error (unless it is an error itself)
	// similar to findExitEntry but stops after last Error as we don't need to loop through all
	public boolean hasLevel(int msgStart, int msgEnd, DebugLevel lvl) {
		ListStruct messages = this.getFieldAsList("Messages");

		if (msgEnd == -1)
			msgEnd = messages.size();
			
		if (msgStart < 0)
			msgStart = 0;
		
		if (msgEnd < 0)
			msgEnd = 0;
		
		if (msgEnd > messages.size())
			msgEnd = messages.size();
		
		if (msgStart > msgEnd)
			msgStart = msgEnd;

		String slvl = lvl.toString();
		
		for (int i = msgStart; i < msgEnd; i++) {
			RecordStruct msg = messages.getItemAsRecord(i);
			
			if (slvl.equals(msg.getFieldAsString("Level")))
				return true;
		}
		
		return false;
	}
	
	// switch errors to warnings for certain range
	public void downgradeErrors(int msgStart, int msgEnd) {
		ListStruct messages = this.getFieldAsList("Messages");

		if (msgEnd == -1)
			msgEnd = messages.size();
			
		if (msgStart < 0)
			msgStart = 0;
		
		if (msgEnd < 0)
			msgEnd = 0;
		
		if (msgEnd > messages.size())
			msgEnd = messages.size();
		
		if (msgStart > msgEnd)
			msgStart = msgEnd;

		String slvl = DebugLevel.Error.toString();
		
		for (int i = msgStart; i < msgEnd; i++) {
			RecordStruct msg = messages.getItemAsRecord(i);
			
			if (slvl.equals(msg.getFieldAsString("Level")))
				msg.with("Level", DebugLevel.Warn.toString());
		}
	}
	
	// logging is hard on heap and GC - so only do it if necessary
	// not generally called by code, internal use mostly
    // call this to bypass the Hub logger - for example a bus callback 
	public void log(OperationContext ctx, RecordStruct entry) {
		this.log(ctx, entry, DebugLevel.parse(entry.getFieldAsString("Level")));
	}
	
	public void log(OperationContext ctx, RecordStruct entry, DebugLevel lvl) {
		if (this.getLevel().getCode() < lvl.getCode())
			return;
		
		// this isn't thread safe, and much of the time it won't be much of an issue
		// but could consider Stamp Lock approach to accessing messages array
		this.getFieldAsList("Messages").with(entry);
		
		this.fireEvent(ctx, OperationConstants.LOG, entry);
	}

	public IOperationLogger getLogger() {
		return this.logger;
	}
	
	public String getLog() {
		try {
			IOperationLogger logger = this.logger;
	
			if (logger != null)
				return logger.logToString();	
		}
		catch (Exception x) {
			HubLog.log(this.getOpId(), DebugLevel.Error, 1, "Error with logger: " + x);
		}
		
		return "";
	}
    
    public void touch(OperationContext ctx) {
    	this.lastactivity = System.currentTimeMillis();
    	
    	// at least once every 5 sec
    	if (this.lastactivity - this.lastdeepactivity > 5000)
    		this.deepTouch(ctx);
    }

    // touch parent context too 
    public void deepTouch(OperationContext ctx) {
    	this.lastdeepactivity = System.currentTimeMillis();
    	
		this.fireEvent(ctx, OperationConstants.TOUCH, null);
    }

	@Override
	public OperationController deepCopy() {
		OperationController cp = new OperationController();
		this.doCopy(cp);
		// TODO copy all members?
		return cp;
	}

	public int logMarker() {
    	return this.getMessages().size();
    }
	
	/**
	 * @return logging level to use with this task
	 */
	public DebugLevel getLevel() {
		return this.level;
	}
	
	public void setLevel(DebugLevel v) {
		this.level = v;
	}
}
