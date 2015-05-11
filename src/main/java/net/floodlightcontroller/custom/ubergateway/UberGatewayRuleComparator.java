package net.floodlightcontroller.custom.ubergateway;

import java.util.Comparator;

public class UberGatewayRuleComparator implements Comparator<UberGatewayRule> {

	@Override
	public int compare(UberGatewayRule r1, UberGatewayRule r2) {

		return r2.getPriority() - r1.getPriority();

	}

}
