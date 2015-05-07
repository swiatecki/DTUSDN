/**
 * 
 */
package net.floodlightcontroller.custom.ubergateway;

import java.util.ArrayList;

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * @author student
 * 
 */
public interface IUberGatewayService extends IFloodlightService {

	// public ConcurrentCircularBuffer<SwitchMessagePair> getBuffer();
	public ArrayList<UberGatewayRule> getRules();

	public void addRule(UberGatewayRule inRule);

}
