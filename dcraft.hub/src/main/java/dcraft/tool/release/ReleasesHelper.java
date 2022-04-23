package dcraft.tool.release;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

public class ReleasesHelper {
	protected List<XElement> rellist = null;
	protected RecordStruct reldata = null;
	protected Path cspath = null;
	
	public List<String> names() {
		List<String> names = new ArrayList<String>();
		
		for (int i = 0; i < rellist.size(); i++)
			names.add(rellist.get(i).getAttribute("Name"));
		
		return names;
	}
	
	public void saveData() {
		IOUtil.saveEntireFile(cspath, this.reldata.toPrettyString());
	}

	public XElement get(int i) {
		return this.rellist.get(i);
	}
	
	public XElement get(String name) {
		for (int i = 0; i < rellist.size(); i++)
			if (rellist.get(i).getAttribute("Name").equals(name))
				return rellist.get(i);
		
		return null;
	}
	
	public RecordStruct getData(String name) {
		return this.reldata.getFieldAsRecord(name);
	}
	
	public boolean init(Path relpath) {
		if (relpath == null) {
			System.out.println("Release path not defined");
			return false;
		}
			
		XElement xres = XmlReader.loadFile(relpath.resolve("release.xml"), false, true);
		
		if (xres == null) {
			System.out.println("Release settings file is not present or has bad xml structure");
			return false;
		}
		
		this.rellist = xres.selectAll("Release");
		
		this.cspath = relpath.resolve("release-data.json");

		if (Files.exists(cspath)) {
			CharSequence res = IOUtil.readEntireFile(cspath);
			
			if (StringUtil.isEmpty(res)) {
				System.out.println("Release data unreadable");
				return false;
			}
			
			this.reldata = Struct.objectToRecord(res);
		}
		
		return true;
	}		
}