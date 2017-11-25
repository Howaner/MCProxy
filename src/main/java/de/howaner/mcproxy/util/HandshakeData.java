package de.howaner.mcproxy.util;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import lombok.Data;

@Data
public class HandshakeData {
	private int protocolVersion;
	private String hostname;
	private int port;
	private int nextState;

	public void read(ByteBuf buf) throws IOException {
		this.protocolVersion = PacketUtils.readVarInt(buf);
		this.hostname = PacketUtils.readString(buf);
		this.port = buf.readUnsignedShort();
		this.nextState = PacketUtils.readVarInt(buf);

		// Remove FML suffix from forge
		if (this.hostname.endsWith("FML\0"))
			this.hostname = this.hostname.substring(0, this.hostname.length() - 4);
	}

}
