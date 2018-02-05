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

		CharSequence md = IOUtil.readEntireFile(this.file);

		if (md.length() == 0) 
			return null;
		
		// TODO md = this.processIncludes(wctx, md);
		
		try {
			Html html = Html.tag();
			
			BufferedReader bufReader = new BufferedReader(new StringReader(md.toString()));
	
			String line = bufReader.readLine();
			
			RecordStruct fields = RecordStruct.record();
			
			// TODO enhance to become https://www.npmjs.com/package/front-matter compatible
			
			// start with $ for non-locale fields
			while (StringUtil.isNotEmpty(line)) {
				int pos = line.indexOf(':');
				
				if (pos == -1)
					break;
				
				String field = line.substring(0, pos);
				
				String value = line.substring(pos + 1).trim();
				
				fields.with(field, value);
	
				line = bufReader.readLine();
			}
			
			String locale = LocaleUtil.normalizeCode(fields.getFieldAsString("Locale", "eng"));  // should be a way to override, but be careful because 3rd party might depend on being "en", sorry something has to be default

			// TODO lookup alternative locales based on OC current locale
			
			for (FieldStruct fld : fields.getFields()) {
				String name = fld.getName();
				
				if (name.startsWith("$")) {
					html.with(
							W3.tag("Meta")
								.attr("Title", name.substring(1))
								.attr("Value", Struct.objectToString(fld.getValue()))
					);
				}
				else {
					html.with(
							W3.tag("Meta")
									.attr("Title", name.substring(1))
									.with(W3.tag("Tr")
											.attr("Locale", locale)
											.attr("Value", Struct.objectToString(fld.getValue()))
									)
					);
				}
			}
			
			// see if there is more - the body
			if (line != null) {
				XText mdata = new XText();
				mdata.setCData(true);
				
				line = bufReader.readLine();
				
				while (line != null) {
					mdata.appendBuffer(line);
					mdata.appendBuffer("\n");
		
					line = bufReader.readLine();
				}
				
				mdata.closeBuffer();
				
				html.with(IncludeParam.tag()
						.attr("Name", "content")
						.with(
							TextWidget.tag()
							.with(W3.tag("Tr")
									.attr("Locale", locale)
									.with(mdata)
							)
						)
				);
			}
			
			String skeleton = fields.getFieldAsString("Skeleton", "general");
			
			html.with(IncludeFragmentInline.tag()
					.withAttribute("Path", "/skeletons/" + skeleton));		// TODO if doesn't start with / assume skeletons folder
			
			this.script = Script.of(html, md);
		}
		catch (Exception x) {
			System.out.println("md parse issue");
		}
		
		return this.script;
	}
}
