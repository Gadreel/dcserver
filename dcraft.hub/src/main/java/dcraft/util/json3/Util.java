package dcraft.util.json3;

import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Util {
	// TODO - improve this, why are there no simple examples of this
	static public String decodeJsonString(String v) {
		if (StringUtil.isEmpty(v))
			return v;
		
		return v
				.replace("\\n", "\n")
				.replace("\\t", "\t")
				.replace("\\\\", "\\")
				.replace("\\\"", "\"")
				;
	}
	
	static public String encodeJsonString(String v) {
		if (StringUtil.isEmpty(v))
			return v;
		
		return v
				.replace("\n", "\\n")
				.replace("\t", "\\t")
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				;
	}

	static public ListStruct importFlatRecords(Path path) {
		CharSequence file = IOUtil.readEntireFile(path);

		if (StringUtil.isEmpty(file)) {
			Logger.error("File is empty or inaccessible: " + path);
			return null;
		}

		String fullfile = file.toString();

		String lines[] = fullfile.split("\n");
		ListStruct records = ListStruct.list();
		RecordStruct currobj = RecordStruct.record();
		boolean valescape = false;
		String currfield = null;
		String currval = "";
		int blankcount = 0;

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();

			try {
				if (valescape) {
					if (line.equals("***")) {
						currobj.with(currfield, currval.trim());
						valescape = false;
						currfield = null;
						currval = "";
					}
					else {
						currval += "\n" + line;

						if (StringUtil.isNotEmpty(line)) {
							if (blankcount == 1)
								System.out.println("only one blank line: " + i);

							blankcount = 0;
						}
						else {
							blankcount++;
						}
					}
				}
				else if (line.indexOf("#record") != -1) {
					if (!currobj.isEmpty()) {
						records.with(currobj);
						currobj = RecordStruct.record();
					}
				}
				else if (StringUtil.isNotEmpty(line)) {
					int pos = line.indexOf(':');

					currfield = line.substring(0, pos).trim();
					currval = line.substring(pos + 1).trim();

					if (currval.equals("***")) {
						valescape = true;
						currval = "";
					}
					else {
						currobj.with(currfield, currval.trim());
						currfield = null;
						currval = "";
					}
				}
			}
			catch (RuntimeException x) {
				Logger.error("Error parsing line: " + i);
				return null;
			}
		}

		if (! currobj.isEmpty())
			records.with(currobj);

		return records;
	}
}
