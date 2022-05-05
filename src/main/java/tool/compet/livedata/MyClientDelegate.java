/*
 * Copyright (c) 2017-2021 DarkCompet. All rights reserved.
 */

package tool.compet.livedata;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

/**
 * Delegate (presenter) for a client which is target to observe LiveData.
 *
 * @param <M> Data type.
 */
abstract class MyClientDelegate<M> {
	// Live data (host)
	protected final MyLiveComponent<M> liveData;

	// To notify data to the client via observer
	protected final Observer<? super M> observer;

	// Unique id for the observer (callback)
	protected String observerId;

	// Current active state of this client
	// True (active): it is ready to handle incoming dispatched data -> welcome data
	// False (inactive): does NOT ready to handle incoming dispatched data -> NOT yet welcome data
	protected boolean active;

	// Version at last dispatched data from host
	// To avoid multiple invocation, we only accept newer version from host
	protected int lastVersion = MyLiveComponent.DATA_START_VERSION;

	// Make this client become more flexible for usage
	protected final TheOptions options;

	protected MyClientDelegate(MyLiveComponent<M> liveData, TheOptions options, Observer<? super M> observer) {
		this.liveData = liveData;
		this.options = options;
		this.observer = observer;
	}

	// Just for debug
	protected abstract String clientName();

	/**
	 * Called when this client-delegate is added to the `host`.
	 * At this time, delegate should start observe client changes.
	 */
	public abstract void observeClient();

	/**
	 * Called when this client-delegate is removed from the `host`.
	 * At this time, delegate should stop observe client changes.
	 */
	public abstract void unobserveClient();

	/**
	 * Check whether this client's state is really active or inactive.
	 */
	protected abstract boolean isClientReallyActive();

	/**
	 * Check whether this delegate is associated with given lifecycleOwner (Activity, Fragment,...).
	 */
	public abstract boolean isAssociatedWith(LifecycleOwner owner);

	/**
	 * Call this when active-state of the client was changed.
	 * This will set new active-state, and notify to LiveData if state be changed.
	 */
	protected void changeActiveState(boolean newActive) {
		if (this.active != newActive) {
			this.active = newActive;
			this.liveData.onClientActiveStateChanged(this, newActive);
		}
	}
}
