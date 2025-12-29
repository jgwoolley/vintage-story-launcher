package com.yelloowstone.vslauncher;

import java.io.File;

public class VintageStoryInstance {
    private final VintageStoryRuntime runtime;
    private final File dataPath;

    public VintageStoryInstance(VintageStoryRuntime runtime, File dataPath) {
        this.runtime = runtime;
        this.dataPath = dataPath;
    }

    public File getDataPath() {
        return dataPath;
    }

    public VintageStoryRuntime getRuntime() {
        return runtime;
    }
}
