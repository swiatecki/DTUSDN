package net.floodlightcontroller.topo_try;

import java.util.Set;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.topology.ITopologyListener;

public interface ITopologyInstanceService extends IFloodlightService {

	public Set<Long> getSwitches();

	public void addListener(ITopologyListener listener);

}
