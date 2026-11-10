USE master;
GO
SET NOCOUNT ON;
DECLARE @directory nvarchar(4000)=CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath'));
IF @directory IS NULL THROW 51000,'Backup directory unavailable. No data changed.',1;
DECLARE @file nvarchar(4000)=@directory+N'\hrm_center_full_before_refresh_'+REPLACE(CONVERT(nvarchar(36),NEWID()),'-','')+N'.bak';
BACKUP DATABASE hrm_center_full TO DISK=@file WITH COPY_ONLY,CHECKSUM,STATS=10;
RESTORE VERIFYONLY FROM DISK=@file WITH CHECKSUM;
SELECT @file backup_file;
