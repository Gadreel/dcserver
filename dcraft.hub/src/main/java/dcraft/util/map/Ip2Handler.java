package dcraft.util.map;

import dcraft.util.StringUtil;
import dcraft.util.csv.CSVReader;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;

public class Ip2Handler {
	protected ArrayList<Ip2LocationEntry> map = new ArrayList<>();

	public Ip2Handler() {
		map.ensureCapacity(4000000);
	}
	
	public void add(Ip2LocationEntry v) {
		/* we know from experience to add to end
		for (int i = this.map.size() - 1; i >= 0; i--) {
			if (v.to < this.map.get(i).to) {
				this.map.add(i, v);
				return;
			}
		}
		*/
		
		// else add to end
		this.map.add(v);
	}
	
	public int getSize() {
		return this.map.size();
	}
	
	public void loadCVSFile(Path file) {
		try (CSVReader reader = new CSVReader(new FileReader(file.toFile()), ',')) {
			while (!reader.isEOF()) {
				do {
					String first = reader.readField();
					
					if (StringUtil.isEmpty(first))
						break;
					
					Ip2LocationEntry entry = new Ip2LocationEntry();
					
					entry.from = Long.valueOf(first);
					entry.to = Long.valueOf(reader.readField());
					
					entry.country = reader.readField();
					reader.readField(); // skip
					entry.state = reader.readField();
					entry.city = reader.readField();
					entry.lat = reader.readField();
					entry.lon = reader.readField();
					entry.zip = reader.readField();
					entry.zone = reader.readField();
					
					this.add(entry);
				} while (reader.hasMoreFieldsOnLine());
			}
		}
		catch (Exception x) {
			System.out.println("Unable to load ip location file: " + x);
		}
	}
	
	public Ip2LocationEntry lookupIPv4(String v) {
		Long addr = this.dot2LongIPv4(v);
		
		if (addr == null)
			return null;
		
		for (int i = 0; i < this.map.size(); i++) {
			Ip2LocationEntry entry = this.map.get(i);
			
			if ((addr.compareTo(entry.from) >= 0) && (addr.compareTo(entry.to) <= 0)) {
				return entry;
			}
		}
		
		return null;
	}
	
	public Long dot2LongIPv4(String ipstring) {
		String[] ipAddressInArray = ipstring.split("\\.");
		
		long result = 0;
		
		for (int x = 3; x >= 0; x--) {
			long ip = Long.parseLong(ipAddressInArray[3 - x]);
			result |= ip << (x << 3);
		}
		
		return result;
	}
	
	public java.math.BigInteger dot2LongIPv6(String ipv6) {
		try {
			java.net.InetAddress ia = java.net.InetAddress.getByName(ipv6);
			byte byteArr[] = ia.getAddress();
			
			if (ia instanceof java.net.Inet6Address) {
				java.math.BigInteger ipnumber = new java.math.BigInteger(1, byteArr);
				return ipnumber;
			}
		}
		catch (Exception x) {
		
		}
		
		return null;
	}
}
