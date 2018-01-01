package dcraft.example;

import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public class CallTwo {
	/*
	 * This expands on CallOne by adding logging. 
	 */
	public static void main(String... args) throws Exception {
		/*
		 * Most of the time you don't need to know why a function call fails,
		 * but sometimes you do. Fortunately there is a way to get a code and message 
		 * related to each failure. That code and message comes from the logging system.
		 * 
		 * So first let's add the logging system, then in CallThree we'll see how to get the 
		 * code and message.
		 * 
		 * First an example that has no errors. But there is something new in lookupChefRatings
		 * so look at NEW comments in that method.
		 */
		ListStruct haroldratings = lookupChefRatings("Harold");
		
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
		 * After reviewing lookupChefRatings you now see that errors may occur in lookupChefRatings 
		 * or in validChefName. Those errors will show in the log file - which in this case is your 
		 * console window. 
		 * 
		 * This error is an 100 series error from lookupChefRatings
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
		
		/*
		 * This error is a 200 series error from validChefName
		 */
		
		ListStruct starratings = lookupChefRatings("$tar");
		
		System.out.println("Here are the ratings for $tar:");
		System.out.println();
		
		if (starratings != null)	
			System.out.println(starratings.toPrettyString());
		else
			System.out.println("No ratings found for $tar");
		
		System.out.println();
		System.out.println();
		System.out.println("Press Enter key to continue");
		System.out.println();
		
		System.in.read();
		
		/*
		 * While the errors are getting logged correctly,
		 * there isn't anyway we can tell what error happened
		 * as callers of function. We just say "No ratings found".
		 * Next we'll see how we can detect what went wrong with 
		 * a call, even if the error occurred at levels deeper 
		 * than the function we called.
		 */
		
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
		if (! validChefName(chef))		// NEW check that the name is valid
			return null;
		
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
		
		Logger.error(100, "Chef not found.");		// NEW log an error if a user is not found
		return null;
	}
	
	/*
	 * NEW
	 * 
	 * Check that the name being requested is a valid format.
	 * Name must not be null, must be at least 3 alphanumeric 
	 * characters. 
	 */
	static public boolean validChefName(String chef) {
		  if (chef == null) {
			  Logger.error(200, "Chef name may not be null");
			  return false;
		  }
		  
	      int strLen = chef.length();
	      
	      if (strLen < 3) {
			  Logger.error(201, "Chef name is too short. Invalid name: " + chef);
			  return false;
		  }
	      
	      for (int i = 0; i < strLen; i++) 
	          if (! Character.isLetterOrDigit(chef.charAt(i))) {
				  Logger.error(202, "Chef name must alphanumeric. Invalid name: " + chef);
	              return false;
	          }

	      return true;
	}
}
