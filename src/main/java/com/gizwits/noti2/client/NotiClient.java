package com.gizwits.noti2.client;

import com.google.gson.Gson;
import io.netty.channel.Channel;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * @author Feel
 * @date 2017/6/16
 * @email fye@gizwits.com
 * @since 0.0.1
 */
public class NotiClient implements IService {

    private static final Logger logger = LoggerFactory.getLogger(NotiClient.class);
    private int port = 2017;
    private String host = "snoti.gizwits.com";
    private BoostrapClient client;
    private Message message;
    private BlockingQueue<Message> queue;
    private BlockingQueue<String> receiveQueue;
    private Callback callback;
    private Gson gson = new Gson();
    private ExecutorService executorService = Executors.newCachedThreadPool(new DefaultThreadFactory("--NotiClientThread-"));
    private static final String lineSeparator = "\n";
    private AtomicBoolean destoryFlag = new AtomicBoolean(false);
    private int maxFrameLength = 8192;

    public NotiClient() {

    }

    public static NotiClient build() {
        return new NotiClient();
    }

    public NotiClient setPort(int port) {
        this.port = port;
        return this;
    }

    public NotiClient setHost(String host) {
        this.host = host;
        return this;
    }

    /**
     * 设置接收单条数据帧长度,默认值是8192
     *
     * @param maxFrameLength
     * @return
     */
    public NotiClient setMaxFrameLength(int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;
        return this;
    }

    public int getMaxFrameLength() {
        return this.maxFrameLength;
    }

    @Override
    public void init() {
        queue = new ArrayBlockingQueue<Message>(50000);
        receiveQueue = new ArrayBlockingQueue<String>(50000);

        if (callback != null) {
            callback.callback(NotiEvent.INIT);
        }
    }


    @Override
    public void doStart() {

        init();
        destoryFlag.set(false);
        this.client = new BoostrapClient(this).setOption(host, port).login(message);

        executorService.execute(this.client);

        if (callback != null) {
            callback.callback(NotiEvent.SATRT);
        }

    }

    public void startPushMessage() {

        if (!destoryFlag.get() && this.client.isRunning()) {

            executorService.execute(() -> {
                while (this.client.isRunning()) {

                    Channel channel = client.getChannelFuture().channel();

                    if (!channel.isOpen() || !channel.isWritable()) {
                        logger.info("channel isOpen:{},channel isWritable :{}", channel.isOpen(), channel.isWritable());
                        restart();

                    } else if (channel.isWritable()) {
                        String message = gson.toJson(message());
                        if ((message != "") && (!message.equalsIgnoreCase("null")) && (message != null)) {
                            channel.writeAndFlush(message + lineSeparator);
                        }
                    }

                }

                destoryFlag.set(true);


            });
        }

    }

    @Override
    public void doStop() {
        destory();
    }


    @Override
    public void restart() {
        if (client != null) {
            destoryFlag.set(false);
            client.restart();
            if (callback != null) {
                callback.callback(NotiEvent.RESATRT);
            }
            if (!isRunning()) {
                destory();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return client.isRunning();
    }

    @Override
    public void destory() {

        try {

            destoryFlag.set(true);

            while (!queue.isEmpty()) {
                TimeUnit.MILLISECONDS.sleep(100);
            }
            if (!this.executorService.isShutdown()) {
                this.executorService.shutdownNow();
            }

            if (client != null) {
                client.destory();
            }

            if (callback != null) {
                callback.callback(NotiEvent.DESTORY);
            }

        } catch (InterruptedException e) {
            logger.error("{}", e);
        }
    }


    public void addListener(Callback callback) {

        this.callback = callback;

    }

    /**
     * @param msg
     * @return
     */
    public boolean dispatch(String msg) {
        if (msg != null) {
            try {
                receiveQueue.put(msg);
                return true;
            } catch (InterruptedException e) {
                logger.error("{}", e);
            }
        }
        return false;
    }

    /**
     * 接收推送事件消息
     *
     * @return
     */
    public String receiveMessage() {

        try {
            return receiveQueue.take();
        } catch (InterruptedException e) {

        }

        return null;
    }

    /**
     * 消息是否处理完
     *
     * @return
     */
    public boolean messageNone() {

        return receiveQueue.isEmpty();
    }

    private Message message() {

        try {
            if (isRunning()) {
                return queue.take();
            }

        } catch (InterruptedException e) {

        }
        return null;
    }

    /**
     * 使用者需要自己处理推送失败的消息
     *
     * @param msg
     * @return
     */
    private boolean putMessage(Message msg) {
        if (msg != null) {
            try {
                if (isRunning() && !destoryFlag.get()) {
                    queue.put(msg);
                    return true;
                }
            } catch (InterruptedException e) {
                logger.error("{}", e);
            }
        }

        return false;
    }


    /**
     * 单个product_key 登入
     *
     * @param productKey
     * @param authId
     * @param authSecret
     * @param subkey
     * @param prefetchCount
     * @param events
     * @return
     */
    public NotiClient login(String productKey, String authId, String authSecret, String subkey, int prefetchCount, List<Events> events) {
        LoginData data = new LoginData();
        data.setProductKey(productKey);
        data.setAuthId(authId);
        data.setAuthSecret(authSecret);
        data.setSubkey(subkey);
        data.setEvents(events);
        Message message = new Message<LoginData>();
        message.setPrefetchCount(prefetchCount);
        message.setCmd(Command.LOGIN);
        message.setData(Arrays.asList(data));
        this.message = message;
        return this;
    }

    /**
     * 单个product_key 登入
     *
     * @param productKey
     * @param authId
     * @param authSecret
     * @param subkey
     * @param events
     * @return
     */
    public NotiClient login(String productKey, String authId, String authSecret, String subkey, List<Events> events) {
        LoginData data = new LoginData();
        data.setProductKey(productKey);
        data.setAuthId(authId);
        data.setAuthSecret(authSecret);
        data.setSubkey(subkey);
        data.setEvents(events);
        Message message = new Message<LoginData>();
        message.setCmd(Command.LOGIN);
        message.setData(Arrays.asList(data));
        this.message = message;
        return this;
    }

    /**
     * 单个product_key 登入
     *
     * @param prefetchCount
     * @param loginData
     * @return
     */
    public NotiClient login(int prefetchCount, LoginData loginData) {
        Message message = new Message<LoginData>();
        message.setPrefetchCount(prefetchCount);
        message.setCmd(Command.LOGIN);
        message.setData(Arrays.asList(loginData));
        this.message = message;
        return this;
    }


    /**
     * 单个product_key 登入
     *
     * @param loginData
     * @return
     */
    public NotiClient login(LoginData loginData) {
        Message message = new Message<LoginData>();
        message.setCmd(Command.LOGIN);
        message.setData(Arrays.asList(loginData));
        this.message = message;
        return this;
    }


    /**
     * 多个product_key 登入
     *
     * @param prefetchCount
     * @param loginData
     * @return
     */
    public NotiClient login(int prefetchCount, List<LoginData> loginData) {
        Message message = new Message<LoginData>();
        message.setPrefetchCount(prefetchCount);
        message.setCmd(Command.LOGIN);
        message.setData(loginData);
        this.message = message;
        return this;
    }

    /**
     * 多个product_key 登入
     *
     * @param loginData
     * @return
     */
    public NotiClient login(List<LoginData> loginData) {
        Message message = new Message<LoginData>();
        message.setCmd(Command.LOGIN);
        message.setData(loginData);
        this.message = message;
        return this;
    }

    /**
     * 发送远程设备控制
     *
     * @param productKey
     * @param mac
     * @param did
     * @param attrs
     */
    public boolean sendControlMessage(String productKey, String mac, String did, Map attrs) {
        Message message = new Message<ControlData>();
        message.setCmd(Command.REMOTE_CONTROL_REQ);
        message.setMsgId(UUID.randomUUID().toString());
        ControlData data = new ControlData();
        data.setCmd(DataCommand.WRITE_ATTRS);

        Payload payload = new Payload();
        payload.setProductKey(productKey);
        payload.setMac(mac);
        payload.setDid(did);
        payload.setAttrs(attrs);
        data.setData(payload);
        message.setData(Arrays.asList(data));
        return this.putMessage(message);
    }

    /**
     * 发送远程设备控制
     *
     * @param productKey
     * @param mac
     * @param did
     * @param cmd
     * @param raw
     */
    public boolean sendControlMessage(String productKey, String mac, String did, DataCommand cmd, Byte[] raw) {
        Message message = new Message<ControlData>();
        message.setCmd(Command.REMOTE_CONTROL_REQ);
        ControlData data = new ControlData();
        data.setCmd(cmd);
        Payload payload = new Payload();
        payload.setProductKey(productKey);
        payload.setMac(mac);
        payload.setDid(did);
        payload.setRaw(raw);
        data.setData(payload);
        message.setData(Arrays.asList(data));
        return this.putMessage(message);

    }

    /**
     * 批量发送远程设备控制
     *
     * @param controlMessage
     */
    public boolean sendControlMessage(List<RemoteControlData> controlMessage) {

        Message message = new Message<ControlData>();
        message.setCmd(Command.REMOTE_CONTROL_REQ);
        ArrayList<Data> listData = new ArrayList<>();
        for (RemoteControlData remoteControlData : controlMessage) {
            ControlData data = new ControlData();
            data.setCmd(remoteControlData.getCmd());
            Payload controlData = new Payload();
            controlData.setProductKey(remoteControlData.getProductKey());
            controlData.setMac(remoteControlData.getMac());
            controlData.setDid(remoteControlData.getDid());
            controlData.setRaw(remoteControlData.getRaw());
            controlData.setAttrs(remoteControlData.getAttrs());
            data.setData(controlData);
            listData.add(data);
        }
        message.setData(listData);
        return this.putMessage(message);

    }


}
