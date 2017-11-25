package tv.rewinside.replayserver.netty;

import de.howaner.mcproxy.util.PacketUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.DecoderException;
import java.util.List;

public class MCLengthDeserializer extends ByteToMessageDecoder {

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		in.markReaderIndex();

		final byte[] buf = new byte[3];
		for (int i = 0; i < buf.length; i++) {
			if (!in.isReadable()) {
				in.resetReaderIndex();
				return;
			}

			buf[i] = in.readByte();
			if (buf[i] >= 0) {
				ByteBuf buffer = null;
				int length;
				try {
					buffer = Unpooled.wrappedBuffer(buf);
					length = PacketUtils.readVarInt(buffer);
				} finally {
					if (buffer != null)
						buffer.release();
				}
				if (length == 0)
					throw new CorruptedFrameException("Empty packet!");

				if (in.readableBytes() < length) {
					in.resetReaderIndex();
					return;
				} else if (length > 2097152) {
					throw new DecoderException("Packet size of " + length + " is larger than protocol maximum of 2097152");
				} else {
					if (in.hasMemoryAddress()) {
						out.add(in.slice(in.readerIndex(), length).retain());
						in.skipBytes(length);
					} else {
						ByteBuf dst = ctx.alloc().directBuffer(length);
						in.readBytes(dst);
						out.add(dst);
					}
					return;
				}
			}
		}

		throw new CorruptedFrameException("length wider than 21-bit");
	}
}
