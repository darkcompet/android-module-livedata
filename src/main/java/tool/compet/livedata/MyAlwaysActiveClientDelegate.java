/*
 * Copyright (c) 2017-2021 DarkCompet. All rights reserved.
 */

package tool.compet.livedata;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

/**
 * This always active unless caller remove it manually.
 *
 * @param <M> Data type.
 */
class MyAlwaysActiveClientDelegate<M> extends MyClientDelegate<M> {
	MyAlwaysActiveClientDelegate(DkLiveData<M> host, TheOptions options, Observer<? super M> observer) {
		super(host, options, observer);
	}

	@Override
	protected String clientName() {
		return "Unknown";
	}

	@Override
	public void observeClient() {
	}

	@Override
	public void unobserveClient() {
	}

	@Override
	protected boolean isClientReallyActive() {
		return true;
	}

	@Override
	public boolean isAssociatedWith(LifecycleOwner owner) {
		return false;
	}
}