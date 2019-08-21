package dcraft.util.map;

import dcraft.hub.app.ApplicationHub;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.util.net.NetUtil;
import dcraft.xml.XElement;

import java.util.ArrayList;
import java.util.List;

public class MapUtil {
	static final public String[] STATE_CODES = "AL,AZ,AR,CA,CO,CT,DE,DC,FL,GA,ID,IL,IN,IA,KS,KY,LA,ME,MD,MA,MI,MN,MS,MO,MT,NE,NV,NH,NJ,NM,NY,NC,ND,OH,OK,OR,PA,RI,SC,SD,TN,TX,UT,VT,VA,WA,WV,WI,WY".split(",");
	static final public String[] STATE_BORDERS = "AL-FL,AL-GA,AL-MS,AL-TN,AR-LA,AR-MO,AR-MS,AR-OK,AR-TN,AR-TX,AZ-CA,AZ-CO,AZ-NM,AZ-NV,AZ-UT,CA-NV,CA-OR,CO-KS,CO-NE,CO-NM,CO-OK,CO-UT,CO-WY,CT-MA,CT-NY,CT-RI,DC-MD,DC-VA,DE-MD,DE-NJ,DE-PA,FL-GA,GA-NC,GA-SC,GA-TN,IA-MN,IA-MO,IA-NE,IA-SD,IA-WI,ID-MT,ID-NV,ID-OR,ID-UT,ID-WA,ID-WY,IL-IA,IL-IN,IL-KY,IL-MO,IL-WI,IN-KY,IN-MI,IN-OH,KS-MO,KS-NE,KS-OK,KY-MO,KY-OH,KY-TN,KY-VA,KY-WV,LA-MS,LA-TX,MA-NH,MA-NY,MA-RI,MA-VT,MD-PA,MD-VA,MD-WV,ME-NH,MI-OH,MI-WI,MN-ND,MN-SD,MN-WI,MO-NE,MO-OK,MO-TN,MS-TN,MT-ND,MT-SD,MT-WY,NC-SC,NC-TN,NC-VA,ND-SD,NE-SD,NE-WY,NH-VT,NJ-NY,NJ-PA,NM-OK,NM-TX,NM-UT,NV-OR,NV-UT,NY-PA,NY-VT,OH-PA,OH-WV,OK-TX,OR-WA,PA-WV,SD-WY,TN-VA,UT-WY,VA-WV".split(",");
	
	static public List<String> getBorderState(String state) {
		List<String> bordering = new ArrayList<>();
		
		state = state.toUpperCase();	// just in case
		
		for (int i = 0; i < MapUtil.STATE_BORDERS.length; i++) {
			String pair = MapUtil.STATE_BORDERS[i];
			
			if (pair.startsWith(state))
				bordering.add(pair.substring(3));
			
			if (pair.endsWith(state))
				bordering.add(pair.substring(0, 2));
		}
		
		return bordering;
	}
	
	static public ListStruct stateToZips(String st) {
		ListStruct result = new ListStruct();
		
		switch (st) {
			case "NY":
				result.with("005","100","101","102","103","104","105","106","107","108","109","110","111","112","113","114","115","116","117","118","119","120","121","122","123","124","125","126","127","128","129","130","131","132","133","134","135","136","137","138","139","140","141","142","143","144","145","146","147","148","149");
				break;
			
			case "PR":
				result.with("006","007","009");
				break;
			
			case "VI":
				result.with("008");
				break;
			
			case "MA":
				result.with("010","011","012","013","014","015","016","017","018","019","020","021","022","023","024","025","026","027","055");
				break;
			
			case "RI":
				result.with("028","029");
				break;
			
			case "NH":
				result.with("030","031","032","033","034","035","036","037","038");
				break;
			
			case "ME":
				result.with("039","040","041","042","043","044","045","046","047","048","049");
				break;
			
			case "VT":
				result.with("050","051","052","053","054","056","057","058","059");
				break;
			
			case "CT":
				result.with("060","061","062","063","064","065","066","067","068","069");
				break;
			
			case "NJ":
				result.with("070","071","072","073","074","075","076","077","078","079","080","081","082","083","084","085","086","087","088","089");
				break;
			
			case "AE":
				result.with("090","091","092","093","094","095","096","097","098");
				break;
			
			case "PA":
				result.with("150","151","152","153","154","155","156","157","158","159","160","161","162","163","164","165","166","167","168","169","170","171","172","173","174","175","176","177","178","179","180","181","182","183","184","185","186","187","188","189","190","191","192","193","194","195","196");
				break;
			
			case "DE":
				result.with("197","198","199");
				break;
			
			case "DC":
				result.with("200","202","203","204","205","569");
				break;
			
			case "VA":
				result.with("201","220","221","222","223","224","225","226","227","228","229","230","231","232","233","234","235","236","237","238","239","240","241","242","243","244","245","246");
				break;
			
			case "MD":
				result.with("206","207","208","209","210","211","212","214","215","216","217","218","219");
				break;
			
			case "WV":
				result.with("247","248","249","250","251","252","253","254","255","256","257","258","259","260","261","262","263","264","265","266","267","268");
				break;
			
			case "NC":
				result.with("270","271","272","273","274","275","276","277","278","279","280","281","282","283","284","285","286","287","288","289");
				break;
			
			case "SC":
				result.with("290","291","292","293","294","295","296","297","298","299");
				break;
			
			case "GA":
				result.with("300","301","302","303","304","305","306","307","308","309","310","311","312","313","314","315","316","317","318","319","398","399");
				break;
			
			case "FL":
				result.with("320","321","322","323","324","325","326","327","328","329","330","331","332","333","334","335","336","337","338","339","341","342","344","346","347","349");
				break;
			
			case "AA":
				result.with("340");
				break;
			
			case "AL":
				result.with("350","351","352","354","355","356","357","358","359","360","361","362","363","364","365","366","367","368","369");
				break;
			
			case "TN":
				result.with("370","371","372","373","374","375","376","377","378","379","380","381","382","383","384","385");
				break;
			
			case "MS":
				result.with("386","387","388","389","390","391","392","393","394","395","396","397");
				break;
			
			case "KY":
				result.with("400","401","402","403","404","405","406","407","408","409","410","411","412","413","414","415","416","417","418","420","421","422","423","424","425","426","427");
				break;
			
			case "OH":
				result.with("430","431","432","433","434","435","436","437","438","439","440","441","442","443","444","445","446","447","448","449","450","451","452","453","454","455","456","457","458","459");
				break;
			
			case "IN":
				result.with("460","461","462","463","464","465","466","467","468","469","470","471","472","473","474","475","476","477","478","479");
				break;
			
			case "MI":
				result.with("480","481","482","483","484","485","486","487","488","489","490","491","492","493","494","495","496","497","498","499");
				break;
			
			case "IA":
				result.with("500","501","502","503","504","505","506","507","508","509","510","511","512","513","514","515","516","520","521","522","523","524","525","526","527","528");
				break;
			
			case "WI":
				result.with("530","531","532","534","535","537","538","539","540","541","542","543","544","545","546","547","548","549");
				break;
			
			case "MN":
				result.with("550","551","553","554","555","556","557","558","559","560","561","562","563","564","565","566","567");
				break;
			
			case "SD":
				result.with("570","571","572","573","574","575","576","577");
				break;
			
			case "ND":
				result.with("580","581","582","583","584","585","586","587","588");
				break;
			
			case "MT":
				result.with("590","591","592","593","594","595","596","597","598","599");
				break;
			
			case "IL":
				result.with("600","601","602","603","604","605","606","607","608","609","610","611","612","613","614","615","616","617","618","619","620","622","623","624","625","626","627","628","629");
				break;
			
			case "MO":
				result.with("630","631","633","634","635","636","637","638","639","640","641","644","645","646","647","648","649","650","651","652","653","654","655","656","657","658");
				break;
			
			case "KS":
				result.with("660","661","662","664","665","666","667","668","669","670","671","672","673","674","675","676","677","678","679");
				break;
			
			case "NE":
				result.with("680","681","683","684","685","686","687","688","689","690","691","692","693");
				break;
			
			case "LA":
				result.with("700","701","703","704","705","706","707","708","710","711","712","713","714");
				break;
			
			case "AR":
				result.with("716","717","718","719","720","721","722","723","724","725","726","727","728","729");
				break;
			
			case "OK":
				result.with("730","731","734","735","736","737","738","739","740","741","743","744","745","746","747","748","749");
				break;
			
			case "TX":
				result.with("733","750","751","752","753","754","755","756","757","758","759","760","761","762","763","764","765","766","767","768","769","770","772","773","774","775","776","777","778","779","780","781","782","783","784","785","786","787","788","789","790","791","792","793","794","795","796","797","798","799","885");
				break;
			
			case "CO":
				result.with("800","801","802","803","804","805","806","807","808","809","810","811","812","813","814","815","816");
				break;
			
			case "WY":
				result.with("820","821","822","823","824","825","826","827","828","829","830","831");
				break;
			
			case "ID":
				result.with("832","833","834","835","836","837","838");
				break;
			
			case "UT":
				result.with("840","841","842","843","844","845","846","847");
				break;
			
			case "AZ":
				result.with("850","851","852","853","855","856","857","859","860","863","864","865");
				break;
			
			case "NM":
				result.with("870","871","872","873","874","875","877","878","879","880","881","882","883","884");
				break;
			
			case "NV":
				result.with("889","890","891","893","894","895","897","898");
				break;
			
			case "CA":
				result.with("900","901","902","903","904","905","906","907","908","910","911","912","913","914","915","916","917","918","919","920","921","922","923","924","925","926","927","928","930","931","932","933","934","935","936","937","938","939","940","941","942","943","944","945","946","947","948","949","950","951","952","953","954","955","956","957","958","959","960","961");
				break;
			
			case "AP":
				result.with("962","963","964","965","966");
				break;
			
			case "HI":
				result.with("967","968");
				break;
			
			case "GU":
				result.with("969");
				break;
			
			case "OR":
				result.with("970","971","972","973","974","975","976","977","978","979");
				break;
			
			case "WA":
				result.with("980","981","982","983","984","985","986","988","989","990","991","992","993","994");
				break;
			
			case "AK":
				result.with("995","996","997","998","999");
				break;
		}
		
		return result;
	}
	
	static public String zipToState(String zip) {
		if (StringUtil.isEmpty(zip) || (zip.length() < 3))
			return null;
		
		zip = zip.substring(0, 3);
		
		if ("005".equals(zip) || (("100".compareTo(zip) <= 0) && ("149".compareTo(zip) >= 0)))
			return "NY";
		
		if ("006".equals(zip) || "007".equals(zip) || "009".equals(zip))
			return "PR";
		
		if ("008".equals(zip))
			return "VI";
		
		if ("055".equals(zip) || (("010".compareTo(zip) <= 0) && ("027".compareTo(zip) >= 0)))
			return "MA";
		
		if ("028".equals(zip) || "029".equals(zip))
			return "RI";
		
		if ((("030".compareTo(zip) <= 0) && ("038".compareTo(zip) >= 0)))
			return "NH";
		
		if ((("039".compareTo(zip) <= 0) && ("049".compareTo(zip) >= 0)))
			return "ME";
		
		if ((("050".compareTo(zip) <= 0) && ("059".compareTo(zip) >= 0)))
			return "VT";
		
		if ((("060".compareTo(zip) <= 0) && ("069".compareTo(zip) >= 0)))
			return "CT";
		
		if ((("070".compareTo(zip) <= 0) && ("089".compareTo(zip) >= 0)))
			return "NJ";
		
		if ((("090".compareTo(zip) <= 0) && ("098".compareTo(zip) >= 0)))
			return "AE";
		
		if ((("150".compareTo(zip) <= 0) && ("196".compareTo(zip) >= 0)))
			return "PA";
		
		if ((("197".compareTo(zip) <= 0) && ("199".compareTo(zip) >= 0)))
			return "DE";
		
		if ("200".equals(zip) || "569".equals(zip) || (("202".compareTo(zip) <= 0) && ("205".compareTo(zip) >= 0)))
			return "DC";
		
		if ("201".equals(zip) || (("220".compareTo(zip) <= 0) && ("246".compareTo(zip) >= 0)))
			return "VA";
		
		if ((("206".compareTo(zip) <= 0) && ("212".compareTo(zip) >= 0)))
			return "MD";
		
		// and
		if ((("214".compareTo(zip) <= 0) && ("219".compareTo(zip) >= 0)))
			return "MD";
		
		if ((("247".compareTo(zip) <= 0) && ("268".compareTo(zip) >= 0)))
			return "WV";
		
		if ((("270".compareTo(zip) <= 0) && ("289".compareTo(zip) >= 0)))
			return "NC";
		
		if ((("290".compareTo(zip) <= 0) && ("299".compareTo(zip) >= 0)))
			return "SC";
		
		if ((("300".compareTo(zip) <= 0) && ("319".compareTo(zip) >= 0)))
			return "GA";
		
		// and
		if ((("398".compareTo(zip) <= 0) && ("399".compareTo(zip) >= 0)))
			return "GA";
		
		if ((("320".compareTo(zip) <= 0) && ("339".compareTo(zip) >= 0)))
			return "FL";
		
		if ((("341".compareTo(zip) <= 0) && ("342".compareTo(zip) >= 0)))
			return "FL";
		
		if ("344".equals(zip) || "346".equals(zip) || "347".equals(zip) || "349".equals(zip))
			return "FL";
		
		if ("340".equals(zip))
			return "AA";
		
		if ((("350".compareTo(zip) <= 0) && ("352".compareTo(zip) >= 0)))
			return "AL";
		
		if ((("354".compareTo(zip) <= 0) && ("369".compareTo(zip) >= 0)))
			return "AL";
		
		if ((("370".compareTo(zip) <= 0) && ("385".compareTo(zip) >= 0)))
			return "TN";
		
		if ((("386".compareTo(zip) <= 0) && ("397".compareTo(zip) >= 0)))
			return "MS";
		
		if ((("400".compareTo(zip) <= 0) && ("418".compareTo(zip) >= 0)))
			return "KY";
		
		if ((("420".compareTo(zip) <= 0) && ("427".compareTo(zip) >= 0)))
			return "KY";
		
		if ((("430".compareTo(zip) <= 0) && ("459".compareTo(zip) >= 0)))
			return "OH";
		
		if ((("460".compareTo(zip) <= 0) && ("479".compareTo(zip) >= 0)))
			return "IN";
		
		if ((("480".compareTo(zip) <= 0) && ("499".compareTo(zip) >= 0)))
			return "MI";
		
		if ((("500".compareTo(zip) <= 0) && ("516".compareTo(zip) >= 0)))
			return "IA";
		
		if ((("520".compareTo(zip) <= 0) && ("528".compareTo(zip) >= 0)))
			return "IA";
		
		if ((("530".compareTo(zip) <= 0) && ("532".compareTo(zip) >= 0)))
			return "WI";
		
		if ((("534".compareTo(zip) <= 0) && ("535".compareTo(zip) >= 0)))
			return "WI";
		
		if ((("537".compareTo(zip) <= 0) && ("549".compareTo(zip) >= 0)))
			return "WI";
		
		if ((("550".compareTo(zip) <= 0) && ("567".compareTo(zip) >= 0)))
			return "MN";
		
		if ((("570".compareTo(zip) <= 0) && ("577".compareTo(zip) >= 0)))
			return "SD";
		
		if ((("580".compareTo(zip) <= 0) && ("588".compareTo(zip) >= 0)))
			return "ND";
		
		if ((("590".compareTo(zip) <= 0) && ("599".compareTo(zip) >= 0)))
			return "MT";
		
		if ((("600".compareTo(zip) <= 0) && ("629".compareTo(zip) >= 0)))
			return "IL";
		
		if ((("630".compareTo(zip) <= 0) && ("658".compareTo(zip) >= 0)))
			return "MO";
		
		if ((("660".compareTo(zip) <= 0) && ("679".compareTo(zip) >= 0)))
			return "KS";
		
		if ((("680".compareTo(zip) <= 0) && ("693".compareTo(zip) >= 0)))
			return "NE";
		
		if ((("700".compareTo(zip) <= 0) && ("714".compareTo(zip) >= 0)))
			return "LA";
		
		if ((("716".compareTo(zip) <= 0) && ("729".compareTo(zip) >= 0)))
			return "AR";
		
		if ("733".equals(zip) || "885".equals(zip))
			return "TX";
		
		if ((("730".compareTo(zip) <= 0) && ("749".compareTo(zip) >= 0)))
			return "OK";
		
		if ((("750".compareTo(zip) <= 0) && ("799".compareTo(zip) >= 0)))
			return "TX";
		
		if ((("800".compareTo(zip) <= 0) && ("816".compareTo(zip) >= 0)))
			return "CO";
		
		if ((("820".compareTo(zip) <= 0) && ("831".compareTo(zip) >= 0)))
			return "WY";
		
		if ((("832".compareTo(zip) <= 0) && ("838".compareTo(zip) >= 0)))
			return "ID";
		
		if ((("840".compareTo(zip) <= 0) && ("847".compareTo(zip) >= 0)))
			return "UT";
		
		if ((("850".compareTo(zip) <= 0) && ("865".compareTo(zip) >= 0)))
			return "UT";
		
		if ((("870".compareTo(zip) <= 0) && ("884".compareTo(zip) >= 0)))
			return "NM";
		
		if ((("889".compareTo(zip) <= 0) && ("898".compareTo(zip) >= 0)))
			return "NV";
		
		if ((("900".compareTo(zip) <= 0) && ("961".compareTo(zip) >= 0)))
			return "CA";
		
		if ((("962".compareTo(zip) <= 0) && ("966".compareTo(zip) >= 0)))
			return "AP";
		
		if ((("967".compareTo(zip) <= 0) && ("968".compareTo(zip) >= 0)))
			return "HI";
		
		if ("969".equals(zip))
			return "GU";
		
		if ((("970".compareTo(zip) <= 0) && ("979".compareTo(zip) >= 0)))
			return "OR";
		
		if ((("980".compareTo(zip) <= 0) && ("994".compareTo(zip) >= 0)))
			return "WA";
		
		if ((("995".compareTo(zip) <= 0) && ("999".compareTo(zip) >= 0)))
			return "AK";
		
		return null;
	}
	
	/*::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
	/*::                                                                         :*/
	/*::  This routine calculates the distance between two points (given the     :*/
	/*::  latitude/longitude of those points). It is being used to calculate     :*/
	/*::  the distance between two locations using GeoDataSource (TM) prodducts  :*/
	/*::                                                                         :*/
	/*::  Definitions:                                                           :*/
	/*::    South latitudes are negative, east longitudes are positive           :*/
	/*::                                                                         :*/
	/*::  Passed to function:                                                    :*/
	/*::    lat1, lon1 = Latitude and Longitude of point 1 (in decimal degrees)  :*/
	/*::    lat2, lon2 = Latitude and Longitude of point 2 (in decimal degrees)  :*/
	/*::    unit = the unit you desire for results                               :*/
	/*::           where: 'M' is statute miles (default)                         :*/
	/*::                  'K' is kilometers                                      :*/
	/*::                  'N' is nautical miles                                  :*/
	/*::  Worldwide cities and other features databases with latitude longitude  :*/
	/*::  are available at http://www.geodatasource.com                          :*/
	/*::                                                                         :*/
	/*::  For enquiries, please contact sales@geodatasource.com                  :*/
	/*::                                                                         :*/
	/*::  Official Web site: http://www.geodatasource.com                        :*/
	/*::                                                                         :*/
	/*::           GeoDataSource.com (C) All Rights Reserved 2015                :*/
	/*::                                                                         :*/
	/*::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
	static public double distance(String coord1, String coord2, char unit) {
		String c1[] = coord1.split(",");
		String c2[] = coord2.split(",");
		
		return distance(Double.parseDouble(c1[0]), Double.parseDouble(c1[1]), Double.parseDouble(c2[0]), Double.parseDouble(c2[1]), unit);
	}
	
	static public double distance(double lat1, double lon1, double lat2, double lon2, char unit) {
		double theta = lon1 - lon2;
		double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2))
				+ Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2))
				* Math.cos(deg2rad(theta));
		
		dist = Math.acos(dist);
		dist = rad2deg(dist);
		dist = dist * 60 * 1.1515;
		
		if (unit == 'K')
			dist = dist * 1.609344;
		else if (unit == 'N')
			dist = dist * 0.8684;
		
		return dist;
	}
	
	/*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
	/*::  This function converts decimal degrees to radians             :*/
	/*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
	static public double deg2rad(double deg) {
		return (deg * Math.PI / 180.0);
	}
	
	/*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
	/*::  This function converts radians to decimal degrees             :*/
	/*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
	static public double rad2deg(double rad) {
		return (rad * 180 / Math.PI);
	}
	
	static public boolean geocodeEnabled() {
		XElement gsettings = ApplicationHub.getCatalogSettings("Map-Service", null);

		if (gsettings == null)
			return false;

		if (gsettings.hasEmptyAttribute("GeocodeKey"))
			return false;

		return true;
	}
	
	static public String getLatLong(String address, String city, String state, String zip) {
		XElement gsettings = ApplicationHub.getCatalogSettings("Map-Service", null);
		
		if (gsettings == null) {
			Logger.error("Missing map settings.");
			return null;
		}
		
		String key = gsettings.attr("GeocodeKey");
		
		if (StringUtil.isEmpty(key)) {
			Logger.error("Missing Geocode Key settings.");
			return null;
		}
		
		return MapUtil.getLatLong(address, city, state, zip, key);
	}

	static public String getLatLong(String address) {
		XElement gsettings = ApplicationHub.getCatalogSettings("Map-Service", null);

		if (gsettings == null) {
			Logger.error("Missing map settings.");
			return null;
		}

		String key = gsettings.attr("GeocodeKey");

		if (StringUtil.isEmpty(key)) {
			Logger.error("Missing Geocode Key settings.");
			return null;
		}

		return MapUtil.getLatLong(address, key);
	}

	static public String getLatLong(String address, String city, String state, String zip, String key) {
		RecordStruct gres = MapUtil.getGeoCode(address, city, state, zip, key);
		
		if (gres == null)
			return null;
		
		//System.out.println("a: " + gres.getFieldAsString("formatted_address"));
		
		RecordStruct locrec = gres.getFieldAsRecord("geometry").getFieldAsRecord("location");
		
		return locrec.getFieldAsString("lat") + "," + locrec.getFieldAsString("lng");
	}

	static public String getLatLong(String address, String key) {
		RecordStruct gres = MapUtil.getGeoCode(address, key);

		if (gres == null)
			return null;

		//System.out.println("a: " + gres.getFieldAsString("formatted_address"));

		RecordStruct locrec = gres.getFieldAsRecord("geometry").getFieldAsRecord("location");

		return locrec.getFieldAsString("lat") + "," + locrec.getFieldAsString("lng");
	}

	static public RecordStruct getGeoCode(String address, String city, String state, String zip, String key) {
		return MapUtil.getGeoCode(address +  ", " + city + ", " + state + ", " + zip, key);
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
