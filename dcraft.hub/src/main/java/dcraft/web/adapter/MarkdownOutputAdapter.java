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
package dcraft.web.adapter;

import dcraft.hub.op.OperatingContextException;
import dcraft.locale.LocaleUtil;
import dcraft.script.Script;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.Html;
import dcraft.web.ui.inst.IncludeFragmentInline;
import dcraft.web.ui.inst.IncludeParam;
import dcraft.web.ui.inst.TextWidget;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XText;

import java.io.BufferedReader;
import java.io.StringReader;

public class MarkdownOutputAdapter extends DynamicOutputAdapter {
	@Override
	public Script getSource() throws OperatingContextException {
		if (this.script != null)
			return this.script;

		this.script = UIUtil.mdToDynamic(this.file);
		
		return this.script;
	}
}
