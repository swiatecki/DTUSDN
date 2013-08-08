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
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyTopoManager implements IFloodlightModule, ITopologyListener {

	protected ITopologyInstanceService topoInstanceService;
	// protected ITopologyService topoService;
	protected Logger logger;

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
		l.add(ITopologyInstanceService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// topoService = context.getServiceImpl(ITopologyService.class);
		topoInstanceService = context.getServiceImpl(ITopologyInstanceService.class);
		logger = LoggerFactory.getLogger(MyTopoManager.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		topoInstanceService.addListener(this);
	}

	// *****************
	// *TopologyListener
	// *****************

	@Override
	public void topologyChanged(List<LDUpdate> linkUpdates) {
		Set<Long> sws = topoInstanceService.getSwitches();

		Iterator<Long> it = sws.iterator();

		while (it.hasNext()) {
			logger.info("****Switch : {}", it.next());
		}

	}
}
