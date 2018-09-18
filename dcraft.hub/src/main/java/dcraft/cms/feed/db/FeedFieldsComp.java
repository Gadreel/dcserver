package dcraft.cms.feed.db;

import dcraft.db.proc.IComposer;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.LocaleResource;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

public class FeedFieldsComp implements IComposer {
	@Override
	public void writeField(ICompositeBuilder out, TablesAdapter db, IVariableAware scope, String table, String id,
						   RecordStruct field, boolean compact) throws OperatingContextException
	{	
		try {
			RecordStruct fields = RecordStruct.record();
			
			List<String> names = db.getStaticListKeys(table, id, "dcmSharedFields");
			
			for (String name : names)
				fields.with(name, db.getStaticList(table, id, "dcmSharedFields", name));
			
			String mylocale = "." + OperationContext.getOrThrow().getResources().getLocale().getDefaultLocale();
			String deflocale = "." + OperationContext.getOrThrow().getTenant().getResources().getLocale().getDefaultLocale();
			
			List<String> lnames = db.getStaticListKeys(table, id, "dcmLocaleFields");
			
			// find values for my locale
			for (String name : lnames) {
				if (name.endsWith(mylocale)) {
					String pubname = name.substring(0, name.length() - mylocale.length());
					
					fields.with(pubname, db.getStaticList(table, id, "dcmLocaleFields", name));
				}
			}
			
			// find values for default locale, if not present in my locale
			for (String name : lnames) {
				if (name.endsWith(deflocale)) {
					String pubname = name.substring(0, name.length() - mylocale.length());
					
					if (! fields.hasField(pubname))
						fields.with(pubname, db.getStaticList(table, id, "dcmLocaleFields", name));
				}
			}
			
			fields.toBuilder(out);
		}
		catch (BuilderStateException x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}
}
