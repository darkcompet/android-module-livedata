/*
 * Copyright (c) 2017-2021 DarkCompet. All rights reserved.
 */

package tool.compet.livedata;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

/**
 * A static class that serves as a central point to execute common tasks.
 */
public class MyArchTaskExecutor extends MyTaskExecutor {
	private static volatile MyArchTaskExecutor sInstance;

	@NonNull
	private MyTaskExecutor mDelegate;

	@NonNull
	private MyTaskExecutor mDefaultTaskExecutor;

	@NonNull
	private static final Executor sMainThreadExecutor = command -> getInstance().postToMainThread(command);

	@NonNull
	private static final Executor sIOThreadExecutor = command -> getInstance().executeOnDiskIO(command);

	private MyArchTaskExecutor() {
		mDefaultTaskExecutor = new MyDefaultTaskExecutor();
		mDelegate = mDefaultTaskExecutor;
	}

	/**
	 * Returns an instance of the task executor.
	 *
	 * @return The singleton ArchTaskExecutor.
	 */
	@NonNull
	public static MyArchTaskExecutor getInstance() {
		if (sInstance != null) {
			return sInstance;
		}
		synchronized (MyArchTaskExecutor.class) {
			if (sInstance == null) {
				sInstance = new MyArchTaskExecutor();
			}
		}
		return sInstance;
	}

	/**
	 * Sets a delegate to handle task execution requests.
	 * <p>
	 * If you have a common executor, you can set it as the delegate and App Toolkit components will
	 * use your executors. You may also want to use this for your tests.
	 * <p>
	 * Calling this method with {@code null} sets it to the default TaskExecutor.
	 *
	 * @param taskExecutor The task executor to handle task requests.
	 */
	public void setDelegate(@Nullable MyTaskExecutor taskExecutor) {
		mDelegate = taskExecutor == null ? mDefaultTaskExecutor : taskExecutor;
	}

	@Override
	public void executeOnDiskIO(@NonNull Runnable runnable) {
		mDelegate.executeOnDiskIO(runnable);
	}

	@Override
	public void postToMainThread(@NonNull Runnable runnable) {
		mDelegate.postToMainThread(runnable);
	}

	@NonNull
	public static Executor getMainThreadExecutor() {
		return sMainThreadExecutor;
	}

	@NonNull
	public static Executor getIOThreadExecutor() {
		return sIOThreadExecutor;
	}

	@Override
	public boolean isMainThread() {
		return mDelegate.isMainThread();
	}
}
