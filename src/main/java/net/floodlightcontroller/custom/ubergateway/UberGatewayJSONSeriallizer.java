package net.floodlightcontroller.custom.ubergateway;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class UberGatewayJSONSeriallizer extends JsonSerializer<UberGatewayRule> {

	@Override
	public void serialize(UberGatewayRule theRule, JsonGenerator jGen, SerializerProvider arg2) throws IOException, JsonProcessingException {

		jGen.writeStartObject();
		jGen.writeStringField("ruleName", theRule.getRuleName());

		jGen.writeFieldName("ruleMatch");
		jGen.writeStartObject();
		Byte b = new Byte(theRule.getMatch().getNetworkProtocol());
		System.out.println("Attempting to write nwProto field name");
		jGen.writeNumberField("nwProto", theRule.getMatch().getNetworkProtocol());

		Short dataLayerType = theRule.getMatch().getDataLayerType();
		jGen.writeNumberField("dataLayerType", dataLayerType);

		if (theRule.getCareAboutPortNo()) {
			jGen.writeBooleanField("careAboutPortNo", theRule.getCareAboutPortNo());
			jGen.writeNumberField("transportDestination", theRule.getMatch().getTransportDestination());
		}

		jGen.writeEndObject();

		// Write outport
		System.out.println("Attempting to write outport");
		jGen.writeNumberField("outport", theRule.getOutport());

		jGen.writeEndObject();
	}

}
