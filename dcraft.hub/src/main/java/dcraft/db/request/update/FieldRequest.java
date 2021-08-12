package dcraft.db.request.update;

import java.time.ZonedDateTime;
import java.util.UUID;

import dcraft.hub.time.BigDateTime;
import dcraft.struct.RecordStruct;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;

/**
 * Base class for supporting the four database structures: Scalar and List
 * 
 * @author Andy
 *
 */
public class FieldRequest {
	protected String name = null;
	protected String subkey = null;
	protected Object value = null;
	protected String locale = null;
	protected FieldTags tags = null;
	protected boolean retired = false;
	protected boolean updateOnly = false;
	
	public String getName() {
		return this.name;
	}
	
	public FieldRequest withValue(Object v) {
		this.value = v;
		return this;
	}
	
	public FieldRequest withName(String v) {
		this.name = v;
		return this;
	}
	
	public FieldRequest withLocale(String v) {
		this.locale = v;
		return this;
	}
	
	public FieldRequest withTags(FieldTags v) {
		this.tags = v;
		return this;
	}
	
	public FieldRequest withRetired() {
		this.retired = true;
		return this;
	}
	
	public FieldRequest withUpdateOnly() {
		this.updateOnly = true;
		return this;
	}

	public FieldRequest withSubKey(String v) {
		this.subkey = v;
		return this;
	}
	
	public FieldRequest withRandomSubKey() {
		this.subkey = RndUtil.nextUUId();
		return this;
	}
	
	public FieldRequest() {		
	}
	
	public RecordStruct getParams(RecordStruct fields) {
		RecordStruct data = new RecordStruct().with("Data", this.value);
		
		if (this.tags != null)
			data.with("Tags", this.tags);
		
		if (this.retired)
			data.with("Retired", true);
		
		if (this.updateOnly)
			data.with("UpdateOnly", true);
		
		if (StringUtil.isNotEmpty(this.locale))
			data.with("Lang", this.locale);
		
		// static scalar stops here
		if (this.subkey == null)
			return data;

		RecordStruct ret = fields.getFieldAsRecord(this.name);
		
		if (ret == null)
			ret = new RecordStruct();

		ret.with(this.subkey, data);

		return ret;
	}
}
