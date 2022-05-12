package dcraft.db.fileindex.Filter;

import dcraft.db.DatabaseException;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.fileindex.IFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.expression.ExpressionUtil;
import dcraft.db.request.schema.Query;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.IndexInfo;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.StackUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// term is expected to work on String (searchable) types only
public class Term extends BasicFilter {
	protected List<byte[]> values = new ArrayList<>();
	protected String lang = null;

	@Override
	public void init(RecordStruct where) throws OperatingContextException {
		this.init(where.getFieldAsString("Term"), where.getFieldAsString("Locale"));
	}

	public void init(String term, String locale) throws OperatingContextException {
		this.lang = StringUtil.isNotEmpty(locale) ? locale : OperationContext.getOrThrow().getLocale();

		List<IndexInfo> tokens = ResourceHub.getResources().getSchema().getType("String").toIndexTokens(term, this.lang);
		
		if (tokens != null) {
			for (IndexInfo info : tokens) {
				this.values.add(ByteUtil.buildValue("|" + info.token));
			}
		}
		
		if (this.values.size() == 0) {
			Logger.error("Term is missing searchable words");
		}
	}
	
	@Override
	public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
		BigDecimal score = BigDecimal.ZERO;

		try {
			if (this.values != null) {
				// TODO enhance to use scoring, to require all token
				boolean[] found = new boolean[this.values.size()];
				
				//BigDecimal multi = BigDecimal.ONE;

				// TODO multi = extra.getFieldAsDecimal("_RankMultiplier", multi);
				
				List<Object> indexkeys = FileIndexAdapter.pathToIndex(vault, path);
				
				indexkeys.add(this.lang);
				indexkeys.add("Search");
				
				byte[] data = adapter.getRequest().getInterface().getRaw(indexkeys.toArray());
				
				if (data == null)
					return ExpressionResult.rejected();
				
				for (int i2 = 0; i2 < this.values.size(); i2++) {
					int score1 = ByteUtil.dataContainsScore(data, this.values.get(i2));		// TODO use score - only the top score if more than one match
					
					if (score1 > 0) {
						found[i2] = true;
						//score = score.add(multi.multiply(BigDecimal.valueOf(count)));
						score = score.add(BigDecimal.valueOf(score1));
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
		}
		catch (DatabaseException x) {
			Logger.error("Error searching value: " + x);
			
			return ExpressionResult.halt();
		}
		
		return this.nestOrAccept(adapter, scope, vault, path, file);
	}
	
	@Override
	public void parse(IParentAwareWork state, XElement code, RecordStruct clause) throws OperatingContextException {
		clause.with("Term", StackUtil.stringFromElement(state, code, "Value"));
	}
}
