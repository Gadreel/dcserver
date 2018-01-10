package dcraft.db.proc.call;

import java.util.function.Function;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.FilterResult;
import dcraft.db.proc.IFilter;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;

public class ListDirect extends LoadRecord {
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
	 ;  Select	field to query
	 ;			"Field"			name
	 ;			"Format"		format of field if scalar
	 ;			"ForeignField"	value field in fk relationship
	 ;			"Composer"		composer scriptold to generate content
	 ;			"Table"			for reverse foreign
	 ;			"Select"		list of fields to query, see above
	 ;					0,
	 ;					1...
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
	 ;	Historical	true / false
	 ;
	 ; Result
	 ;		List of values, content based on Select
	 ;
	 */
	@Override
	public void execute(DbServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		
		String table = params.getFieldAsString("Table");
		BigDateTime when = params.getFieldAsBigDateTime("When");
		boolean compact = params.hasField("Compact") ? params.getFieldAsBooleanOrFalse("Compact") : true;
		boolean historical = params.getFieldAsBooleanOrFalse("Historical");	
		ListStruct select = ListStruct.list(params.getFieldAsRecord("Select"));
		RecordStruct where = params.getFieldAsRecord("Where");
		
		if (when == null)
			when = BigDateTime.nowDateTime();
		
		BigDateTime fwhen = when;
		
		// TODO add db filter option
		//d runFilter("Query") quit:Errors  ; if any violations in filter then do not proceed
		
		TablesAdapter db = TablesAdapter.of(request);
		
		/*
		// TODO support collector
		*/
		
		
		ICompositeBuilder out = new ObjectBuilder();
		
		try (OperationMarker om = OperationMarker.create()) {
			out.startList();
			
			
			IFilter filter = Unique.unique()
					.withNested(new BasicFilter() {
						@Override
						public FilterResult check(TablesAdapter adapter, Object id, BigDateTime when, boolean historical) throws OperatingContextException {
							if (adapter.checkSelect(table, id.toString(), when, where, historical)) {
								try {
									ListDirect.this.writeField(request, out, db, table, id.toString(), fwhen, select.getItemAsRecord(0),
											historical, compact);
								}
								catch (BuilderStateException x) {
									return FilterResult.halt();
								}
								
								return FilterResult.accepted();
							}
							
							return FilterResult.rejected();
						}
					});
			
			
			db.traverseRecords(table, when, historical, filter);
	
			out.endList();
			
			if (! om.hasErrors()) {
				callback.returnValue(out.toLocal());
				return;
			}
		}
		catch (Exception x) {
			Logger.error("Issue with select list: " + x);
		}
		
		callback.returnEmpty();
	}
}
