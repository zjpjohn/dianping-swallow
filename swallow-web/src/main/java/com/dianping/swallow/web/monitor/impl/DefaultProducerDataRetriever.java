package com.dianping.swallow.web.monitor.impl;

import com.dianping.swallow.common.internal.action.SwallowCallableWrapper;
import com.dianping.swallow.common.internal.action.impl.CatCallableWrapper;
import com.dianping.swallow.common.internal.util.CommonUtils;
import com.dianping.swallow.common.server.monitor.data.QPX;
import com.dianping.swallow.common.server.monitor.data.StatisType;
import com.dianping.swallow.common.server.monitor.data.statis.AbstractAllData;
import com.dianping.swallow.common.server.monitor.data.statis.CasKeys;
import com.dianping.swallow.common.server.monitor.data.statis.ProducerAllData;
import com.dianping.swallow.common.server.monitor.data.statis.ProducerServerStatisData;
import com.dianping.swallow.common.server.monitor.data.structure.*;
import com.dianping.swallow.web.container.ResourceContainer;
import com.dianping.swallow.web.model.resource.TopicResource;
import com.dianping.swallow.web.model.stats.ProducerTopicStatsData;
import com.dianping.swallow.web.monitor.*;
import com.dianping.swallow.web.monitor.collector.MongoStatsDataCollector;
import com.dianping.swallow.web.monitor.collector.MongoStatsDataContainer;
import com.dianping.swallow.web.service.MongoStatsDataService;
import com.dianping.swallow.web.service.ProducerServerStatsDataService;
import com.dianping.swallow.web.service.ProducerTopicStatsDataService;
import com.dianping.swallow.web.util.ThreadFactoryUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author mengwenchao
 *         <p/>
 *         2015年4月21日 上午11:04:09
 */
@Component
public class DefaultProducerDataRetriever
        extends
        AbstractMonitorDataRetriever<ProducerTopicData, ProducerServerData, ProducerServerStatisData, ProducerMonitorData>
        implements ProducerDataRetriever {

    public static final String FACTORY_NAME = "ProducerOrderInDb";

    @Autowired
    private ProducerServerStatsDataService pServerStatsDataService;

    @Autowired
    private ProducerTopicStatsDataService pTopicStatsDataService;

    @Autowired
    private ResourceContainer resourceContainer;

    @Autowired
    private MongoStatsDataCollector mongoStatsDataCollector;

    @Autowired
    private MongoStatsDataService mongoStatsDataService;

    @Override
    public boolean dataExistInMemory(CasKeys keys, long start, long end) {
        return dataExistInMemory(keys, StatisType.SAVE, start, end);
    }

    public List<OrderStatsData> getOrder(int size) {
        return getOrder(size, getDefaultStart(), getDefaultEnd());
    }

    public List<OrderStatsData> getOrder(int size, long start, long end) {

        if (dataExistInMemory(new CasKeys(TOTAL_KEY, TOTAL_KEY), start, end)) {
            return getOrderInMemory(size, start, end);
        }

        return getOrderInDb(size, start, end);
    }

    public List<OrderStatsData> getOrderInMemory(int size, long start, long end) {
        Set<String> topics = statis.getTopics(false);
        if (topics == null) {
            return null;
        }
        OrderStatsData orderDelayResult = new OrderStatsData(size, createDelayDesc(TOTAL_KEY, StatisType.SAVE), start, end);
        OrderStatsData orderQpsResult = new OrderStatsData(size, createQpxDesc(TOTAL_KEY, StatisType.SAVE), start, end);
        long fromKey = getKey(start);
        long toKey = getKey(end);
        Iterator<String> iterator = topics.iterator();
        while (iterator.hasNext()) {
            String topicName = iterator.next();
            if (TOTAL_KEY.equals(topicName)) {
                continue;
            }
            NavigableMap<Long, StatisData> lastDatas = statis.getLastValueLessOrEqualThan(new CasKeys(TOTAL_KEY, topicName), StatisType.SAVE, toKey);
            NavigableMap<Long, StatisData> firstDatas = statis.getFirstValueGreaterOrEqualThan(new CasKeys(TOTAL_KEY, topicName), StatisType.SAVE, fromKey);
            if (lastDatas != null && !lastDatas.isEmpty() && firstDatas != null && !firstDatas.isEmpty()) {
                StatisData lastData = lastDatas.lastEntry().getValue();
                StatisData firstData = firstDatas.lastEntry().getValue();
                long subTotalDelay = lastData.getTotalDelay() - firstData.getTotalDelay();
                long subTotalCount = lastData.getTotalCount() - firstData.getTotalCount();
                orderDelayResult.add(new OrderEntity(topicName, StringUtils.EMPTY, subTotalDelay, subTotalCount));
                orderQpsResult.add(new OrderEntity(topicName, StringUtils.EMPTY, subTotalCount, getQpsSampleCount(start, end)));
            }
        }
        List<OrderStatsData> orderStatsDatas = new ArrayList<OrderStatsData>();
        orderStatsDatas.add(orderDelayResult);
        orderStatsDatas.add(orderQpsResult);
        return orderStatsDatas;
    }

    public List<OrderStatsData> getOrderInDb(int size, long start, long end) {
        long fromKey = getKey(start);
        long toKey = getKey(end);
        OrderStatsData delayOrderResult = new OrderStatsData(size, createDelayDesc(TOTAL_KEY, StatisType.SAVE), start,
                end);
        OrderStatsData qpxOrderResult = new OrderStatsData(size, createQpxDesc(TOTAL_KEY, StatisType.SAVE), start, end);
        List<TopicResource> topicResources = resourceContainer.findTopicResources(false);
        if (topicResources != null && topicResources.size() > 0) {
            QueryQrderTask queryQrderTask = new QueryQrderTask();
            for (TopicResource topicResource : topicResources) {
                String topicName = topicResource.getTopic();
                if (TOTAL_KEY.equals(topicName)) {
                    continue;
                }
                queryQrderTask.submit(new QueryOrderParam(topicName, fromKey, toKey, delayOrderResult, qpxOrderResult));
            }
            queryQrderTask.await();
        }
        List<OrderStatsData> orderStatsDatas = new ArrayList<OrderStatsData>();
        orderStatsDatas.add(delayOrderResult);
        orderStatsDatas.add(qpxOrderResult);
        return orderStatsDatas;
    }

    @Override
    public StatsData getSaveDelay(String topic, long start, long end) {

        if (dataExistInMemory(new CasKeys(TOTAL_KEY, topic), start, end)) {
            return getDelayInMemory(topic, StatisType.SAVE, start, end);
        }

        return getDelayInDb(topic, StatisType.SAVE, start, end);
    }

    protected StatsData getDelayInDb(String topic, StatisType type, long start, long end) {

        long startKey = getKey(start);
        long endKey = getKey(end);

        NavigableMap<Long, Long> rawData = pTopicStatsDataService.findSectionDelayData(topic, startKey, endKey);
        rawData = fillStatsData(rawData, startKey, endKey);
        return createStatsData(createDelayDesc(topic, StatisType.SAVE), rawData, start, end);
    }

    public StatsData getIpDelay(String topic, String ip, long start, long end) {
        if (dataExistInMemory(new CasKeys(TOTAL_KEY, topic, ip), start, end)) {
            return getIpDelayInMemory(topic, ip, StatisType.SAVE, start, end);
        }
        return getIpDelayInMemory(topic, ip, StatisType.SAVE, start, end);
    }

    public Map<String, StatsData> getAllIpDelay(String topic, long start, long end) {
        Map<String, StatsData> statsDatas = new HashMap<String, StatsData>();
        Set<String> keys = statis.getKeys(new CasKeys(TOTAL_KEY, topic), StatisType.SAVE);
        if (keys != null) {
            for (String key : keys) {
                if (TOTAL_KEY.equals(key)) {
                    continue;
                }
                statsDatas.put(key, getIpDelay(topic, key, start, end));
            }
            return statsDatas;
        }
        return null;
    }

    public Map<String, StatsData> getAllIpDelay(String topic) {
        return getAllIpDelay(topic, getDefaultStart(), getDefaultEnd());
    }

    @Override
    public StatsData getQpx(String topic, QPX qpx, long start, long end) {

        if (dataExistInMemory(new CasKeys(TOTAL_KEY, topic), start, end)) {
            return getQpxInMemory(topic, StatisType.SAVE, start, end);
        }
        return getQpxInDb(topic, StatisType.SAVE, start, end);
    }

    protected StatsData getQpxInDb(String topic, StatisType type, long start, long end) {
        long startKey = getKey(start);
        long endKey = getKey(end);

        NavigableMap<Long, Long> rawData = pTopicStatsDataService.findSectionQpsData(topic, startKey, endKey);
        rawData = fillStatsData(rawData, startKey, endKey);
        return createStatsData(createQpxDesc(topic, type), rawData, start, end);
    }

    public StatsData getIpQpx(String topic, String ip, long start, long end) {
        if (dataExistInMemory(new CasKeys(TOTAL_KEY, topic, ip), start, end)) {
            return getIpQpxInMemory(topic, ip, StatisType.SAVE, start, end);
        }
        return getIpQpxInMemory(topic, ip, StatisType.SAVE, start, end);
    }

    public Map<String, StatsData> getAllIpQpx(String topic, long start, long end) {
        Map<String, StatsData> statsDatas = new HashMap<String, StatsData>();
        Set<String> keys = statis.getKeys(new CasKeys(TOTAL_KEY, topic), StatisType.SAVE);
        if (keys != null) {
            for (String key : keys) {
                if (TOTAL_KEY.equals(key)) {
                    continue;
                }
                statsDatas.put(key, getIpQpx(topic, key, start, end));
            }
            return statsDatas;
        }
        return null;
    }

    public Map<String, StatsData> getAllIpQpx(String topic) {
        return getAllIpQpx(topic, getDefaultStart(), getDefaultEnd());
    }

    @Override
    public Map<String, StatsData> getServerQpx(QPX qpx, long start, long end) {

        if (dataExistInMemory(new CasKeys(TOTAL_KEY, TOTAL_KEY), start, end)) {
            return getServerQpxInMemory(qpx, StatisType.SAVE, start, end);
        }

        return getServerQpxInDb(qpx, StatisType.SAVE, start, end);
    }

    protected Map<String, StatsData> getServerQpxInDb(QPX qpx, StatisType type, long start, long end) {
        Map<String, StatsData> result = new HashMap<String, StatsData>();

        long startKey = getKey(start);
        long endKey = getKey(end);
        Map<String, NavigableMap<Long, Long>> statsDataMaps = pServerStatsDataService.findSectionQpsData(startKey,
                endKey);

        for (Map.Entry<String, NavigableMap<Long, Long>> statsDataMap : statsDataMaps.entrySet()) {
            String serverIp = statsDataMap.getKey();

            if (StringUtils.equals(TOTAL_KEY, serverIp)) {
                continue;
            }

            NavigableMap<Long, Long> statsData = statsDataMap.getValue();
            statsData = fillStatsData(statsData, startKey, endKey);
            result.put(serverIp, createStatsData(createServerQpxDesc(serverIp, type), statsData, start, end));

        }
        return result;
    }

    @Override
    public StatsData getQpx(String topic, QPX qpx) {

        return getQpx(topic, qpx, getDefaultStart(), getDefaultEnd());
    }

    @Override
    public StatsData getSaveDelay(final String topic) throws Exception {

        SwallowCallableWrapper<StatsData> wrapper = new CatCallableWrapper<StatsData>(CAT_TYPE, "getSaveDelay");

        return wrapper.doCallable(new Callable<StatsData>() {

            @Override
            public StatsData call() throws Exception {

                return getSaveDelay(topic, getDefaultStart(), getDefaultEnd());
            }
        });
    }

    @Override
    protected AbstractAllData<ProducerTopicData, ProducerServerData, ProducerServerStatisData, ProducerMonitorData> createServerStatis() {

        return new ProducerAllData();
    }

    @Override
    public Map<String, StatsData> getServerQpx(QPX qpx) {
        return getServerQpx(qpx, getDefaultStart(), getDefaultEnd());
    }

    @Override
    public Map<MongoStatsDataCollector.MongoStatsDataKey, StatsData> getMongoQpx(QPX qpx, long start, long end) {
        if (dataExistInMemory(new CasKeys(TOTAL_KEY, TOTAL_KEY), start, end)) {
            return getMongoQpxInMemory(qpx, StatisType.SAVE, start, end);
        }

        return getMongoQpxInDb(qpx, StatisType.SAVE, start, end);
    }

    protected Map<MongoStatsDataCollector.MongoStatsDataKey, StatsData> getMongoQpxInMemory(QPX qpx, StatisType type, long start, long end) {

        Map<MongoStatsDataCollector.MongoStatsDataKey, StatsData> result = new HashMap<MongoStatsDataCollector.MongoStatsDataKey, StatsData>();

        long startKey = getKey(start);
        long endKey = getKey(end);

        Map<MongoStatsDataCollector.MongoStatsDataKey, NavigableMap<Long, Long>> mongoQpxs = mongoStatsDataCollector.retrieveAllQpx(qpx);

        for (Map.Entry<MongoStatsDataCollector.MongoStatsDataKey, NavigableMap<Long, Long>> entry : mongoQpxs.entrySet()) {

            MongoStatsDataCollector.MongoStatsDataKey mongoIp = entry.getKey();
            NavigableMap<Long, Long> mongoQpx = entry.getValue();
            if (mongoQpx != null) {
                mongoQpx = mongoQpx.subMap(startKey, true, endKey, true);
                mongoQpx = fillStatsData(mongoQpx, startKey, endKey);
            }
            result.put(mongoIp, createStatsData(createMongoQpxDesc(mongoIp, type), mongoQpx, start, end));
        }

        return result;
    }

    protected Map<MongoStatsDataCollector.MongoStatsDataKey, StatsData> getMongoQpxInDb(QPX qpx, StatisType type, long start, long end) {
        Map<MongoStatsDataCollector.MongoStatsDataKey, StatsData> result = new HashMap<MongoStatsDataCollector.MongoStatsDataKey, StatsData>();

        long startKey = getKey(start);
        long endKey = getKey(end);
        Map<String, NavigableMap<Long, Long>> statsDataMaps = mongoStatsDataService.findSectionQpsData(qpx, startKey, endKey);

        for (Map.Entry<String, NavigableMap<Long, Long>> statsDataMap : statsDataMaps.entrySet()) {
            String serverIp = statsDataMap.getKey();

            NavigableMap<Long, Long> statsData = statsDataMap.getValue();
            statsData = fillStatsData(statsData, startKey, endKey);
            MongoStatsDataCollector.MongoStatsDataKey mongoStatsDataKey = mongoStatsDataCollector.generateMongoStatsDataKey(serverIp);
            result.put(mongoStatsDataKey, createStatsData(createMongoQpxDesc(mongoStatsDataKey, type), statsData, start, end));

        }
        return result;
    }

    @Override
    public Map<MongoStatsDataCollector.MongoStatsDataKey, StatsData> getMongoQpx(QPX qpx) {
        return getMongoQpx(qpx, getDefaultStart(), getDefaultEnd());
    }

    @Override
    public String getMongoDebugInfo(String server) {
        Map<MongoStatsDataCollector.MongoStatsDataKey, MongoStatsDataContainer> mongoStatsDataMap = mongoStatsDataCollector.getMongoStatsDataMap();
        String mongoStatsDataString = "";
        if(mongoStatsDataMap != null){
            mongoStatsDataString = mongoStatsDataMap.toString();
        }
        Map<String, String> topicToMongo = mongoStatsDataCollector.getTopicToMongo();
        String topicToMongoString = "";
        if(topicToMongo != null){
            topicToMongoString = topicToMongo.toString();
        }

        return mongoStatsDataString + "\n----------------\n" + topicToMongoString;

    }

    protected StatsDataDesc createDelayDesc(String topic, String ip, StatisType type) {
        return new ProducerIpStatsDataDesc(topic, ip, type.getDelayDetailType());
    }

    @Override
    protected StatsDataDesc createDelayDesc(String topic, StatisType type) {

        return new ProducerStatsDataDesc(topic, type.getDelayDetailType());
    }

    protected StatsDataDesc createQpxDesc(String topic, String ip, StatisType type) {
        return new ProducerIpStatsDataDesc(topic, ip, type.getQpxDetailType());
    }

    @Override
    protected StatsDataDesc createQpxDesc(String topic, StatisType type) {

        return new ProducerStatsDataDesc(topic, type.getQpxDetailType());
    }

    @Override
    protected StatsDataDesc createServerQpxDesc(String serverIp, StatisType type) {

        return new ProducerServerDataDesc(serverIp, MonitorData.TOTAL_KEY, type.getQpxDetailType());
    }

    @Override
    protected StatsDataDesc createMongoQpxDesc(MongoStatsDataCollector.MongoStatsDataKey server, StatisType type) {
        return new MongoStatsDataDesc(server.getCatalog(), server.getIp(), type.getQpxDetailType());
    }

    @Override
    protected StatsDataDesc createServerDelayDesc(String serverIp, StatisType type) {

        return new ProducerServerDataDesc(serverIp, MonitorData.TOTAL_KEY, type.getDelayDetailType());
    }

    private ProducerTopicStatsData getPrePTopicStatsData(String topicName, long startKey, long endKey) {
        ProducerTopicStatsData pTopicStatsData = pTopicStatsDataService.findOneByTopicAndTime(topicName, startKey,
                endKey, true);
        if (pTopicStatsData != null) {
            return pTopicStatsData;
        }
        return new ProducerTopicStatsData();
    }

    private ProducerTopicStatsData getPostPTopicStatsData(String topicName, long startKey, long endKey) {
        ProducerTopicStatsData pTopicStatsData = pTopicStatsDataService.findOneByTopicAndTime(topicName, startKey,
                endKey, false);
        if (pTopicStatsData != null) {
            return pTopicStatsData;
        }
        return new ProducerTopicStatsData();
    }

    private class QueryQrderTask {

        private static final int poolSize = CommonUtils.DEFAULT_CPU_COUNT * 6;

        private static final int MAX_WAIT_TIME = 60;

        private ExecutorService executorService = Executors.newFixedThreadPool(poolSize,
                ThreadFactoryUtils.getThreadFactory(FACTORY_NAME));

        public QueryQrderTask() {
            logger.info("[QueryQrderTask] poolSize {} .", poolSize);
        }

        public void submit(final QueryOrderParam orderParam) {
            executorService.submit(new Runnable() {

                @Override
                public void run() {
                    queryOrder(orderParam);
                }
            });
        }

        private void queryOrder(QueryOrderParam orderParam) {
            ProducerTopicStatsData preStatsData = getPrePTopicStatsData(orderParam.getTopicName(),
                    orderParam.getFromKey(), orderParam.getToKey());
            ProducerTopicStatsData postStatsData = getPostPTopicStatsData(orderParam.getTopicName(),
                    orderParam.getFromKey(), orderParam.getToKey());
            long start = orderParam.getDelayStatsData().getStart();
            long end = orderParam.getDelayStatsData().getEnd();
            orderParam.getDelayStatsData().add(
                    new OrderEntity(orderParam.getTopicName(), StringUtils.EMPTY, postStatsData.getTotalDelay()
                            - preStatsData.getTotalDelay(), postStatsData.getTotalQps() - preStatsData.getTotalQps()));
            orderParam.getQpxStatsData().add(
                    new OrderEntity(orderParam.getTopicName(), StringUtils.EMPTY, postStatsData.getTotalQps()
                            - preStatsData.getTotalQps(), getQpsSampleCount(start, end)));
        }

        public void await() {
            executorService.shutdown();
            try {
                executorService.awaitTermination(MAX_WAIT_TIME, TimeUnit.SECONDS);
                executorService.shutdownNow();
                logger.info("[await] QueryQrderTask is over .");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private static class QueryOrderParam {

        private String topicName;

        private long fromKey;

        private long toKey;

        private OrderStatsData delayStatsData;

        private OrderStatsData qpxStatsData;

        public QueryOrderParam(String topicName, long fromKey, long toKey, OrderStatsData delayStatsData,
                               OrderStatsData qpxStatsData) {
            this.topicName = topicName;
            this.fromKey = fromKey;
            this.toKey = toKey;
            this.delayStatsData = delayStatsData;
            this.qpxStatsData = qpxStatsData;
        }

        public String getTopicName() {
            return topicName;
        }

        public long getFromKey() {
            return fromKey;
        }

        public long getToKey() {
            return toKey;
        }

        public OrderStatsData getDelayStatsData() {
            return delayStatsData;
        }

        public OrderStatsData getQpxStatsData() {
            return qpxStatsData;
        }

        @Override
        public String toString() {
            return "QueryOrderParam [topicName=" + topicName + ", fromKey=" + fromKey + ", toKey=" + toKey
                    + ", delayStatsData=" + delayStatsData + ", qpxStatsData=" + qpxStatsData + "]";
        }

    }
}