package com.dianping.swallow.web.service;

import java.util.List;

import com.dianping.swallow.web.model.stats.ProducerIpStatsData;

/**
 * 
 * @author qiyin
 *
 *         2015年9月15日 下午5:40:02
 */
public interface ProducerIpStatsDataService {
	
	boolean insert(ProducerIpStatsData ipStatsData);

	List<ProducerIpStatsData> find(String topicName, String ip, long startKey, long endKey);
	
	long findAvgQps(String topicName, String ip, long startKey, long endKey);

}
