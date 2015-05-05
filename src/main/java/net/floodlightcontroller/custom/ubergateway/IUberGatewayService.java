/**
 * 
 */
package net.floodlightcontroller.custom.ubergateway;

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * @author student
 * 
 */
public interface IUberGatewayService extends IFloodlightService {

	// public ConcurrentCircularBuffer<SwitchMessagePair> getBuffer();
	public String getStatus();

}
