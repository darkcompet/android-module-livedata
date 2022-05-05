/*
 * Copyright (c) 2017-2021 DarkCompet. All rights reserved.
 */

package tool.compet.livedata;

public class TheOptions {
	// Specify which thread will be executed when observer receive data
	public static final int THREAD_MODE_MAIN = 1;
	public static final int THREAD_MODE_POSTER = 2;
	public int threadMode = THREAD_MODE_MAIN;

	public TheOptions() {
	}

	public TheOptions threadMode(int threadMode) {
		this.threadMode = threadMode;
		return this;
	}
}
