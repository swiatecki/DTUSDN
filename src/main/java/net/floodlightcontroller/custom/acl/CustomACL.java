package net.floodlightcontroller.custom.acl;

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

/*
 * for this module L3+L4 ACL will be applied on traffic.
 * this module uses the Forwarding module of FL
 */

public class CustomACL implements IFloodlightModule, IOFMessageListener {

	private IFloodlightProviderService provider;
	private static Logger logger;
	private ArrayList<CustomAclRule> aclRules;
	public static final int CUSTOM_ACL_APP_ID = 14;

	static {
		AppCookie.registerApp(CUSTOM_ACL_APP_ID, "CustomACL");
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
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());

		if (packetMatchesAnyAcl(match)) {
			logger.info("*** Packet matches ACL ---> BLOCK ***");
			installAclFlow(sw, pi);
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

	private void installAclFlow(IOFSwitch sw, OFPacketIn pi) {
		logger.info("######## Begin Install ACL Flow !!! #########");
		long cookie = AppCookie.makeCookie(CUSTOM_ACL_APP_ID, 0);

		// Create flow-mod
		OFFlowMod fm = (OFFlowMod) provider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);

		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		match.setWildcards(Wildcards.FULL.getInt() & ~OFMatch.OFPFW_DL_TYPE & ~OFMatch.OFPFW_NW_SRC_ALL
				& ~OFMatch.OFPFW_NW_DST_ALL & ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_TP_DST & ~OFMatch.OFPFW_TP_SRC);

		// create and configure actions
		List<OFAction> actions = new ArrayList<OFAction>();

		// set the empty list of actions
		fm.setActions(actions);
		fm.setCookie(cookie).setHardTimeout((short) 0).setIdleTimeout((short) 0)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE).setMatch(match).setLengthU(OFFlowMod.MINIMUM_LENGTH);
		fm.setPriority((short) 100).setCommand(OFFlowMod.OFPFC_ADD);

		// Push rule to the switch.
		pushMessage(sw, fm);
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
