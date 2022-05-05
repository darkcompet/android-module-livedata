/*
 * Copyright (c) 2017-2021 DarkCompet. All rights reserved.
 */

package tool.compet.livedata;

import android.os.Looper;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import java.util.Iterator;
import java.util.Map;

import tool.compet.core.DkLogcats;
import tool.compet.core.DkRunner1;
import tool.compet.core.DkRuntimeException;

/**
 * This holds a value, and a list of observer (client's callback) which is observing the change of the value.
 * Nomally, when a client which is bounded by that observer is in active-state (for eg,. Activity, Fragment are in onStart() -> onStop()),
 * this component will consider dispatch the value to that client via the observer with defined rule.
 *
 * For eg of `defined rule`: when a new value was set, it notifies to all active clients via observers.
 * When new observer is registered, it also notify to the observer when the client becomes to active.
 *
 * @param <M> Value type.
 */
@SuppressWarnings("unchecked")
abstract class MyLiveComponent<M> {
	protected final MySafeIterableMap<Observer<? super M>, MyClientDelegate<M>> observer2delegate = new MySafeIterableMap<>();

	// Count of observer which is in active state
	protected int activeCount = 0;

	// To handle active/inactive reentry, we guard with this boolean
	protected boolean isUpdatingActiveState;

	protected static final int DATA_START_VERSION = -1;
	protected static final Object DATA_NOT_SET = new Object();
	// Lock to sync data
	protected final Object dataLock = new Object();
	// Value to send
	protected volatile Object data;
	// When setData is called, we set the pending data and actual data swap happens on the main thread
	protected volatile Object pendingData = DATA_NOT_SET;
	// Increment when set new value
	protected int version;
	protected boolean isDispatchingData;
	protected boolean dispatchInvalidated;
	protected final Runnable postDataAction = () -> {
		Object newValue;
		synchronized (dataLock) {
			newValue = pendingData;
			pendingData = DATA_NOT_SET;
		}
		setValue((M) newValue);
	};

	/**
	 * Creates a LiveComponent initialized with the given {@code data}.
	 *
	 * @param value Initial data
	 */
	public MyLiveComponent(M value) {
		this.data = value;
		this.version = DATA_START_VERSION + 1;
	}

	/**
	 * Creates a LiveComponent with NO data assigned to it.
	 */
	public MyLiveComponent() {
		this.data = DATA_NOT_SET;
		this.version = DATA_START_VERSION;
	}

	/**
	 * Adds the given observer to the observers list within the lifespan of the given
	 * owner. The events are dispatched on the main thread. If LiveComponent already has data
	 * set, it will be delivered to the observer.
	 * <p>
	 * The observer will only receive events if the owner is in {@link Lifecycle.State#STARTED}
	 * or {@link Lifecycle.State#RESUMED} state (active).
	 * <p>
	 * If the owner moves to the {@link Lifecycle.State#DESTROYED} state, the observer will
	 * automatically be removed.
	 * <p>
	 * When data changes while the {@code owner} is not active, it will not receive any updates.
	 * If it becomes active again, it will receive the last available data automatically.
	 * <p>
	 * LiveComponent keeps a strong reference to the observer and the owner as long as the
	 * given LifecycleOwner is not destroyed. When it is destroyed, LiveComponent removes references to
	 * the observer and the owner.
	 * <p>
	 * If the given owner is already in {@link Lifecycle.State#DESTROYED} state, LiveComponent
	 * ignores the call.
	 * <p>
	 * If the given owner, observer tuple is already in the list, the call is ignored.
	 * If the observer is already in the list with another owner, LiveComponent throws an
	 * {@link IllegalArgumentException}.
	 *
	 * @param owner For eg,. Activity, Fragment...
	 * @param observer Callback for data-change.
	 * @param onAdded Callbacked iff new client delegate for given observer was added.
	 */
	boolean addObserver(LifecycleOwner owner, Observer<? super M> observer, DkRunner1<MyClientDelegate<M>> onAdded) {
		// Validate at debug mode to avoid mistake
		if (BuildConfig.DEBUG) {
			assertMainThread("observe");
			if (owner.getLifecycle().getCurrentState().compareTo(Lifecycle.State.CREATED) >= 0) {
				throw new DkRuntimeException(this, "Should NOT observe after `%s.onCreate()`", owner.getClass().getName());
			}
		}
		if (owner.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
			DkLogcats.warning(this, "Ignore observe when lifecycleOwner goto destroyed state");
			return false;
		}

		// Register observer if not yet. After register client listener,
		// we will receive callback of active-state from client later at `onClientActiveStateChanged()`
		final MyClientDelegate<M> newClientDelegate = new MyLifecycleClientDelegate<>(this, owner, new TheOptions(), observer);
		final MyClientDelegate<M> prevClientDelegate = this.observer2delegate.putIfAbsent(observer, newClientDelegate);

		// The observer has been registered before -> Nothing was modified.
		if (prevClientDelegate != null) {
			DkLogcats.warning(this, "Ignore add same observer to LiveComponent, pls review !");

			if (! prevClientDelegate.isAssociatedWith(owner)) {
				throw new IllegalArgumentException("Cannot add same observer with different lifecycleOwner !");
			}
			return false;
		}

		// We have added new delegate for the observer into map.
		// Now, tell caller know to continue the processing.
		onAdded.run(newClientDelegate);

		return true;
	}

	/**
	 * Posts a task to a main thread to set the given value. So if you have a following code
	 * executed in the main thread:
	 * <pre class="prettyprint">
	 * LiveComponent.postValue("a");
	 * LiveComponent.setValue("b");
	 * </pre>
	 * The value "b" would be set at first and later the main thread would override it with
	 * the value "a".
	 * <p>
	 * If you called this method multiple times before a main thread executed a posted task, only
	 * the last value would be dispatched.
	 *
	 * @param value The new value
	 */
	public void postValue(M value) {
		boolean shouldPost;
		// Set coming-value to pending-data
		synchronized (this.dataLock) {
			// Only post when NO pending-data
			shouldPost = (this.pendingData == DATA_NOT_SET);
			this.pendingData = value;
		}
		if (shouldPost) {
			MyArchTaskExecutor.getInstance().postToMainThread(this.postDataAction);
		}
	}

	/**
	 * When active-state of a client has changed, we perform 2 actions:
	 * - inactive -> active: We dispatch data to the client.
	 * - active -> inactive: We update total active-status for LiveComponent (for eg,. counting....)
	 */
	@MainThread
	void onClientActiveStateChanged(MyClientDelegate<M> client, boolean newActive) {
		updateLiveComponentActiveStatus(newActive ? 1 : -1);

		// Dispatch data to ONLY the client
		if (newActive) {
			dispatchValue(client);
		}
	}

	@MainThread
	private void updateLiveComponentActiveStatus(int changeCount) {
		int prevActiveCount = this.activeCount;
		this.activeCount += changeCount;

		// Since this block call other methods `onActive()` or `onInactive()`,
		// so we need prevent recursive calling by introduce updating-flag.
		if (! this.isUpdatingActiveState) {
			try {
				this.isUpdatingActiveState = true;

				while (prevActiveCount != this.activeCount) {
					boolean needToCallActive = (prevActiveCount == 0 && this.activeCount > 0);
					boolean needToCallInactive = (prevActiveCount > 0 && this.activeCount == 0);

					prevActiveCount = this.activeCount;

					// Maybe activeCount is changed at this time,
					// so we need perform while until prevActiveCount equals to activeCount
					if (needToCallActive) {
						onActive();
					}
					else if (needToCallInactive) {
						onInactive();
					}
				}
			}
			finally {
				this.isUpdatingActiveState = false;
			}
		}
	}

	/**
	 * It is convenience method, like as EventBus, send an event to subscriber at specified thread.
	 * So it combines `setValue()`, `postValue()`... to match with client options.
	 */
	public void sendValue(M value) {
		final Iterator<Map.Entry<Observer<? super M>, MyClientDelegate<M>>> it = observer2delegate.iteratorWithAdditions();
		final boolean isMainThread = (Thread.currentThread() == Looper.getMainLooper().getThread());

		while (it.hasNext()) {
			MyClientDelegate<M> client = it.next().getValue();

			if (client.options.threadMode == TheOptions.THREAD_MODE_MAIN) {
				if (isMainThread) {
					setValue(value);
				}
				else {
					postValue(value);
				}
			}
			else if (client.options.threadMode == TheOptions.THREAD_MODE_POSTER) {
				setValue(value);
			}
			else {
				throw new RuntimeException("Not yet support thread mode: " + client.options.threadMode);
			}
		}
	}

	/**
	 * Sets the value. If there are active observers, the value will be dispatched to them.
	 * <p>
	 * This method must be called from the main thread. If you need set a value from a background
	 * thread, you should use `postValue()` or `sendValue()`.
	 *
	 * @param value The new value
	 */
	@MainThread
	public void setValue(M value) {
		if (BuildConfig.DEBUG) {
			assertMainThread("setValue");
		}
		this.data = value;
		this.version++;

		// Dispatch data to all observers
		dispatchValue(null);
	}

	/**
	 * Called when the data was changed (updated). This will notify the change to all active observers.
	 *
	 * @param client Null to dispatch to all observers. Otherwise only dispatch to target observer.
	 */
	@MainThread
	protected void dispatchValue(@Nullable MyClientDelegate<M> client) {
		if (this.isDispatchingData) {
			this.dispatchInvalidated = true;
			return;
		}

		this.isDispatchingData = true;

		do {
			this.dispatchInvalidated = false;

			// Notify to all observers
			if (client == null) {
				Iterator<Map.Entry<Observer<? super M>, MyClientDelegate<M>>> it = observer2delegate.iteratorWithAdditions();

				while (it.hasNext()) {
					attemptNotify(it.next().getValue());

					if (this.dispatchInvalidated) {
						break;
					}
				}
			}
			// Notify to target observer
			else {
				attemptNotify(client);
				client = null;
			}
		}
		while (this.dispatchInvalidated);

		this.isDispatchingData = false;
	}

	/**
	 * We only notify iff: (client is active) + (data was set) + (versions are not same).
	 */
	@MainThread
	protected void attemptNotify(MyClientDelegate<M> clientDelegate) {
		if (this.data == DATA_NOT_SET || clientDelegate.lastVersion == this.version || ! clientDelegate.active) {
			return;
		}
		if (! clientDelegate.isClientReallyActive()) {
			DkLogcats.warning(clientDelegate, "Skip dispatch data since client is really inactive but NOT yet sync with client.active");
			clientDelegate.changeActiveState(false);
			return;
		}
		// OK, start dispatch data
		clientDelegate.lastVersion = this.version;
		clientDelegate.observer.onChanged((M) this.data);

		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "Notified data-change to an observer of client: " + clientDelegate.clientName());
		}
	}

	/**
	 * Unset current value to `DATA_NOT_SET` which does not equal to any value.
	 * Call this to make all observers don't get notification until new value is dispatched.
	 */
	@MainThread
	public void unsetValue() {
		if (BuildConfig.DEBUG) {
			assertMainThread("unsetValue");
		}
		// Just reset data, don't need reset data-version of observers
		// since this LiveComponent does not dispatch until new data was set.
		this.data = DATA_NOT_SET;
	}

	/**
	 * Remove an observer from LiveComponent.
	 *
	 * @param observer Callback for data-change.
	 */
	@MainThread
	public void removeObserver(@NonNull final Observer<? super M> observer) {
		if (BuildConfig.DEBUG) {
			assertMainThread("removeObserver");
		}
		final MyClientDelegate<M> clientDelegate = observer2delegate.remove(observer);
		if (clientDelegate != null) {
			onObserverRemoved(clientDelegate);
		}
	}

	@MainThread
	@CallSuper
	protected void onObserverRemoved(@NonNull MyClientDelegate<M> clientDelegate) {
		clientDelegate.unobserveClient();
		clientDelegate.changeActiveState(false);
	}

	/**
	 * Removes all observers that are tied to the given `LifecycleOwner`.
	 *
	 * @param lifecycleOwner The `LifecycleOwner` scope for the observers to be removed.
	 */
	@SuppressWarnings("WeakerAccess")
	@MainThread
	public void removeObservers(@NonNull final LifecycleOwner lifecycleOwner) {
		if (BuildConfig.DEBUG) {
			assertMainThread("removeObservers");
		}
		for (Map.Entry<Observer<? super M>, MyClientDelegate<M>> entry : this.observer2delegate) {
			if (entry.getValue().isAssociatedWith(lifecycleOwner)) {
				removeObserver(entry.getKey());
			}
		}
	}

	/**
	 * Called when the number of active observers change from 0 to 1.
	 *
	 * This callback can be used to know that this LiveComponent is being used thus should be kept
	 * up to date.
	 */
	@MainThread
	protected void onActive() {
	}

	/**
	 * Called when the number of active observers change from 1 to 0.
	 *
	 * This does not mean that there are no observers left, there may still be observers but their
	 * lifecycle states aren't {@link Lifecycle.State#STARTED} or {@link Lifecycle.State#RESUMED}
	 * (like an Activity in the back stack).
	 *
	 * You can check if there are observers via `hasObservers()`.
	 */
	@MainThread
	protected void onInactive() {
	}

	/**
	 * Returns true if this LiveComponent has observers.
	 */
	public boolean hasObservers() {
		return this.observer2delegate.size() > 0;
	}

	/**
	 * Returns true if this LiveComponent has active observers.
	 */
	public boolean hasActiveObservers() {
		return this.activeCount > 0;
	}

	protected void assertMainThread(String methodName) {
		if (! MyArchTaskExecutor.getInstance().isMainThread()) {
			throw new IllegalStateException("Cannot invoke " + methodName + " on a background thread");
		}
	}

	/**
	 * Returns the current value.
	 * Note that calling this method on a background thread does NOT guarantee that
	 * the latest value set will be received.
	 *
	 * @return The current value.
	 */
	@Nullable
	public M getValue() {
		return (this.data != DATA_NOT_SET) ? (M) this.data : null;
	}

	/**
	 * Get version of current this data, it is just count of `setValue()` invocation.
	 */
	public int getVersion() {
		return this.version;
	}
}
