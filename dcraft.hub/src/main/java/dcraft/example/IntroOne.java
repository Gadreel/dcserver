package dcraft.example;

import java.math.BigDecimal;

import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public class IntroOne {
	/*
	 * Prerequisites for learning to code desginCraft Server (dc).
	 * 
	 * - familiar with basic coding such as variables, loops, methods/functions and objects
	 * - are comfortable with JSON as presented here http://json.org/ (if you know JavaScript objects you are 95% there)
	 * - are comfortable with basic XML (if you know HTML5 syntax then you are 90% there, just make sure to end your tags)
	 * - some idea of what a thread is will be helpful, but not critical
	 * 
	 * IntroOne
	 * 
	 * Here we see how JSON is handled in desginCraft Server. We use JSON like data structures all through out the code
	 * so understanding this code will be critical to working with dc.
	 * 
	 * Java is a strict language structure - especially before Java 8 - it isn't as easy as to create or access JSON 
	 * structures as it is in JavaScript. In Java the words Array and Object are used all over the place and don't 
	 * mean JSON array or JSON object. So we have another name JSON array we call ListStruct and JSON object we 
	 * call RecordStruct.
	 * 
	 */
	public static void main(String... args) throws Exception {
		/*
		 * Here we build a list of users in JavaScript
		 * 
		 *	var users = [
		 *	    { Username: "gholmes", First: "Ginny", Last: "Holmes", Password: "time123" },
		 *	    { Username: "mholmes", First: "Mike", Last: "Holmes", Password: "time234" },
		 *	    { Username: "jolson", First: "Janet", Last: "Olson", Password: "time345" }
		 *	]
		 * 
		 * How do this in dc?
		 * 
		 */
		
		ListStruct users = ListStruct.list(
             RecordStruct.record()
             	.with("Username", "gholmes")
             	.with("First", "Ginny")
             	.with("Last", "Holmes")
             	.with("Password", "time123"),		// note comma here
             RecordStruct.record()
             	.with("Username", "mholmes")
             	.with("First", "Mike")
             	.with("Last", "Holmes")
             	.with("Password", "time234"),		// and comma here, they separates the records - three records in the list
             RecordStruct.record()
             	.with("Username", "jolson")
             	.with("First", "Janet")
             	.with("Last", "Olson")
             	.with("Password", "time345")
         );
		
		/*
		 * Java is old and wordy, but it is pretty cool too.
		 * 
		 * So our JavaScript code isn't truly JSON yet - JSON is a string of characters and what we have are objects.
		 * In JavaScript we can turn almost any object or array into JSON simply, like this:
		 * 
			console.log(JSON.stringify(users, null, '\t'))

			[
				{
					"Username": "gholmes",
					"First": "Ginny",
					"Last": "Holmes",
					"Password": "time123"
				},
				{
					"Username": "mholmes",
					"First": "Mike",
					"Last": "Holmes",
					"Password": "time234"
				},
				{
					"Username": "jolson",
					"First": "Janet",
					"Last": "Olson",
					"Password": "time345"
				}
			]
		 * 
		 * How do this in dc?
		 * 
		 * note: System.out.println is like console.log
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
		 * In JavaScript we can add a user to the list like this:
		 * 
		 * users.push( { Username: "mspooner", First: "Mary", Last: "Spooner", Password: "time456" } )
		 * 
		 * In dc:
		 * 
		 */
		
		users.with( 
			RecordStruct.record()
	         	.with("Username", "mspooner")
	         	.with("First", "Mary")
	         	.with("Last", "Spooner")
	         	.with("Password", "time456")
		);
		
		/*
		 * or like this - step by step:
		 */
		RecordStruct user5 = RecordStruct.record();
		
		user5.with("Username", "ewalts");
		user5.with("First", "Ernie");
		user5.with("Last", "Walts");
		user5.with("Password", "time567");
		
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
		 * Lists (JSON arrays) can hold values types other than records (objects). JSON has values
		 * types: string, number, true, false and null as well as arrays and objects. Here is an example
		 * in JavaScript.
		 * 
			var values = [ "apple", "card", 271, "phone", true, "pully", null, "heart", false, 67.75 ]
			
			console.log(JSON.stringify(values, null, '\t'))
			
			[
				"apple",
				"card",
				271,
				"phone",
				true,
				"pully",
				null,
				"heart",
				false,
				67.75
			]
		 * 
		 * 
		 * same list in dc:
		 */
		
		ListStruct values = ListStruct.list("apple", "card", 271, "phone", true, 
				"pully", null, "heart", false, 67.75);
		
		System.out.println();
		System.out.println();
		System.out.println("Here is the values list:");
		System.out.println();
		
		System.out.println(values.toPrettyString());
		
		System.out.println();
		System.out.println();
		System.out.println("Press Enter key to continue");
		System.out.println();
		
		System.in.read();
		
		/*
		 * Here is a list in a list:
		 * 
		 * [ "a", [ "x", "y", "z" ], "c" ]
		 * 
		 * In dc:
		 * 
		 */
		
		ListStruct listlist = ListStruct.list("a", ListStruct.list("x", "y", "z") , "c");
		
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
		 * We already saw records in lists, lists can be in records too. 
		 * 
		 * { Name: "List List", Values: [ "a", [ "x", "y", "z" ], "c" ], Rating: 7.5, Active: true, Alias: null }
		 * 
		 */
		
		RecordStruct reclist = RecordStruct.record()
				.with("Name", "List List")
				.with("Values", listlist)
				.with("Rating", 7.5)
				.with("Active", true)
				.with("Alias", null);
		
		System.out.println();
		System.out.println();
		System.out.println("Here is the list in a record, plus some value types:");
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
		
		String recname = reclist.getFieldAsString("Name");
		
		System.out.println();
		System.out.println("Record's Name: " + recname);
		
		/*
		 * How about the rating
		 */
		
		BigDecimal rating = reclist.getFieldAsDecimal("Rating");
		
		BigDecimal newrating = rating		// add two to the rate
				.add(BigDecimal.ONE)
				.add(BigDecimal.ONE);
		
		System.out.println("Record's Rating (adjusted): " + newrating);
		
		/*
		 * Or the Active field?
		 */
		
		Boolean recactive = reclist.getFieldAsBoolean("Active");
		
		if (recactive)
			System.out.println("Record is active");
		else
			System.out.println("Record is not active");
		
		/*
		 * Get a value from list.
		 */
		
		String value1 = reclist.getFieldAsList("Values").getItemAsString(2);	// get the third item, should be "c"
		
		System.out.println("Record's Values list: " + value1);
		
		/*
		 * Get a value from list's list.
		 */
		
		String value2 = reclist.getFieldAsList("Values").getItemAsList(1)
				.getItemAsString(0);	// get the first inner list item, should be "x"
		
		System.out.println("Record's Values inner list: " + value2);
		
		/*
		 * What we see from these final examples is you have to know what data type you want
		 * when getting values from a record or a list. Fortunately it will convert for you. If
		 * the list is:
		 * 
		 * [ "true" ]
		 * 
		 * .getItemAsBoolean(0) 
		 * 
		 * will automatically see that as a boolean not as a string. Or if the record is this:
		 * 
		 * { Age: "75" }
		 * 
		 * .getFieldAsInteger("Age")
		 * 
		 * will automatically see that as a number not as a string. So you don't have to know how the
		 * was added, you just have to know what type of data you want out of the list or record.
		 * 
		 * That covers the basic elements of how to work with JSON like data in dc.
		 * 
		 */
		
		System.out.println();
		System.out.println("Example done, proceed to next example.");
		System.out.println();
	}
}
