package com.dianping.swallow.consumer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;

import com.dianping.swallow.common.codec.JsonDecoder;
import com.dianping.swallow.common.codec.JsonEncoder;
import com.dianping.swallow.consumer.netty.MessageClientHandler;

public class ConsumerClient {

	private ClientBootstrap bootstrap;
	
	private MessageListener listener;
	
	public ClientBootstrap getBootstrap() {
		return bootstrap;
	}

	public MessageListener getListener() {
		return listener;
	}

	public void setListener(MessageListener listener) {
		this.listener = listener;
	}

	/**
	 * 开始连接服务器，同时把连slave的线程启起来。
	 * @param address
	 */
	public void beginConnect(InetSocketAddress address){
		init();
	   	ConSlaveThread slave = new ConSlaveThread();
    	slave.setBootstrap(bootstrap);
	   	Thread slaveThread = new Thread(slave);
	   	slaveThread.start();
	   	while(true){
	   		synchronized(bootstrap){
		   		ChannelFuture future = bootstrap.connect();
		   		future.getChannel().getCloseFuture().awaitUninterruptibly();//等待channel关闭，否则一直阻塞!	
		   	}
	   		try {
				Thread.sleep(1000);//TODO 配置变量
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	   	}	
	}
	
	//连接swollowC，获得ChannelFuture
    public void init(){
    	bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));
        final MessageClientHandler handler = new MessageClientHandler(this);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {  
            @Override  
            public ChannelPipeline getPipeline() throws Exception {  
            ChannelPipeline pipeline = Channels.pipeline();
            pipeline.addLast("frameDecoder", new ProtobufVarint32FrameDecoder());
            pipeline.addLast("jsonDecoder", new JsonDecoder());
            pipeline.addLast("frameEncoder", new ProtobufVarint32LengthFieldPrepender());
            pipeline.addLast("jsonEncoder", new JsonEncoder());
            pipeline.addLast("handler", handler);
            return pipeline;  
            }  
        }); 
    }
    
}