package dcraft.db.fileindex.Filter;

import dcraft.db.DatabaseException;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.IndexInfo;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// term is expected to work on String (searchable) types only
public class Tags extends BasicFilter {
	protected List<byte[]> values = new ArrayList<>();

	@Override
	public void init(RecordStruct where) throws OperatingContextException {
		this.init(where.getFieldAsList("Tags"));
	}

	public void init(ListStruct tags) throws OperatingContextException {
		if (tags != null) {
			for (BaseStruct tag : tags.items()) {
				String tagstr = tag.toString();

				if (tagstr.endsWith("*"))
					this.values.add(ByteUtil.buildValue("|" + tagstr.substring(0, tagstr.length() - 1)));
				else
					this.values.add(ByteUtil.buildValue("|" + tagstr + "|"));
			}
		}
		
		if (this.values.size() == 0) {
			Logger.error("Tag is missing searchable tags");
		}
	}
	
	@Override
	public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
		try {
			if (this.values != null) {
				List<Object> indexkeys = FileIndexAdapter.pathToIndex(vault, path);
				
				indexkeys.add("Tags");
				
				byte[] data = adapter.getRequest().getInterface().getRaw(indexkeys.toArray());

				//System.out.println("tags: " + ByteUtil.extractValue(data));

				if (data != null) {
					for (int i2 = 0; i2 < this.values.size(); i2++) {
						if (ByteUtil.dataContains(data, this.values.get(i2))) {
							return this.nestOrAccept(adapter, scope, vault, path, file);
						}
					}
				}
			}

			return ExpressionResult.rejected();
		}
		catch (DatabaseException x) {
			Logger.error("Error searching value: " + x);
			
			return ExpressionResult.halt();
		}
	}
	
	@Override
	public void parse(IParentAwareWork state, XElement code, RecordStruct clause) throws OperatingContextException {
		clause.with("Tags", StackUtil.stringFromElement(state, code, "Value"));
	}
}
