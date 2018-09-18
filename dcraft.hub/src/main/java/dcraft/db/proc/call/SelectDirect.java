package dcraft.db.proc.call;

import dcraft.db.proc.*;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.ICallContext;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.*;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.schema.DbCollector;
import dcraft.schema.SchemaResource;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;

public class SelectDirect implements IStoredProc {
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
	 ;				"Filter"		filter scriptold to generate content
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
	 ;				"Filter"		filter scriptold to generate content
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
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		
		//System.out.println("Query: " + params.toPrettyString());
		
		String table = params.getFieldAsString("Table");
		BigDateTime when = params.getFieldAsBigDateTime("When");
		boolean compact = params.hasField("Compact") ? params.getFieldAsBooleanOrFalse("Compact") : true;
		boolean historical = params.getFieldAsBooleanOrFalse("Historical");	
		ListStruct select = params.getFieldAsList("Select");
		RecordStruct where = params.getFieldAsRecord("Where");
		
		// TODO add db filter option
		//d runFilter("Query") quit:Errors  ; if any violations in filter then do not proceed
		
		TablesAdapter db = TablesAdapter.of(request, when, historical);
		ICompositeBuilder out = new ObjectBuilder();

		IVariableAware scope = OperationContext.getOrThrow();

		IFilter filter = Unique.unique()
				.withNested(new CurrentRecord()
					.withNested(new BasicFilter() {
						@Override
						public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object id) throws OperatingContextException {
							//System.out.println("check: " + id);
							RecordScope rscope =  RecordScope.of(scope);

							if (adapter.checkSelect(rscope, table, id.toString(), where)) {
								try {
									TableUtil.writeRecord(out, db, rscope, table, id.toString(), select, compact, false);
								} catch (BuilderStateException x) {
									return ExpressionResult.halt();
								}

								return ExpressionResult.accepted();
							}

							return ExpressionResult.rejected();
						}
					})
				);
		
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
						sp.collect(request, db, scope, table, collector, filter);
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
				db.traverseRecords(scope, table, filter);
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
