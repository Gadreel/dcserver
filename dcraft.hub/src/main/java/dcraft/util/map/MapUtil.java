package dcraft.util.map;

import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.net.NetUtil;
import z.dga.Util;

public class MapUtil {
	
	static public String getLatLong(String address, String key) {
		RecordStruct gres = MapUtil.getGeoCode(address, key);
		
		if (gres == null)
			return null;
		
		//System.out.println("a: " + gres.getFieldAsString("formatted_address"));
		
		RecordStruct locrec = gres.getFieldAsRecord("geometry").getFieldAsRecord("location");
		
		return locrec.getFieldAsString("lat") + "," + locrec.getFieldAsString("lng");
	}
	
	static public RecordStruct getGeoCode(String address, String key) {
		address = NetUtil.urlEncodeUTF8(address);
		
		String url = "https://maps.googleapis.com/maps/api/geocode/json?address="
				+ address + "&key=" + key;
		
		CompositeStruct res = CompositeParser.parseJsonUrl(url);
		
		if (res == null)
			return null;
		
		return ((RecordStruct) res).getFieldAsList("results").getItemAsRecord(0);
	}
	
}
