package com.dianping.swallow.web.alarmer.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.plexus.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.dianping.lion.client.ConfigCache;
import com.dianping.lion.client.ConfigChange;
import com.dianping.lion.client.LionException;
import com.dianping.swallow.common.internal.action.SwallowAction;
import com.dianping.swallow.common.internal.action.SwallowActionWrapper;
import com.dianping.swallow.common.internal.action.impl.CatActionWrapper;
import com.dianping.swallow.common.internal.exception.SwallowException;
import com.dianping.swallow.web.alarmer.container.AlarmResourceContainer;
import com.dianping.swallow.web.model.event.EventType;
import com.dianping.swallow.web.model.event.ServerEvent;
import com.dianping.swallow.web.model.event.ServerType;
import com.dianping.swallow.web.model.resource.ConsumerServerResource;
import com.dianping.swallow.web.service.IPCollectorService;
import com.dianping.swallow.web.util.NetUtil;

@Component
public class ConsumerPortAlarmer extends AbstractServiceAlarmer {

	private static final String CONSUMER_SERVER_MASTER_PORT_KEY = "swallow.consumer.server.master.port";

	private static final String CONSUMER_SERVER_SLAVE_PORT_KEY = "swallow.consumer.server.slave.port";

	private volatile int masterPort = 8081;

	private volatile int slavePort = 8082;

	private Map<String, Boolean> isSlaveIps = new ConcurrentHashMap<String, Boolean>();

	private static final String KEY_SPLIT = "&";

	@Autowired
	private IPCollectorService ipCollectorService;

	private ConfigCache configCache;

	@Autowired
	private AlarmResourceContainer resourceContainer;

	@Override
	public void doInitialize() throws Exception {
		super.doInitialize();
		try {
			configCache = ConfigCache.getInstance();
			masterPort = configCache.getIntProperty(CONSUMER_SERVER_MASTER_PORT_KEY);
			slavePort = configCache.getIntProperty(CONSUMER_SERVER_SLAVE_PORT_KEY);
			configCache.addChange(new ConfigChange() {
				@Override
				public void onChange(String key, String value) {
					if (key.equals(CONSUMER_SERVER_MASTER_PORT_KEY)) {
						masterPort = Integer.parseInt(value);
					} else if (key.equals(CONSUMER_SERVER_SLAVE_PORT_KEY)) {
						slavePort = Integer.parseInt(value);
					}
				}

			});
		} catch (LionException e) {
			logger.error("lion read consumer master slave port failed", e);
		}

	}

	@Override
	public void doAlarm() {
		SwallowActionWrapper catWrapper = new CatActionWrapper(CAT_TYPE, getClass().getSimpleName());
		catWrapper.doAction(new SwallowAction() {
			@Override
			public void doAction() throws SwallowException {
				checkPort();
			}
		});
	}

	public boolean checkPort() {

		List<ConsumerServerResource> cMasterReources = resourceContainer.findConsumerSlaveServerResources();
		List<ConsumerServerResource> cSlaveReources = resourceContainer.findConsumerSlaveServerResources();

		if (cMasterReources == null || cMasterReources.size() == 0 || cSlaveReources == null
				|| cSlaveReources.size() == 0) {
			logger.error("[checkPort] cannot find consumermaster or consumerslave reources.");
			return false;
		}
		int index = 0;
		for (ConsumerServerResource cMasterReource : cMasterReources) {
			String masterIp = cMasterReource.getIp();
			if (StringUtils.isBlank(masterIp) || !cMasterReource.isAlarm()) {
				continue;
			}
			ConsumerServerResource cSlaveReource = cSlaveReources.get(index);
			alarmPort(masterIp, cSlaveReource);
			index++;
		}
		return true;
	}

	private boolean alarmPort(String masterIp, ConsumerServerResource cSlaveReource) {
		String slaveIp = cSlaveReource.getIp();
		boolean usingMaster = checkPort(masterIp, masterPort);
		boolean usingSlave = checkPort(slaveIp, slavePort);
		String key = masterIp + KEY_SPLIT + slaveIp;
		if (!usingMaster && usingSlave) {
			isSlaveIps.put(masterIp, true);
			ServerEvent serverEvent = eventFactory.createServerEvent();
			serverEvent.setIp(masterIp).setSlaveIp(slaveIp).setServerType(ServerType.SLAVEPORT_OPENED)
					.setEventType(EventType.CONSUMER).setCreateTime(new Date());
			eventReporter.report(serverEvent);
			lastCheckStatus.put(key, false);
			return false;
		} else if (usingMaster && usingSlave) {
			isSlaveIps.put(masterIp, false);
			ServerEvent serverEvent = eventFactory.createServerEvent();
			serverEvent.setIp(masterIp).setSlaveIp(slaveIp).setServerType(ServerType.BOTHPORT_OPENED)
					.setEventType(EventType.CONSUMER).setCreateTime(new Date());
			eventReporter.report(serverEvent);
			lastCheckStatus.put(key, false);
			return false;
		} else if (!usingMaster && !usingSlave) {
			isSlaveIps.put(masterIp, false);
			ServerEvent serverEvent = eventFactory.createServerEvent();
			serverEvent.setIp(masterIp).setSlaveIp(slaveIp).setServerType(ServerType.BOTHPORT_UNOPENED)
					.setEventType(EventType.CONSUMER).setCreateTime(new Date());
			eventReporter.report(serverEvent);
			lastCheckStatus.put(key, false);
			return false;
		} else {
			isSlaveIps.put(masterIp, false);
			if (lastCheckStatus.containsKey(key) && !lastCheckStatus.get(key).booleanValue()) {
				ServerEvent serverEvent = eventFactory.createServerEvent();
				serverEvent.setIp(masterIp).setSlaveIp(slaveIp).setServerType(ServerType.PORT_OPENED_OK)
						.setEventType(EventType.CONSUMER).setCreateTime(new Date());
				eventReporter.report(serverEvent);
				lastCheckStatus.put(key, true);
			}
		}
		return true;
	}

	public int getMasterPort() {
		return masterPort;
	}

	public int getSlavePort() {
		return slavePort;
	}

	public boolean isSlaveOpen(String ip) {
		if (isSlaveIps.containsKey(ip)) {
			return isSlaveIps.get(ip);
		}
		return false;
	}

	private boolean checkPort(String ip, int port) {
		boolean usingPort = NetUtil.isPortOpen(ip, port);
		if (!usingPort) {
			threadSleep();
			usingPort = NetUtil.isPortOpen(ip, port);
		}
		return usingPort;
	}

}
