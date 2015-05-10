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

	/*
	 * private static final String HEADERS_KEY = "org.restlet.http.headers";
	 * 
	 * static Series<Header> getMessageHeaders(Message message) {
	 * 
	 * ConcurrentMap<String, Object> attrs = message.getAttributes();
	 * 
	 * Series<Header> headers = (Series<Header>) attrs.get(HEADERS_KEY);
	 * 
	 * if (headers == null) {
	 * 
	 * headers = new Series<Header>(Header.class);
	 * 
	 * Series<Header> prev = (Series<Header>) attrs.putIfAbsent(HEADERS_KEY,
	 * headers);
	 * 
	 * if (prev != null) {
	 * headers = prev;
	 * }
	 * 
	 * }
	 * return headers;
	 * }
	 * 
	 * @Options
	 * public void doOptions(Representation entity) {
	 * 
	 * getMessageHeaders(getResponse()).add("Access-Control-Allow-Origin", "*");
	 * 
	 * getMessageHeaders(getResponse()).add("Access-Control-Allow-Methods",
	 * "POST,OPTIONS,GET");
	 * 
	 * getMessageHeaders(getResponse()).add("Access-Control-Allow-Headers",
	 * "Content-Type");
	 * 
	 * getMessageHeaders(getResponse()).add("Access-Control-Allow-Credentials",
	 * "true");
	 * 
	 * getMessageHeaders(getResponse()).add("Access-Control-Max-Age", "60");
	 * 
	 * }
	 */

}
