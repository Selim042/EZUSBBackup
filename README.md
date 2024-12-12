# EZUSBBackup

THIS ONLY WORKS ON LINUX SYSTEMS

A tool to quickly and easily backup USB storage devices. Once running, this program will automatically make a disk image for any inserted USB storage devices and post updates via the Discord webhook provided in the `config.yaml` file.

When the drive is inserted, each partition will be scanned for a `backup-info.yaml` file that can contain a name for the backup and versions.