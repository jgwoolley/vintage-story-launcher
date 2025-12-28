package com.yelloowstone.vslauncher;

import java.util.Objects;

public class VintageStoryVersion {

	public static enum OperatingSystem {
		LINUX, MACOS, WINDOWS;
	}

	private VintageStoryVersion.OperatingSystem operatingSystem;
	private final int majorVersion;
	private final int minorVersion;
	private final int patchVersion;
	private final String identifier;
	
	public VintageStoryVersion(final OperatingSystem operatingSystem, final int majorVersion, final int minorVersion, final int patchVersion,
			final String identifier) {
		super();
		this.operatingSystem = operatingSystem;
		this.majorVersion = majorVersion;
		this.minorVersion = minorVersion;
		this.patchVersion = patchVersion;
		this.identifier = identifier;
	}

	public VintageStoryVersion.OperatingSystem getOperatingSystem() {
		return operatingSystem;
	}

	public int getMajorVersion() {
		return majorVersion;
	}
	
	public int getMinorVersion() {
		return minorVersion;
	}
	
	public int getPatchVersion() {
		return patchVersion;
	}
	
	public String getIdentifier() {
		return identifier;
	}
	
	public VintageStoryVersion withOperatingSystem(final OperatingSystem operatingSystem) {
		return new VintageStoryVersion(operatingSystem, this.getMajorVersion(), this.getMinorVersion(), this.getPatchVersion(), this.getIdentifier());
	}
	
	public VintageStoryVersion withMajorVersion(final int majorVersion) {
		return new VintageStoryVersion(this.getOperatingSystem(), majorVersion, this.getMinorVersion(), this.getPatchVersion(), this.getIdentifier());
	}
	
	public VintageStoryVersion withMinorVersion(final int minorVersion) {
		return new VintageStoryVersion(this.getOperatingSystem(), this.getMajorVersion(), minorVersion, this.getPatchVersion(), this.getIdentifier());
	}
	
	public VintageStoryVersion withPatchVersion(final int patchVersion) {
		return new VintageStoryVersion(this.getOperatingSystem(), this.getMajorVersion(), this.getMinorVersion(), patchVersion, this.getIdentifier());
	}
	
	public VintageStoryVersion withIdentifier(final String identifier) {
		return new VintageStoryVersion(this.getOperatingSystem(), this.getMajorVersion(), this.getMinorVersion(), this.getPatchVersion(), identifier);
	}
	
	public String getClientUrl() {
		final StringBuilder builder = new StringBuilder();
		builder.append("https://cdn.vintagestory.at/gamefiles/");
		final boolean isUnstable = this.getIdentifier() == null || this.getIdentifier().isBlank();
		
		if(isUnstable) {
			builder.append("unstable/");
		} else {
			builder.append("stable/");
		}
		
		switch(this.getOperatingSystem()) {
			case LINUX:
				builder.append("vs_client_linux-x64_");
				this.buildVersionString(builder);
				builder.append(".tar.gz");
				break;
			case MACOS:
				builder.append("vs_client_osx-x64_");
				this.buildVersionString(builder);
				builder.append(".tar.gz");
				break;
			case WINDOWS:
				builder.append("vs_install_win-x64_");
				this.buildVersionString(builder);
				builder.append(".exe");
				break;
			default:
				break;
		}
		
		return builder.toString();
				
	}
	
	public void buildVersionString(final StringBuilder builder) {
		builder.append(getMajorVersion());
		builder.append(".");
		builder.append(getMinorVersion());
		builder.append(".");
		builder.append(getPatchVersion());

		final String identifier = getIdentifier();
		if (identifier != null && !identifier.isEmpty()) {
			builder.append('-');
			builder.append(identifier);
		}
	}

	public String getVersionString() {
		final StringBuilder builder = new StringBuilder();
		buildVersionString(builder);
		return builder.toString();
	}
	
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		this.buildVersionString(builder);
		builder.append(" (");
		builder.append(this.getOperatingSystem());
		builder.append(")");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		return Objects.hash(identifier, majorVersion, minorVersion, operatingSystem, patchVersion);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		VintageStoryVersion other = (VintageStoryVersion) obj;
		return Objects.equals(identifier, other.identifier) && majorVersion == other.majorVersion
				&& minorVersion == other.minorVersion && operatingSystem == other.operatingSystem
				&& patchVersion == other.patchVersion;
	}
}
