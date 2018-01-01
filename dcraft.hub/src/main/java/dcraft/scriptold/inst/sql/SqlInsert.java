/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.scriptold.inst.sql;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.scriptold.ExecuteState;
import dcraft.scriptold.Instruction;
import dcraft.scriptold.StackEntry;
import dcraft.sql.SqlNull;
import dcraft.sql.SqlType;
import dcraft.sql.SqlHub;
import dcraft.sql.SqlHub.SqlDatabase;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.util.StringBuilder32;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class SqlInsert extends Instruction {
	@Override
	public void run(StackEntry stack) throws OperatingContextException {
		//OperationContext.get().info("Doing a SQL INSERT on thread " + Thread.currentThread().getName());
		
		String dbname = stack.stringFromSource("Database", "default");
		String table = stack.stringFromSource("Table");

		if (StringUtil.isEmpty(dbname) || StringUtil.isEmpty(table)) {
			Logger.error("Missing table for insert");
			stack.setState(ExecuteState.Done);
			stack.resume();
			return;
		}
		
		XElement codeEl = stack.getInstruction().getXml();
		
		StringBuilder32 sb1 = new StringBuilder32();
		sb1.append("INSERT INTO " + table + " (");
		
		StringBuilder32 sb2 = new StringBuilder32();
		sb2.append(" VALUES (");
		
		List<XElement> fields = codeEl.selectAll("Field");
		boolean firstfld = true;
		Object[] vals = new Object[fields.size()];
    	int pos = 0;
		
		for (XElement el : fields) {
			if (firstfld)
				firstfld = false;
			else {
				sb1.append(',');
				sb2.append(',');
			}
			
			sb1.append(stack.stringFromElement(el, "Name"));
			sb2.append('?');
			
        	vals[pos] = SqlInsert.convertValueToInternal(stack, el);
			pos++;
		}
		
		sb1.append(')');
		sb2.append(')');
		
		// no fields found
		if (firstfld) {
			stack.setState(ExecuteState.Done);
			stack.resume();
			return;
		}
		
		String sql = sb1.toString() + sb2.toString();
		
		SqlDatabase db = SqlHub.getDatabase(dbname);
		
		if (db == null) {
			Logger.errorTr(185, dbname);
			stack.setState(ExecuteState.Done);
			stack.resume();
			return;
		}
        
		Integer rsres = db.executeInsert(sql, vals);
		
		if (rsres != 1) 
			Logger.error("INSERT failed, expected 1 row result count");		
		
		stack.setState(ExecuteState.Done);
		stack.resume();
	}
	
	@Override
	public void cancel(StackEntry stack) {
		// do nothing, this isn't cancellable
	}
	
	static public Object convertValueToInternal(StackEntry stack, XElement el) {
		String ftype = stack.stringFromElement(el, "Type");
        Struct dt = stack.refFromElement(el, "Value");
        
        SqlType stype = SqlType.valueOf(ftype);
        
		if (stype == SqlType.Int) {
        	Long ret = Struct.objectToInteger(dt);
        	return (ret == null) ? SqlNull.Int : ret;
		}
		
		if (stype == SqlType.Long) {
        	Long ret = Struct.objectToInteger(dt);
        	return (ret == null) ? SqlNull.Long : ret;
		}
		
		if (stype == SqlType.Double) {
        	BigDecimal ret = Struct.objectToDecimal(dt);
        	return (ret == null) ? SqlNull.Double : ret;
		}
		
		if (stype == SqlType.BigDecimal) {
        	BigDecimal ret = Struct.objectToDecimal(dt);
        	return (ret == null) ? SqlNull.BigDecimal : ret;
		}

		if (stype == SqlType.DateTime) {
			ZonedDateTime ret = Struct.objectToDateTime(dt);
        	return (ret == null) ? SqlNull.DateTime : ret;
		}
		
		if (stype == SqlType.Text) {
			if (dt == NullStruct.instance)
        		return SqlNull.Text;
			
        	String ret = Struct.objectToString(dt);
        	
        	if (ret == null)
        		return SqlNull.Text;

			if ("True".equals(stack.stringFromElement(el, "Encrypt")))
				ret = ApplicationHub.getClock().getObfuscator().encryptStringToHex(ret);
			
    		return ret;
		}

		if (dt == NullStruct.instance)
    		return SqlNull.VarChar;
		
    	String ret = Struct.objectToString(dt);
    	
    	if (ret == null)
    		return SqlNull.VarChar;
    	
		if ("True".equals(stack.stringFromElement(el, "Encrypt")))
			ret = ApplicationHub.getClock().getObfuscator().encryptStringToHex(ret);
			
    	return ret;
	}
}
