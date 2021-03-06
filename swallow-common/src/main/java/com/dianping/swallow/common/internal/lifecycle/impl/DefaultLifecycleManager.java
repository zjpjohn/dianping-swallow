package com.dianping.swallow.common.internal.lifecycle.impl;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.dianping.swallow.common.internal.lifecycle.Disposable;
import com.dianping.swallow.common.internal.lifecycle.Initializble;
import com.dianping.swallow.common.internal.lifecycle.LifecycleCallback;
import com.dianping.swallow.common.internal.lifecycle.LifecycleManager;
import com.dianping.swallow.common.internal.lifecycle.Startable;
import com.dianping.swallow.common.internal.lifecycle.Stopable;

/**
 * @author mengwenchao
 *
 * 2015年2月12日 下午5:52:21
 */
public class DefaultLifecycleManager implements LifecycleManager {
	
	private final Logger logger     = LogManager.getLogger(getClass());

	private String currentPhaseName = LifecycleManager.CREATED_PHASE_NAME;
	
	private Object component;
	
	public DefaultLifecycleManager(Object component){
		this.component = component;
	}
	
	@Override
	public void initialize(LifecycleCallback callback) throws Exception {
		
		if(isCreated() || isDisposed()){
			if(logger.isInfoEnabled()){
				logger.info("[initialize][begin]" + component);
			}
			callback.onTransition();
			currentPhaseName = Initializble.PHASE_NAME;
			if(logger.isInfoEnabled()){
				logger.info("[initialize][end]" + component);
			}
			return;
		}
		logger.warn("[initialize][can not initialize]" + getCurrentPhaseName() + ", " + component);
	}

	@Override
	public void start(LifecycleCallback callback) throws Exception {

		if(isInitialized() || isStopped()){
			if(logger.isInfoEnabled()){
				logger.info("[start][begin]" + component);
			}
			callback.onTransition();
			currentPhaseName = Startable.PHASE_NAME;
			if(logger.isInfoEnabled()){
				logger.info("[start][end]" + component);
			}
			return;
		}
		logger.warn("[start][can not start]" + getCurrentPhaseName() + ", " + component);
	}

	@Override
	public void stop(LifecycleCallback callback) throws Exception {

		if(isStarted()){
			if(logger.isInfoEnabled()){
				logger.info("[stop][begin]" + component);
			}
			callback.onTransition();
			currentPhaseName = Stopable.PHASE_NAME;
			if(logger.isInfoEnabled()){
				logger.info("[stop][end]" + component);
			}
			return;
		}
		logger.warn("[stop][can not stop]" + getCurrentPhaseName() + ", " + component);
	}

	@Override
	public void dispose(LifecycleCallback callback) throws Exception {

		if(isInitialized() || isStopped()){
			if(logger.isInfoEnabled()){
				logger.info("[dispose][begin]" + component);
			}
			callback.onTransition();
			currentPhaseName = Disposable.PHASE_NAME;
			if(logger.isInfoEnabled()){
				logger.info("[dispose][end]" + component);
			}
			return;
		}
		logger.warn("[dispose][can not dispose]" + getCurrentPhaseName() + ", " + component);
	}

	@Override
	public boolean isCreated() {
		
		if(phaseIn(LifecycleManager.CREATED_PHASE_NAME)){
			return true;
		}
		return false;
	}

	@Override
	public boolean isInitialized() {
		
		if(phaseIn(Initializble.PHASE_NAME, Startable.PHASE_NAME, Stopable.PHASE_NAME)){
			return true;
		}
		return false;
	}

	@Override
	public boolean isStarted() {
		
		if(phaseIn(Startable.PHASE_NAME)){
			return true;
		}
		return false;
	}

	@Override
	public boolean isStopped() {
		
		if(phaseIn(Stopable.PHASE_NAME)){
			return true;
		}
		return false;
	}

	@Override
	public boolean isDisposed() {
		
		if(phaseIn(Disposable.PHASE_NAME)){
			return true;
		}
		return false;
	}

	private boolean phaseIn(String ... phases) {
		for(String phase : phases){
			if(currentPhaseName.equals(phase)){
				return true;
			}
		}
		return false;
	}

	@Override
	public String getCurrentPhaseName() {
		
		return currentPhaseName;
	}

}
