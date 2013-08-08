package net.floodlightcontroller.topo_try;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import net.floodlightcontroller.core.types.MacVlanPair;
import net.floodlightcontroller.packet.Ethernet;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.LRULinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InspectTopo implements IFloodlightModule, IOFMessageListener {

	// flow-mod - for use in the cookie
	public static final int LEARNING_SWITCH_APP_ID = 1;
	// LOOK! This should probably go in some class that encapsulates
	// the app cookie management
	public static final int APP_ID_BITS = 12;
	public static final int APP_ID_SHIFT = (64 - APP_ID_BITS);
	public static final long LEARNING_SWITCH_COOKIE = (long) (LEARNING_SWITCH_APP_ID & ((1 << APP_ID_BITS) - 1)) << APP_ID_SHIFT;

	// more flow-mod defaults
	protected static short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 120; // in seconds
	protected static short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; // infinite
	protected static short FLOWMOD_PRIORITY = 100;

	protected IFloodlightProviderService flProvider;
	protected static Logger logger;
	protected Map<IOFSwitch, Map<MacVlanPair, Short>> swTable;

	@Override
	public String getName() {
		return "InspectTopo";
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
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		logger.info("MAC Addr :{}  seen  on switch {}", eth.getSourceMAC(), sw.getId());

		if (msg.getType() == OFType.PACKET_IN) {
			logger.info("Processing PacketIn message from {}", eth.getSourceMAC());
			processPktIn(sw, (OFPacketIn) msg, cntx);
		} else if (msg.getType() == OFType.FLOW_REMOVED) {
			logger.info("Flow removed !!!");
		}

		return Command.CONTINUE;

	}

	private void processPktIn(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		Long srcMac = Ethernet.toLong(match.getDataLayerSource());
		Long dstMac = Ethernet.toLong(match.getDataLayerDestination());
		Short vlan = match.getDataLayerVirtualLan();
		addToPortMap(sw, srcMac, vlan, pi.getInPort());

		Short outPort = getFromPortMap(sw, dstMac, vlan);

		if (outPort != null) {
			// install flow for this dstMac to the outPort
			logger.info("Packet has a match !!!");

			// pushPacketOut(sw, pi, outPort, match);

			installFLow(sw, pi, outPort, match);
		} else {
			// flood packet
			logger.info("Flood packet!!!");

			pushPacketOut(sw, pi, OFPort.OFPP_FLOOD.getValue(), match);
		}
	}

	private void installFLow(IOFSwitch sw, OFPacketIn pi, Short outPort, OFMatch match) {
		// pushPacketOut(sw, pi, outPort);

		logger.info("*** Begin *** Install Flow !!! ***");

		match.setWildcards(((Integer) sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).intValue()
				& ~OFMatch.OFPFW_IN_PORT & ~OFMatch.OFPFW_DL_VLAN & ~OFMatch.OFPFW_DL_SRC & ~OFMatch.OFPFW_DL_DST
				& ~OFMatch.OFPFW_NW_SRC_MASK & ~OFMatch.OFPFW_NW_DST_MASK);

		OFFlowMod flow = (OFFlowMod) flProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		flow.setMatch(match);
		flow.setCookie(InspectTopo.LEARNING_SWITCH_COOKIE);
		flow.setCommand(OFFlowMod.OFPFC_ADD);
		flow.setIdleTimeout(InspectTopo.FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		flow.setHardTimeout(InspectTopo.FLOWMOD_DEFAULT_HARD_TIMEOUT);
		flow.setPriority(InspectTopo.FLOWMOD_PRIORITY);
		// flow.setBufferId(OFPacketOut.BUFFER_ID_NONE);

		flow.setBufferId(pi.getBufferId());
		flow.setOutPort(OFPort.OFPP_NONE);
		flow.setFlags((short) (1 << 0));
		flow.setActions(Arrays.asList((OFAction) new OFActionOutput(outPort, (short) 0xffff)));
		flow.setLength((short) (OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH));
		logger.info(" Installed flow from port {} to port {}", pi.getInPort(), outPort);
		try {
			sw.write(flow, null);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		logger.info("*** End *** Install Flow !!! ***");

	}

	private void pushPacketOut(IOFSwitch sw, OFPacketIn pi, Short outPort, OFMatch match) {
		OFPacketOut pktOut = (OFPacketOut) flProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
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
		flProvider = context.getServiceImpl(IFloodlightProviderService.class);
		logger = LoggerFactory.getLogger(InspectTopo.class);
		swTable = new ConcurrentHashMap<IOFSwitch, Map<MacVlanPair, Short>>();
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		flProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}

	protected void addToPortMap(IOFSwitch sw, long mac, short vlan, short portVal) {
		Map<MacVlanPair, Short> swMap = swTable.get(sw);

		if (vlan == (short) 0xffff) {
			// OFMatch.loadFromPacket sets VLAN ID to 0xffff if the packet
			// contains no VLAN tag;
			// for our purposes that is equivalent to the default VLAN ID 0
			vlan = 0;
		}

		if (swMap == null) {
			// May be accessed by REST API so we need to make it thread safe
			swMap = Collections.synchronizedMap(new LRULinkedHashMap<MacVlanPair, Short>(20));
			swTable.put(sw, swMap);
		}
		swMap.put(new MacVlanPair(mac, vlan), portVal);
	}

	public Short getFromPortMap(IOFSwitch sw, long mac, short vlan) {
		if (vlan == (short) 0xffff) {
			vlan = 0;
		}
		Map<MacVlanPair, Short> swMap = swTable.get(sw);
		if (swMap != null)
			return swMap.get(new MacVlanPair(mac, vlan));

		// if none found
		return null;
	}

}
