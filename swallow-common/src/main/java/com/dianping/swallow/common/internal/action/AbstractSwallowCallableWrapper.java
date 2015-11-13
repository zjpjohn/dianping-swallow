package com.dianping.swallow.common.internal.action;


import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * @author mengwenchao
 *
 * 2015年5月14日 下午9:05:57
 */
public abstract class AbstractSwallowCallableWrapper<V> implements SwallowCallableWrapper<V>{

	protected Logger logger = LoggerFactory.getLogger(getClass());

}
