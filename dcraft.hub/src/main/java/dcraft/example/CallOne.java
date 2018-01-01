package dcraft.example;

import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public class CallOne {
	/*
	 * Next we'll learn to call methods (functions) and services within dcServer. Calling 
	 * methods and services let's you use features already available such as reading a social media
	 * feed, loading data from the database or updating a web page within your code.
	 * 
	 * dcServer has a standard approach for calling functions and services, which is what we will cover in 
	 * this series of examples. In most cases calls to functions and services will return a value 
	 * (often as a RecordStruct or ListStruct) if it succeeds and an empty value (null) if it does not. 
	 * If there is no value to return then the function will simply return true if it completed and
	 * false if it failed.
	 * 
	 * We'll start with functions but the same ideas will apply when calling services. 
	 * 
	 */
	public static void main(String... args) throws Exception {
		/*
		 * We are building up an example of querying data from some source - perhaps
		 * a database or a web service. In some ways it doesn't matter, the function you
		 * call does the work - you get the results.
		 * 
		 * For this first function call we pass in the name of a chef and get back
		 * a list of all the ratings given to dishes prepared by that chef.
		 */
		ListStruct haroldratings = lookupChefRatings("Harold");
		
		System.out.println("Here are the ratings for Harold:");
		System.out.println();
		
		if (haroldratings != null)	
			System.out.println(haroldratings.toPrettyString());
		else
			System.out.println("No ratings found for Harold");
		
		System.out.println();
		System.out.println();
		System.out.println("Press Enter key to continue");
		System.out.println();
		
		System.in.read();
		
		/*
		 * Now scroll down and look at the function we called, read it and come back.
		 * 
		 * Let's format that dota a little bit, rather than dumping plain JSON.
		 */
		
		System.out.println("Here are the ratings for Harold:");
		System.out.println();
		
		if (haroldratings != null) {
			for (int n = 0; n < haroldratings.size(); n++) {
				RecordStruct ratingrec = haroldratings.getItemAsRecord(n);
				
				System.out.println(ratingrec.getFieldAsString("Rating") + " for " + 
						ratingrec.getFieldAsString("Dish") + " by " + ratingrec.getFieldAsString("Judge"));
			}
		}
		else {
			System.out.println("No ratings found for Harold");
		}
		
		System.out.println();
		System.out.println();
		System.out.println("Press Enter key to continue");
		System.out.println();
		
		System.in.read();
		
		/*
		 * When we try to load data for Barry we get no data back,
		 * barryratings is null. This is a typical approach in dcServer, if the
		 * data or calculation requested of a function cannot return valid
		 * data it just returns nothing. Which very often is just fine,
		 * especially once we start to see how the error logging works.
		 */
		
		ListStruct barryratings = lookupChefRatings("Barry");
		
		System.out.println("Here are the ratings for Barry:");
		System.out.println();
		
		if (barryratings != null)	
			System.out.println(barryratings.toPrettyString());
		else
			System.out.println("No ratings found for Barry");
		
		System.out.println();
		System.out.println();
		System.out.println("Press Enter key to continue");
		System.out.println();
		
		System.in.read();
		
		System.out.println();
		System.out.println("Example done, proceed to next example.");
		System.out.println();
	}
	
	/*
	 * This function returns a list of ratings for dishes prepared by the chef
	 * name given in the parameter.
	 * 
	 * This function could have loaded data from a database or from a web service,
	 * but to keep it simple and focused we have a hard coded data set. Only two 
	 * chefs are available in that set.
	 */
	static public ListStruct lookupChefRatings(String chef) {
		if (chef.equals("Harold"))
			return ListStruct.list( 
					RecordStruct.record()
						.with("Dish", "Mac and Cheese")
				     	.with("Judge", "Janet")
				     	.with("Rating", 7.0),
					RecordStruct.record()
						.with("Dish", "Cheesy Potatoes")
				     	.with("Judge", "Ernie")
				     	.with("Rating", 4.5)
			 );
		
		if (chef.equals("Wanda"))
			return ListStruct.list( 
					RecordStruct.record()
						.with("Dish", "Rice Pilaf")
				     	.with("Judge", "Mike")
				     	.with("Rating", 8.5),
					RecordStruct.record()
						.with("Dish", "Ginger Carrot Soup")
				     	.with("Judge", "Ginny")
				     	.with("Rating", 9.0)
			 );
		
		return null;
	}
}
