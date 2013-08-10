package net.floodlightcontroller.my_static;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.routing.Link;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstallFlows implements IOFMessageListener, IFloodlightModule {
	private static final int STATIC_FLOW_APP_ID = 2;
	private IFloodlightProviderService flService;
	private Logger logger;
	ILinkDiscoveryService discoveryService;
	private short FLOWMOD_IDLE_TIMEOUT = 0;
	private short FLOWMOD_HARD_TIMEOUT = 0;

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		// No services offered in this module
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		// No service implementations
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightService.class);
		return null;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		flService = context.getServiceImpl(IFloodlightProviderService.class);
		logger = LoggerFactory.getLogger(InstallFlows.class);
		discoveryService = context.getServiceImpl(ILinkDiscoveryService.class);

	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		flService.addOFMessageListener(OFType.PACKET_IN, this);
	}

	// **********************
	// *IOFMessageListener
	// **********************

	@Override
	public String getName() {
		return "InstallFlows";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

		OFPacketIn pi = (OFPacketIn) msg;

		// get the IDs for all the switches

		logger.info("!!!!!!!Flow comming from switch {}", sw.getId());

		Set<Long> sws = getAllSwitches();

		ArrayList<Long> swArray = new ArrayList<Long>();
		Iterator<Long> it = sws.iterator();
		// logger.info("**** A list of switches : ************");

		while (it.hasNext()) {
			// logger.info(" ****Swich Id : {}", it.next());
			swArray.add(it.next());
		}

		// install flows in all the switches
		short outP3 = 3;
		short outP2 = 2;
		short outP1 = 1;

		if (sw.getId() == 1) {
			// install first round of flows s5->s4->s3->s2->s1
			try {
				pushFlowMod(pi, swArray.get(4), outP2, outP1, cntx);
				pushFlowMod(pi, swArray.get(3), outP2, outP3, cntx);
				pushFlowMod(pi, swArray.get(2), outP2, outP3, cntx);
				pushFlowMod(pi, swArray.get(1), outP2, outP3, cntx);
				pushFlowMod(pi, swArray.get(0), outP1, outP2, cntx);
				// send packet out
				pushPacketOut(sw, pi, outP1, outP2);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} else if (sw.getId() == 5) {
			// install first round of flows s5->s4->s3->s2->s1
			try {
				pushFlowMod(pi, swArray.get(0), outP2, outP1, cntx);
				pushFlowMod(pi, swArray.get(1), outP3, outP2, cntx);
				pushFlowMod(pi, swArray.get(2), outP3, outP2, cntx);
				pushFlowMod(pi, swArray.get(3), outP3, outP2, cntx);
				pushFlowMod(pi, swArray.get(4), outP1, outP2, cntx);
				// send packet out
				pushPacketOut(sw, pi, outP1, outP2);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} else {
			logger.info(">>>>>>Flow arrived on port {} ", pi.getInPort());
			OFMatch match = new OFMatch();
			match.loadFromPacket(pi.getPacketData(), pi.getInPort());

			logger.info(">>>>>>Flow has Ether Type  {} ", match.getDataLayerType());
			logger.info(">>>>>>Flow has netowrk protocol  {} ", match.getNetworkProtocol());
		}

		return null;
	}

	private Set<Long> getAllSwitches() {
		Map<Long, Set<Link>> swLinkMap = discoveryService.getSwitchLinks();

		// Set<Long> sws = swLinkMap.keySet();

		return swLinkMap.keySet();
	}

	private void pushFlowMod(OFPacketIn pi, long switchId, short inpPortId, short outPortId, FloodlightContext cntx)
			throws Exception {
		// Create an application cookie.
		long cookie = AppCookie.makeCookie(STATIC_FLOW_APP_ID, 0);

		// Need to set up the path s1 ---> s5
		logger.info("######## Push Flow Mod ########");
		logger.info(" ########Installed flow on switch {}", switchId);
		logger.info(" ########Installed flow input port: {}  output port: {}", inpPortId, outPortId);
		// Create a match.
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		match.setInputPort(inpPortId);

		// match.get
		// Create an action.
		List<OFAction> actions = Collections.singletonList((OFAction) new OFActionOutput(outPortId, (short) 0xFFFF));

		// Create a FlowMod.
		OFFlowMod fm = (OFFlowMod) flService.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		fm.setIdleTimeout(FLOWMOD_IDLE_TIMEOUT).setHardTimeout(FLOWMOD_HARD_TIMEOUT)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE).setCookie(cookie).setCommand(OFFlowMod.OFPFC_ADD)
				.setMatch(match).setActions(actions)
				.setLengthU(OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH);

		// Push rule to the switch.
		pushMessage(switchId, fm, cntx);

	}

	private void pushMessage(long switchId, OFFlowMod fm, FloodlightContext cntx) {

		try {
			flService.getSwitch(switchId).write(fm, null);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		logger.info("######## End Install Flow !!! #########");

	}

	private void pushPacketOut(IOFSwitch sw, OFPacketIn pi, Short inPort, Short outPort) {
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		match.setInputPort(inPort);

		OFPacketOut pktOut = (OFPacketOut) flService.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
		short pktOutLength = (short) OFPacketOut.MINIMUM_LENGTH;

		logger.info("*** Begin *** Push Packet out !!! ***");
		logger.info("Packet arrived on port {}, DL_SRC: {} ", pi.getInPort(), match.getDataLayerSource());

		// buffer id, length, port
		pktOut.setBufferId(pi.getBufferId());
		pktOut.setInPort(pi.getInPort());
		pktOut.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
		pktOutLength += pktOut.getActionsLength();

		// actions
		List<OFAction> actions = new ArrayList<OFAction>(1);
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

}
