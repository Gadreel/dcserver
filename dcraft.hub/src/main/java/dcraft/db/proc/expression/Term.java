package dcraft.db.proc.expression;

import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IExpression;
import dcraft.db.proc.IFilter;
import dcraft.db.request.schema.Query;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.RndUtil;
import dcraft.xml.XElement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// term is expected to work on String (searchable) types only
public class Term implements IExpression {
	protected List<ExpressionUtil.FieldInfo> fieldInfo = new ArrayList<>();
	protected List<RecordStruct> fieldExtra = new ArrayList<>();
	protected List<byte[]> values = null;
	protected String lang = null;
	protected String table = null;
	protected IFilter nested = null;

	@Override
	public IFilter withNested(IFilter v) {
		this.nested = v;
		return this;
	}

	@Override
	public IFilter shiftNested(IFilter v) {
		v.withNested(this.nested);

		this.nested = v;
		return this;
	}

	@Override
	public void init(String table, RecordStruct where) throws OperatingContextException {
		this.table = table;
		this.lang = where.getFieldAsString("Locale", OperationContext.getOrThrow().getLocale());
		
		RecordStruct fldrec = where.getFieldAsRecord("A");
		
		if (fldrec == null) {
			Logger.error("Term is missing a field rec");
			return;
		}
		
		ListStruct fldlist = fldrec.getFieldAsList("Value");
		
		if (fldlist == null) {
			Logger.error("Term is missing a field list");
			return;
		}
		
		for (int i = 0; i < fldlist.size(); i++) {
			RecordStruct pdef = fldlist.getItemAsRecord(i);

			this.fieldInfo.add(ExpressionUtil.loadField(table, pdef));

			RecordStruct anyrec = null;

			if (pdef.hasField("Value"))
				anyrec = pdef.getFieldAsRecord("Value");

			if (anyrec == null)
				anyrec = RecordStruct.record();

			this.fieldExtra.add(anyrec);
		}

		if (this.fieldInfo.size() == 0) {
			Logger.error("Term is missing a field");
			return;
		}
		
		/* trust the coder
		for (ExpressionUtil.FieldInfo info : this.fieldInfo) {
			if (! info.type.isSearchable()) {
				Logger.error("Term include a non-searchable field");
				return;
			}
		}
		*/
		
		this.values = ExpressionUtil.loadSearchValues(where.getFieldAsRecord("B"), this.fieldInfo.get(0), this.lang, "|", null);
		
		if (this.values == null) {
			Logger.error("Term is missing values");
			return;
		}
	}
	
	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id) throws OperatingContextException {
		BigDecimal score = BigDecimal.ZERO;

		if (values != null) {
			// TODO enhance to use scoring, to require all token
			boolean[] found = new boolean[this.values.size()];

			for (int i0 = 0; i0 < this.fieldInfo.size(); i0++) {
				ExpressionUtil.FieldInfo info = this.fieldInfo.get(i0);
				RecordStruct extra = this.fieldExtra.get(i0);

				BigDecimal multi = BigDecimal.ONE;

				if (extra != null) {
					multi = extra.getFieldAsDecimal("_RankMultiplier", multi);
				}

				List<byte[]> data = adapter.getRaw(table, id, info.field.getName(), info.subid, "Search");

				if (data == null)
					continue;

				for (int i = 0; i < data.size(); i++) {
					for (int i2 = 0; i2 < this.values.size(); i2++) {
						int count = ByteUtil.dataContainsCount(data.get(i), this.values.get(i2));

						if (count > 0) {
							found[i2] = true;
							score = score.add(multi.multiply(BigDecimal.valueOf(count)));
						}
					}
				}
			}

			// TODO make some values (terms) optional?
			for (boolean b : found) {
				if (! b)
					return ExpressionResult.rejected();
			}
		}

		if (score.equals(BigDecimal.ZERO))
			return ExpressionResult.rejected();

		if (scope != null) {
			RecordStruct rcache = (RecordStruct) scope.queryVariable("_RecordCache");

			if (rcache != null) {
				// TODO make so init can decide the fld name in case multiple terms are being used
				rcache.with("TermScore", score);
			}
		}
		
		return this.nestOrAccept(adapter, scope, table, id);
	}

	public ExpressionResult nestOrAccept(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
		if (this.nested != null)
			return this.nested.check(adapter, scope, table, val);

		return ExpressionResult.ACCEPTED;
	}

	@Override
	public void parse(IParentAwareWork state, XElement code, RecordStruct clause) throws OperatingContextException {
		String[] fields = StackUtil.stringFromElement(state, code, "Fields", "").split(",");
		ListStruct fvalue = ListStruct.list();
		
		for (String fld : fields) {
			int pos = fld.indexOf(":");
			
			if (pos > -1) {
				fvalue.with(RecordStruct.record()
						.with("Field", fld.substring(0, pos))
						.with("SubId", fld.substring(pos + 1))
				);
			}
			else {
				fvalue.with(RecordStruct.record().with("Field", fld));
			}
		}
		
		clause.with("A", RecordStruct.record().with("Value", fvalue));
		clause.with("B", Query.createWhereValue(state, code, "Value"));
	}
}
