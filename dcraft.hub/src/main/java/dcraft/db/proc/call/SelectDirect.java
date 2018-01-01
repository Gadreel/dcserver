package dcraft.db.proc.call;

import java.util.HashMap;
import java.util.function.Function;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.ICollector;
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
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;
import dcraft.util.StringUtil;

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
		
		RecordStruct collector = params.getFieldAsRecord("Collector");
		
		if (collector != null) {
			try (OperationMarker om = OperationMarker.create()) {
				out.startList();
				
				// TODO make sure we produce only unique records
				// enhance by making this use ^dcTemp for large number of records
				HashMap<String, Boolean> unique = new HashMap<>();
				
				String func = collector.getFieldAsString("Func");
				String fname = collector.getFieldAsString("Field");
				String subid = collector.getFieldAsString("SubId");
				
				ListStruct values = collector.getFieldAsList("Values");
				
				Function<Object,Boolean> uniqueConsumer = new Function<Object,Boolean>() {
					// return true only if value was accepted into output stream
					@Override
					public Boolean apply(Object t) {
						try {
							String id = t.toString();
							
							// we have already returned this one
							if (unique.containsKey(id))
								return false;
							
							if (db.checkSelect(table, id, fwhen, where, historical)) {
								unique.put(id, true);
								
								SelectDirect.this.writeRecord(request, out, db, table, id, fwhen, select, compact, false, historical);
								
								return true;
							}
						}
						catch (Exception x) {
							Logger.error("Issue with select direct: " + x);
						}
						
						return false;
					}
				};				
				
				if (StringUtil.isNotEmpty(func)) {
					SchemaResource schema = ResourceHub.getResources().getSchema();
					DbCollector proc = schema.getDbCollector(func);
					
					if (proc != null) {
						ICollector sp = proc.getCollector();

						if (sp != null) {
							sp.collect(request, collector, uniqueConsumer);
						}
						else {
							Logger.error("Stored func not found or bad: " + func);
						}
					}
					else {
						Logger.error("Stored func not found or bad: " + func);
					}
				}
				else if (values != null) {
					for (Struct s : values.items()) { 
						if ("Id".equals(fname))
							uniqueConsumer.apply(s);
						else
							db.traverseIndex(table, fname, Struct.objectToCore(s), subid, when, historical, uniqueConsumer);
					}
				}
				else {
					Object from = Struct.objectToCore(collector.getField("From"));
					Object to = Struct.objectToCore(collector.getField("To"));
					
					db.traverseIndexRange(table, fname, from, to, when, historical, uniqueConsumer);
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
			return;
		}
		
		/*
		// TODO support collector
		// m collector=Params("Collector")
		 * 
		 i collector("Name")'="" d  quit
		 . s cname=collector("Name")		; "cstate" is a variable available to the collector for state across calls
		 . i ^dcProg("collector",cname)="" q
		 . w StartList
		 . f  x "s id=$$"_^dcProg("collector",cname)_"()" q:id=""  d  q:Errors
		 . . d:$$check^dcDbSelect(table,id,when,.where,historical) writeRec(table,id,when,.select,5,1,0,historical)
		 . w EndList
		 ;
		 i collector("Field")'="" d  quit
		 . i $d(collector("Values")) d  q
		 . . n values m values=collector("Values")
		 . . w StartList
		 . . f  s id=$$loopValues^dcDb(table,collector("Field"),.values,.cstate,when,historical) q:id=""  d
		 . . . d:$$check^dcDbSelect(table,id,when,.where,historical) writeRec(table,id,when,.select,5,1,0,historical)
		 . . w EndList
		 . ;
		 . w StartList
		 . f  s id=$$loopRange^dcDb(table,collector("Field"),collector("From"),collector("To"),.cstate,when,historical) q:id=""  d 
		 . . d:$$check^dcDbSelect(table,id,when,.where,historical) writeRec(table,id,when,.select,5,1,0,historical)
		 . w EndList
		 ;
		 w StartList		
		*/
		
		try (OperationMarker om = OperationMarker.create()) {
			out.startList();
	
			db.traverseRecords(table, when, historical, new Function<Object,Boolean>() {				
				@Override
				public Boolean apply(Object t) {
					try {
						String id = t.toString();
						
						if (db.checkSelect(table, id, fwhen, where, historical)) {
							SelectDirect.this.writeRecord(request, out, db, table, id, fwhen, select, compact, false, historical);
							
							return true;
						}
					}
					catch (Exception x) {
						Logger.error("Issue with select direct: " + x);
					}
					
					return false;
				}
			});
			
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
