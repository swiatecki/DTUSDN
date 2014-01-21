package net.floodlightcontroller.custom.firewall;

public class CustomFirewallRule {

	private String host1;
	private String host2;

	public CustomFirewallRule() {
	}

	public CustomFirewallRule(String host1, String host2) {
		this.host1 = host1;
		this.host2 = host2;
	}

	public String getHost1() {
		return host1;
	}

	public void setHost1(String host1) {
		this.host1 = host1;
	}

	public String getHost2() {
		return host2;
	}

	public void setHost2(String host2) {
		this.host2 = host2;
	}

	public boolean matches(String netSource, String netDest) {
		if (netSource.equals(host1) && netDest.equals(host2) || netSource.equals(host2) && netDest.equals(host1)) {
			return true;
		}
		return false;
	}

}
