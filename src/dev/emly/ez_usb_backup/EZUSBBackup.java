package dev.emly.ez_usb_backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.yaml.snakeyaml.Yaml;

import net.samuelcampos.usbdrivedetector.USBDeviceDetectorManager;
import net.samuelcampos.usbdrivedetector.USBStorageDevice;
import net.samuelcampos.usbdrivedetector.events.DeviceEventType;

public class EZUSBBackup {

	private static String OUTPUT_DIR;
	private static SimpleDateFormat TIMESTAMP_FORMAT;
	private static int DISCOVERY_TIMEOUT_MS;
	private static String DISCORD_WEBHOOK_URL;

	private static final Yaml YAML = new Yaml();
	private static final USBDeviceDetectorManager DETECTOR = new USBDeviceDetectorManager();
	private static final Map<String, DeviceHolder> DEVICES = new ConcurrentHashMap<>();

	public static void main(String... args) throws IOException, InterruptedException {
		loadConfig();

		DETECTOR.addDriveListener(e -> {
			if (e.getEventType().equals(DeviceEventType.CONNECTED)) {
				USBStorageDevice device = e.getStorageDevice();
				String deviceName = stripEndNumbers(device.getDevice());
				DeviceHolder deviceHolder = DEVICES.get(deviceName);
				if (deviceHolder == null) {
					System.out.println("discovered new device: " + deviceName);

					deviceHolder = new DeviceHolder(deviceName);
					DEVICES.put(deviceName, deviceHolder);
				}

				System.out.println(e.getEventType() + ": " + e.getStorageDevice().getDeviceName() + "("
						+ e.getStorageDevice().getDevice() + ")");
				deviceHolder.addUSBDevice(device);
			}
		});

		while (true) {
			long now = System.currentTimeMillis();
			for (Entry<String, DeviceHolder> entry : DEVICES.entrySet())
				if (!entry.getValue().hasBackedUp() && now - entry.getValue().getLastModified() > DISCOVERY_TIMEOUT_MS)
					unmountAndBackup(entry.getValue());
			Thread.sleep(10);
		}
	}

	private static void loadConfig() {
		System.out.println("looking for config at ./config.yaml");
		File configFile = new File("./config.yaml");

		if (configFile.exists()) {
			System.out.println("found config");
			if (configFile.canRead()) {
				try {
					FileInputStream configStream = new FileInputStream(configFile);
					Map<String, Object> data = YAML.load(configStream);
					configStream.close();

					OUTPUT_DIR = (String) data.getOrDefault(ConfigKeys.OUTPUT_DIR, null);
					if (data.containsKey(ConfigKeys.TIMESTAMP_FORMAT))
						TIMESTAMP_FORMAT = new SimpleDateFormat((String) data.get(ConfigKeys.TIMESTAMP_FORMAT));
					DISCOVERY_TIMEOUT_MS = (int) data.getOrDefault(ConfigKeys.DISCOVERY_TIMEOUT, -1);
					DISCORD_WEBHOOK_URL = (String) data.getOrDefault(ConfigKeys.DISCORD_WEBHOOK, null);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				System.out.println("config found, not not readable. using defaults");
			}
		} else {
			System.out.println("config doesn't exist, using defaults");
		}
		fillDefaultConfigs();
	}

	private static void fillDefaultConfigs() {
		if (OUTPUT_DIR != null)
			OUTPUT_DIR = "./backups/";
		if (TIMESTAMP_FORMAT != null)
			TIMESTAMP_FORMAT = new SimpleDateFormat("YYYY-MM-dd_HHmm");
		if (DISCOVERY_TIMEOUT_MS != -1)
			DISCOVERY_TIMEOUT_MS = 1000;
		if (DISCORD_WEBHOOK_URL != null)
			DISCORD_WEBHOOK_URL = null;
	}

	private static String stripEndNumbers(String str) {
		if (str.matches(".*\\d"))
			return stripEndNumbers(str.substring(0, str.length() - 1));
		return str;
	}

	private static void unmountAndBackup(DeviceHolder deviceHolder) {
		BackupInfo backupInfo = getBackupInfo(deviceHolder);

		String outFile = getOutFile(backupInfo);
		logAndHook("backing up " + outFile);
		ProcessBuilder processB = new ProcessBuilder("dd", "if=" + deviceHolder.getDevice(),
				"of=" + OUTPUT_DIR + outFile, "status=progress");
		try {
			System.out.println("unmounting partitions");
			for (USBStorageDevice usbDevice : deviceHolder.getUSBDevices())
				DETECTOR.unmountStorageDevice(usbDevice);
			System.out.println("executing: ");
			System.out.println(processB.command());
			Process process = processB.start();
//			process.getInputStream().transferTo(System.out);
			process.onExit().thenRun(() -> {
				System.out.println("finished with exit code " + process.exitValue());
				DEVICES.remove(deviceHolder.getDevice());
				logAndHook("completed " + outFile);
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
		deviceHolder.setBackedUp();
	}

	private static BackupInfo getBackupInfo(DeviceHolder deviceHolder) {
		String backupName = null;
		String version = null;
		for (USBStorageDevice device : deviceHolder.getUSBDevices()) {
			String backupInfoPath = device.getRootDirectory().getAbsolutePath() + "/backup-info.yaml";
			System.out.println("looking for " + backupInfoPath);
			File backupInfo = new File(backupInfoPath);

			if (backupInfo.exists()) {
				System.out.println("found file");
				if (backupInfo.canRead()) {
					try {
						FileInputStream backupInput = new FileInputStream(backupInfo);
						Map<String, Object> data = YAML.load(backupInput);
						backupInput.close();
						backupName = (String) data.get(ConfigKeys.BACKUP_NAME);
						version = (String) data.getOrDefault(ConfigKeys.VERSION, null);
						break;
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					System.out.println("file found, not not readable");
				}
			} else {
				System.out.println("file doesn't exist");
			}
		}
		if (backupName == null) {
			logAndHook("could not find any backup-info.yaml files, falling back to first partition name");
			backupName = deviceHolder.getUSBDevices().get(0).getDeviceName();
		}
		return new BackupInfo(backupName, version);
	}

	private static String getOutFile(BackupInfo backupInfo) {
		String timestamp = TIMESTAMP_FORMAT.format(new Date());

		StringBuilder output = new StringBuilder(backupInfo.getBackupName());
		if (backupInfo.getVersion() != null) {
			output.append("_");
			output.append(backupInfo.getVersion());
		}
		output.append("_");
		output.append(timestamp);
		output.append(".img");

		return output.toString();
	}

	private static void logAndHook(String msg) {
		DiscordWebhook hook = new DiscordWebhook(DISCORD_WEBHOOK_URL);
		hook.setContent(msg);
		try {
			hook.execute();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("failed to send via webhook, trying once more");
			try {
				hook.execute();
			} catch (IOException e1) {
				e1.printStackTrace();
				System.out.println("giving up on webhook");
			}
		}
	}

}
