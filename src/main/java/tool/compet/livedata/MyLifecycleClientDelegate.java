/*
 * Copyright (c) 2017-2021 DarkCompet. All rights reserved.
 */

package tool.compet.livedata;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

/**
 * Delegate (presenter) for a client which is target to observe LiveData.
 *
 * @param <M> Data type.
 */
class MyLifecycleClientDelegate<M> extends MyClientDelegate<M> implements LifecycleEventObserver {
	protected final LifecycleOwner lifecycleOwner;

	MyLifecycleClientDelegate(MyLiveComponent<M> liveData, LifecycleOwner lifecycleOwner, TheOptions options, Observer<? super M> observer) {
		super(liveData, options, observer);

		this.lifecycleOwner = lifecycleOwner;
	}

	@Override
	protected String clientName() {
		return this.lifecycleOwner.getClass().getName();
	}

	@Override
	public void observeClient() {
		// Start listen to lifespan of lifecycleOwner
		// Note: The given observer will be brought to the current state of the LifecycleOwner.
		// For eg, if the LifecycleOwner is in `Lifecycle.State.STARTED` state, the given observer
		// will receive `Lifecycle.Event.ON_CREATE`, `Lifecycle.Event.ON_START` events.
		lifecycleOwner.getLifecycle().addObserver(this);
	}

	@Override
	public void unobserveClient() {
		// Stop listen lifespan of lifecycleOwner
		lifecycleOwner.getLifecycle().removeObserver(this);
	}

	/**
	 * Called when owner's lifecycleState has changed.
	 */
	@Override
	public void onStateChanged(@NonNull LifecycleOwner lifecycleOwner, @NonNull Lifecycle.Event event) {
		Lifecycle.State curState = lifecycleOwner.getLifecycle().getCurrentState();

		// Auto-remove client when lifecycleOwner (View) got destroyed
		if (curState == Lifecycle.State.DESTROYED) {
			this.liveData.removeObserver(this.observer);
			return;
		}

		// Change active-state and Let LiveData know
		Lifecycle.State prevState = null;
		while (prevState != curState) {
			// By tell host know that its active-state was changed,
			// host will dispatch event to observers
			prevState = curState;
			changeActiveState(isClientReallyActive());
			curState = lifecycleOwner.getLifecycle().getCurrentState();
		}
	}

	/**
	 * Since the client maybe not ready for receive data from LiveData,
	 * so we should check this active-status before dispatch data to the client.
	 */
	@Override
	protected boolean isClientReallyActive() {
		// Normally, in range [View.onStart()+, View.onDestroy()-]
		return lifecycleOwner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
	}

	@Override
	public boolean isAssociatedWith(LifecycleOwner owner) {
		return owner == lifecycleOwner;
	}
}