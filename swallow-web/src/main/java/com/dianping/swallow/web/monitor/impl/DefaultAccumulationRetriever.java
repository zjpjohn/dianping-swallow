package com.dianping.swallow.web.monitor.impl;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.dianping.swallow.common.server.monitor.data.statis.CasKeys;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.dianping.swallow.common.internal.action.SwallowAction;
import com.dianping.swallow.common.internal.action.SwallowActionWrapper;
import com.dianping.swallow.common.internal.action.impl.CatActionWrapper;
import com.dianping.swallow.common.internal.config.ObjectConfigChangeListener;
import com.dianping.swallow.common.internal.dao.ClusterManager;
import com.dianping.swallow.common.internal.dao.MessageDAO;
import com.dianping.swallow.common.internal.dao.impl.mongodb.MongoClusterFactory;
import com.dianping.swallow.common.internal.exception.SwallowException;
import com.dianping.swallow.common.internal.threadfactory.MQThreadFactory;
import com.dianping.swallow.common.internal.util.ConsumerIdUtil;
import com.dianping.swallow.common.internal.util.MapUtil;
import com.dianping.swallow.common.server.monitor.data.StatisDetailType;
import com.dianping.swallow.common.server.monitor.data.structure.MonitorData;
import com.dianping.swallow.web.config.WebConfig;
import com.dianping.swallow.web.config.impl.DefaultWebConfig;
import com.dianping.swallow.web.monitor.AccumulationListener;
import com.dianping.swallow.web.monitor.AccumulationRetriever;
import com.dianping.swallow.web.monitor.ConsumerDataRetriever;
import com.dianping.swallow.web.monitor.OrderEntity;
import com.dianping.swallow.web.monitor.OrderStatsData;
import com.dianping.swallow.web.monitor.StatsData;
import com.dianping.swallow.web.monitor.StatsDataDesc;
import com.dianping.swallow.web.service.ConsumerIdStatsDataService;

/**
 * @author mengwenchao
 *         <p/>
 *         2015年5月28日 下午3:06:46
 */
@Component
public class DefaultAccumulationRetriever extends AbstractRetriever implements AccumulationRetriever,
        ObjectConfigChangeListener {

    private List<AccumulationListener> accumulationListeners = new ArrayList<AccumulationListener>();

    private Map<String, TopicAccumulation> topics = new ConcurrentHashMap<String, DefaultAccumulationRetriever.TopicAccumulation>();

	@Autowired
	private MessageDAO<?> messageDao;

	@Autowired
	private ClusterManager clusterManager;
	
	@Autowired
	private MongoClusterFactory mongoClusterFactory;

    @Autowired
    private WebConfig webConfig;

    @Autowired
    private ConsumerDataRetriever consumerDataRetriever;

    private ExecutorService executors;

    @Autowired
    private ConsumerIdStatsDataService consumerIdStatsDataService;


    @Override
    protected void doInitialize() throws Exception {

        super.doInitialize();

        int clusterCount = clusterManager.allClusters().size();
		int corePoolSize = clusterCount * 10;
		int maxPoolSize = clusterCount * mongoClusterFactory.getMongoOptions().getConnectionsPerHost();
		if (logger.isInfoEnabled()) {
			logger.info("[postDefaultAccumulationRetriever]" + corePoolSize);
		}
		
		executors = new ThreadPoolExecutor(corePoolSize, maxPoolSize, 30, 
				TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
				new MQThreadFactory("ACCUMULATION_RETRIEVER-"), new ThreadPoolExecutor.CallerRunsPolicy());
		webConfig.addChangeListener(this);
    }

    @Override
    protected void doBuild() {

        SwallowActionWrapper actionWrapper = new CatActionWrapper(CAT_TYPE, getClass().getSimpleName() + "-doBuild");

        actionWrapper.doAction(new SwallowAction() {
            @Override
            public void doAction() throws SwallowException {

                buildAllAccumulations();
            }
        });

        // 通知监听者
        doChangeNotify();
    }

    protected void buildAllAccumulations() {

        Map<String, Set<String>> topics = consumerDataRetriever.getAllTopics();

        if (logger.isInfoEnabled()) {
            logger.info("[buildAllAccumulations][begin]");
        }

        final CountDownLatch latch = new CountDownLatch(latchSize(topics));
        for (Entry<String, Set<String>> entry : topics.entrySet()) {

            final String topicName = entry.getKey();
            final Set<String> consumerIds = entry.getValue();

            executors.execute(new Runnable() {

                @Override
                public void run() {
                    try {
                        putAccumulation(topicName, consumerIds);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }
        try {
            boolean result = latch.await(getBuildInterval(), TimeUnit.SECONDS);
            if (!result) {
                logger.error("[buildAllAccumulations][wait returned, but task has not finished yet!]");
            }
        } catch (InterruptedException e) {
            logger.error("[buildAllAccumulations]", e);
        }
    }

    private int latchSize(Map<String, Set<String>> topics) {

        return topics.size();
    }

    private void putAccumulation(final String topicName, final Set<String> consumerIds) {

        CatActionWrapper catAction = new CatActionWrapper("putAccumulationTopic", topicName);

        catAction.doAction(new SwallowAction() {

            @Override
            public void doAction() throws SwallowException {

                for (String consumerId : consumerIds) {

                    putAccumulation(topicName, consumerId);
                    TopicAccumulation topic = topics.get(topicName);
                    if (topic != null) {
                        topic.retain(consumerIds);
                    }
                }
            }
        });
    }

    protected void putAccumulation(final String topicName, final String consumerId) {

        if (ConsumerIdUtil.isNonDurableConsumerId(consumerId)) {
            return;
        }

        CatActionWrapper catAction = new CatActionWrapper("putAccumulationConsumerId", topicName + ":" + consumerId);

        catAction.doAction(new SwallowAction() {

            @Override
            public void doAction() throws SwallowException {

                long size = 0;
                try {
                    size = messageDao.getAccumulation(topicName, consumerId);
                } catch (Exception e) {
                    logger.error("[putAccumulation]" + topicName + "," + consumerId, e);
                }
                TopicAccumulation topicAccumulation = MapUtil.getOrCreate(topics, topicName, TopicAccumulation.class);
                topicAccumulation.addConsumerId(consumerId, size);
            }
        });

    }

    @Override
    protected void doRemove(long toKey) {

        for (TopicAccumulation topicAccumulation : topics.values()) {
            topicAccumulation.removeBefore(toKey);
        }
    }

    @Override
    protected Set<String> getTopicsInMemory(long start, long end) {

        return topics.keySet();
    }

    @Override
    public OrderStatsData getAccuOrderForAllConsumerId(int size) {
        return getAccuOrderForAllConsumerId(size, getDefaultStart(), getDefaultEnd());
    }

    @Override
    public OrderStatsData getAccuOrderForAllConsumerId(int size, long start, long end) {
        return getAccuOrderForAllConsumerIdInMemory(size, start, end);
    }

    protected OrderStatsData getAccuOrderForAllConsumerIdInMemory(int size, long start, long end) {
        long fromKey = getKey(start);
        long toKey = getKey(end);

        OrderStatsData orderResults = new OrderStatsData(size, new ConsumerStatsDataDesc(TOTAL_KEY,
                StatisDetailType.ACCUMULATION), start, end);

        for (Map.Entry<String, TopicAccumulation> topicAccumulation : topics.entrySet()) {
            String topicName = topicAccumulation.getKey();
            Map<String, ConsumerIdAccumulation> consumerIdAccus = topicAccumulation.getValue().consumers();
            for (Map.Entry<String, ConsumerIdAccumulation> consumerIdAccu : consumerIdAccus.entrySet()) {
                NavigableMap<Long, Long> rawDatas = consumerIdAccu.getValue()
                        .getAccumulations(getSampleIntervalCount());
                orderResults.add(new OrderEntity(topicName, consumerIdAccu.getKey(), getSumStatsData(rawDatas, fromKey,
                        toKey), getOtherSampleCount(start, end)));
            }
        }
        return orderResults;
    }

    public boolean dataExistInMemory(CasKeys keys, long start, long end) {
        return consumerDataRetriever.dataExistInMemory(keys, start, end);
    }

    @Override
    public Map<String, StatsData> getAccumulationForAllConsumerId(String topic, long start, long end) {

        if (dataExistInMemory(new CasKeys(TOTAL_KEY, topic), start, end)) {
            return getAccumulationForAllConsumerIdInMemory(topic, start, end);
        }

        return getAccumulationForAllConsumerIdInDb(topic, start, end);

    }

    private Map<String, StatsData> getAccumulationForAllConsumerIdInDb(String topic, long start, long end) {
        long startKey = getKey(start);
        long endKey = getKey(end);
        Map<String, NavigableMap<Long, Long>> accuStatsDatas = consumerIdStatsDataService.findSectionAccuData(topic,
                startKey, endKey);
        Map<String, StatsData> result = new HashMap<String, StatsData>();
        if (accuStatsDatas != null && !accuStatsDatas.isEmpty()) {
            for (Map.Entry<String, NavigableMap<Long, Long>> accuStatsData : accuStatsDatas.entrySet()) {
                String consumerId = accuStatsData.getKey();
                if (MonitorData.TOTAL_KEY.equals(consumerId)) {
                    continue;
                }
                StatsDataDesc desc = new ConsumerStatsDataDesc(topic, consumerId, StatisDetailType.ACCUMULATION);
                NavigableMap<Long, Long> accuRawData = accuStatsData.getValue();
                accuRawData = fillStatsData(accuRawData, startKey, endKey);
                StatsData statsData = new StatsData(desc, getValue(accuRawData), getStartTime(accuRawData, start, end),
                        getStorageIntervalTime());
                result.put(consumerId, statsData);
            }
        }
        return result;
    }

    private Map<String, StatsData> getAccumulationForAllConsumerIdInMemory(String topic, long start, long end) {

        Map<String, StatsData> result = new HashMap<String, StatsData>();
        TopicAccumulation topicAccumulation = topics.get(topic);
        if (topicAccumulation == null) {
            return result;
        }
        for (Entry<String, ConsumerIdAccumulation> entry : topicAccumulation.consumers.entrySet()) {

            String consumerId = entry.getKey();
            ConsumerIdAccumulation consumerIdAccumulation = entry.getValue();

            StatsDataDesc desc = new ConsumerStatsDataDesc(topic, consumerId, StatisDetailType.ACCUMULATION);

            NavigableMap<Long, Long> rawData = consumerIdAccumulation.getAccumulations(getSampleIntervalCount());
            rawData = rawData.subMap(getKey(start), true, getKey(end), true);
            result.put(consumerId, createStatsData(desc, rawData, start, end));
        }

        return result;
    }

    @Override
    public Map<String, StatsData> getAccumulationForAllConsumerId(String topic) {

        return getAccumulationForAllConsumerId(topic, getDefaultStart(), getDefaultEnd());
    }

    public static class TopicAccumulation {

        private Map<String, ConsumerIdAccumulation> consumers = new ConcurrentHashMap<String, DefaultAccumulationRetriever.ConsumerIdAccumulation>();

        public void addConsumerId(String consumerId, long accumulation) {

            ConsumerIdAccumulation consumerIdAccumulation = MapUtil.getOrCreate(consumers, consumerId,
                    ConsumerIdAccumulation.class);
            consumerIdAccumulation.add(accumulation);
        }

        public void retain(Set<String> consumerIds) {

            Set<String> currentIds = new HashSet<String>(consumers.keySet());
            currentIds.removeAll(consumerIds);

            for (String removeId : currentIds) {

                consumers.remove(removeId);
            }
        }

        public void removeBefore(Long toKey) {

            for (ConsumerIdAccumulation consumer : consumers.values()) {

                consumer.removeBefore(toKey);
            }
        }

        public void remove(String consumerId) {

            consumers.remove(consumerId);
        }

        public Map<String, ConsumerIdAccumulation> consumers() {
            return consumers;
        }
    }

    public static class ConsumerIdAccumulation {

        private NavigableMap<Long, Long> accumulations = new ConcurrentSkipListMap<Long, Long>();

        protected final Logger logger = LogManager.getLogger(getClass());

        protected long lastInsertTime = System.currentTimeMillis();

        public void add(long accumulation) {

            Long key = getKey(System.currentTimeMillis());
            if (logger.isDebugEnabled()) {
                logger.debug("[add]" + key + ":" + accumulation);
            }
            accumulations.put(key, accumulation);
        }

        /**
         * For unit test
         *
         * @param key
         * @param accumulation
         */
        @Deprecated
        public void add(Long key, long accumulation) {

            if (logger.isDebugEnabled()) {
                logger.debug("[add]" + key + ":" + accumulation);
            }
            accumulations.put(key, accumulation);
        }

        private void ajustData(int intervalCount) {

            Long current = System.currentTimeMillis();

            Long lastKey = getKey(lastInsertTime);
            Long currentKey = getKey(current);

            NavigableMap<Long, Long> sub = accumulations.subMap(lastKey, true, currentKey, false);

            Long last = -1L;

            for (Long key : sub.keySet()) {

                if (last != -1) {
                    Long add = (key - last) / intervalCount - 1;

                    for (int i = 0; i < add; i++) {
                        accumulations.put(last + intervalCount, 0L);
                    }
                }
                last = key;
            }

            lastInsertTime = current;
        }

        public NavigableMap<Long, Long> getAccumulations(int intervalCount) {

            ajustData(intervalCount);
            return accumulations;
        }

        public List<Long> data() {

            List<Long> result = new LinkedList<Long>();
            result.addAll(accumulations.values());
            return result;
        }

        public void removeBefore(Long toKey) {

            Map<Long, Long> toDelete = accumulations.headMap(toKey);
            for (Long key : toDelete.keySet()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("[removeBefore]" + key);
                }
                accumulations.remove(key);
            }
        }
    }

    @Override
    protected long getBuildInterval() {

        return webConfig.getAccumulationBuildInterval();
    }

    @Override
    protected int getSampleIntervalTime() {

        return webConfig.getAccumulationBuildInterval();
    }

    @Override
    public void onChange(Object config, String key) throws Exception {

        if (key.equals(DefaultWebConfig.FIELD_ACCUMULATION)) {
            stop();
            start();
        }
    }

    @Override
    public void registerListener(AccumulationListener listener) {
        accumulationListeners.add(listener);
    }

    protected void doChangeNotify() {
        for (AccumulationListener accumulationListener : accumulationListeners) {
            accumulationListener.achieveAccumulation();
        }
    }

    public NavigableMap<Long, Long> getConsumerIdAccumulation(String topic, String consumerId) {
        TopicAccumulation topicAccumulation = topics.get(topic);
        if (topicAccumulation == null) {
            return null;
        }
        ConsumerIdAccumulation consumerIdAccumulation = topicAccumulation.consumers().get(consumerId);
        if (consumerIdAccumulation == null) {
            return null;
        }
        return consumerIdAccumulation.getAccumulations(getStorageIntervalCount());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
