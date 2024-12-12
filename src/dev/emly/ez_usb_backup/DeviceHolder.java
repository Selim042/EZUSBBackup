package dev.emly.ez_usb_backup;

import java.util.LinkedList;
import java.util.List;

import net.samuelcampos.usbdrivedetector.USBStorageDevice;

public class DeviceHolder {

	private final String device;
	private final List<USBStorageDevice> usbDevices;
	private long lastModified;
	private boolean hasBackedUp;

	public DeviceHolder(String device) {
		this.device = device;
		this.usbDevices = new LinkedList<>();
		this.updateModified();
	}

	public String getDevice() {
		return this.device;
	}

	public List<USBStorageDevice> getUSBDevices() {
		return this.usbDevices;
	}

	public long getLastModified() {
		return this.lastModified;
	}

	public DeviceHolder addUSBDevice(USBStorageDevice usbDevice) {
		this.usbDevices.add(usbDevice);
		this.updateModified();
		return this;
	}

	private void updateModified() {
		this.lastModified = System.currentTimeMillis();
	}

	public void setBackedUp() {
		this.hasBackedUp = true;
	}

	public boolean hasBackedUp() {
		return this.hasBackedUp;
	}

}
