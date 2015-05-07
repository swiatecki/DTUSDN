package net.floodlightcontroller.custom.ubergateway;

import java.util.ArrayList;
import java.util.List;

import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class UberGatewayService extends ServerResource {

	@Get("json")
	public List<UberGatewayRule> retrieve() {
		// IPktinHistoryService pihr = (IPktinHistoryService)
		// getContext().getAttributes().get(IPktinHistoryService.class.getCanonicalName());
		// List<SwitchMessagePair> l = new ArrayList<SwitchMessagePair>();
		List<UberGatewayRule> l = new ArrayList<UberGatewayRule>();

		// ArrayList<UberGatewayRule>
		// l.addAll(java.util.Arrays.asList(pihr.getBuffer().snapshot()));

		IUberGatewayService ugws = (IUberGatewayService) getContext().getAttributes().get(IUberGatewayService.class.getCanonicalName());

		l.addAll(ugws.getRules());

		return l;
	}

	@Post("json")
	public void receiveRule(UberGatewayRule in) {
		IUberGatewayService ugws = (IUberGatewayService) getContext().getAttributes().get(IUberGatewayService.class.getCanonicalName());

		ugws.addRule(in);
	}

}
