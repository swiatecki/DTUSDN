package net.floodlightcontroller.custom.acl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import net.floodlightcontroller.util.MACAddress;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * for this module L3+L4 ACL will be applied on traffic.
 * the module does not perform ARP Req/Reply like a regular router at L3
 * hence, the hosts have to be in the same subnet to be able to reach each other
 * the ARP traffic will be flooded
 * the L3 traffic if fitlered using the ACL
 */

public class CustomACL implements IFloodlightModule, IOFMessageListener {

	// the macLearningTable contains a mapping between the MAC addr of the
	// device and the attachment point
	protected ConcurrentHashMap<Long, ConcurrentHashMap<MACAddress, Short>> macLearningTable;
	private IFloodlightProviderService provider;
	private static Logger logger;
	private ArrayList<CustomAclRule> aclRules;
	public static final int FORWARDING_APP_ID = 10;

	static {
		AppCookie.registerApp(FORWARDING_APP_ID, "CustomAcl");
	}

	// ****************************
	// ***** IFloodlightModule*****
	// ****************************
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> list = new ArrayList<Class<? extends IFloodlightService>>();
		list.add(IFloodlightProviderService.class);
		return list;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		provider = context.getServiceImpl(IFloodlightProviderService.class);
		logger = LoggerFactory.getLogger(CustomACL.class);
		macLearningTable = new ConcurrentHashMap<Long, ConcurrentHashMap<MACAddress, Short>>(2, 0.9f, 1);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		provider.addOFMessageListener(OFType.PACKET_IN, this);
		// set up the ACLs
		aclRules = new ArrayList<CustomAclRule>();
		aclRules.add(new CustomAclRule((byte) 0x6, IPv4.toIPv4Address("10.0.1.0"), (short) 24, (short) 23,
				CustomAclRule.Direction.OUTBOUND, CustomAclRule.Operation.BLOCK));

	}

	// ****************************
	// ***** IOFMessageListener****
	// ****************************

	@Override
	public String getName() {

		return CustomACL.class.getSimpleName();
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
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		Short outputPort;
		OFPacketIn pi = (OFPacketIn) msg;
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		MACAddress sourceMac = MACAddress.valueOf(match.getDataLayerSource());
		MACAddress destinationMac = MACAddress.valueOf(match.getDataLayerDestination());

		// if the switch does not have a mapping already, then create a mapping
		// and add this current MAC-port association
		if (!macLearningTable.containsKey(sw.getId())) {
			ConcurrentHashMap<MACAddress, Short> macPort = new ConcurrentHashMap<MACAddress, Short>(8, 0.9f, 1);
			macPort.put(sourceMac, pi.getInPort());
			macLearningTable.put(sw.getId(), macPort);
		} else {
			// if the mapping exists, update the mapping(in case the host moved
			// to a different port)
			macLearningTable.get(sw.getId()).put(sourceMac, pi.getInPort());
		}
		// search for destination mac-port mapping
		outputPort = macLearningTable.get(sw.getId()).get(destinationMac);

		if (packetMatchesAnyAcl(match)) {
			logger.info("*** Packet matches ACL ---> BLOCK ***");
			installAclFlow(sw, pi);
		} else {
			// if output port is null then flood else use the output port
			if (outputPort != null) {
				// if output port is not FLOOD then install also a flow entry
				pushPacketOut(sw, pi, pi.getInPort(), outputPort, match);
				installFlow(sw, pi, pi.getInPort(), outputPort, match);
			} else {
				logger.info("*** Flooding of packet***");
				// no output port for this MAC, flood the packet
				pushPacketOut(sw, pi, pi.getInPort(), OFPort.OFPP_FLOOD.getValue(), match);
			}
		}
		return Command.CONTINUE;
	}

	private boolean packetMatchesAnyAcl(OFMatch flowDefinition) {
		for (CustomAclRule acl : aclRules) {
			if (acl.matchesPacket(flowDefinition)) {
				logger.info("*** Packet matches an ACL ---> Block flow ***");
				return true;
			}
		}
		return false;
	}

	private void pushPacketOut(IOFSwitch sw, OFPacketIn pi, Short inPort, Short outPort, OFMatch match) {
		match.setInputPort(inPort);

		OFPacketOut pktOut = (OFPacketOut) provider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
		short pktOutLength = (short) OFPacketOut.MINIMUM_LENGTH;

		logger.info("*** Begin *** Push Packet out !!! ***");
		logger.info("Packet arrived on port {}, DL_SRC: {} ", pi.getInPort(), match.getDataLayerSource());

		// buffer id, length, port
		pktOut.setBufferId(pi.getBufferId());
		pktOut.setInPort(pi.getInPort());
		pktOut.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
		pktOutLength += pktOut.getActionsLength();

		List<OFAction> actions = new ArrayList<OFAction>(1);
		// set actions

		actions.add(new OFActionOutput(outPort, (short) 0));

		pktOut.setActions(actions);

		if (pi.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
			byte[] pData = pi.getPacketData();
			pktOut.setPacketData(pData);
			pktOutLength += (short) pData.length;
		}

		// finally, set the total length
		pktOut.setLength(pktOutLength);

		// and write it out
		try {
			sw.write(pktOut, null);
		} catch (IOException e) {
		}
		logger.info("*** End *** Push Packet out !!! ***");
	}

	private void installAclFlow(IOFSwitch sw, OFPacketIn pi) {
		logger.info("######## Begin Install ACL Flow !!! #########");
		long cookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);

		// Create flow-mod one for IP another for ARP
		OFFlowMod fm = (OFFlowMod) provider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		match.setWildcards(Wildcards.FULL.getInt() & ~OFMatch.OFPFW_DL_TYPE & ~OFMatch.OFPFW_NW_SRC_ALL
				& ~OFMatch.OFPFW_NW_DST_ALL & ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_TP_DST & ~OFMatch.OFPFW_TP_SRC);

		logger.info("######## protocol : {} port dstination : {}", match.getNetworkProtocol(),
				match.getTransportDestination());

		// create and configure actions
		List<OFAction> actions = new ArrayList<OFAction>();

		// set the empty list of actions for the two flow mods;
		fm.setActions(actions);
		fm.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE).setMatch(match).setLengthU(OFFlowMod.MINIMUM_LENGTH);
		fm.setPriority((short) 100).setCommand(OFFlowMod.OFPFC_ADD);

		// Push rule to the switch.
		pushMessage(sw, fm);
	}

	private void installFlow(IOFSwitch sw, OFPacketIn pi, Short inputPort, Short outputPort, OFMatch match) {
		logger.info("######## Begin Install MAC Learning Flow !!! #########");
		// this method install a dropping flow with priority > 0 and timer = 0

		// create a cookie to put inside the flows
		long cookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);

		// Create flow-mod one for IP another for ARP
		OFFlowMod fmIp = (OFFlowMod) provider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		// create an arraylist of actions, no actions means dropping
		List<OFAction> actions = new ArrayList<OFAction>();

		OFMatch matchIp = new OFMatch();

		matchIp.setWildcards(Wildcards.FULL.getInt() & ~OFMatch.OFPFW_DL_TYPE & ~OFMatch.OFPFW_NW_SRC_ALL
				& ~OFMatch.OFPFW_NW_DST_ALL & ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_TP_DST & ~OFMatch.OFPFW_TP_SRC);

		matchIp.setDataLayerType(match.getDataLayerType()).setNetworkSource(match.getNetworkSource())
				.setNetworkDestination(match.getNetworkDestination()).setNetworkProtocol(match.getNetworkProtocol())
				.setTransportDestination(match.getTransportDestination())
				.setTransportSource(match.getTransportSource());

		// Configure the actions
		actions.add(new OFActionOutput(outputPort, (short) 0));

		// set the empty list of actions for the two flow mods;
		fmIp.setActions(actions);

		fmIp.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE).setMatch(matchIp)
				.setLengthU(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH);
		fmIp.setPriority((short) 50).setCommand(OFFlowMod.OFPFC_ADD);

		// Push rule to the switch.
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
		logger.info("######## End Install Flow !!! #########2");

	}

}
