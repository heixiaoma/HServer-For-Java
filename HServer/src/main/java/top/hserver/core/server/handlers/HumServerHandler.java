package top.hserver.core.server.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.hserver.core.interfaces.HumAdapter;
import top.hserver.core.ioc.IocUtil;
import top.hserver.core.server.context.HumMessage;
import top.hserver.core.server.util.HumMessageUtil;

import java.util.List;

import static top.hserver.core.server.context.ConstConfig.SERVER_NAME;

public class HumServerHandler extends
        SimpleChannelInboundHandler<DatagramPacket> {
    private static final Logger log = LoggerFactory.getLogger(HumServerHandler.class);


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        ctx.close();
        cause.printStackTrace();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        HumMessage message = HumMessageUtil.getMessage(msg.content());
        if (message != null) {
            if (SERVER_NAME.equals(message.getType())) {
                log.debug("hum消息:{}", message.getData().toString());
            } else {
                List<HumAdapter> listBean = IocUtil.getListBean(HumAdapter.class);
                if (listBean != null) {
                    for (HumAdapter humAdapter : listBean) {
                        //交换角色，不要搞错了
                        humAdapter.message(message, new Hum(msg, ctx, Hum.Type.CLIENT));
                    }
                }
            }
        }
    }
}
