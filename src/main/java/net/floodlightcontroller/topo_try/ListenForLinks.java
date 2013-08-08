package net.floodlightcontroller.topo_try;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListenForLinks implements ITopologyListener, IFloodlightModule {

	protected static Logger logger;
	ITopologyService topo;
	ILinkDiscoveryService discover;

	//
	// // **********************
	// // *LinkDiscoveryListener
	// // **********************
	//
	// @Override
	// public void linkDiscoveryUpdate(LDUpdate update) {
	// // TODO Auto-generated method stub
	//
	// }
	//
	// @Override
	// public void linkDiscoveryUpdate(List<LDUpdate> updateList) {
	// // TODO Auto-generated method stub
	//
	// }

	// **********************
	// *FloodLightModule
	// **********************

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
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(ITopologyService.class);
		l.add(ILinkDiscoveryService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		topo = context.getServiceImpl(ITopologyService.class);
		discover = context.getServiceImpl(ILinkDiscoveryService.class);
		logger = LoggerFactory.getLogger(ListenForLinks.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		topo.addListener(this);

	}

	// **********************
	// *TopologyListener
	// **********************

	@Override
	public void topologyChanged(List<LDUpdate> linkUpdates) {
		logger.info("**** topology has changed!!! *******");
		Map<Link, LinkInfo> links = discover.getLinks();

		Set<Link> keySet = links.keySet();

		Iterator<Link> it = keySet.iterator();

		logger.info("****** The links in the topology are :");

		while (it.hasNext()) {
			Link l = it.next();

			logger.info("******LINK:  sw {}:{} ---->  ", l.getSrc(), l.getSrcPort());
			logger.info("********************* ---->  sw {}:{}", l.getDst(), l.getDstPort());

		}

	}
}