package dev.emly.ez_usb_backup;

public class BackupInfo {

	private final String backupName;
	private final String version;

	public BackupInfo(String backupName, String version) {
		super();
		this.backupName = backupName;
		this.version = version;
	}

	public String getBackupName() {
		return backupName;
	}

	public String getVersion() {
		return version;
	}

}
