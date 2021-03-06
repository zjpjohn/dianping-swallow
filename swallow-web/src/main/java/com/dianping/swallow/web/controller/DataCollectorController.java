package com.dianping.swallow.web.controller;

import com.dianping.swallow.common.internal.action.SwallowAction;
import com.dianping.swallow.common.internal.action.SwallowActionWrapper;
import com.dianping.swallow.common.internal.action.impl.CatActionWrapper;
import com.dianping.swallow.common.internal.exception.SwallowException;
import com.dianping.swallow.common.server.monitor.data.structure.ConsumerMonitorData;
import com.dianping.swallow.common.server.monitor.data.structure.ProducerMonitorData;
import com.dianping.swallow.web.monitor.ConsumerDataRetriever;
import com.dianping.swallow.web.monitor.ProducerDataRetriever;
import com.dianping.swallow.web.service.IPCollectorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;


/**
 * @author mengwenchao
 *
 * 2015年4月14日 下午9:24:38
 */
@Controller
public class DataCollectorController extends AbstractController{
	
	@Autowired
	private ProducerDataRetriever producerDataRetriever;
	
	@Autowired
	private ConsumerDataRetriever consumerDataRetriever;
	
	@Autowired
	private IPCollectorService ipCollectorService;
	
	private static final Logger logger = LogManager.getLogger(DataCollectorController.class);
	
	@RequestMapping(value = "/api/stats/producer", method = RequestMethod.POST)
	@ResponseBody
	public void addProducerMonitor(@RequestBody final ProducerMonitorData  producerMonitorData) throws IOException{
		
		if(logger.isDebugEnabled()){
			logger.debug("[addProducerMonitor]" + producerMonitorData);
		}
		SwallowActionWrapper action = new CatActionWrapper("DataCollectorController", "addProducerMonitor");
		action.doAction(new SwallowAction() {
			
			@Override
			public void doAction() throws SwallowException {
				producerDataRetriever.add(producerMonitorData);
				ipCollectorService.addStatisIps(producerMonitorData);
			}
		});
		
	}

	@RequestMapping(value = "/api/stats/consumer", method = RequestMethod.POST)
	@ResponseBody
	public void addConsumerMonitor(@RequestBody final ConsumerMonitorData consumerMonitorData) throws IOException{

		if(logger.isDebugEnabled()){
			logger.debug("[addConsumerMonitor]" + consumerMonitorData);
		}
		SwallowActionWrapper action = new CatActionWrapper("DataCollectorController", "addConsumerMonitor");
		action.doAction(new SwallowAction() {

			@Override
			public void doAction() throws SwallowException {
				
				consumerDataRetriever.add(consumerMonitorData);
				ipCollectorService.addStatisIps(consumerMonitorData);
			}
		});

	}

}
