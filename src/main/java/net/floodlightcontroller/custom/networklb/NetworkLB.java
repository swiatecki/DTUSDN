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
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.UpdateOperation;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkLB implements IFloodlightModule, IOFMessageListener, ITopologyListener {
	private IFloodlightProviderService provider;
	private ITopologyService topoService;
	private static Logger logger;
	public static final int FORWARDING_APP_ID = 2;

	// ****************************
	// ***** IFloodlightModule*****
	// ****************************
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
		Collection<Class<? extends IFloodlightService>> list = new ArrayList<Class<? extends IFloodlightService>>();
		list.add(IFloodlightProviderService.class);
		list.add(ITopologyService.class);
		list.add(ILinkDiscoveryService.class);
		return list;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		provider = context.getServiceImpl(IFloodlightProviderService.class);
		topoService = context.getServiceImpl(ITopologyService.class);
		logger = LoggerFactory.getLogger(NetworkLB.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		provider.addOFMessageListener(OFType.PACKET_IN, this);
		topoService.addListener(this);

	}

	// ****************************
	// ***** IOFMessageListener*****
	// ****************************

	@Override
	public String getName() {
		return NetworkLB.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return (type.equals(OFType.PACKET_IN) && (name.equals("topology") || name.equals("devicemanager") || name
				.equals("virtualizer")));
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		OFPacketIn pi = (OFPacketIn) msg;
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		// get output port--> choose one of the two paths
		short outputPort = getOuputPortForPacket(pi);
		// push flows to the specific port
		// installs both forwarding flows in S1 and reverse flows in S4
		installLoadBalancingFlows(sw, pi.getInPort(), outputPort, pi);
		return Command.CONTINUE;
	}

	private void installArpFlow(IOFSwitch sw, short inputPort, short outputPort) {
		logger.info("######## Begin Install ARP Flow !!! #########");
		long cookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);

		// Create flow-mod one for IP another for ARP
		OFFlowMod fm = (OFFlowMod) provider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		OFMatch match = new OFMatch();
		match.setWildcards(Wildcards.FULL.getInt() & ~OFMatch.OFPFW_IN_PORT & ~OFMatch.OFPFW_DL_TYPE);

		match.setInputPort(inputPort).setDataLayerType((short) 2054);

		// create and configure actions
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(new OFActionOutput(outputPort, (short) 0));

		// set the empty list of actions for the two flow mods;
		fm.setActions(actions);
		fm.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE).setMatch(match)
				.setLengthU(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH);
		fm.setPriority((short) 50).setCommand(OFFlowMod.OFPFC_ADD);

		// Push rule to the switch.
		pushMessage(sw, fm);
	}

	private short getOuputPortForPacket(OFPacketIn pi) {
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		if (match.getTransportSource() % 2 == 0) {
			return 1;
		} else
			return 2;
	}

	// ****************************
	// ***** ITopologyListener*****
	// ****************************
	@Override
	public void topologyChanged(List<LDUpdate> linkUpdates) {
		logger.info("**** topology has changed!!! *******");
		for (LDUpdate update : linkUpdates) {
			if (update.getOperation().equals(UpdateOperation.SWITCH_UPDATED)) {
				if (update.getSrc() == 2 || update.getSrc() == 3) {
					// install flow in the switch
					logger.info("**** install default flow for switch {}*******", update.getSrc());
					installFlow(provider.getSwitch(update.getSrc()), (short) 1, (short) 2);
					installFlow(provider.getSwitch(update.getSrc()), (short) 2, (short) 1);
				} else if (update.getSrc() == 1) {
					// install reverse flow entries in s1
					// the forward flows will be balanced according to the
					// src_TCP_port
					logger.info("**** install default flow for switch {}*******", update.getSrc());
					installFlow(provider.getSwitch(update.getSrc()), (short) 1, (short) 3);
					installFlow(provider.getSwitch(update.getSrc()), (short) 2, (short) 3);
					installArpFlow(provider.getSwitch(update.getSrc()), (short) 3, (short) 1);
				} else if (update.getSrc() == 4) {
					// install forward flows in s4
					// the reverse flows depend on the path chosen by s1 for
					// forward direction
					logger.info("**** install default flow for switch {}*******", update.getSrc());
					installFlow(provider.getSwitch(update.getSrc()), (short) 1, (short) 3);
					installFlow(provider.getSwitch(update.getSrc()), (short) 2, (short) 3);
					installArpFlow(provider.getSwitch(update.getSrc()), (short) 3, (short) 1);
				}
			}
		}
	}

	// ****************************
	// ***** Helper Methods*****
	// ****************************

	private void installLoadBalancingFlows(IOFSwitch sw, Short inputPort, Short outputPort, OFPacketIn pi) {
		logger.info("######## Begin Install Load Balancing Flow !!! #########");
		long cookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);

		// Create flow-mod one for IP another for ARP
		OFFlowMod fmForward = (OFFlowMod) provider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		OFFlowMod fmBackward = (OFFlowMod) provider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		OFMatch matchForward = new OFMatch();
		matchForward.loadFromPacket(pi.getPacketData(), inputPort);

		matchForward.setWildcards(Wildcards.FULL.getInt() & ~OFMatch.OFPFW_IN_PORT & ~OFMatch.OFPFW_NW_DST_ALL
				& ~OFMatch.OFPFW_DL_TYPE & ~OFMatch.OFPFW_NW_SRC_ALL & ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_TP_DST
				& ~OFMatch.OFPFW_TP_SRC);

		OFMatch matchBackward = matchForward.clone();
		matchBackward.setNetworkDestination(matchForward.getNetworkSource());
		matchBackward.setNetworkSource(matchForward.getNetworkDestination());
		matchBackward.setTransportDestination(matchForward.getTransportSource());
		matchBackward.setTransportSource(matchForward.getTransportDestination());

		// create and configure actions
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(new OFActionOutput(outputPort, (short) 0));

		// set the empty list of actions for the two flow mods;
		fmForward.setActions(actions);
		fmForward.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE).setMatch(matchForward)
				.setLengthU(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH);
		fmForward.setPriority((short) 100).setCommand(OFFlowMod.OFPFC_ADD);

		fmBackward.setActions(actions);
		fmBackward.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE).setMatch(matchBackward)
				.setLengthU(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH);
		fmBackward.setPriority((short) 100).setCommand(OFFlowMod.OFPFC_ADD);

		// Push rule to the switch.

		if (sw.getId() == 1) {

			pushMessage(provider.getSwitch(4), fmBackward);
		} else {
			logger.info("this flow did not arrive from s1 but from {} on port {}", sw.getId(), pi.getInPort());
			pushMessage(provider.getSwitch(1), fmBackward);
		}
		pushPacketOut(sw, pi, inputPort, outputPort, matchForward);
		pushMessage(sw, fmForward);

	}

	private void installFlow(IOFSwitch sw, Short inputPort, Short outputPort) {
		logger.info("######## Begin Install Flow !!! #########");
		long cookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);

		// Create flow-mod one for IP another for ARP
		OFFlowMod fm = (OFFlowMod) provider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		OFMatch match = new OFMatch();
		match.setWildcards(Wildcards.FULL.getInt() & ~OFMatch.OFPFW_IN_PORT);

		match.setInputPort(inputPort);

		// create and configure actions
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(new OFActionOutput(outputPort, (short) 0));

		// set the empty list of actions for the two flow mods;
		fm.setActions(actions);
		fm.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE).setMatch(match)
				.setLengthU(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH);
		fm.setPriority((short) 50).setCommand(OFFlowMod.OFPFC_ADD);

		// Push rule to the switch.
		pushMessage(sw, fm);
	}

	private void pushMessage(IOFSwitch sw, OFFlowMod fm) {

		try {
			sw.write(fm, null);
			sw.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		logger.info("######## End Install Flow !!! #########");
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
			sw.flush();
		} catch (IOException e) {
		}
		logger.info("*** End *** Push Packet out !!! ***");
	}

}
