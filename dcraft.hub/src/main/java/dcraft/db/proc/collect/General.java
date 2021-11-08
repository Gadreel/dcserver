package dcraft.db.proc.collect;

import dcraft.db.IRequestContext;
import dcraft.db.proc.ICollector;
import dcraft.db.proc.IFilter;
import dcraft.db.proc.expression.ExpressionUtil;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Max;
import dcraft.db.ICallContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.schema.DataType;
import dcraft.schema.DbField;
import dcraft.schema.SchemaResource;
import dcraft.script.StackUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.ArrayList;
import java.util.List;

public class General implements ICollector {
	@Override
	public void collect(IRequestContext task, TablesAdapter db, IVariableAware scope, String table, RecordStruct collector, IFilter filter) throws OperatingContextException {
		String fname = collector.getFieldAsString("Field");
		String subid = collector.getFieldAsString("SubId");

		// TODO we should give a way to override this
		filter = CurrentRecord.current().withNested(filter);

		if (collector.isNotFieldEmpty("Max"))
			filter = Max.max().withMax(Struct.objectToInteger(Struct.objectToCore(collector.getField("Max")))).withNested(filter);

		ListStruct values = collector.getFieldAsList("Values");

		if (values != null) {
			for (BaseStruct s : values.items()) {
				if ("Id".equals(fname))
					filter.check(db, scope, table, s.toString());
				else
					db.traverseIndex(scope, table, fname, Struct.objectToCore(s), subid, filter);
			}
		}
		else {
			Object from = Struct.objectToCore(collector.getField("From"));
			Object to = Struct.objectToCore(collector.getField("To"));
			boolean reverse = false;
			
			SchemaResource schema = ResourceHub.getResources().getSchema();
			DbField ffdef = schema.getDbField(table, fname);
			
			if (ffdef == null)
				return;
			
			DataType dtype = schema.getType(ffdef.getTypeId());
			
			if (dtype == null)
				return;
			
			List<byte[]> fromkey = new ArrayList<>();
			List<byte[]> tokey = new ArrayList<>();
			
			if (from != null)
				fromkey.add(ByteUtil.buildKey(dtype.toIndex(from, "eng")));    // TODO increase locale support
			
			if (to != null)
				tokey.add(ByteUtil.buildKey(dtype.toIndex(to, "eng")));    // TODO increase locale support
			
			if (ExpressionUtil.compare(fromkey, tokey) > 0)
				reverse = true;

			if (Struct.objectToBooleanOrFalse(reverse))
				db.traverseIndexReverseRange(scope, table, fname, from, to, filter);
			else
				db.traverseIndexRange(scope, table, fname, from, to, filter);
		}
	}

	@Override
	public RecordStruct parse(IParentAwareWork state, XElement code) throws OperatingContextException {
		RecordStruct clause = RecordStruct.record()
				.with("Func", "dcCollectorGeneral")
				.with("Field", StackUtil.refFromElement(state, code, "Field"))
				.with("SubId", StackUtil.refFromElement(state, code, "SubId"))
				//.with("Reverse", StackUtil.refFromElement(state, code, "Reverse"))
				.with("Max", StackUtil.refFromElement(state, code, "Max", true))
				.with("From", StackUtil.refFromElement(state, code, "From", true))
				.with("To", StackUtil.refFromElement(state, code, "To", true));

		if (code.hasNotEmptyAttribute("Values")) {
			String values = code.getAttribute("Values");

			if (values.startsWith("$")) {
				// this should be a ListStruct
				clause.with("Values", StackUtil.refFromElement(state, code, "Values"));
			}
			else {
				String svalues = StackUtil.stringFromElement(state, code, "Values");
				
				if (StringUtil.isNotEmpty(svalues))
					clause.with("Values", ListStruct.list((Object[]) svalues.split(",")));
				else
					clause.with("Values", ListStruct.list());
			}
		}

		return clause;
	}
}
