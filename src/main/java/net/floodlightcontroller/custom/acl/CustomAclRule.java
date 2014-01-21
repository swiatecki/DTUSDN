package net.floodlightcontroller.custom.acl;

import org.openflow.protocol.OFMatch;

public class CustomAclRule {
	private byte nwProto;
	private int networkAddress;
	private short networkMask;
	private short transportPort;
	private Operation operation;
	private Direction direction;

	public enum Operation {
		ALLOW, BLOCK
	}

	public enum Direction {
		INBOUND, OUTBOUND
	}

	public CustomAclRule() {

	}

	public CustomAclRule(byte nProto, int nAddr, short nMask, short tPort, Direction dir, Operation op) {
		setNw_proto(nProto);
		setNetworkAddress(nAddr);
		setNetworkMask(nMask);
		setTransportPort(tPort);
		setDirection(dir);
		operation = op;
	}

	public Operation getOperation() {
		return operation;
	}

	public void setOperation(Operation operation) {
		this.operation = operation;
	}

	public byte getNw_proto() {
		return nwProto;
	}

	public void setNw_proto(byte nw_proto) {
		this.nwProto = nw_proto;
	}

	public int getNetworkAddress() {
		return networkAddress;
	}

	public void setNetworkAddress(int networkAddress) {
		this.networkAddress = networkAddress;
	}

	public short getNetworkMask() {
		return networkMask;
	}

	public void setNetworkMask(short networkMask) {
		this.networkMask = networkMask;
	}

	public short getTransportPort() {
		return transportPort;
	}

	public void setTransportPort(short transportPort) {
		this.transportPort = transportPort;
	}

	public Direction getDirection() {
		return direction;
	}

	public void setDirection(Direction direction) {
		this.direction = direction;
	}

	public boolean matchesPacket(OFMatch flowDefinition) {
		// extract relevant headers from the OFMatch obejct : port, protocol,
		// net address...
		int netDest = flowDefinition.getNetworkDestination();
		int netDestMask = flowDefinition.getNetworkDestinationMaskLen();

		int netSrc = flowDefinition.getNetworkSource();
		int netSrcMask = flowDefinition.getNetworkSourceMaskLen();

		short trDst = flowDefinition.getTransportDestination();
		byte ntProto = flowDefinition.getNetworkProtocol();

		if (ntProto == nwProto && trDst == transportPort) {
			if ((direction == Direction.INBOUND && ipInSubnet(netDest, netDestMask))
					|| (direction == Direction.OUTBOUND && ipInSubnet(netSrc, netSrcMask))) {
				return true;
			}
		}
		return false;
	}

	/*
	 * This method verifies if the IP address of the packet lies within the
	 * address space (subnet) defined in the ACL
	 */
	private boolean ipInSubnet(int netDest, int netDestMask) {
		int subnetMask = ((1 << networkMask) - 1) << (32 - networkMask);
		int subnetIP = (networkAddress & subnetMask) >> (32 - networkMask);
		int packetTruncatedIP = (netDest & subnetMask) >> (32 - networkMask);
		return (subnetIP == packetTruncatedIP);
	}
}
