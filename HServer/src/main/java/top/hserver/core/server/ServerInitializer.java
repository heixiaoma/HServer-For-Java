package top.hserver.core.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.rtsp.RtspDecoder;
import io.netty.handler.codec.rtsp.RtspEncoder;
import io.netty.handler.ssl.OptionalSslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.util.ReferenceCountUtil;
import top.hserver.cloud.common.Msg;
import top.hserver.cloud.common.codec.RpcDecoder;
import top.hserver.cloud.common.codec.RpcEncoder;
import top.hserver.cloud.server.handler.RpcServerHandler;
import top.hserver.core.server.codec.WebSocketMqttCodec;
import top.hserver.core.server.context.ConstConfig;
import top.hserver.core.server.handlers.*;
import top.hserver.core.server.util.ByteBufUtil;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author hxm
 */
public class ServerInitializer extends ChannelInitializer<Channel> {

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new ProtocolDispatcher());
    }


    static class ProtocolDispatcher extends ByteToMessageDecoder {

        @Override
        public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            if (in.readableBytes() < 5) {
                return;
            }
            int readerIndex = in.readerIndex();
            final int magic1 = in.getByte(readerIndex);
            final int magic2 = in.getByte(readerIndex + 1);
            if (isHttp(magic1, magic2)) {
                ByteBuf copy = in.copy();
                byte[] bytes = ByteBufUtil.byteBufToBytes(copy);
                String s = new String(bytes);
                if (magic1 == 'O' && magic2 == 'P' && s.indexOf("rtsp://") > 0) {
                    dispatchRTSP(ctx);
                } else if (magic1 == 'G' && magic2 == 'E' && s.indexOf("Sec-WebSocket-Protocol: mqtt") > 0) {
                    dispatchWebMqtt(ctx);
                } else {
                    dispatchHttp(ctx);
                }
                ReferenceCountUtil.release(copy);
            } else if (isMqtt(magic1, magic2)) {
                dispatchMqtt(ctx);
            } else {
                dispatchRpc(ctx);
            }
        }

        private boolean isMqtt(int magic1, int magic2) {
            return magic1 == 16 && magic2 == 44;
        }

        private void dispatchRTSP(ChannelHandlerContext ctx) {
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.addLast(new RtspDecoder());
            pipeline.addLast(new RtspEncoder());
            pipeline.addLast(ConstConfig.BUSINESS_EVENT, new RtspServerHandler());
            pipeline.remove(this);
            ctx.fireChannelActive();
        }

        private boolean isHttp(int magic1, int magic2) {
            return
                    magic1 == 'G' && magic2 == 'E' || // GET
                            magic1 == 'P' && magic2 == 'O' || // POST
                            magic1 == 'P' && magic2 == 'U' || // PUT
                            magic1 == 'H' && magic2 == 'E' || // HEAD
                            magic1 == 'O' && magic2 == 'P' || // OPTIONS
                            magic1 == 'P' && magic2 == 'A' || // PATCH
                            magic1 == 'D' && magic2 == 'E' || // DELETE
                            magic1 == 'T' && magic2 == 'R' || // TRACE
                            magic1 == 'C' && magic2 == 'O';   // CONNECT
        }

        private void dispatchHttp(ChannelHandlerContext ctx) {
            ChannelPipeline pipeline = ctx.pipeline();

            if (ConstConfig.sslContext != null) {
                pipeline.addLast(new OptionalSslHandler(ConstConfig.sslContext));
            }

            if (ConstConfig.WRITE_LIMIT != null && ConstConfig.READ_LIMIT != null) {
                pipeline.addLast(new GlobalTrafficShapingHandler(ctx.executor().parent(), ConstConfig.WRITE_LIMIT, ConstConfig.READ_LIMIT));
            }
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(ConstConfig.HTTP_CONTENT_SIZE));
            //有websocket才走他
            if (WebSocketServerHandler.WebSocketRouter.size() > 0) {
                pipeline.addLast(ConstConfig.BUSINESS_EVENT, new WebSocketServerHandler());
            }
            if (ConstConfig.DEFAULT_STALE_CONNECTION_TIMEOUT != null) {
                pipeline.addLast(new IdleStateHandler(0, ConstConfig.DEFAULT_STALE_CONNECTION_TIMEOUT, 0, TimeUnit.MILLISECONDS));
            }
            pipeline.addLast(new HServerContentHandler());
            pipeline.addLast(ConstConfig.BUSINESS_EVENT, new RouterHandler());
            pipeline.remove(this);
            ctx.fireChannelActive();
        }

        private void dispatchMqtt(ChannelHandlerContext ctx) {
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.addLast(MqttEncoder.INSTANCE);
            pipeline.addLast(new MqttDecoder());
            pipeline.addLast(ConstConfig.BUSINESS_EVENT, MqttHeartBeatBrokerHandler.INSTANCE);
            pipeline.remove(this);
            ctx.fireChannelActive();
        }


        private void dispatchWebMqtt(ChannelHandlerContext ctx) {
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(ConstConfig.HTTP_CONTENT_SIZE));
            pipeline.addLast(new HttpContentCompressor());
            pipeline.addLast(new WebSocketServerProtocolHandler("/mqtt", "mqtt,mqttv3.1,mqttv3.1.1", true, 65536));
            pipeline.addLast(new WebSocketMqttCodec());
            pipeline.addLast(new MqttDecoder());
            pipeline.addLast(MqttEncoder.INSTANCE);
            pipeline.addLast(ConstConfig.BUSINESS_EVENT, MqttHeartBeatBrokerHandler.INSTANCE);
            pipeline.remove(this);
            ctx.fireChannelActive();
        }


        private void dispatchRpc(ChannelHandlerContext ctx) {
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.addLast(new RpcDecoder(Msg.class));
            pipeline.addLast(new RpcEncoder(Msg.class));
            pipeline.addLast(ConstConfig.BUSINESS_EVENT, "RpcServerProviderHandler", new RpcServerHandler());
            pipeline.remove(this);
            ctx.fireChannelActive();
        }
    }
}
