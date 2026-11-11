package l1j.server.server.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.ReferenceCountUtil;
import java.util.List;

final class SessionPipeline {

    static final class ReadBudget extends ChannelInboundHandlerAdapter {
        
        final NettySession session;

        ReadBudget(NettySession s) {
            session = s;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf) || !session.rawRead(buf.readableBytes())) {
                ReferenceCountUtil.release(msg);
                return;
            }
            ctx.fireChannelRead(msg);
        }
    }

    static final class Frames extends ByteToMessageDecoder {
        
        final NettySession session;

        Frames(NettySession s) {
            session = s;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            if (session.isClosing()) {
                in.skipBytes(in.readableBytes());
                return;
            }
            
            SessionApplication.Framing f = session.framing();
            
            if (in.readableBytes() == 0) {
                return;
            }
            
            if (in.readableBytes() < f.prefixBytes()) {
                session.partialFrame();
                return;
            }
            
            long declared = f.prefixBytes() == 2 
                    ? in.getUnsignedShortLE(in.readerIndex())
                    : in.getUnsignedIntLE(in.readerIndex());
                    
            long body = f.lengthIncludesPrefix() ? declared - f.prefixBytes() : declared;
            long wire = body + f.prefixBytes();
            
            if (body < f.minBodyBytes() || body > f.maxBodyBytes() || wire > session.config.maxFrameBytes) {
                session.close(CloseReason.INVALID_FRAME);
                in.skipBytes(in.readableBytes());
                return;
            }
            
            if (in.readableBytes() < wire) {
                session.partialFrame();
                return;
            }
            
            in.skipBytes(f.prefixBytes());
            byte[] encrypted = new byte[(int) body];
            in.readBytes(encrypted);
            
            session.frameComplete(encrypted, (int) wire);
        }
    }

    static final class Lifecycle extends ChannelInboundHandlerAdapter {
        
        final NettySession session;

        Lifecycle(NettySession s) {
            session = s;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            try {
                session.opened();
            } catch (Exception e) {
                session.close(CloseReason.INITIALIZATION_ERROR);
            }
            ctx.fireChannelActive();
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            session.writable();
            ctx.fireChannelWritabilityChanged();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            try {
                session.inactive();
            } finally {
                ctx.fireChannelInactive();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            session.close(CloseReason.IO_ERROR);
        }
    }
}