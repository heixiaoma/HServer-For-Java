package cn.hserver.plugin.gateway.handler.http4;

import cn.hserver.core.ioc.IocUtil;
import cn.hserver.plugin.gateway.bean.Http4Data;
import cn.hserver.plugin.gateway.business.Business;
import cn.hserver.plugin.gateway.business.BusinessHttp4;
import cn.hserver.plugin.gateway.business.BusinessHttp7;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;

public class Http4FrontendHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(Http4FrontendHandler.class);

    private Channel outboundChannel;
    private final String host;
    private static BusinessHttp4 businessHttp4;

    public Http4FrontendHandler(String host) {
        this.host = host;
        for (Business business : IocUtil.getListBean(Business.class)) {
            if (business instanceof BusinessHttp4) {
               businessHttp4 = (BusinessHttp4)business;
            }
        }
    }

    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            businessHttp4.close(ch);
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }


    public void write(ChannelHandlerContext ctx, Object msg) {
        outboundChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                ctx.channel().read();
            } else {
                future.channel().close();
                ReferenceCountUtil.release(msg);
            }
        });
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        try{
        Object in = businessHttp4.in(ctx,new Http4Data(host, msg));
        if (in == null) {
            return;
        }

        if (outboundChannel != null) {
            if (outboundChannel.isActive()) {
                write(ctx, msg);
            } else {
                outboundChannel.close();
                outboundChannel = null;
                ReferenceCountUtil.release(msg);
            }
        } else {
            final Channel inboundChannel = ctx.channel();
            Bootstrap b = new Bootstrap();
            b.group(inboundChannel.eventLoop());
            b.option(ChannelOption.AUTO_READ, true)
                    .channel(NioSocketChannel.class)
                    .handler(new Http4BackendHandler(inboundChannel, businessHttp4));
            SocketAddress proxyHost = businessHttp4.getProxyHost(ctx, new Http4Data(host, msg), ctx.channel().remoteAddress());
            b.connect(proxyHost).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        outboundChannel = future.channel();
                        inboundChannel.read();
                        write(ctx, msg);
                        businessHttp4.connectController(true,null);
                    } else {
                        inboundChannel.close();
                        ReferenceCountUtil.release(msg);
                        if (businessHttp4.connectController(false,future.cause())){
                            b.connect(proxyHost).addListener(this);
                        }
                    }
                }
            });
        }
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        } finally {
            ctx.close();
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        closeOnFlush(ctx.channel());
    }
}
