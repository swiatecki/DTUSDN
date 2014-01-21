package net.floodlightcontroller.custom.networklb;

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
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

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
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionNetworkLayerSource;
import org.openflow.protocol.action.OFActionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetLoadBalance implements IFloodlightModule, IOFMessageListener {

	private static IFloodlightProviderService provider;
	private static Logger logger;
	private static final short ARP_ETHERTYPE_INT = 2054;
	private static final short IP_ETHERTYPE_INT = 2048;
	public static final int NET_LOAD_BALANCE_ID = 12;
	private static final String MAC_LB = "00:00:00:00:00:12";
	private static final String MAC_H1 = "00:00:00:00:00:01";
	private static final String MAC_H2 = "00:00:00:00:00:02";
	private static final String MAC_H3 = "00:00:00:00:00:03";
	private static final String IP_LB = "10.0.0.100";
	private static final String IP_H1 = "10.0.0.1";
	private static final String IP_H2 = "10.0.0.2";
	private static final String IP_H3 = "10.0.0.3";

	static {
		AppCookie.registerApp(NET_LOAD_BALANCE_ID, "NetLoadBalance");
	}

	// *****IFloodlightModule******

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
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		logger = LoggerFactory.getLogger(NetLoadBalance.class);
		provider = context.getServiceImpl(IFloodlightProviderService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		provider.addOFMessageListener(OFType.PACKET_IN, this);
	}

	// *****IOFMessageListener*********

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
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

		OFPacketIn pi = (OFPacketIn) msg;

		Ethernet ethPayload = provider.bcStore.get(cntx, provider.CONTEXT_PI_PAYLOAD);

		short etherType = ethPayload.getEtherType();

		if (etherType == ARP_ETHERTYPE_INT) {
			// If it's an ARP packet, then send an ARP Reply
			sendArpReply(sw, cntx, ethPayload, pi.getInPort());
		} else if (etherType == IP_ETHERTYPE_INT) {
			// if it's an IP packet then change packet headers & forward
			// 1) install flow mods
			// 2) send packet_out
			// 3) install reverse flow-mods
			installDirectFlowEntries(sw, cntx, pi);
			// installReverseFlowEntries(sw, cntx, pi);
		}

		return Command.CONTINUE;
	}

	// ********private methods********

	private void installDirectFlowEntries(IOFSwitch sw, FloodlightContext cntx, OFPacketIn pi) {
		long cookie = AppCookie.makeCookie(NET_LOAD_BALANCE_ID, 0);

		OFMatch directMatch = new OFMatch();
		directMatch.loadFromPacket(pi.getPacketData(), pi.getInPort());
		int destinationIP = directMatch.getNetworkDestination();

		// if we have an incoming request for the servers destined towards the
		// IP of the load balancer
		if (IPv4.toIPv4Address(IP_LB) == destinationIP) {

			short outputPort = getOuputPortForPacket(directMatch.getTransportSource());
			directMatch.setWildcards(Wildcards.FULL.getInt() & ~OFMatch.OFPFW_DL_TYPE & ~OFMatch.OFPFW_NW_DST_ALL
					& ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_TP_SRC);

			OFFlowMod directFlowMod = (OFFlowMod) provider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

			List<OFAction> actions = new ArrayList<>();
			OFAction rewriteMacDestinationAction = null;
			OFAction rewriteIpDestinationAction = null;
			if (outputPort == 2) {
				// destination host h2
				rewriteMacDestinationAction = new OFActionDataLayerDestination(Ethernet.toMACAddress(MAC_H2));
				rewriteIpDestinationAction = new OFActionNetworkLayerDestination(IPv4.toIPv4Address(IP_H2));
			} else {
				// destination host h3
				rewriteMacDestinationAction = new OFActionDataLayerDestination(Ethernet.toMACAddress(MAC_H3));
				rewriteIpDestinationAction = new OFActionNetworkLayerDestination(IPv4.toIPv4Address(IP_H3));
			}
			OFAction outputAction = new OFActionOutput(outputPort);
			actions.add(rewriteIpDestinationAction);
			actions.add(rewriteMacDestinationAction);
			actions.add(outputAction);
			// configure the flow_mod
			directFlowMod.setActions(actions).setMatch(directMatch);
			directFlowMod.setLengthU(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH
					+ OFActionDataLayerDestination.MINIMUM_LENGTH + OFActionNetworkLayerDestination.MINIMUM_LENGTH);
			directFlowMod.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0);
			directFlowMod.setCommand(OFFlowMod.OFPFC_ADD).setPriority((short) 50);

			if (pi.getBufferId() != OFPacketOut.BUFFER_ID_NONE) {
				directFlowMod.setBufferId(pi.getBufferId());
			} else {
				directFlowMod.setBufferId(OFPacketOut.BUFFER_ID_NONE);
			}
			pushMessage(sw, directFlowMod);
			// push reverse flow
			installReverseFlow(sw, cookie, outputPort);
		}

	}

	private void installReverseFlow(IOFSwitch sw, long cookie, short outputPort) {

		// create and configure the match for the reverse direction
		OFMatch reverseMatch = new OFMatch();
		reverseMatch.setWildcards(Wildcards.FULL.getInt() & ~OFMatch.OFPFW_DL_TYPE & ~OFMatch.OFPFW_IN_PORT);
		reverseMatch.setDataLayerType((short) 2048);
		reverseMatch.setInputPort(outputPort);

		// create and configure actions for reverse flow entry
		List<OFAction> reverseActions = new ArrayList<>();
		OFAction reverseRewriteSourceIP = new OFActionNetworkLayerSource(IPv4.toIPv4Address(IP_LB));
		OFAction reverseRewriteSourceMAC = new OFActionDataLayerSource(Ethernet.toMACAddress(MAC_LB));
		OFAction reverseActionOutput = new OFActionOutput((short) 1);
		reverseActions.add(reverseRewriteSourceIP);
		reverseActions.add(reverseRewriteSourceMAC);
		reverseActions.add(reverseActionOutput);

		// create and configure the flow_mod OF message for the reverse flow
		// entry
		OFFlowMod reverseFlowMod = (OFFlowMod) provider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		reverseFlowMod.setActions(reverseActions).setCookie(cookie).setPriority((short) 50);
		reverseFlowMod.setMatch(reverseMatch).setCommand(OFFlowMod.OFPFC_ADD);
		reverseFlowMod.setLengthU(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH
				+ OFActionDataLayerSource.MINIMUM_LENGTH + OFActionNetworkLayerSource.MINIMUM_LENGTH);
		reverseFlowMod.setHardTimeout((short) 0).setIdleTimeout((short) 0).setBufferId(OFPacketOut.BUFFER_ID_NONE);

		pushMessage(sw, reverseFlowMod);
	}

	/*
	 * Sends ARP Reply for all ARP requests in the network; requests for IP_LB
	 * will be replied with MAC_LB requests for IP_H1 will be replied with
	 * MAC_H1
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

		if (IPv4.toIPv4Address(arpRequest.getTargetProtocolAddress()) == IPv4.toIPv4Address(IP_LB)) {
			// if request for IP address of the load balancer
			arpReply.setSenderHardwareAddress(Ethernet.toMACAddress(MAC_LB));
		} else if (IPv4.toIPv4Address(arpRequest.getTargetProtocolAddress()) == IPv4.toIPv4Address(IP_H1)) {
			// if h2 or h3 request for the IP Address of h1
			arpReply.setSenderHardwareAddress(Ethernet.toMACAddress(MAC_H1));
		}
		// create Packet out and send the ARP reply
		Ethernet ethernetArpReply = new Ethernet().setSourceMACAddress(Ethernet.toMACAddress(MAC_LB))
				.setDestinationMACAddress(ethPayload.getSourceMACAddress()).setEtherType(Ethernet.TYPE_ARP);
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

	/*
	 * returns the output port for the load balancer depending on the source
	 * transport port
	 */
	private short getOuputPortForPacket(short sourceTransport) {
		if (sourceTransport % 2 == 0) {
			return 2;
		} else
			return 3;
	}

	// pushes the flow_mod entry to the switch
	private void pushMessage(IOFSwitch sw, OFFlowMod fm) {

		try {
			sw.write(fm, null);
			sw.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		logger.info("######## End Install Flow !!! #########2");

	}
}
