package dcraft.example;

import java.math.BigDecimal;

import dcraft.xml.XElement;

public class IntroTwo {
	/*
	 * IntroTwo
	 * 
	 * Here we see how XML is handled in desginCraft Server. XML is not nearly as widely used in
	 * dc as JSON is. XML is mostly used in settings files and in web page templates. Still it is
	 * very important to know.
	 * 
	 * The following demonstrates how dc handles XML. For the uses cases above dc's functions are
	 * useful. If you are processing large amounts of XML or need special XML features consider 
	 * another XML library.
	 * 
	 */
	public static void main(String... args) throws Exception {
		/*
		 * Here is a list of users in XML:
		 * 
		 *	<Users>
		 *	    <User Username="gholmes" First="Ginny" Last="Holmes" Password="time123" />
		 *	    <User Username="mholmes" First="Mike" Last="Holmes" Password="time234" />
		 *	    <User Username="jolson" First="Janet" Last="Olson" Password="time345" />
		 *	</Users>
		 * 
		 * How would we build this XML in dc? 
		 * 
		 */
		
		XElement users = XElement.tag("Users").with(
			XElement.tag("User")
             	.withAttribute("Username", "gholmes")
             	.withAttribute("First", "Ginny")
             	.withAttribute("Last", "Holmes")
             	.withAttribute("Password", "time123"),		// note comma here
			XElement.tag("User")
             	.withAttribute("Username", "mholmes")
             	.withAttribute("First", "Mike")
             	.withAttribute("Last", "Holmes")
             	.withAttribute("Password", "time234"),		// and comma here, they separates the records - three records in the list
			XElement.tag("User")
             	.withAttribute("Username", "jolson")
             	.withAttribute("First", "Janet")
             	.withAttribute("Last", "Olson")
             	.withAttribute("Password", "time345")
		);
		
		/*
		 * XML is really characters, not the above objects. How do we see the XML these objects represent in dc?
		 */
		
		System.out.println("Here is the user list:");
		System.out.println();
		
		System.out.println(users.toPrettyString());
		
		System.out.println();
		System.out.println();
		System.out.println("Press Enter key to continue");
		System.out.println();
		
		System.in.read();
		
		/*
		 * To add a user to the list like this:
		 * 
		 */
		
		users.with( 
			XElement.tag("User")
	         	.withAttribute("Username", "mspooner")
	         	.withAttribute("First", "Mary")
	         	.withAttribute("Last", "Spooner")
	         	.withAttribute("Password", "time456")
		);
		
		/*
		 * or like this - step by step:
		 */
		XElement user5 = XElement.tag("User");
		
		user5.withAttribute("Username", "ewalts");
		user5.withAttribute("First", "Ernie");
		user5.withAttribute("Last", "Walts");
		user5.withAttribute("Password", "time567");
		
		users.with(user5);
		
		/*
		 * Let's print the updated list
		 */
		
		System.out.println();
		System.out.println();
		System.out.println("Here is the updated user list:");
		System.out.println();
		
		System.out.println(users.toPrettyString());
		
		System.out.println();
		System.out.println();
		System.out.println("Press Enter key to continue");
		System.out.println();
		
		System.in.read();
		
		/*
		 * XML may contain text content like this.
		 * 
		 * <Copy>Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do 
		 * eiusmod tempor incididunt ut labore et dolore magna aliqua. </Copy>
		 * 
		 * in dc:
		 * 
		 */
		
		XElement copy = XElement.tag("Copy")
				.withText("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. ");
		
		System.out.println();
		System.out.println();
		System.out.println("Here is the copy example:");
		System.out.println();
		
		System.out.println(copy.toPrettyString());
		
		System.out.println();
		System.out.println();
		System.out.println("Press Enter key to continue");
		System.out.println();
		
		System.in.read();
		
		/*
		 * Here is a list in a list:
		 * 
		 * <list> 
		 * 		<a /> 
		 * 		<list> 
		 * 			<x />
		 * 			<y />
		 * 			<z />
		 * 		</list>
		 * 		<c />
		 * </list>
		 * 
		 * In dc:
		 * 
		 */
		
		XElement listlist = XElement.tag("list").with(
			XElement.tag("a"),
			XElement.tag("list").with(
				XElement.tag("x"),
				XElement.tag("y"),
				XElement.tag("z")
			),
			XElement.tag("c")
		);
		
		System.out.println();
		System.out.println();
		System.out.println("Here is the list in a list:");
		System.out.println();
		
		System.out.println(listlist.toPrettyString());
		
		System.out.println();
		System.out.println();
		System.out.println("Press Enter key to continue");
		System.out.println();
		
		System.in.read();
		
		/*
		 * Add more complexity to the XML. 
		 * 
		 * <record Name="List List" Rating="7.5" Active="true">
		 *		 <list> 
		 * 			<a /> 
		 * 			<list> 
		 * 				<x />
		 * 				<y />
		 * 				<z />
		 * 			</list>
		 * 			<c />
		 * 		</list>
		 * </record>
		 * 
		 */
		
		XElement reclist = XElement.tag("record")
				.withAttribute("Name", "List List")
				.with(listlist)
				.withAttribute("Rating", "7.5")
				.withAttribute("Active", "true");
		
		System.out.println();
		System.out.println();
		System.out.println("Here is the list in a record (so to speak), plus some value types:");
		System.out.println();
		
		System.out.println(reclist.toPrettyString());
		
		System.out.println();
		System.out.println();
		System.out.println("Press Enter key to continue");
		System.out.println();
		
		System.in.read();
		
		/*
		 * Reading values from objects and arrays in JavaScript is so easy. Unfortunately 
		 * it is more effort in dc. It'll be worth it in the long run.
		 * 
		 * Let's get the Name from that record:
		 */
		
		String recname = reclist.getAttribute("Name");
		
		System.out.println();
		System.out.println("Record's Name: " + recname);
		
		/*
		 * How about the rating
		 */
		
		BigDecimal rating = reclist.getAttributeAsDecimal("Rating");
		
		BigDecimal newrating = rating		// add two to the rate
				.add(BigDecimal.ONE)
				.add(BigDecimal.ONE);
		
		System.out.println("Record's Rating (adjusted): " + newrating);
		
		/*
		 * Or the Active field?
		 */
		
		Boolean recactive = reclist.getAttributeAsBoolean("Active");
		
		if (recactive)
			System.out.println("Record is active");
		else
			System.out.println("Record is not active");
		
		/*
		 * Get a value from list.
		 */
		
		String value1 = reclist.find("list").getChildAsElement(2).toString();	// get the third item, should be "c"
		
		System.out.println("Record's Values list: " + value1);
		
		/*
		 * Get a value from list's list.
		 */
		
		String value2 = reclist.find("list").getChildAsElement(1).getChildAsElement(0)
				.toString();	// get the first inner list item, should be "x"
		
		System.out.println("Record's Values inner list: " + value2);
		
		/*
		 * What we see from these final examples is you have to know what data type you want
		 * when getting values from an attribute. Fortunately it will convert for you. If
		 * 
		 * Age="75"
		 * 
		 * .getAttributeAsInteger("Age")
		 * 
		 * will automatically see that as a number not as a string. So you don't have to know how the
		 * was added, you just have to know what type of data you want out of an attribute.
		 * 
		 * That covers the basic elements of how to work with XML like data in dc.
		 * 
		 */
		
		System.out.println();
		System.out.println("Example done, proceed to next example.");
		System.out.println();
		
		
	}
}
