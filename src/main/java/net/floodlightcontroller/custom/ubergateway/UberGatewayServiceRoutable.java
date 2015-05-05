package net.floodlightcontroller.custom.ubergateway;

import net.floodlightcontroller.restserver.RestletRoutable;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class UberGatewayServiceRoutable implements RestletRoutable {

	@Override
	public Restlet getRestlet(Context context) {

		Router router = new Router(context);
		router.attach("/status/json", UberGatewayService.class);
		return router;
	}

	@Override
	public String basePath() {

		return "/wm/ubergateway";
	}

}
