/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.cms.store;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.BaseService;
import dcraft.service.ServiceRequest;
import dcraft.struct.RecordStruct;

public class Service extends BaseService {
	@Override
	public void start() {
		super.start();
	}

	@Override
	public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		String feature = request.getFeature();

		// =========================================================
		//  store categories
		// =========================================================
		
		if ("Category".equals(feature))
			return Products.handleCategories(request, callback);

		// =========================================================
		//  store products
		// =========================================================
		
		if ("Product".equals(feature))
			return Products.handleProducts(request, callback);

		// =========================================================
		//  store coupons
		// =========================================================
		
		if ("Coupons".equals(feature))
			return Products.handleCoupons(request, callback);
		
		// =========================================================
		//  store orders
		// =========================================================
		
		if ("Orders".equals(feature))
			return Orders.handle(request, callback);
		
		// =========================================================
		//  gift registry
		// =========================================================
		
		if ("GiftRegistry".equals(feature))
			return GiftRegistry.handle(request, callback);

		return false;
	}
}
