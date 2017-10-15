package com.xuhuawei.construct.nio;

import com.xuhuawei.construct.MyConstants;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Administrator on 2017/10/15 0015.
 */
public class NioServer {
    private ServerSocketChannel server;
    private int port = 8080;
    private Selector selector;
    //接受数据的缓存区
    private ByteBuffer receiverBuffer = ByteBuffer.allocate(1024);
    private ByteBuffer senderBuffer = ByteBuffer.allocate(1024);
    private Map<SelectionKey, String> receiverHashmap = new HashMap<SelectionKey, String>();

    public NioServer(int port) {
        this.port = port;
        try {
            //先把高速公路建立起来
            server = ServerSocketChannel.open();
            //关卡打开了 可以多路复用了
            server.bind(new InetSocketAddress(this.port));
            //默认是阻塞的 必须手动设置非阻塞
            server.configureBlocking(false);
            //管家开始营业
            selector = Selector.open();

            //告诉管家  Boss已经准备就绪 等会有客人 要通知我一下
            server.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("NIO 服务已经启动，监听的端口是" + this.port);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void listener() throws IOException {
        while (true) {
            int value = selector.select();
            if (value == 0) {
                continue;
            }
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();
            while (iterator.hasNext()) {
                //来一个 处理一个
                process(iterator.next());
                //处理完之后打发走
                iterator.remove();
            }
        }
    }

    private void process(SelectionKey key) throws IOException {
        if (key.isAcceptable()) { //判断客户有没有跟我们BOSS建立好连接
            SocketChannel client = server.accept();
            client.configureBlocking(false);
            //告诉管家 我要开始读数据了 这一次轮询完 下一次轮询 下一次在key.isReadable()就开始读数据了
            client.register(selector, SelectionKey.OP_READ);
        } else if (key.isReadable()) {//判断是否可以读数据了
            SocketChannel client = (SocketChannel) key.channel();
            receiverBuffer.clear();
            int length = client.read(receiverBuffer);
            if (length > 0) {
                String str = new String(receiverBuffer.array(), 0, length);
                receiverHashmap.put(key, str);
                System.out.println("receiverData:" + str);
            }
            //这一次读完了 下一次就可以写了
            client.register(selector, SelectionKey.OP_WRITE);
        } else if (key.isWritable()) {//是否可以写数据了
            if (!receiverHashmap.containsKey(key)) {
                return;
            }
            SocketChannel client = (SocketChannel) key.channel();
            String sendMsg = receiverHashmap.remove(key) + ",你好，你的任务已经处理完毕!";
            System.out.println("serverSend:" + sendMsg);
            //首先清空一下数据
            senderBuffer.clear();
            senderBuffer.put(sendMsg.getBytes());
            //相当于刷新推送
            senderBuffer.flip();
            //开始写数据 必须写一个buffer
            client.write(senderBuffer);
            //我已经写好了 告诉管家 下一次我又可以读了
            client.register(selector, SelectionKey.OP_READ);
        }
    }

    public static void main(String[] args) {
        NioServer server = new NioServer(MyConstants.port);
        try {
            server.listener();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
