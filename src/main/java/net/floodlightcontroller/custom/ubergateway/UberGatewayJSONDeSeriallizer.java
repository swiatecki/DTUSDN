package net.floodlightcontroller.custom.ubergateway;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

public class UberGatewayJSONDeSeriallizer extends JsonDeserializer<UberGatewayRule> {

	@Override
	public UberGatewayRule deserialize(JsonParser jPar, DeserializationContext dContext) throws IOException, JsonProcessingException {

		JsonNode node = jPar.getCodec().readTree(jPar);
		String ruleName = node.get("ruleName").asText();

		System.out.println("Got ruleName OK");

		JsonNode ruleMatchNode = node.get("ruleMatch");
		System.out.println(ruleMatchNode.toString());

		// System.out.println(ruleMatchNode.path("nwProto").asText());

		System.out.println("Try dst parse");
		Boolean careAboutPortNo = ruleMatchNode.get("careAboutPortNo").asBoolean();

		String nwp = ruleMatchNode.get("nwProto").asText();
		Byte nwProto = Byte.decode("0x" + nwp);

		String dlt = ruleMatchNode.get("dataLayerType").asText();

		System.out.println("Got Raw DLT: " + dlt);
		short dataLayerType = Short.parseShort(dlt);

		System.out.println("Got DLT: " + (short) dataLayerType);
		System.out.println("Got rule name: " + ruleName);

		short outPort = Short.parseShort(node.get("outPort").asText());

		System.out.println("Port: " + outPort);

		UberGatewayRule u = new UberGatewayRule(outPort, ruleName);

		u.setNwProto(nwProto);
		u.getMatch().setDataLayerType(dataLayerType);

		if (careAboutPortNo) {
			System.out.println("Try dst parse");
			Short transportDestination = Short.parseShort(ruleMatchNode.get("transportDestination").asText());
			System.out.println("Got dst: " + transportDestination);
			u.setTransportDst(transportDestination);
		}

		return u;

	}
}
