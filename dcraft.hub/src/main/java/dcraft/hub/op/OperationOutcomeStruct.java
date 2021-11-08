package dcraft.hub.op;

import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.service.ServiceRequest;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.cb.TimeoutPlan;

abstract public class OperationOutcomeStruct extends OperationOutcome<BaseStruct> {
	protected DataType valuetype = null;
	protected boolean checkfinal = true;
	
	public OperationOutcomeStruct() throws OperatingContextException {
		super();
	}

	public OperationOutcomeStruct(TimeoutPlan plan) throws OperatingContextException {
		super(plan);
	}

	public DataType getResultType() {
		return this.valuetype;
	}
	
	public void setResultType(DataType v) {
		this.valuetype = v;
	}
	
	public OperationOutcomeStruct withAsIncomplete() {
		this.checkfinal = false;
		return this;
	}
	
	@Override
	public void setResult(BaseStruct v) {
		if (this.valuetype != null) {
			try (OperationMarker om = OperationMarker.create()) {
				BaseStruct nv = this.valuetype.normalizeValidate(this.checkfinal, ! this.checkfinal, v);
				
				if (om.hasErrors()){
					Logger.error("Unable to validate and normalize operation outcome.");
					this.value = null;
				}
				else {
					this.value = nv;
				}
			}
			catch (Exception x) {
				Logger.error("Error validating stream record: " + x);
			}
		}
		else {
			this.value = v;
		}
	}
	
	/**
	 * @return the message body as String
	 */
	public String getAsString() {
		return Struct.objectToString(this.getResult());
	}
	
	/**
	 * @return the message body as Integer
	 */
	public Long getAsInteger() {
		return Struct.objectToInteger(this.getResult());
	}

	/**
	 * @return the message body as RecordStruct
	 */
	public RecordStruct getAsRecord() {
		return Struct.objectToRecord(this.getResult());
	}

	/**
	 * @return the message body as ListStruct
	 */
	public ListStruct getAsList() {
		return Struct.objectToList(this.getResult());
	}	
}
