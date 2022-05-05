/*
 * Copyright (c) 2017-2021 DarkCompet. All rights reserved.
 */

package tool.compet.livedata;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import tool.compet.core.DkLogcats;
import tool.compet.core.DkCaller;

/**
 * Observer subscribes this, when an new data was set or client becomes active, it will dispatch data to each observer.
 * The dispatching-considtion is occured when all below conditions are matched:
 * - Data was set.
 * - Client is active (for eg,. a Fragment's lifecycle is from onStart() to onStop()).
 * - Data-version of client is smaller than data-version of LiveData.
 *
 * @param <M> Model (data) type.
 */
public class DkLiveData<M> extends MyLiveComponent<M> {
	@MainThread
	public boolean observe(LifecycleOwner lifecycleOwner, Observer<? super M> observer) {
		return super.addObserver(lifecycleOwner, observer, clientDelegate -> {
			// Start observing client's lifespan
			clientDelegate.observeClient();
		});
	}

	/**
	 * Adds the given observer to the observers list. This call is similar to
	 * `observe(LifecycleOwner, Observer)` with a LifecycleOwner which is always active.
	 * This means that the given observer will receive all events and will never
	 * be automatically removed. You should manually call {@link #removeObserver(Observer)} to stop
	 * observing this LiveData.
	 * While LiveData has one of such observers, it will be considered as active.
	 * <p>
	 * If the observer was already added with an owner to this LiveData, LiveData throws an `IllegalArgumentException`.
	 *
	 * @param observer Callback for data-change.
	 */
	@MainThread
	public void observeForever(@NonNull DkCaller<TheOptions> options, @NonNull Observer<? super M> observer) {
		if (BuildConfig.DEBUG) {
			assertMainThread("observeForever");
		}
		MyClientDelegate<M> newClientDelegate = new MyAlwaysActiveClientDelegate<>(this, options.call(), observer);
		MyClientDelegate<M> prevClientDelegate = observer2delegate.putIfAbsent(observer, newClientDelegate);
		if (prevClientDelegate != null) {
			DkLogcats.warning(this, "Ignore add same observer to LiveData, pls review !");
			return;
		}

		newClientDelegate.changeActiveState(true);
	}
}
