package com.dianping.swallow.web.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dianping.swallow.web.common.Pair;
import com.dianping.swallow.web.dao.ProducerServerResourceDao;
import com.dianping.swallow.web.model.alarm.QPSAlarmSetting;
import com.dianping.swallow.web.model.resource.ProducerServerResource;
import com.dianping.swallow.web.service.AbstractSwallowService;
import com.dianping.swallow.web.service.ProducerServerResourceService;

/**
 * @author mingdongli
 *
 *         2015年8月10日下午4:48:36
 */
@Service("producerServerResourceService")
public class ProducerServerResourceServiceImpl extends AbstractSwallowService implements ProducerServerResourceService {

	@Autowired
	private ProducerServerResourceDao producerServerResourceDao;

	@Override
	public boolean insert(ProducerServerResource producerServerResource) {

		return producerServerResourceDao.insert(producerServerResource);
	}

	@Override
	public boolean update(ProducerServerResource producerServerResource) {

		return producerServerResourceDao.update(producerServerResource);
	}

	@Override
	public int remove(String ip) {

		return producerServerResourceDao.remove(ip);
	}

	@Override
	public ProducerServerResource findByIp(String ip) {

		return producerServerResourceDao.findByIp(ip);
	}

	@Override
	public ProducerServerResource findDefault() {

		return producerServerResourceDao.findDefault();
	}

	@Override
	public Pair<Long, List<ProducerServerResource>> findProducerServerResourcePage(int offset, int limit) {

		return producerServerResourceDao.findProducerServerResourcePage(offset, limit);
	}

	@Override
	public List<ProducerServerResource> findAll() {

		return producerServerResourceDao.findAll();
	}

	@Override
	public ProducerServerResource buildProducerServerResource(String ip, String hostName) {
		ProducerServerResource serverResource = new ProducerServerResource();
		serverResource.setIp(ip);
		serverResource.setAlarm(true);
		serverResource.setActive(true);
		serverResource.setCreateTime(new Date());
		serverResource.setUpdateTime(new Date());
		serverResource.setHostname(hostName);
		serverResource.setIsQpsAlarm(true);
		ProducerServerResource defaultResource = findDefault();
		if (defaultResource == null) {
			serverResource.setAlarm(false);
			serverResource.setSaveAlarmSetting(new QPSAlarmSetting());
		} else {
			serverResource.setSaveAlarmSetting(defaultResource.getSaveAlarmSetting());
		}
		return serverResource;
	}
}
