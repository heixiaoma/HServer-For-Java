package top.hserver.core.queue;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.WorkHandler;
import top.hserver.core.ioc.IocUtil;
import top.hserver.core.queue.fmap.MemoryData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author hxm
 */

public class QueueEventHandler implements EventHandler<QueueData>, WorkHandler<QueueData> {

    private String queueName;
    private Method method;


    public QueueEventHandler(String queueName, Method method) {
        this.queueName = queueName;
        this.method = method;
    }

    @Override
    public void onEvent(QueueData event, long sequence, boolean endOfBatch) throws Exception {
        invoke(event);
    }

    @Override
    public void onEvent(QueueData event) throws Exception {
        invoke(event);
    }

    private void invoke(QueueData queueData) {
        Object[] args = queueData.getArgs();
        try {
            method.setAccessible(true);
            method.invoke(IocUtil.getBean(queueName), args);
        } catch (Exception e) {
            if (e instanceof InvocationTargetException) {
                ((InvocationTargetException) e).getTargetException().printStackTrace();
            } else {
                e.printStackTrace();
            }
        }finally {
            MemoryData.remove(queueData.getId());
        }
    }
}