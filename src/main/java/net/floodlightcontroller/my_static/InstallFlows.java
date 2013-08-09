package net.floodlightcontroller.my_static;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;

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
	private IFloodlightProviderService flService;
	private Logger logger;
	ILinkDiscoveryService discoveryService;
	private short FLOWMOD_IDLE_TIMEOUT = 120;
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

		// install flows in all the switches
		pushFlowMod(pi, sw.getId(), pi.getInPort(), outPortId, cntx);

		return null;
	}

	private void pushFlowMod(OFPacketIn pi, long switchId, short inpPortId, short outPortId, FloodlightContext cntx)
			throws Exception {
		// Create an application cookie.
		long cookie = AppCookie.makeCookie(3, 0);

		// Need to set up the path s1 ---> s5

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
		// pushMessage(switchId, fm, cntx);

	}

}
