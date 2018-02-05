package dcraft.service;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.SchemaHub;
import dcraft.schema.SchemaResource.OpInfo;
import dcraft.service.work.ServiceChainWork;
import dcraft.stream.IServiceStreamDest;
import dcraft.stream.IStream;
import dcraft.stream.StreamFragment;
import dcraft.stream.record.VerifyRecordStream;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.IWorkBuilder;

import java.security.InvalidParameterException;

/*
 * This class may be used to create requests for either Local Hub calls or Remote (API) Hub calls. Except for "validate"
 * the code should be neutral to which destination will be used. "validate" is for local calls
 */
public class ServiceRequest implements IWorkBuilder {
	// be in the calling context when you use this method, otherwise may miss the proper OpInfo def
	static public ServiceRequest of(String name, String feature, String op) {
		ServiceRequest req = new ServiceRequest();
		req.name = name;
		req.feature = feature;
		req.op = op;
		return req;
	}
	
	protected String name = null;
	protected String feature = null;
	protected String op = null;
	protected Struct data = null;
	protected OpInfo def = null;
	protected OperationOutcomeStruct outcome = null;
	
	// on the server, when accepting an RPC call, set this flag so specialized services know to check the user badges closer (if this is not set then we don't check access during service calls)
	protected boolean fromRpc = false;
	
	protected StreamFragment streamSource = null;
	protected StreamFragment streamDest = null;
	
	protected ServiceRequest() {
	}
	
	public StreamFragment getResponseStream() {
		return this.streamDest;
	}
	
	public void setResponseStream(StreamFragment v) {
		this.streamDest = v;
	}
	
	public ServiceRequest withResponseStream(StreamFragment v) {
		this.setResponseStream(v);
		return this;
	}
	
	public ServiceRequest withResponseStream(IStream v) throws OperatingContextException {
		if (v instanceof IServiceStreamDest<?>) {
			if (this.outcome != null)
				throw new InvalidParameterException("Cannot set service stream dest when Outcome is already set.");
			
			this.outcome = new OperationOutcomeStruct() {
				@Override
				public void callback(Struct result) throws OperatingContextException {
					((IServiceStreamDest<?>) v).end(this);
				}
			};
		}
		
		this.setResponseStream(StreamFragment.of(v));
		return this;
	}
	
	public StreamFragment getRequestStream() {
		return this.streamSource;
	}
	
	public void setRequestStream(StreamFragment v) {
		this.streamSource = v;
	}
	
	public ServiceRequest withRequestStream(StreamFragment v) {
		this.setRequestStream(v);
		return  this;
	}
	
	public ServiceRequest withRequestStream(IStream v) {
		this.setRequestStream(StreamFragment.of(v));
		return  this;
	}
	
	public String getName() {
		return this.name;
	}

	public ServiceRequest withName(String v) {
		this.name = v;
		return this;
	}

	public String getFeature() {
		return this.feature;
	}

	public ServiceRequest withFeature(String v) {
		this.feature = v;
		return this;
	}

	public String getOp() {
		return this.op;
	}

	public ServiceRequest withOp(String v) {
		this.op = v;
		return this;
	}

	// on available after call to validate
	public OpInfo getDefinition() {
		return this.def;
	}
	
	public ServiceRequest withFromRpc() {
		this.fromRpc = true;
		return this;
	}
	
	public ServiceRequest withFromInternal() {
		this.fromRpc = false;
		return this;
	}
	
	public boolean isFromRpc() {
		return this.fromRpc;
	}
	
	public ServiceRequest withData(Struct v) {
		this.data = v;
		return this;
	}
	
	public ServiceRequest wrapData(Object v) {
		this.data = Struct.objectToStruct(v);
		return this;
	}
	
	public Struct getData() {
		return this.data;
	}
	
	public RecordStruct getDataAsRecord() {
		return Struct.objectToRecord(this.data);
	}
	
	public ListStruct getDataAsList() {
		return Struct.objectToList(this.data);
	}
	
	public ServiceRequest withOutcome(OperationOutcomeStruct v) {
		this.outcome = v;
		return this;
	}
	
	public OperationOutcomeStruct getOutcome() {
		return this.outcome;
	}
	
	public OperationOutcomeStruct requireOutcome() throws OperatingContextException {
		if (this.outcome == null) {
			this.outcome = new OperationOutcomeStruct() {
				@Override
				public void callback(Struct result) throws OperatingContextException {
					// nothing to do, this is for a send and forget message - or it should be anyway
				}
			};
		}
		
		return this.outcome;
	}
	
	@Override
	public IWork toWork() {
		return ServiceChainWork.of(this);
	}
	
	// call this only if the request is destined for local hub, not for API requests
	public boolean validate() throws OperatingContextException {
		if (Logger.isDebug())
			Logger.debug("Message being validated for : " + this.name);
		
		OperationContext tc = OperationContext.getOrNull();
		
		if (tc == null) {
			Logger.error("Missing context when calling service hub");
			return false;
		}
		
		this.def = SchemaHub.getServiceOpInfo(name, feature, op);
		
		if (this.def == null) {
			Logger.error(1, "Service schema not on this hub: " + name);
			return false;
		}
		
		// for RPC calls, verify that the user is allowed to call
		if (this.fromRpc && ! tc.getUserContext().isTagged(this.def.getSecurityTags())) {
			Logger.errorTr(434);
			return false;
		}
		
		DataType rdt = this.def.getOp().getRequest();
		
		// if there is data there better be a type to validate against
		if ((rdt == null) && (this.data != null)) {
			Logger.errorTr(433);
			return false;
		}
		
		if (rdt != null) {
			try (OperationMarker om = OperationMarker.create()) {
				Struct ndata = rdt.normalizeValidate(this.data);
				
				// TODO find other calls to normalizeValidate and change to use OM
				if (om.hasErrors()) {
					Logger.error("Unable to validate and normalize request message.");
					return false;
				}
				
				this.data = ndata;		// use the normalized data
			}
			catch (Exception x) {
				Logger.error("Unable to validate and normalize request message: " + x);
				return false;
			}
		}
		
		DataType stype = this.def.getOp().getResponse();
		
		if (stype != null)
			this.requireOutcome().setResultType(stype);
		
		if (this.streamSource != null) {
			DataType rtype = this.def.getOp().getRequestStream();
			
			if (rtype != null)
				this.streamSource.withAppend(VerifyRecordStream.forType(rtype));
		}
		
		if (this.streamDest != null) {
			DataType rtype = this.def.getOp().getResponseStream();
			
			if (rtype != null)
				this.streamDest.withPrepend(VerifyRecordStream.forType(rtype));
		}
		
		return true;
	}
}
