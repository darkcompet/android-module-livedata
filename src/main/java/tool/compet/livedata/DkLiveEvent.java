package tool.compet.livedata;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import tool.compet.core.DkObjectIntArrayMap;

/**
 * This is live-component which dispatches to an observer only if new value was set.
 * Different with LiveData, this does NOT only check active-state of an observer,
 * but also check last-data version of the observer to consider whether or not dispatch the event.
 *
 * For eg,. after a client (Activity, Fragment,...) got config-change which cause it got destroyed,
 * if that client got dispatched-event in past, then unless new data was set,
 * that observer will NOT be received the event more.
 */
public class DkLiveEvent<M> extends MyLiveComponent<M> {
	protected final DkObjectIntArrayMap<String> observerid2version = new DkObjectIntArrayMap<>();

	public boolean observe(LifecycleOwner lifecycleOwner, String observerId, Observer<? super M> observer) {
		return super.addObserver(lifecycleOwner, observer, clientDelegate -> {
			// Enter-time: Perform setup/recover data for the observer
			clientDelegate.observerId = observerId;
			clientDelegate.lastVersion = observerid2version.get(observerId, DATA_START_VERSION);

			// Start observing client's lifespan
			clientDelegate.observeClient();
		});
	}

	@Override
	protected void onObserverRemoved(@NonNull MyClientDelegate<M> clientDelegate) {
		// Remember last data-version for the observer
		observerid2version.put(clientDelegate.observerId, clientDelegate.lastVersion);

		super.onObserverRemoved(clientDelegate);
	}
}
