/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2012 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.db.request.update;

import dcraft.db.request.update.DbRecordRequest;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.schema.TableView;
import dcraft.struct.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Update a record in dcDatabase, see dcUpdateRecord schema.
 * Limit to 1MB of values (total size)
 * 
 * @author Andy
 *
 * TODO review 
 */
public class ImportRecordRequest extends DbRecordRequest {
	
	/**
	 * import data must have an Id 
	 * 
	 * @param table name
	 * @param record to import see Export for format
	 */
	public ImportRecordRequest(String table, RecordStruct record) throws OperatingContextException {
		super("dcUpdateRecord");
		
		this.withTable(table);
		
		this.withId(record.getFieldAsRecord("Id").getFieldAsString("Data"));
		
		TableView view = ResourceHub.getResources().getSchema().getTableView(table);
		
		List<String> names = new ArrayList<>();
		
		for (FieldStruct fld : record.getFields()) {
			names.add(fld.getName());
		}
		
		for (String name : names) {
			if (view.getField(name) == null) {
				record.removeField(name);
				System.out.println("remove field: " + name + " for " + this.id);
			}
		}
		
		for (dcraft.schema.DbField sfld : view.getFields()) {
			String name = sfld.getName();
			
			if (record.isNotFieldEmpty(name)) {
				if (sfld.isList()) {
					ListStruct items = record.getFieldAsList(name);
					
					for (BaseStruct itm : items.items()) {
						if (itm != null) {
							RecordStruct itmdata = (RecordStruct) itm;
							
							if (itmdata.getFieldAsBooleanOrFalse("Retired"))
								this.withRetireField(name);
							else
								this.withUpdateField(name, itmdata.selectAsString("SubId"), itmdata.getField("Data"));
						}
					}
				}
				else {
					RecordStruct flddata = record.getFieldAsRecord(name);
					
					if (flddata.getFieldAsBooleanOrFalse("Retired"))
						this.withRetireField(name);
					else
						this.withUpdateField(name, flddata.getField("Data"));
				}
			}
		}
	}
	
	/*

	// TODO tags
	public static DbField buildImport(String name, RecordStruct data) {
		DynamicListField fld = new DynamicListField(name, data.getFieldAsString("Sid"), data.getFieldAsAny("Data"));
		
		if (data.hasField("From"))
			fld.from = data.getFieldAsBigDateTime("From");
		
		if (data.hasField("To"))
			fld.to = data.getFieldAsBigDateTime("To");
		
		return fld;
	}
	 
	 

	// TODO tags
	public static DbField buildImport(String name, RecordStruct data) {
		DynamicScalarField fld = new DynamicScalarField(name, data.getFieldAsString("Sid"), data.getFieldAsAny("Data"));
		
		if (data.hasField("From"))
			fld.from = data.getFieldAsBigDateTime("From");
		
		return fld;
	}

	// TODO tags
	public static DbField buildImport(String name, RecordStruct data) {
		ScalarField fld = new ScalarField(name, data.getFieldAsAny("Data"));
		
		return fld;
	}
	 
	 */
}
