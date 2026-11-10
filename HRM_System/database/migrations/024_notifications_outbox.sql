/* HRM-Center: apply to the selected application database, SQL Server 2016+.
   UTC timestamps; reference_id is polymorphic, validated by the target API.
   No cascade delete: preserve evidence. No historical notifications are backfilled. */
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.accounts',N'U') IS NULL THROW 51000,N'Accounts table is required.',1;
IF OBJECT_ID(N'dbo.NotificationOutbox',N'U') IS NULL
BEGIN
 CREATE TABLE dbo.NotificationOutbox (
  event_id uniqueidentifier NOT NULL CONSTRAINT DF_NotificationOutbox_id DEFAULT NEWSEQUENTIALID(),
  idempotency_key nvarchar(200) NOT NULL,
  actor_id bigint NULL,
  event_type varchar(100) NOT NULL,
  title nvarchar(200) NOT NULL,
  message nvarchar(1000) NOT NULL,
  reference_type varchar(80) NOT NULL,
  reference_id bigint NOT NULL,
  status varchar(12) NOT NULL CONSTRAINT DF_NotificationOutbox_status DEFAULT 'PENDING',
  attempt_count int NOT NULL CONSTRAINT DF_NotificationOutbox_attempts DEFAULT 0,
  available_at datetime2(3) NOT NULL CONSTRAINT DF_NotificationOutbox_available DEFAULT SYSUTCDATETIME(),
  created_at datetime2(3) NOT NULL CONSTRAINT DF_NotificationOutbox_created DEFAULT SYSUTCDATETIME(),
  processed_at datetime2(3) NULL,
  last_error nvarchar(1000) NULL,
  CONSTRAINT PK_NotificationOutbox PRIMARY KEY(event_id),
  CONSTRAINT UQ_NotificationOutbox_key UNIQUE(idempotency_key),
  CONSTRAINT FK_NotificationOutbox_actor FOREIGN KEY(actor_id) REFERENCES dbo.accounts(account_id),
  CONSTRAINT CK_NotificationOutbox_status CHECK(status IN ('PENDING','RETRY','PROCESSED','DEAD')),
  CONSTRAINT CK_NotificationOutbox_attempts CHECK(attempt_count>=0),
  CONSTRAINT CK_NotificationOutbox_processed CHECK((status='PROCESSED' AND processed_at IS NOT NULL) OR (status<>'PROCESSED' AND processed_at IS NULL)),
  CONSTRAINT CK_NotificationOutbox_reference CHECK(reference_id>0),
  CONSTRAINT CK_NotificationOutbox_content CHECK(LEN(LTRIM(RTRIM(title)))>0 AND LEN(LTRIM(RTRIM(message)))>0 AND LEN(LTRIM(RTRIM(idempotency_key)))>0)
 );
 CREATE INDEX IX_NotificationOutbox_dispatch ON dbo.NotificationOutbox(status,available_at,created_at) INCLUDE(attempt_count);
END;
IF OBJECT_ID(N'dbo.NotificationOutboxRecipients',N'U') IS NULL
BEGIN
 CREATE TABLE dbo.NotificationOutboxRecipients (
  event_id uniqueidentifier NOT NULL,
  recipient_id bigint NOT NULL,
  CONSTRAINT PK_NotificationOutboxRecipients PRIMARY KEY(event_id,recipient_id),
  CONSTRAINT FK_NotificationOutboxRecipients_event FOREIGN KEY(event_id) REFERENCES dbo.NotificationOutbox(event_id),
  CONSTRAINT FK_NotificationOutboxRecipients_account FOREIGN KEY(recipient_id) REFERENCES dbo.accounts(account_id)
 );
END;
IF OBJECT_ID(N'dbo.Notifications',N'U') IS NULL
BEGIN
 CREATE TABLE dbo.Notifications (
  notification_id bigint IDENTITY(1,1) NOT NULL,
  event_id uniqueidentifier NOT NULL,
  recipient_id bigint NOT NULL,
  event_type varchar(100) NOT NULL,
  title nvarchar(200) NOT NULL,
  message nvarchar(1000) NOT NULL,
  reference_type varchar(80) NOT NULL,
  reference_id bigint NOT NULL,
  is_read bit NOT NULL CONSTRAINT DF_Notifications_read DEFAULT 0,
  created_at datetime2(3) NOT NULL CONSTRAINT DF_Notifications_created DEFAULT SYSUTCDATETIME(),
  read_at datetime2(3) NULL,
  CONSTRAINT PK_Notifications PRIMARY KEY(notification_id),
  CONSTRAINT UQ_Notifications_event_recipient UNIQUE(event_id,recipient_id),
  CONSTRAINT FK_Notifications_delivery FOREIGN KEY(event_id,recipient_id) REFERENCES dbo.NotificationOutboxRecipients(event_id,recipient_id),
  CONSTRAINT FK_Notifications_recipient FOREIGN KEY(recipient_id) REFERENCES dbo.accounts(account_id),
  CONSTRAINT CK_Notifications_reference CHECK(reference_id>0),
  CONSTRAINT CK_Notifications_read CHECK((is_read=0 AND read_at IS NULL) OR (is_read=1 AND read_at IS NOT NULL)),
  CONSTRAINT CK_Notifications_content CHECK(LEN(LTRIM(RTRIM(title)))>0 AND LEN(LTRIM(RTRIM(message)))>0)
 );
 CREATE INDEX IX_Notifications_inbox ON dbo.Notifications(recipient_id,notification_id DESC) INCLUDE(is_read,created_at);
 CREATE INDEX IX_Notifications_unread ON dbo.Notifications(recipient_id,notification_id DESC) WHERE is_read=0;
END;
COMMIT TRANSACTION;
