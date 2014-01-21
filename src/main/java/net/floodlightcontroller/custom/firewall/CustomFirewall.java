package net.floodlightcontroller.custom.firewall;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.packet.IPv4;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.action.OFAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomFirewall implements IOFMessageListener, IFloodlightModule {

	protected IFloodlightProviderService floodlightProvider;
	protected static Logger logger;
	private ArrayList<CustomFirewallRule> rules;
	public static final int FORWARDING_APP_ID = 2;

	// **************************************
	// *******IOFMessageListener*************
	// **************************************

	// return the name of the class to FL
	@Override
	public String getName() {
		return CustomFirewall.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return (type.equals(OFType.PACKET_IN) && (name.equals("forwarding")));

	}

	// the processing of PACKET_IN messages is done in this method
	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

		// convert msg to OFPacketIn message type
		OFPacketIn pi = (OFPacketIn) msg;

		// create a match object
		OFMatch match = new OFMatch();

		// load match object from the packet_in
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		// get IP src and dst
		String ips = IPv4.fromIPv4Address(match.getNetworkSource());
		String ipd = IPv4.fromIPv4Address(match.getNetworkDestination());

		// if there is a rule with these IP addresses stop the event propagation
		// if not, then continue
		if (packetMatchesFirewallRule(ips, ipd)) {
			// installDroppingFlow(sw, pi, match);
			return Command.STOP;
		} else {
			return Command.CONTINUE;
		}
	}

	// ****************************************
	// ********* helper methods**************
	// ****************************************

	private void installDroppingFlow(IOFSwitch sw, OFPacketIn pi, OFMatch match) {
		logger.info("######## Begin Install Dropping Flow !!! #########");
		// this method install a dropping flow with priority > 0 and timer = 0

		// create a cookie to put inside the flows
		long cookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);

		// Create flow-mod one for IP another for ARP
		OFFlowMod fmIp = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		OFFlowMod fmArp = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		// create an arraylist of actions, no actions means dropping
		List<OFAction> actions = new ArrayList<OFAction>();

		OFMatch matchIp = new OFMatch();
		matchIp.setWildcards(Wildcards.FULL.getInt() & ~OFMatch.OFPFW_DL_TYPE & ~OFMatch.OFPFW_NW_SRC_ALL
				& ~OFMatch.OFPFW_NW_DST_ALL);
		matchIp.setDataLayerType((short) 2048).setNetworkSource(match.getNetworkSource())
				.setNetworkDestination(match.getNetworkDestination());

		OFMatch matchArp = new OFMatch();
		matchArp = matchIp.clone();
		matchArp.setDataLayerType((short) 2054);

		// set the empty list of actions for the two flow mods
		fmArp.setActions(actions);
		fmIp.setActions(actions);

		// set the rest of the fields for the two flow mods
		fmArp.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE).setMatch(matchArp).setLengthU(OFFlowMod.MINIMUM_LENGTH);
		fmArp.setPriority((short) 100).setCommand(OFFlowMod.OFPFC_ADD);

		fmIp.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE).setMatch(matchIp).setLengthU(OFFlowMod.MINIMUM_LENGTH);
		fmIp.setPriority((short) 100).setCommand(OFFlowMod.OFPFC_ADD);

		// Push rule to the switch.
		pushMessage(sw, fmArp);
		pushMessage(sw, fmIp);
	}

	private void pushMessage(IOFSwitch sw, OFFlowMod fm) {

		try {
			sw.write(fm, null);
			// sw.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		logger.info("######## End Install Flow !!! #########");

	}

	private boolean packetMatchesFirewallRule(String netSource, String netDest) {
		for (CustomFirewallRule rule : rules) {
			if (rule.matches(netSource, netDest))
				return true;
		}
		return false;
	}

	// ***************************************
	// *********IFloodlightModule*************
	// ***************************************

	// this method is used only when the module provides web services
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		logger = LoggerFactory.getLogger(CustomFirewall.class);

	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

		// create firewall rules to be applied
		rules = new ArrayList<CustomFirewallRule>();
		rules.add(new CustomFirewallRule("10.0.0.1", "10.0.0.4"));
	}

}
