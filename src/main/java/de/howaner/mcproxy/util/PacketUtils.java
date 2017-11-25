package de.howaner.mcproxy.util;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import java.io.IOException;

public class PacketUtils {

	public static int readVarInt(ByteBuf buffer) {
		int number = 0;
		int round = 0;
		byte currentByte;

		do {
			currentByte = buffer.readByte();
			number |= (currentByte & 127) << round++ * 7;

			if (round > 5) {
				throw new RuntimeException("VarInt is too big");
			}
		} while ((currentByte & 128) == 128);

		return number;
	}

	public static void writeVarInt(ByteBuf buffer, int number) {
		while ((number & -128) != 0) {
			buffer.writeByte(number & 127 | 128);
			number >>>= 7;
		}

		buffer.writeByte(number);
	}

	public static int getVarIntLength(int number) {
		if ((number & 0xFFFFFF80) == 0) {
			return 1;
		} else if ((number & 0xFFFFC000) == 0) {
			return 2;
		} else if ((number & 0xFFE00000) == 0) {
			return 3;
		} else if ((number & 0xF0000000) == 0) {
			return 4;
		}
		return 5;
	}

	public static String readString(ByteBuf buffer) throws IOException {
		int length = readVarInt(buffer);

		if (length > 32767) {
			throw new IOException("The received encoded string buffer length is longer than maximum allowed (" + length + " > 32767)");
		} else if (length < 0) {
			throw new IOException("The received encoded string buffer length is less than zero! Weird string!");
		} else {
			byte[] stringBytes = new byte[length];
			buffer.readBytes(stringBytes);

			return new String(stringBytes, Charsets.UTF_8);
		}
	}

	public static void writeString(ByteBuf buffer, String string) throws IOException {
		byte[] var2 = string.getBytes(Charsets.UTF_8);

		if (var2.length > 32767) {
			throw new IOException("String too big (was " + string.length() + " bytes encoded, max " + 32767 + ")");
		} else {
			writeVarInt(buffer, var2.length);
			buffer.writeBytes(var2);
		}
	}

}
