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
import dcraft.service.BaseDataService;
import dcraft.service.BaseService;
import dcraft.service.ServiceRequest;
import dcraft.struct.RecordStruct;

public class Service extends BaseDataService {
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
		
		if ("Product".equals(feature)) {
			if (Products.handleProducts(request, callback))
				return true;

			return super.handle(request, callback);
		}

		// =========================================================
		//  store coupons
		// =========================================================
		
		if ("Discounts".equals(feature))
			return super.handle(request, callback);

		// =========================================================
		//  store orders
		// =========================================================
		
		if ("Orders".equals(feature)) {
			if (Orders.handle(request, callback))
				return true;

			return super.handle(request, callback);
		}

		// =========================================================
		//  payment handler
		// =========================================================

		if ("Payment".equals(feature)) {
			return super.handle(request, callback);
		}

		// =========================================================
		//  gift registry
		// =========================================================
		
		if ("GiftRegistry".equals(feature))
			return GiftRegistry.handle(request, callback);

		return false;
	}
}
