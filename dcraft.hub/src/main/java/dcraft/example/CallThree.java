package dcraft.example;

import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public class CallThree {
	/*
	 * This expands on CallTwo by adding OperationContext. 
	 */
	public static void main(String... args) throws Exception {
		/*
		 * Most of the time you don't need to know why a function call fails,
		 * but sometimes you do. Fortunately there is a way to check logging codes
		 * related to each failure. By checking a code, or range of codes, we can detect 
		 * what went wrong with a call, even if the error occurred at levels deeper 
		 * than the function we called.
		 * 
		 * To check those codes we need two things - an OperationContext and an
		 * OperationMarker. The OperationContext sets the application user that 
		 * requested the service. Often, with websites, this will Guest User
		 * because the application user has not signed in. Both users that
		 * sign in and guest users will have an OperationContext. 
		 * 
		 * With the OperationContext we not only know who is requesting the service,
		 * we also know what their role is (Badges) and we can track all log
		 * records for that context.
		 * 
		 * We'll skip the sign in process for now and just show how to hardcode
		 * the OperationContext. First we need a record with at least 3 fields.
		 * 
		 */
		
		UserContext currusr = UserContext.rootUser();
		
		/*
		 * Now create the OperationContext object
		 */
		
		OperationContext opctx = OperationContext.context(currusr);
		
		/*
		 * And set it to be active - that is it, this is effectively what a sign in
		 * does. Sign in has been simplified for the example.
		 */
		
		OperationContext.set(opctx);
		
		/*
		 * Not especially important right now, but you can call methods on the OperationContext
		 * to get information about your current context. In fact you can collect information
		 * about the current user in any function - here is a quick way to tell if the 
		 * current user is signed in which you could use in any function or service no matter
		 * how deep in the call levels.
		 * 
		 * OperationContext.getAsTaskOrThrow().isAuthorized("User")
		 * 
		 * That may be a little advanced for now though, just note below that you can get 
		 * username, operation id and other details about the context.
		 */
		
		System.out.println("Current username: " + opctx.getUserContext().getUsername());
		System.out.println("Current operation id: " + opctx.getOpId());
		System.out.println("User is system admin (root): " + currusr.isTagged("SysAdmin"));
		
		/*
		 * OperationMarker is the other part we need to track log codes. This object creates markers
		 * into the OperationContext log that help us identify what happened during one or more 
		 * function calls (where each function call may have in turn called other functions).
		 * 
		 * It is important to isolate which methods you are marking. A way to help with the isolation 
		 * is to use the try (resource) Java feature. Like this:
		 * 
		 * try (OperationMarker om = OperationMarker.create()) {
		 * 		// your function call(s)
		 * }
		 * 
		 * The variable om will no longer be in scope after the } and so the use of OperationMarker
		 * has been isolated. Inside the try you may use method to detect if anything went wrong
		 * just during the scope of the marker.
		 * 
		 * om.hasErrors()				- just generally if there were errors or not
		 * om.hasCode(100)				- did a specific code occur
		 * om.hasCodeRange(200, 209)	- or did a range of log codes occur
		 * 
		 * First an example that has no errors. 
		 */
		try (OperationMarker om = OperationMarker.create()) {
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
			else if (om.hasCode(100)) {
				System.out.println("Harold not in database");
			}
			else if (om.hasCodeRange(200, 209)) {
				System.out.println("Harold failed name validation");
			}
			else {
				System.out.println("Unexpected error for Harold");
			}
		}
		
		System.out.println();
		System.out.println();
		System.out.println("Press Enter key to continue");
		System.out.println();
		
		System.in.read();
		
		/*
		 * We'll start another marker whose scope is isolated to Barry.
		 * 
		 * This error is an 100 series error from lookupChefRatings
		 */
		
		try (OperationMarker om = OperationMarker.create()) {
			ListStruct barryratings = lookupChefRatings("Barry");
			
			System.out.println("Here are the ratings for Barry:");
			System.out.println();
			
			if (barryratings != null) {
				System.out.println(barryratings.toPrettyString());
			}
			else if (om.hasCode(100)) {
				System.out.println("Barry not in database");
			}
			else if (om.hasCodeRange(200, 209)) {
				System.out.println("Barry failed name validation");
			}
			else {
				System.out.println("Unexpected error for Barry");
			}
		}
		
		System.out.println();
		System.out.println();
		System.out.println("Press Enter key to continue");
		System.out.println();
		
		System.in.read();
		
		/*
		 * And start another marker whose scope is isolated to $tar.
		 * 
		 * This error is a 200 series error from validChefName
		 */
		
		try (OperationMarker om = OperationMarker.create()) {
			ListStruct starratings = lookupChefRatings("$tar");
			
			System.out.println("Here are the ratings for $tar:");
			System.out.println();
			
			if (starratings != null) {
				System.out.println(starratings.toPrettyString());
			}
			else if (om.hasCode(100)) {
				System.out.println("$tar not in database");
			}
			else if (om.hasCodeRange(200, 209)) {
				System.out.println("$tar failed name validation");
			}
			else {
				System.out.println("Unexpected error for $tar");
			}
		}
		
		System.out.println();
		System.out.println();
		System.out.println("Press Enter key to continue");
		System.out.println();
		
		System.in.read();
		
		/*
		 * We can also check codes on OperationContext but OperationContext is not
		 * isolated, it collects all log records during its full life. While OperationContext
		 * usually has a short life, it is not the most effective way to check for 
		 * codes resulting from a specific call. Note how both the 1NN and 2NN
		 * range checks are positive here:
		 */

		if (opctx.getController().hasCodeRange(100, 199)) {
			System.out.println("1NN errors were encounterd within this context");
		}
		else {
			System.out.println("No 1NN errors within this context");
		}

		if (opctx.getController().hasCodeRange(200, 299)) {
			System.out.println("2NN errors were encounterd within this context");
		}
		else {
			System.out.println("No 2NN errors within this context");
		}

		if (opctx.getController().hasCodeRange(300, 399)) {
			System.out.println("3NN errors were encounterd within this context");
		}
		else {
			System.out.println("No 3NN errors within this context");
		}
		
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
		if (! validChefName(chef))	
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
		
		Logger.error(100, "Chef not found.");	
		return null;
	}
	
	/*
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
