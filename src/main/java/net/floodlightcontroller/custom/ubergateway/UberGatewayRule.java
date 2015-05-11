package net.floodlightcontroller.custom.ubergateway;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.Wildcards;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = UberGatewayJSONSeriallizer.class)
@JsonDeserialize(using = UberGatewayJSONDeSeriallizer.class)
public class UberGatewayRule {

	public static final byte PROTOCOL_ICMP = 0x1;

	public static final byte PROTOCOL_TCP = 0x6;

	public static final byte PROTOCOL_UDP = 0x11;

	public static final short DL_TYPE_IP = 2048; // 0x0800

	private Boolean careAboutPortNo;

	// private byte nwProto;

	private String ruleName;

	private OFMatch match;
	private short outport;
	private short priority;

	public UberGatewayRule(short outport, String RuleName) {
		this.match = new OFMatch();
		match.setWildcards(Wildcards.FULL.getInt());
		this.outport = outport;
		setRuleName(RuleName);

		careAboutPortNo = false;

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

		// Matches ICMP / tcp / UDP

		if (m.getNetworkProtocol() == PROTOCOL_ICMP && m.getNetworkProtocol() == match.getNetworkProtocol()) {
			// ICMP does not have a port number, so no reason to check for it..

			return true;

		} else if (m.getNetworkProtocol() == match.getNetworkProtocol()) {
			// Either TCP or UDP (or other

			if (careAboutPortNo) {

				if (m.getTransportDestination() == match.getTransportDestination()) {

					// Port mathes rule

					return true;
				} else {

					return false;
				}

			} else {
				// We dont care about ports, but the transporttech (TCP/UDP) is
				// ok

				return true;

			}

		}

		// No match
		return false;

		/*
		 * if (m.getNetworkProtocol() == match.getNetworkProtocol()) {
		 * 
		 * return true;
		 * }
		 */

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
		// System.out.println("Pre:" + w.getInt());
		match.setWildcards(w.matchOn(Wildcards.Flag.DL_TYPE));
		// System.out.println("post:" + match.getWildcards());
	}

	public void setTransportDst(short transportDestination) {
		// Set the TransDst AND update the wildcard

		match.setTransportDestination(transportDestination);

		Wildcards w = match.getWildcardObj();

		match.setWildcards(w.matchOn(Wildcards.Flag.TP_DST));

		// Okay, we now care about the port!

		careAboutPortNo = true;

	}

	public short getOutport() {
		return this.outport;
	}

	public Boolean getCareAboutPortNo() {

		return careAboutPortNo;
	}

	public short getPriority() {

		return priority;
	}

	public void setPriority(short priority) {
		this.priority = priority;
	}

}
