package net.floodlightcontroller.custom.ubergateway;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionOutput;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UberGateway implements IFloodlightModule, IOFMessageListener, IUberGatewayService {

	IFloodlightProviderService provider; // For connecting to the
											// ProviderService
	IRestApiService restApi; // Connect to the REST service

	private static Logger logger;

	public static final int APP_ID = 1337;

	private static final short LAN_PORT = 1;
	private static final short WAN_PORT1 = 2;
	private static final short WAN_PORT2 = 3;

	private static final short ARP_ETHERTYPE_INT = 2054;
	private static final short IP_ETHERTYPE_INT = 2048;
	private static final String GW_MAC_LAN = "de:ad:be:ef:ba:be";
	private static final String GW_MAC_WAN = "fa:ce:b0:0c:fa:ce";
	private static final String GW_LAN_IP = "10.0.0.1";
	private static final String GW_WAN_IP = "10.0.1.1";

	private static final String MAC_LAN = "00:00:00:00:00:01";
	private static final String MAC_WAN1 = "00:00:00:00:00:02";
	private static final String MAC_WAN2 = "00:00:00:00:00:03";

	IOFSwitch masterSW;
	FloodlightContext masterCntx;

	static {
		AppCookie.registerApp(APP_ID, "uberGateway");
	}

	// Map<Long, HashMap<Long, Short>> switches = new HashMap<Long,
	// HashMap<Long, Short>>();

	ArrayList<UberGatewayRule> rules = new ArrayList<UberGatewayRule>();

	// We tell the module system that we provide the IUberGatewayService.
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IUberGatewayService.class);
		return l;
	}

	// The getServiceImpls() call tells the module system that we are the class
	// that provides the service.

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IUberGatewayService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {

		// Make our module depend on the ProviderService

		Collection<Class<? extends IFloodlightService>> l = new ArrayList<>();
		l.add(IFloodlightProviderService.class);
		l.add(IRestApiService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// Retrive the implementation of the ProviderService
		provider = context.getServiceImpl(IFloodlightProviderService.class);
		restApi = context.getServiceImpl(IRestApiService.class);

		logger = LoggerFactory.getLogger(UberGateway.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub

		System.out.println("Hello from uberGW");

		// Register to Packet_IN events
		provider.addOFMessageListener(OFType.PACKET_IN, this);

		// Add the routable
		restApi.addRestletRoutable(new UberGatewayServiceRoutable());

		// OFMatch rm = new OFMatch();
		// rm.setWildcards(Wildcards.FULL.getInt() & ~OFMatch.OFPFW_IN_PORT);
		// rm.setNetworkProtocol(UberGatewayRule.PROTOCOL_ICMP);
		// System.out.println("Initial rule is" + rm.toString());

		UberGatewayRule r = new UberGatewayRule(WAN_PORT2, "ICMP out");
		r.setNwProto(UberGatewayRule.PROTOCOL_ICMP);
		r.setDlType(Ethernet.TYPE_IPv4);
		r.setPriority((short) 1);
		rules.add(r);

		logger.debug("Rule is initially: " + r.getMatch());

		/*
		 * UberGatewayRule telnetRule = new UberGatewayRule(WAN_PORT1,
		 * "Telnet to endpointA");
		 * 
		 * telnetRule.setNwProto(UberGatewayRule.PROTOCOL_TCP);
		 * telnetRule.setDlType(UberGatewayRule.DL_TYPE_IP);
		 * 
		 * rules.add(telnetRule);
		 */

		/*
		 * UberGatewayRule transportPortRule = new UberGatewayRule(WAN_PORT2,
		 * "Test port 9090");
		 * 
		 * transportPortRule.setNwProto(UberGatewayRule.PROTOCOL_TCP);
		 * transportPortRule.setDlType(UberGatewayRule.DL_TYPE_IP);
		 * transportPortRule.setTransportDst((short) 9090);
		 * transportPortRule.setPriority((short) 5);
		 * 
		 * rules.add(transportPortRule);
		 */

		UberGatewayRule transportPortRule2 = new UberGatewayRule(WAN_PORT1, "TCP port 7070");

		transportPortRule2.setNwProto(UberGatewayRule.PROTOCOL_TCP);
		transportPortRule2.setDlType(UberGatewayRule.DL_TYPE_IP);
		transportPortRule2.setTransportDst((short) 7070);
		transportPortRule2.setPriority((short) 100);

		rules.add(transportPortRule2);

	}

	@Override
	public String getName() {
		return "UberGateway";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

		// Handles incomming packet

		Ethernet ethPayload = provider.bcStore.get(cntx, provider.CONTEXT_PI_PAYLOAD);

		OFPacketIn p = (OFPacketIn) msg;
		masterSW = sw;
		short etherType = ethPayload.getEtherType();

		if (etherType == ARP_ETHERTYPE_INT) {
			// If it's an ARP packet, then send an ARP Reply
			sendArpReply(sw, cntx, ethPayload, p.getInPort());
			logger.debug("-----Got ARP. SRC: " + ethPayload.getSourceMAC().toString() + ", DST MAC: " + ethPayload.getDestinationMAC().toString());
		} else if (etherType == IP_ETHERTYPE_INT) {
			// if it's an IP packet then install ACL flow entries
			// installDirectFlowEntries(sw, cntx, pi);

			OFMatch m = new OFMatch();

			m.loadFromPacket(p.getPacketData(), p.getInPort());

			// Determine which way the packet is traversing

			if (m.getInputPort() == LAN_PORT) {
				logger.debug("Got a pkt on LAN interface: DataLayerType " + (short) m.getDataLayerType() + ", Netproto " + (byte) m.getNetworkProtocol());

				/* Perform a lookup in the rules */

				Collections.sort(rules, new UberGatewayRuleComparator());

				int i = 0;
				for (UberGatewayRule r : rules) {
					logger.debug("-->" + r.getPriority());
					if (r.matches(m)) {
						System.out.println("****** NICE!!!! MATCH****");

						// installDefault(sw, msg, r.getOutport(), cntx);
						installRule(sw, msg, r, cntx);
						logger.info("Installed rule " + r.getRuleName());
						reflectRule(r, sw, cntx);
						logger.info("Installed reverse rule " + r.getRuleName());
						++i;
						break;
					}

				}
				System.out.println("Installed " + i + " rules");

			}

		}

		return Command.CONTINUE;
	}

	private void installRule(IOFSwitch sw, OFMessage msg, UberGatewayRule u, FloodlightContext cntx) {

		masterCntx = cntx;

		long cookie = AppCookie.makeCookie(APP_ID, 0);

		short egressPort = u.getOutport();

		OFMatch rule = u.getMatch();

		OFPacketIn packetIn = (OFPacketIn) msg;
		OFFlowMod flowMod = (OFFlowMod) provider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		// Create a copy of the rule, add in the IN_PORT. Otherwise we would
		// modify the store rule
		OFMatch m = rule.clone();

		// match.loadFromPacket(packetIn.getPacketData(), packetIn.getInPort());
		m.setInputPort(packetIn.getInPort());
		m.setWildcards(m.getWildcardObj().matchOn(Wildcards.Flag.IN_PORT));

		// create actions
		List<OFAction> actions = new ArrayList<>();

		OFActionDataLayerDestination rewriteMacDestinationAction = new OFActionDataLayerDestination();

		if (egressPort == 2) {
			// destination host endpointA
			rewriteMacDestinationAction.setDataLayerAddress(Ethernet.toMACAddress(MAC_WAN1));
			logger.debug("Rewriting MAC!");

		} else {
			// destination host endpointB
			rewriteMacDestinationAction.setDataLayerAddress(Ethernet.toMACAddress(MAC_WAN2));

		}

		/*
		 * MUY IMPORTANTE!!!
		 * Add rewrite actions BEFORE outputActions!!
		 * If the order is fliped, no rewrite will happen!!!!
		 */

		actions.add(rewriteMacDestinationAction);

		// add an output action to the list of actions
		actions.add(new OFActionOutput(egressPort));

		// configure the flow_mod
		flowMod.setActions(actions);
		flowMod.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0).setBufferId(OFPacketOut.BUFFER_ID_NONE).setMatch(m)
				.setLengthU(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH + OFActionDataLayerDestination.MINIMUM_LENGTH);
		flowMod.setPriority(u.getPriority()).setCommand(OFFlowMod.OFPFC_ADD);

		try {
			sw.write(flowMod, cntx);
			sw.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void reflectRule(UberGatewayRule r, IOFSwitch sw, FloodlightContext cntx) {

		// Allows for reverse rules to be installed.

		long cookie = AppCookie.makeCookie(APP_ID, 0);

		OFFlowMod flowMod = (OFFlowMod) provider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		// Create a copy of the rule, add in the IN_PORT
		OFMatch m = r.getMatch().clone();

		m.setInputPort(r.getOutport());
		m.setWildcards(m.getWildcardObj().matchOn(Wildcards.Flag.IN_PORT));

		if (r.getCareAboutPortNo()) {
			// Switch portnumbers from DST -> SRC
			m.setWildcards(m.getWildcardObj().wildcard(Wildcards.Flag.TP_DST));
			m.setWildcards(m.getWildcardObj().matchOn(Wildcards.Flag.TP_SRC));
			m.setTransportSource(m.getTransportDestination());
		}

		// System.out.println("m is " + m.toString());

		// create actions
		List<OFAction> actions = new ArrayList<>();

		OFAction rewriteMacDestinationAction = null;

		// destination LAN side
		// The idea is that the LAN side traffic should be handled by a separate
		// L2 sw.
		rewriteMacDestinationAction = new OFActionDataLayerDestination(Ethernet.toMACAddress(MAC_LAN));

		actions.add(rewriteMacDestinationAction);

		// add an output action to the list of actions
		actions.add(new OFActionOutput(LAN_PORT));
		// configure the flow_mod
		flowMod.setActions(actions);
		flowMod.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0).setBufferId(OFPacketOut.BUFFER_ID_NONE).setMatch(m)
				.setLengthU(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH + OFActionDataLayerDestination.MINIMUM_LENGTH);
		flowMod.setPriority(r.getPriority()).setCommand(OFFlowMod.OFPFC_ADD);

		try {
			sw.write(flowMod, cntx);
			sw.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/*
	 * Sends ARP Reply for all ARP requests in the network; requests for IP_LB
	 * will be replied with MAC_LB requests for IP_H1 will be replied with
	 * MAC_H1
	 * 
	 * Taken from Sample Load balancer
	 */
	private void sendArpReply(IOFSwitch sw, FloodlightContext cntx, Ethernet ethPayload, short inputPort) {
		ARP arpRequest = (ARP) ethPayload.getPayload();
		ARP arpReply = new ARP();
		// configure the arpReply packet
		arpReply.setHardwareType(ARP.HW_TYPE_ETHERNET);
		arpReply.setHardwareAddressLength(arpRequest.getHardwareAddressLength());
		arpReply.setOpCode(ARP.OP_REPLY);
		arpReply.setProtocolAddressLength(arpRequest.getProtocolAddressLength());
		arpReply.setProtocolType(ARP.PROTO_TYPE_IP);
		arpReply.setSenderProtocolAddress(arpRequest.getTargetProtocolAddress());
		arpReply.setTargetHardwareAddress(arpRequest.getSenderHardwareAddress());
		arpReply.setTargetProtocolAddress(arpRequest.getSenderProtocolAddress());

		Ethernet ethernetArpReply = new Ethernet();

		if (IPv4.toIPv4Address(arpRequest.getTargetProtocolAddress()) == IPv4.toIPv4Address(GW_LAN_IP)) {
			// if request for LAN IP address of the GW
			arpReply.setSenderHardwareAddress(Ethernet.toMACAddress(GW_MAC_LAN));

			ethernetArpReply.setSourceMACAddress(Ethernet.toMACAddress(GW_MAC_LAN)).setDestinationMACAddress(ethPayload.getSourceMACAddress()).setEtherType(Ethernet.TYPE_ARP);

		} else if (IPv4.toIPv4Address(arpRequest.getTargetProtocolAddress()) == IPv4.toIPv4Address(GW_WAN_IP)) {
			// if WAN side requests
			logger.debug("Sending out ARP reply on WAN");
			arpReply.setSenderHardwareAddress(Ethernet.toMACAddress(GW_MAC_WAN));

			ethernetArpReply.setSourceMACAddress(Ethernet.toMACAddress(GW_MAC_WAN)).setDestinationMACAddress(ethPayload.getSourceMACAddress()).setEtherType(Ethernet.TYPE_ARP);

		} else {

			logger.debug("Unhandled ARP :( ");

		}
		// create Packet out and send the ARP reply
		// Ethernet ethernetArpReply = new
		// Ethernet().setSourceMACAddress(Ethernet.toMACAddress(MAC_LB)).setDestinationMACAddress(ethPayload.getSourceMACAddress()).setEtherType(Ethernet.TYPE_ARP);
		ethernetArpReply.setPayload(arpReply);

		// convert the Ethernet payload into a byte array
		byte[] data = ethernetArpReply.serialize();

		// create OF Packet_out to send the ARP reply to the switch
		OFPacketOut ofArpReply = (OFPacketOut) provider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);

		// put the Ethernet payload into the OF packet_out message
		ofArpReply.setPacketData(data);

		// create output action
		List<OFAction> actions = new ArrayList<>();
		actions.add(new OFActionOutput(inputPort));
		// configure the Packet_out OF message
		ofArpReply.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
		ofArpReply.setActions(actions);
		ofArpReply.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		ofArpReply.setInPort(OFPort.OFPP_NONE);
		ofArpReply.setLengthU(OFPacketOut.MINIMUM_LENGTH + data.length + OFActionOutput.MINIMUM_LENGTH);

		// send the packet_out to the switch
		try {
			sw.write(ofArpReply, cntx);
			sw.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void pushMessage(IOFSwitch sw, OFFlowMod fm) {

		try {
			sw.write(fm, null);
			sw.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	// private void deleteRule(OFMessage msg, UberGatewayRule u,
	// FloodlightContext cntx) {
	private void deleteFlow(UberGatewayRule u) {

		long cookie = AppCookie.makeCookie(APP_ID, 0);

		short egressPort = u.getOutport();

		OFMatch rule = u.getMatch();
		// OFPacketIn packetIn = (OFPacketIn) msg;
		OFFlowMod flowMod = (OFFlowMod) provider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		// Create a copy of the rule, add in the IN_PORT. Otherwise we would
		// modify the store rule
		OFMatch m = rule.clone();

		// m.setInputPort(packetIn.getInPort());
		// m.setWildcards(m.getWildcardObj().matchOn(Wildcards.Flag.IN_PORT));

		// create actions
		List<OFAction> actions = new ArrayList<>();

		OFActionDataLayerDestination rewriteMacDestinationAction = new OFActionDataLayerDestination();

		if (egressPort == 2) {
			// destination host endpointA
			rewriteMacDestinationAction.setDataLayerAddress(Ethernet.toMACAddress(MAC_WAN1));
			logger.debug("Rewriting MAC!");

		} else {
			// destination host endpointB
			rewriteMacDestinationAction.setDataLayerAddress(Ethernet.toMACAddress(MAC_WAN2));

		}

		/*
		 * MUY IMPORTANTE!!!
		 * Add rewrite actions BEFORE outputActions!!
		 * If the order is fliped, no rewrite will happen!!!!
		 */

		actions.add(rewriteMacDestinationAction);

		// add an output action to the list of actions
		actions.add(new OFActionOutput(u.getOutport()));

		// configure the flow_mod
		flowMod.setActions(actions);
		flowMod.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0).setBufferId(OFPacketOut.BUFFER_ID_NONE).setMatch(m)
				.setLengthU(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH + OFActionDataLayerDestination.MINIMUM_LENGTH);
		flowMod.setPriority(u.getPriority()).setCommand(OFFlowMod.OFPFC_DELETE);

		try {
			logger.debug("Sending flowMod for delete");
			if (masterSW == null) {
				logger.error("Nope, nope, nope!");
			}
			masterSW.write(flowMod, null);
			masterSW.flush();
		} catch (IOException e) {
			logger.error("Error in deleting");
			e.printStackTrace();
		} catch (ResourceException r) {
			logger.error("Something went wrong");
			r.printStackTrace();

		}
	}

	private void deleteReflectedFlow(UberGatewayRule r) {

		long cookie = AppCookie.makeCookie(APP_ID, 0);

		OFFlowMod flowMod = (OFFlowMod) provider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		short egressPort = r.getOutport();

		// Create a copy of the rule, add in the IN_PORT
		OFMatch m = r.getMatch().clone();

		m.setInputPort(r.getOutport());
		m.setWildcards(m.getWildcardObj().matchOn(Wildcards.Flag.IN_PORT));

		if (r.getCareAboutPortNo()) {
			// Switch portnumbers from DST -> SRC
			m.setWildcards(m.getWildcardObj().wildcard(Wildcards.Flag.TP_DST));
			m.setWildcards(m.getWildcardObj().matchOn(Wildcards.Flag.TP_SRC));
			m.setTransportSource(m.getTransportDestination());
		}

		// System.out.println("m is " + m.toString());

		// create actions
		List<OFAction> actions = new ArrayList<>();

		OFAction rewriteMacDestinationAction = null;

		// destination LAN side
		// The idea is that the LAN side traffic should be handled by a separate
		// L2 sw.
		rewriteMacDestinationAction = new OFActionDataLayerDestination(Ethernet.toMACAddress(MAC_LAN));

		actions.add(rewriteMacDestinationAction);

		// add an output action to the list of actions
		actions.add(new OFActionOutput(LAN_PORT));
		// configure the flow_mod
		flowMod.setActions(actions);
		flowMod.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0).setBufferId(OFPacketOut.BUFFER_ID_NONE).setMatch(m)
				.setLengthU(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH + OFActionDataLayerDestination.MINIMUM_LENGTH);
		flowMod.setPriority(r.getPriority()).setCommand(OFFlowMod.OFPFC_DELETE);

		try {
			logger.debug("Sending flowMod for delete");
			masterSW.write(flowMod, this.masterCntx);
			masterSW.flush();
		} catch (IOException e) {
			logger.error("Error in deleting");
			e.printStackTrace();
		} catch (ResourceException re) {
			logger.error("Something went wrong");
			re.printStackTrace();

		}
	}

	/* REST methods below */
	@Override
	public ArrayList<UberGatewayRule> getRules() {

		// Method implementing REST

		return rules;
	}

	@Override
	public void addRule(UberGatewayRule inRule) {
		logger.debug("Attemtting to install rule received by REST API");

		rules.add(inRule);

	}

	public void deleteRule(String ruleName) {
		logger.debug("Attempting to delete rule with name:'" + ruleName + "'");

		int i = 0;
		for (UberGatewayRule r : rules) {
			logger.debug("Current rule name ='" + r.getRuleName() + "'");

			if (r.getRuleName().equals(ruleName)) {
				logger.info("Found rule to delete");

				logger.debug("Calling flowDelete");
				// FLOW mod delete
				deleteFlow(r);

				// delete reverse flow

				deleteReflectedFlow(r);

				// Remove from internal store
				logger.debug("Trying to remove from internal list");
				if (rules.remove(r)) {

					logger.debug("Successfully removed from rules");
				} else {
					logger.warn("Error in deleting from rules");
				}

				++i;
				break;
			}

		}

	}
}
