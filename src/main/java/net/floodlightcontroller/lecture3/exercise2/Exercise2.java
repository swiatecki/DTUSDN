package net.floodlightcontroller.lecture3.exercise2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.RoleInfo;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.packet.Ethernet;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;

public class Exercise2 implements IFloodlightModule, IOFMessageListener {

	IFloodlightProviderService provider;

	private static final String h1MAC = "00:00:00:00:00:01";
	private static final String h2MAC = "00:00:00:00:00:02";
	private static final String BROADCAST_MAC = "ff:ff:ff:ff:ff:ff";
	private static final short OUTPUT_PORT_2 = 2;
	private static final short OUTPUT_PORT_1 = 1;
	public static final int FANCY_APP_ID = 11;
	static {
		AppCookie.registerApp(FANCY_APP_ID, "fancymodule");
	}

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
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		provider = context.getServiceImpl(IFloodlightProviderService.class);

	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		System.out.println("I am a module running in FLoodlight");
		RoleInfo info = provider.getRoleInfo();
		System.out.println("info :" + info.getRole());

		// tell floodlight that we want to receive Packet_In events
		provider.addOFMessageListener(OFType.PACKET_IN, this);

	}

	// ******IOFMessageListener********

	@Override
	public String getName() {
		return "fancymodule";
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

		Ethernet ethPayload = provider.bcStore.get(cntx, provider.CONTEXT_PI_PAYLOAD);

		long sourceMAC = Ethernet.toLong(ethPayload.getSourceMACAddress());
		System.out.println("mac :" + HexString.toHexString(sourceMAC, 6) + " has been seen on switch: " + sw.getId());

		long destMAC = Ethernet.toLong(ethPayload.getDestinationMACAddress());
		String destMAChexString = HexString.toHexString(destMAC, 6);

		// if (destMAChexString.equals(BROADCAST_MAC)) {
		// // install flow for flooding MAC broadcast packets
		// installForwardingFlow(sw, msg, OFPort.OFPP_FLOOD.getValue(), cntx);
		// } else if (destMAChexString.equals(h2MAC)) {
		// // install flow mod from h1 to h2
		// installForwardingFlow(sw, msg, OUTPUT_PORT_2, cntx);
		// } else if (destMAChexString.equals(h1MAC)) {
		// // install flow mod from h2 to h1
		// installForwardingFlow(sw, msg, OUTPUT_PORT_1, cntx);
		// }

		return Command.CONTINUE;
	}

	private void installForwardingFlow(IOFSwitch sw, OFMessage msg, short outputPort2, FloodlightContext cntx) {
		long cookie = AppCookie.makeCookie(FANCY_APP_ID, 0);

		OFPacketIn packetIn = (OFPacketIn) msg;

		OFFlowMod flowMod = (OFFlowMod) provider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		// we need to set matching headers & actions for this flow_mod
		// create the match object-> describes the packet headers for this flow
		// entry
		OFMatch match = new OFMatch();
		match.loadFromPacket(packetIn.getPacketData(), packetIn.getInPort());
		match.setWildcards(Wildcards.FULL.getInt() & ~OFMatch.OFPFW_DL_DST);

		// create actions
		List<OFAction> actions = new ArrayList<>();

		// add an output action to the list of actions
		actions.add(new OFActionOutput(outputPort2));

		// configure the flow_mod
		flowMod.setActions(actions);
		flowMod.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE).setMatch(match)
				.setLengthU(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH);
		flowMod.setPriority((short) 100).setCommand(OFFlowMod.OFPFC_ADD);

		try {
			sw.write(flowMod, cntx);
			sw.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
