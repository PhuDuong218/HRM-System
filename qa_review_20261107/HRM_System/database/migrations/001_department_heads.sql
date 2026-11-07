/* Run in the existing HRM application database, never master. Re-runnable.
   No DROP, no DELETE, no update to historical assignments. UTC audit; [start,end).
   The application lock is also used by every application assignment write path.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

BEGIN TRY
    BEGIN TRANSACTION;
    IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.employee_assignments') IS NULL
        THROW 51100, N'Chon dung database HRM da co schema truoc khi chay migration.', 1;
    DECLARE @lock int;
    EXEC @lock=sys.sp_getapplock @Resource=N'HRM_ASSIGNMENT_TIMELINE',
        @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
    IF @lock<0 THROW 51101,N'Khong lay duoc khoa phan cong; thu lai.',1;

    IF NOT EXISTS(SELECT 1 FROM dbo.positions WHERE position_code='TRUONG_PHONG')
        INSERT dbo.positions(position_code,position_name,description,is_active)
        VALUES ('TRUONG_PHONG',N'Trưởng phòng',N'Chức vụ xác định người đứng đầu phòng ban theo lịch sử phân công',1);
    IF EXISTS(SELECT 1 FROM dbo.positions WHERE position_code='TRUONG_PHONG' AND is_active=0)
        THROW 51102,N'Chuc vu TRUONG_PHONG dang ngung su dung; doi soat truoc khi migration.',1;

    IF EXISTS(SELECT 1 FROM dbo.employee_assignments a JOIN dbo.employee_assignments b
        ON a.employee_id=b.employee_id AND a.assignment_id<b.assignment_id
        WHERE (a.effective_to IS NULL OR b.effective_from<a.effective_to)
          AND (b.effective_to IS NULL OR a.effective_from<b.effective_to))
        THROW 51103,N'Dang co phan cong nhan vien chong ngay; doi soat lich su truoc.',1;
    IF EXISTS(SELECT 1 FROM dbo.employee_assignments a JOIN dbo.employee_assignments b
        ON a.department_id=b.department_id AND a.assignment_id<b.assignment_id
        JOIN dbo.positions p ON p.position_id=a.position_id
        JOIN dbo.positions q ON q.position_id=b.position_id
        WHERE p.position_code='TRUONG_PHONG' AND q.position_code='TRUONG_PHONG'
          AND (a.effective_to IS NULL OR b.effective_from<a.effective_to)
          AND (b.effective_to IS NULL OR a.effective_from<b.effective_to))
        THROW 51104,N'Dang co truong phong chong ngay; doi soat truoc.',1;

    EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_assignments_timeline_guard
        ON dbo.employee_assignments AFTER INSERT, UPDATE AS
        BEGIN
            SET NOCOUNT ON;
            DECLARE @lock int;
            EXEC @lock=sys.sp_getapplock @Resource=N''HRM_ASSIGNMENT_TIMELINE'',
                @LockMode=''Exclusive'',@LockOwner=''Transaction'',@LockTimeout=10000;
            IF @lock<0 THROW 51105,N''Khong lay duoc khoa phan cong.'',1;
            IF EXISTS(SELECT 1 FROM inserted a JOIN dbo.employee_assignments b WITH (UPDLOCK,HOLDLOCK)
                ON a.employee_id=b.employee_id AND a.assignment_id<>b.assignment_id
                WHERE (a.effective_to IS NULL OR b.effective_from<a.effective_to)
                  AND (b.effective_to IS NULL OR a.effective_from<b.effective_to))
                THROW 51106,N''Phan cong nhan vien chong hieu luc.'',1;
            IF EXISTS(SELECT 1 FROM inserted a JOIN dbo.positions p ON p.position_id=a.position_id
                JOIN dbo.employee_assignments b WITH (UPDLOCK,HOLDLOCK)
                    ON a.department_id=b.department_id AND a.assignment_id<>b.assignment_id
                JOIN dbo.positions q ON q.position_id=b.position_id
                WHERE p.position_code=''TRUONG_PHONG'' AND q.position_code=''TRUONG_PHONG''
                  AND (a.effective_to IS NULL OR b.effective_from<a.effective_to)
                  AND (b.effective_to IS NULL OR a.effective_from<b.effective_to))
                THROW 51107,N''Moi phong chi co mot truong phong tai mot thoi diem.'',1;
            IF EXISTS(SELECT 1 FROM inserted a JOIN dbo.positions p ON p.position_id=a.position_id
                JOIN dbo.employees e ON e.employee_id=a.employee_id
                JOIN dbo.departments d ON d.department_id=a.department_id
                WHERE p.position_code=''TRUONG_PHONG'' AND a.effective_to IS NULL
                  AND (e.employment_status<>''DANG_LAM'' OR d.is_active=0 OR p.is_active=0 OR a.effective_from<e.hire_date))
                THROW 51108,N''Truong phong phai dang lam viec va thuoc phong/chuc vu dang hoat dong.'',1;
        END;');

    EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_departments_head_active
        ON dbo.departments AFTER UPDATE AS
        BEGIN SET NOCOUNT ON;
            IF EXISTS(SELECT 1 FROM inserted d JOIN dbo.employee_assignments a ON a.department_id=d.department_id
                JOIN dbo.positions p ON p.position_id=a.position_id
                WHERE d.is_active=0 AND p.position_code=''TRUONG_PHONG'' AND a.effective_to IS NULL)
                THROW 51109,N''Ket thuc truong phong truoc khi ngung su dung phong.'',1;
        END;');
    EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_employees_head_termination
        ON dbo.employees AFTER UPDATE AS
        BEGIN SET NOCOUNT ON;
            IF EXISTS(SELECT 1 FROM inserted e JOIN dbo.employee_assignments a ON a.employee_id=e.employee_id
                JOIN dbo.positions p ON p.position_id=a.position_id
                WHERE e.employment_status=''DA_NGHI'' AND p.position_code=''TRUONG_PHONG'' AND a.effective_to IS NULL)
                THROW 51110,N''Ket thuc truong phong truoc khi nhan vien nghi viec.'',1;
        END;');
    EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_positions_head_code
        ON dbo.positions AFTER UPDATE AS
        BEGIN SET NOCOUNT ON;
            IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.position_id=i.position_id
                WHERE (d.position_code=''TRUONG_PHONG'' OR i.position_code=''TRUONG_PHONG'')
                  AND (i.position_code<>d.position_code OR i.is_active=0))
                THROW 51111,N''Khong doi ma hoac ngung su dung chuc vu he thong TRUONG_PHONG.'',1;
        END;');
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
