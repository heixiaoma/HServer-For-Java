package top.hserver.core.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.hserver.core.server.util.ExceptionUtil;
import top.hserver.core.server.util.SerializationUtil;
import top.hserver.core.ioc.IocUtil;
import top.hserver.core.ioc.annotation.queue.QueueHandler;
import top.hserver.core.ioc.annotation.queue.QueueListener;
import top.hserver.core.ioc.ref.PackageScanner;
import top.hserver.core.queue.fqueue.FQueue;
import top.hserver.core.queue.fqueue.exception.FileFormatException;
import top.hserver.core.server.util.NamedThreadFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static top.hserver.core.server.context.ConstConfig.PERSIST_PATH;


/**
 * @author hxm
 */
public class QueueDispatcher {
    private static final Logger log = LoggerFactory.getLogger(QueueDispatcher.class);
    private static final Map<String, QueueHandleInfo> handleMethodMap = new ConcurrentHashMap<>();
    private static final Map<String, FQueue> FQ = new ConcurrentHashMap<>();
    private static final int buffSize = 1024;

    private QueueDispatcher() {
    }

    public static void removeQueue(String queueName, boolean trueDelete) {
        QueueHandleInfo queueHandleInfo = handleMethodMap.get(queueName);
        if (queueHandleInfo != null && queueHandleInfo.getQueueFactory() != null) {
            queueHandleInfo.getQueueFactory().stop();
        }
        handleMethodMap.remove(queueName);
        FQueue fQueue = FQ.get(queueName);
        if (fQueue != null) {
            if (trueDelete) {
                fQueue.clear();
            }
            try {
                fQueue.close();
            } catch (IOException | FileFormatException e) {
                log.error(ExceptionUtil.getMessage(e));
            }
        }
        FQ.remove(queueName);
    }

    public static List<String> getAllQueueName() {
        return new ArrayList<>(FQ.keySet());
    }

    public static void addQueueListener(String queueName, Class clazz) {
        Object obj = IocUtil.getBean(clazz);
        if (obj == null) {
            log.error("{} 容器中不存在", clazz.getName());
            return;
        }
        QueueListener queueListener = obj.getClass().getAnnotation(QueueListener.class);
        if (queueListener == null) {
            log.error("{} 它不是一个消息监听器", clazz.getName());
            return;
        }
        IocUtil.addBean(queueName, obj);
        QueueHandleInfo eventHandleInfo = new QueueHandleInfo();
        eventHandleInfo.setQueueHandlerType(queueListener.type());
        eventHandleInfo.setQueueName(queueName);
        eventHandleInfo.setBufferSize(buffSize);
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            QueueHandler queueHandler = method.getAnnotation(QueueHandler.class);
            if (queueHandler != null) {
                if (queueHandler.size() > eventHandleInfo.getThreadSize()) {
                    eventHandleInfo.setThreadSize(queueHandler.size());
                }
                eventHandleInfo.add(new QueueHandleMethod(method, queueHandler.level(), queueHandler.size()));
                log.debug("寻找队列 [{}] 的方法 [{}.{}]", queueName, clazz.getSimpleName(),
                        method.getName());
            }
        }
        handleMethodMap.put(queueName, eventHandleInfo);
        initConfigQueue(eventHandleInfo);
    }

    /**
     * 初始化事件分发器
     */
    public static void init(PackageScanner scanner) throws IOException {
        // 载入事件处理类
        Set<Class<?>> classes = scanner.getAnnotationList(QueueListener.class);
        // 解析事件处理类
        for (Class<?> clazz : classes) {
            QueueListener queueListener = clazz.getAnnotation(QueueListener.class);
            if (queueListener == null) {
                continue;
            }
            Object obj;
            try {
                obj = clazz.newInstance();
            } catch (Exception e) {
                log.error("initialize " + clazz.getSimpleName() + " error", e);
                continue;
            }
            if (queueListener.queueName().trim().length() == 0) {
                IocUtil.addBean(obj);
                continue;
            }
            IocUtil.addBean(queueListener.queueName(), obj);
            QueueHandleInfo eventHandleInfo = new QueueHandleInfo();
            eventHandleInfo.setQueueHandlerType(queueListener.type());
            eventHandleInfo.setQueueName(queueListener.queueName());
            eventHandleInfo.setBufferSize(buffSize);
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                QueueHandler queueHandler = method.getAnnotation(QueueHandler.class);
                if (queueHandler != null) {
                    if (queueHandler.size() > eventHandleInfo.getThreadSize()) {
                        eventHandleInfo.setThreadSize(queueHandler.size());
                    }
                    eventHandleInfo.add(new QueueHandleMethod(method, queueHandler.level(), queueHandler.size()));
                    log.debug("寻找队列 [{}] 的方法 [{}.{}]", queueListener.queueName(), clazz.getSimpleName(),
                            method.getName());
                }
            }
            handleMethodMap.put(queueListener.queueName(), eventHandleInfo);
        }
    }

    private static void initConfigQueue(QueueHandleInfo v) {
        try {
            FQueue fQueue = new FQueue(PERSIST_PATH + File.separator + v.getQueueName());
            FQ.put(v.getQueueName(), fQueue);
        } catch (Exception ignored) {
        }
        QueueFactory queueFactory = new QueueFactoryImpl();
        queueFactory.createQueue(v.getQueueName(), v.getBufferSize(), v.getQueueHandlerType(), v.getQueueHandleMethods());
        v.setQueueFactory(queueFactory);
        v.getQueueFactory().start();
    }

    /**
     * 创建队列
     */
    public static void startTaskThread() {
        /**
         * 检查历史是否有，有的话先关闭掉
         */
        handleMethodMap.forEach((k, v) -> {
            if (v.getQueueFactory() != null) {
                v.getQueueFactory().stop();
            }
        });
        FQ.forEach((k, v) -> {
            try {
                v.close();
            } catch (Exception e) {
                log.error(ExceptionUtil.getMessage(e));
            }
        });
        FQ.clear();
        //再来重新开始
        handleMethodMap.forEach((k, v) -> {
            initConfigQueue(v);
        });
        Thread thread = new NamedThreadFactory("hserver_queue").newThread(() -> {
            while (true) {
                if (FQ.size() > 0) {
                    FQ.forEach((k, v) -> {
                        try {
                            QueueInfo queueInfo = queueInfo(k);
                            QueueHandleInfo queueHandleInfo = handleMethodMap.get(k);
                            if (queueHandleInfo == null) {
                                sleep();
                                return;
                            }
                            int threadSize = queueHandleInfo.getThreadSize();
                            if (queueInfo != null && (queueInfo.getBufferSize() - queueInfo.getRemainQueueSize() < threadSize)) {
                                byte[] poll;
                                if (threadSize == 1) {
                                    poll = v.peek();
                                } else {
                                    poll = v.poll();
                                }
                                if (poll != null) {
                                    QueueData deserialize = SerializationUtil.deserialize(poll, QueueData.class);
                                    dispatcherQueue(deserialize, deserialize.getQueueName());
                                } else {
                                    sleep();
                                }
                            } else {
                                sleep();
                            }
                        } catch (Exception e) {
                            sleep();
                            log.error(ExceptionUtil.getMessage(e));
                        }
                    });
                } else {
                    sleep();
                }
            }
        });
        thread.start();
    }

    /**
     * 分发队列
     *
     * @param queueName 事件URI
     */
    private static boolean dispatcherQueue(QueueData queueData, String queueName) {
        QueueHandleInfo queueHandleInfo = handleMethodMap.get(queueName);
        if (queueHandleInfo != null) {
            if (queueData != null) {
                queueData.setfQueue(FQ.get(queueName));
                queueData.setThreadSize(queueHandleInfo.getThreadSize());
                queueHandleInfo.getQueueFactory().producer(queueData);
            }
            return true;
        } else {
            log.error("不存在:{} 队列", queueName);
            return false;
        }
    }


    /**
     * 持久化存储
     *
     * @param queueName
     * @param args
     */
    public static boolean dispatcherSerializationQueue(String queueName, Object... args) {
        FQueue fQueue = FQ.get(queueName);
        if (fQueue == null) {
            log.error("不存在:{} 队列", queueName);
            return false;
        }
        fQueue.offer(SerializationUtil.serialize(new QueueData(queueName, args, null)));
        return true;
    }

    public static QueueInfo queueInfo(String queueName) {
        QueueHandleInfo queueHandleInfo = handleMethodMap.get(queueName);
        if (queueHandleInfo != null && queueHandleInfo.getQueueFactory() != null) {
            QueueInfo queueInfo = queueHandleInfo.getQueueFactory().queueInfo();
            queueInfo.setFqueueSize(FQ.get(queueName).size());
            return queueInfo;
        }
        return null;
    }


    private static void sleep(){
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            log.error(ExceptionUtil.getMessage(e));
        }
    }

}