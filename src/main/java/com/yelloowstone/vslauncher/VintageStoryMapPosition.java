package com.yelloowstone.vslauncher;

public interface VintageStoryMapPosition {

	public int getRawPosition();
	public byte[] getRawData();
	public byte[] getPixles();
	public int getVersion();
	public long getLastModified();
}
