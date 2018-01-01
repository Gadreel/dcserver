package dcraft.example;

import java.time.ZonedDateTime;

import dcraft.hub.op.IOperationLogger;
import dcraft.hub.op.ObserverState;
import dcraft.hub.op.OperationConstants;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationEvent;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

public class CallFour {
	/*
	 * This expands on CallThree by adding OperationContext logging. 
	 */
	public static void main(String... args) throws Exception {
		/*
		 * Anonymous classes are quite common in Java so you probably have some
		 * exposure to them. We'll need to use them extensively in coming 
		 * examples. This example provides some introduction but is not a complete
		 * lesson on anonymous classes. If you have no background with using 
		 * anonymous classes you may wish to review other resources on the topic.
		 * 
		 * IF you are looking for a review, perhaps start here:
		 * https://docs.oracle.com/javase/tutorial/java/javaOO/anonymousclasses.html
		 * 
		 * And if you need a review on Java classes, start at the top:
		 * https://docs.oracle.com/javase/tutorial/java/javaOO/index.html
		 * 
		 * However, you may pick it up just by seeing a few examples here. The first
		 * example shows how to make a custom logger that generates a log file just for
		 * your OperationContext and that is separate from the system logger. Such a
		 * log file might be useful for debugging a function call without having to hunt
		 * through system logs. Or such a file might be a requirement for compliance
		 * with a requirement for your task.
		 * 
		 * Right now though, more important than the "why" have a custom log is the 
		 * "how to" have a custom log because it will get us thinking about anonymous classes
		 * and we'll need anonymous classes for the next part of the discussion on 
		 * function calls and service calls.
		 * 
		 * First we need the OperationContext for the custom logger. As with CallThree
		 * we'll hard code the sign in:
		 */
		
		UserContext currusr = UserContext.rootUser();
		
		OperationContext opctx = OperationContext.context(currusr);
		
		OperationContext.set(opctx);
		
		/*
		 * OK, we are signed in. Before we do anything else let's get the logger
		 * setup so we can log all the activity in the context.
		 * 
		 * Create an instance of IOperationLogger inside the code - this is that
		 * anonymous classes. It has methods, member variables and most of the
		 * features of a regular class but doesn't have a formal "class" definition.
		 */
		
		IOperationLogger customlogger = new IOperationLogger() {
			protected StringBuilder log = new StringBuilder();
			
			@Override
			public void init(OperationContext ctx) {
				// we don't need to do any initialization for this example
				System.out.println("custom logger initiated");
			}
			
			/*
			 * This is called when log events occur on the OperationContext.
			 */
			@Override
			public ObserverState fireEvent(OperationContext ctx, OperationEvent event, Object detail) {
				if (event == OperationConstants.LOG) {
					// log detail is always a record, it is safe to cast 
					RecordStruct entry = (RecordStruct) detail;
					
					String msg = entry.getFieldAsString("Message");
					
					// filter empty messages
					if (StringUtil.isEmpty(msg))
						return ObserverState.Continue;
					
					ZonedDateTime occured = entry.getFieldAsDateTime("Occurred");
					
					// format typical for US Central time
					String time = TimeUtil.fmtDateTimeLong(occured, "America/Chicago", "en-US");
					
					time = StringUtil.alignLeft(time, ' ', 35);
					
					String lvl = entry.getFieldAsString("Level");
					
					lvl = StringUtil.alignLeft(lvl, ' ', 6);
					
					String code = entry.getFieldAsString("Code");
					
					code = StringUtil.alignLeft(code, ' ', 5);
					
					// format and add the log entry 
					this.log.append(time + " " + lvl + code + msg);
					this.log.append('\n');
					
					System.out.println("Custom log entry created");
				}
				
				return ObserverState.Continue;
			}
			
			@Override
			public String logToString() {
				return this.log.toString();
			}
		};
		
		/*
		 * Add the custom logger to the OperationContext and now it is active, all
		 * Logger activity after this will get in our custom log.
		 */
		opctx.getController().addObserver(customlogger);
		
		/*
		 * A marker whose scope is isolated to Harold.
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
		
		System.out.println("Here is the output from the custom logger:");
		System.out.print(customlogger.logToString());
		
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
