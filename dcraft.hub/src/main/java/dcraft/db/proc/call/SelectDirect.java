package dcraft.db.proc.call;

import java.util.HashMap;
import java.util.function.Function;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.FilterResult;
import dcraft.db.proc.ICollector;
import dcraft.db.proc.IFilter;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.schema.DbCollector;
import dcraft.schema.SchemaResource;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class SelectDirect extends LoadRecord {
	/*
	 ;  Table			name
	 ;	When			"" = now
	 ;					[stamp] = on or before this time
	 ;
	 ;  Where
	 ;			"Name"				expression name: "And", "Or", "Equal", etc.  -or-  field name  -or-  "Filter"
	 ;			"A"					value to compare against, or param to Filter
	 ;				"Field"			if from database
	 ;				"Format"
	 ;				"Value"			if literal
	 ;				"Composer"		composer scriptold to generate content
	 ;			"B"					value to compare against, or param to Filter
	 ;			"C"					value to compare against, or param to Filter
	 ;			"Children"
	 ;					0,"Name"
	 ;					1...
	 ;
	 ;  Select	list of fields to query
	 ;			0,
	 ;				"Field"			name
	 ;				"Format"		format of field if scalar
	 ;				"Name"			display name of field
	 ;				"ForeignField"	value field in fk relationship
	 ;				"Table"			for reverse foreign
	 ;				"Composer"		composer scriptold to generate content
	 ;				"Select"		list of fields to query, see above
	 ;						0,
	 ;						1...
	 ;			1...
	 ;
	 ;  Collector
	 ;			"Name"				code to execute to get collection
	 ;			"Values"				
	 ;					0 			value to match
	 ;					1...
	 ;			"From"				value to start at, inclusive
	 ;			"To"				value to end at, exclusive
	 ;			"Field"				if not using Code to get collection, use a Field instead
	 ;
	 ;	Historical	true / false   - ignore the To field in Record and in Field - meaning we can see back in time, but not in future, From is still obeyed
	 ;
	 ; Result
	 ;		List of records, content based on Select
	 */
	@Override
	public void execute(DbServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		
		String table = params.getFieldAsString("Table");
		BigDateTime when = params.getFieldAsBigDateTime("When");
		boolean compact = params.hasField("Compact") ? params.getFieldAsBooleanOrFalse("Compact") : true;
		boolean historical = params.getFieldAsBooleanOrFalse("Historical");	
		ListStruct select = params.getFieldAsList("Select");
		RecordStruct where = params.getFieldAsRecord("Where");
		
		if (when == null)
			when = BigDateTime.nowDateTime();
		
		BigDateTime fwhen = when;
		
		// TODO add db filter option
		//d runFilter("Query") quit:Errors  ; if any violations in filter then do not proceed
		
		TablesAdapter db = TablesAdapter.of(request);
		ICompositeBuilder out = new ObjectBuilder();
		
		IFilter filter = Unique.unique()
				.withNested(new BasicFilter() {
					@Override
					public FilterResult check(TablesAdapter adapter, Object id, BigDateTime when, boolean historical) throws OperatingContextException {
						if (adapter.checkSelect(table, id.toString(), when, where, historical)) {
							try {
								SelectDirect.this.writeRecord(request, out, db, table, id.toString(), fwhen, select, compact, false, historical);
							}
							catch (BuilderStateException x) {
								return FilterResult.halt();
							}
							
							return FilterResult.accepted();
						}
						
						return FilterResult.rejected();
					}
				});
		
		try (OperationMarker om = OperationMarker.create()) {
			out.startList();
			
			RecordStruct collector = params.getFieldAsRecord("Collector");
			
			if (collector != null) {
				String func = collector.getFieldAsString("Func", "dcCollectorGeneral");
				
				SchemaResource schema = ResourceHub.getResources().getSchema();
				DbCollector proc = schema.getDbCollector(func);
				
				if (proc != null) {
					ICollector sp = proc.getCollector();
					
					if (sp != null) {
						sp.collect(request, db, table, when, historical, collector, filter);
					}
					else {
						Logger.error("Stored func not found or bad: " + func);
					}
				}
				else {
					Logger.error("Stored func not found or bad: " + func);
				}
			}
			else {
				db.traverseRecords(table, when, historical, filter);
			}
			
			out.endList();
			
			if (! om.hasErrors()) {
				callback.returnValue(out.toLocal());
				return;
			}
		}
		catch (Exception x) {
			Logger.error("Issue with select direct: " + x);
		}
		
		callback.returnEmpty();
	}
}
