package top.hserver.cloud.task;

import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import top.hserver.cloud.CloudManager;
import top.hserver.core.interfaces.TaskJob;
import top.hserver.core.server.context.ConstConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * @author hxm
 */
@Slf4j
public class BroadcastNacosTask implements TaskJob {

    private NamingService naming;

    @Override
    public void exec(Object... args) {
        //存在Rpc服务就上报吧,
        String rpcServerName = args[0].toString();
        //注册中心的
        String host = args[1].toString();
        String post = args[2].toString();
        String host1 = args[3].toString();
        String post2 = args[4].toString();
        Boolean flag = Boolean.valueOf(args[5].toString());
        try {
            if (naming == null) {
                naming = NamingFactory.createNamingService(host + ":" + post);
            }
            //消费者和提供者都上报服务器
            Instance instance = new Instance();
            instance.setIp(host1);
            instance.setPort(Integer.parseInt(post2));
            instance.setHealthy(true);
            instance.setWeight(0);
            if (CloudManager.isRpcService()) {
                Map<String, String> data = new HashMap<>(1);
                data.put("key", ConstConfig.OBJECT_MAPPER.writeValueAsString(CloudManager.getClasses().toString()));
                instance.setMetadata(data);
            }
            if (flag){
                Map<String, String> data = new HashMap<>(2);
                data.put("ip", host1);
                data.put("port",String.valueOf(CloudManager.port));
                instance.setMetadata(data);
            }
            naming.registerInstance(rpcServerName, instance);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Nacos 注册中心注册失败");
            naming = null;
        }
    }
}
