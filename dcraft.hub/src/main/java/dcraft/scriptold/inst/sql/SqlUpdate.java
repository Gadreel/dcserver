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

import java.util.Arrays;
import java.util.List;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.scriptold.ExecuteState;
import dcraft.scriptold.Instruction;
import dcraft.scriptold.StackEntry;
import dcraft.sql.SqlNull;
import dcraft.sql.SqlHub;
import dcraft.sql.SqlHub.SqlDatabase;
import dcraft.util.StringBuilder32;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class SqlUpdate extends Instruction {
	@Override
	public void run(final StackEntry stack) throws OperatingContextException {
		//OperationContext.get().info("Doing a SQL UPDATE on thread " + Thread.currentThread().getName());
		
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
		sb1.append("UPDATE " + table + " SET ");
		
		List<XElement> fields = codeEl.selectAll("Field");
		XElement el2 = codeEl.selectFirst("Where");
		
		if (el2 == null) {
			Logger.error("Missing WHERE in SQL UPDATE");
			stack.setState(ExecuteState.Done);
			stack.resume();
			return;
		}
		
		boolean firstfld = true;
    	int pos = 0;
    	
		Object[] vals = new Object[fields.size() + ((el2 == null) ? 0 : 1)];
	
		for (XElement el : fields) {
			boolean skipnull = stack.boolFromElement(el, "SkipNull", false);
			
	        Object val = SqlInsert.convertValueToInternal(stack, el);

			if (skipnull && (val instanceof SqlNull)) 
				continue;
			
			if (firstfld)
				firstfld = false;
			else 
				sb1.append(',');
			
			sb1.append(stack.stringFromElement(el, "Name") + " = ?");
			
			vals[pos] = val;
			pos++;
		}
		
		vals[pos] = SqlInsert.convertValueToInternal(stack, el2);
		pos++;
		
		// if too big, resize array
		if (pos < vals.length) 
			vals = Arrays.copyOfRange(vals, 0, pos);
		
		// no fields updated - don't error, might just mean there was no match after skip checks
		if (firstfld) {
			stack.setState(ExecuteState.Done);
			stack.resume();
			return;
		}
		
		String sql = sb1.toString() + " WHERE " + stack.stringFromElement(el2, "Name") + " = ?";
		
		SqlDatabase db = SqlHub.getDatabase(dbname);
		
		if (db == null) {
			Logger.errorTr(185, dbname);
			stack.setState(ExecuteState.Done);
			stack.resume();
			return;
		}
        
		Integer rsres = db.executeUpdate(sql, vals);
		
		if (rsres != 1) 
			Logger.error("UPDATE failed, expected 1 row result count");		
		
		stack.setState(ExecuteState.Done);
		stack.resume();
	}
	
	@Override
	public void cancel(StackEntry stack) {
		// do nothing, this isn't cancellable
	}
}
