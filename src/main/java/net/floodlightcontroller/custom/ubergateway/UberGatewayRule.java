package net.floodlightcontroller.custom.ubergateway;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.Wildcards;

public class UberGatewayRule {

	public static final byte PROTOCOL_ICMP = 0x1;

	public static final byte PROTOCOL_TCP = 0x6;

	public static final byte PROTOCOL_UDP = 0x11;

	// private byte nwProto;
	private int dstIP;
	private short transportPort;
	private String ruleName;

	private OFMatch match;
	private short outport;

	public UberGatewayRule(short outport, String RuleName) {
		this.match = new OFMatch();
		match.setWildcards(Wildcards.FULL.getInt());
		setOutport(outport);
		setRuleName(RuleName);

	}

	public int getDstIP() {
		return dstIP;
	}

	public short getTransportPort() {
		return transportPort;
	}

	public short getOutport() {
		return outport;
	}

	public void setOutport(short outport) {
		this.outport = outport;
	}

	public OFMatch getMatch() {
		System.out.println("Retriving a match: " + match.toString());
		return match;
	}

	public void setMatch(OFMatch match) {
		this.match = match;
		// this.match.setWildcards( Wildcards.FULL.getInt() & ~
	}

	public Boolean matches(OFMatch m) {

		if (m.getNetworkProtocol() == match.getNetworkProtocol()) {

			return true;
		}

		return false;

	}

	public String getRuleName() {
		return ruleName;
	}

	public void setRuleName(String ruleName) {
		this.ruleName = ruleName;
	}

	public void setNwProto(byte nwProto) {
		// Set the NWProto AND update the wildcard
		match.setNetworkProtocol(nwProto);
		Wildcards w = match.getWildcardObj();

		match.setWildcards(w.matchOn(Wildcards.Flag.NW_PROTO));
	}

	public void setDlType(short dataLayerType) {
		// Set the DLType AND update the wildcard
		match.setDataLayerType(dataLayerType);
		Wildcards w = match.getWildcardObj();

		match.setWildcards(w.matchOn(Wildcards.Flag.DL_TYPE));
	}

}
