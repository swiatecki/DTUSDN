package net.floodlightcontroller.custom.ubergateway;

import java.util.ArrayList;
import java.util.List;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class UberGatewayService extends ServerResource {

	@Get("json")
	public List<String> retrieve() {
		// IPktinHistoryService pihr = (IPktinHistoryService)
		// getContext().getAttributes().get(IPktinHistoryService.class.getCanonicalName());
		// List<SwitchMessagePair> l = new ArrayList<SwitchMessagePair>();
		List<String> l = new ArrayList<String>();
		// l.addAll(java.util.Arrays.asList(pihr.getBuffer().snapshot()));

		IUberGatewayService ugws = (IUberGatewayService) getContext().getAttributes().get(IUberGatewayService.class.getCanonicalName());

		l.add(ugws.getStatus());

		return l;
	}
}
