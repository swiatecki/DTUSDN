package net.floodlightcontroller.topo_try;

import java.util.Set;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.topology.ITopologyListener;

/********
 * This interface exposes pieces of functionality from TopologyManager. If you
 * need to extract some functionality from TopologyManager, just add more
 * methods here and then get an implementation of the interface.
 * 
 * @author student
 */

public interface ITopologyInstanceService extends IFloodlightService {

	public Set<Long> getSwitches();

	public void addListener(ITopologyListener listener);

}
