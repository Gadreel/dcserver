package dcraft.db.util;

import java.util.ArrayList;
import java.util.List;

import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;

public class ExportTable {	
	protected String table = null;
	protected boolean clean = true;

	public String getTable() {
		return this.table;
	}
	
	public ExportTable(String table) {
		this.table = table;
	}
	
	public RecordStruct transform(RecordStruct source) {
		if (this.clean) {
			List<String> toremove = new ArrayList<String>();
			
			for (FieldStruct fld : source.getFields())
				if (fld.isEmpty())
					toremove.add(fld.getName());
			
			for (String fname : toremove)
				source.removeField(fname);
		}
		
		return source;
	}
	
	public void done() {
	}
}
