/* HRM-Center / SQL Server 2016+ (ISJSON), chạy bằng SSMS hoặc sqlcmd -b -f 65001.
   Script khởi tạo một lần: không DROP/TRUNCATE, không ghi đè database có bảng.
   DB: HRMCenter. Có thể đổi tên ở CREATE DATABASE và USE trước khi chạy.
   Thời điểm audit: UTC; giờ làm việc: giờ địa phương trung tâm (Asia/Bangkok).
   Khoảng effective/period: [start, end); contracts.end_date là ngày cuối bao gồm.
   Enum hợp đồng/công là lựa chọn triển khai ban đầu; chính sách tiền chưa chốt.
   Mẫu lương chỉ NHAP, không phải công thức lương chính thức hoặc đã thanh toán.
   Không có đăng nhập SQL Server, không cấp sysadmin/db_owner cho ứng dụng.
*/
USE [master];
GO
IF DB_ID(N'hrm_db') IS NULL
    EXEC(N'CREATE DATABASE [hrm_db]');
GO
USE [hrm_db];
GO
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
    IF EXISTS (SELECT 1 FROM sys.tables WHERE is_ms_shipped = 0)
        THROW 51000, N'Database đã có bảng. Dừng để không ghi đè dữ liệu; dùng migration khi cần nâng cấp.', 1;

    -- 1. Danh mục độc lập và hồ sơ nhân viên: tạo trước các bảng tham chiếu.
    CREATE TABLE dbo.roles (
        role_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_roles PRIMARY KEY,
        role_code varchar(50) NOT NULL CONSTRAINT UQ_roles_code UNIQUE,
        role_name nvarchar(100) NOT NULL,
        CONSTRAINT CK_roles_code CHECK (role_code IN ('ADMIN','HR','EMPLOYEE'))
    );
    CREATE TABLE dbo.permissions (
        permission_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_permissions PRIMARY KEY,
        permission_code varchar(100) NOT NULL CONSTRAINT UQ_permissions_code UNIQUE,
        permission_name nvarchar(200) NOT NULL,
        description nvarchar(1000) NULL
    );
    CREATE TABLE dbo.departments (
        department_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_departments PRIMARY KEY,
        department_code varchar(50) NOT NULL CONSTRAINT UQ_departments_code UNIQUE,
        department_name nvarchar(200) NOT NULL,
        description nvarchar(1000) NULL,
        is_active bit NOT NULL CONSTRAINT DF_departments_active DEFAULT (1)
    );
    CREATE TABLE dbo.positions (
        position_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_positions PRIMARY KEY,
        position_code varchar(50) NOT NULL CONSTRAINT UQ_positions_code UNIQUE,
        position_name nvarchar(200) NOT NULL,
        description nvarchar(1000) NULL,
        is_active bit NOT NULL CONSTRAINT DF_positions_active DEFAULT (1)
    );
    CREATE TABLE dbo.qualification_levels (
        qualification_level_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_qualification_levels PRIMARY KEY,
        level_code varchar(50) NOT NULL CONSTRAINT UQ_qualification_levels_code UNIQUE,
        level_name nvarchar(200) NOT NULL,
        description nvarchar(1000) NULL,
        is_active bit NOT NULL CONSTRAINT DF_qualification_levels_active DEFAULT (1)
    );
    CREATE TABLE dbo.contract_types (
        contract_type_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_contract_types PRIMARY KEY,
        type_code varchar(50) NOT NULL CONSTRAINT UQ_contract_types_code UNIQUE,
        type_name nvarchar(200) NOT NULL,
        is_active bit NOT NULL CONSTRAINT DF_contract_types_active DEFAULT (1)
    );
    CREATE TABLE dbo.work_shifts (
        shift_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_work_shifts PRIMARY KEY,
        shift_code varchar(50) NOT NULL CONSTRAINT UQ_work_shifts_code UNIQUE,
        shift_name nvarchar(200) NOT NULL,
        start_time time(0) NOT NULL,
        end_time time(0) NOT NULL,
        end_day_offset int NOT NULL CONSTRAINT DF_work_shifts_offset DEFAULT (0),
        break_minutes int NOT NULL CONSTRAINT DF_work_shifts_break DEFAULT (0),
        is_active bit NOT NULL CONSTRAINT DF_work_shifts_active DEFAULT (1),
        CONSTRAINT CK_work_shifts_time CHECK (
            end_day_offset IN (0,1) AND break_minutes >= 0
            AND DATEDIFF(MINUTE,start_time,end_time) + 1440 * end_day_offset > 0
            AND break_minutes <= DATEDIFF(MINUTE,start_time,end_time) + 1440 * end_day_offset)
    );
    CREATE TABLE dbo.leave_types (
        leave_type_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_leave_types PRIMARY KEY,
        type_code varchar(50) NOT NULL CONSTRAINT UQ_leave_types_code UNIQUE,
        type_name nvarchar(200) NOT NULL,
        is_paid bit NOT NULL CONSTRAINT DF_leave_types_paid DEFAULT (0),
        is_active bit NOT NULL CONSTRAINT DF_leave_types_active DEFAULT (1)
    );
    CREATE TABLE dbo.allowance_types (
        allowance_type_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_allowance_types PRIMARY KEY,
        type_code varchar(50) NOT NULL CONSTRAINT UQ_allowance_types_code UNIQUE,
        type_name nvarchar(200) NOT NULL,
        description nvarchar(1000) NULL,
        is_active bit NOT NULL CONSTRAINT DF_allowance_types_active DEFAULT (1)
    );
    CREATE TABLE dbo.salary_components (
        component_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_salary_components PRIMARY KEY,
        component_code varchar(50) NOT NULL CONSTRAINT UQ_salary_components_code UNIQUE,
        component_name nvarchar(200) NOT NULL,
        direction varchar(10) NOT NULL,
        is_active bit NOT NULL CONSTRAINT DF_salary_components_active DEFAULT (1),
        CONSTRAINT CK_salary_components_direction CHECK (direction IN ('ADD','DEDUCT'))
    );
    CREATE TABLE dbo.employees (
        employee_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_employees PRIMARY KEY,
        employee_code varchar(50) NOT NULL CONSTRAINT UQ_employees_code UNIQUE,
        full_name nvarchar(200) NOT NULL,
        birth_date date NULL,
        email nvarchar(254) NULL,
        phone varchar(30) NULL,
        address nvarchar(500) NULL,
        identity_number varchar(50) NULL,
        hire_date date NOT NULL,
        employment_status varchar(20) NOT NULL CONSTRAINT DF_employees_status DEFAULT ('DANG_LAM'),
        termination_date date NULL,
        CONSTRAINT CK_employees_name CHECK (LEN(LTRIM(RTRIM(full_name))) > 0),
        CONSTRAINT CK_employees_dates CHECK (birth_date IS NULL OR birth_date < hire_date),
        CONSTRAINT CK_employees_status CHECK (
            (employment_status = 'DANG_LAM' AND termination_date IS NULL)
            OR (employment_status = 'DA_NGHI' AND termination_date IS NOT NULL AND termination_date >= hire_date))
    );
    -- Không tự áp đặt UNIQUE email/giấy tờ khi chính sách chưa xác định.
    CREATE TABLE dbo.accounts (
        account_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_accounts PRIMARY KEY,
        employee_id bigint NOT NULL CONSTRAINT UQ_accounts_employee UNIQUE,
        role_id bigint NOT NULL,
        username nvarchar(100) COLLATE Latin1_General_100_CI_AS NOT NULL CONSTRAINT UQ_accounts_username UNIQUE,
        password_hash varchar(255) NOT NULL,
        is_active bit NOT NULL CONSTRAINT DF_accounts_active DEFAULT (1),
        token_version int NOT NULL CONSTRAINT DF_accounts_token_version DEFAULT (0),
        last_login_at datetime2(3) NULL,
        CONSTRAINT FK_accounts_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees(employee_id),
        CONSTRAINT FK_accounts_role FOREIGN KEY (role_id) REFERENCES dbo.roles(role_id),
        CONSTRAINT CK_accounts_username CHECK (LEN(LTRIM(RTRIM(username))) > 0),
        CONSTRAINT CK_accounts_hash CHECK (LEN(password_hash) >= 60),
        CONSTRAINT CK_accounts_token_version CHECK (token_version >= 0)
    );
    CREATE TABLE dbo.role_permissions (
        role_id bigint NOT NULL,
        permission_id bigint NOT NULL,
        CONSTRAINT PK_role_permissions PRIMARY KEY (role_id,permission_id),
        CONSTRAINT FK_role_permissions_role FOREIGN KEY (role_id) REFERENCES dbo.roles(role_id),
        CONSTRAINT FK_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES dbo.permissions(permission_id)
    );
    CREATE TABLE dbo.employee_assignments (
        assignment_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_employee_assignments PRIMARY KEY,
        employee_id bigint NOT NULL,
        department_id bigint NOT NULL,
        position_id bigint NOT NULL,
        effective_from date NOT NULL,
        effective_to date NULL,
        CONSTRAINT FK_assignments_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees(employee_id),
        CONSTRAINT FK_assignments_department FOREIGN KEY (department_id) REFERENCES dbo.departments(department_id),
        CONSTRAINT FK_assignments_position FOREIGN KEY (position_id) REFERENCES dbo.positions(position_id),
        CONSTRAINT CK_assignments_dates CHECK (effective_to IS NULL OR effective_to > effective_from)
    );
    CREATE UNIQUE INDEX UX_assignments_open ON dbo.employee_assignments(employee_id) WHERE effective_to IS NULL;
    CREATE INDEX IX_assignments_employee_dates ON dbo.employee_assignments(employee_id,effective_from,effective_to);
    CREATE INDEX IX_assignments_department ON dbo.employee_assignments(department_id,effective_from);
    CREATE TABLE dbo.employee_qualifications (
        employee_qualification_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_employee_qualifications PRIMARY KEY,
        employee_id bigint NOT NULL,
        qualification_level_id bigint NOT NULL,
        major nvarchar(200) NULL,
        institution nvarchar(300) NULL,
        graduation_year int NULL,
        certificate_number varchar(100) NULL,
        is_primary bit NOT NULL CONSTRAINT DF_employee_qualifications_primary DEFAULT (0),
        CONSTRAINT FK_qualifications_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees(employee_id),
        CONSTRAINT FK_qualifications_level FOREIGN KEY (qualification_level_id) REFERENCES dbo.qualification_levels(qualification_level_id),
        CONSTRAINT CK_qualifications_year CHECK (graduation_year IS NULL OR graduation_year BETWEEN 1900 AND 9999)
    );
    CREATE UNIQUE INDEX UX_qualifications_primary ON dbo.employee_qualifications(employee_id) WHERE is_primary = 1;
    CREATE TABLE dbo.contracts (
        contract_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_contracts PRIMARY KEY,
        employee_id bigint NOT NULL,
        contract_type_id bigint NOT NULL,
        contract_number varchar(50) NOT NULL CONSTRAINT UQ_contracts_number UNIQUE,
        signed_date date NOT NULL,
        start_date date NOT NULL,
        end_date date NULL,
        status varchar(20) NOT NULL CONSTRAINT DF_contracts_status DEFAULT ('DA_KY'),
        CONSTRAINT FK_contracts_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees(employee_id),
        CONSTRAINT FK_contracts_type FOREIGN KEY (contract_type_id) REFERENCES dbo.contract_types(contract_type_id),
        CONSTRAINT CK_contracts_dates CHECK (end_date IS NULL OR end_date >= start_date),
        CONSTRAINT CK_contracts_status CHECK (status IN ('DA_KY','DA_KET_THUC'))
    );
    CREATE INDEX IX_contracts_employee_dates ON dbo.contracts(employee_id,start_date,end_date);
    CREATE TABLE dbo.contract_salary_terms (
        salary_term_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_contract_salary_terms PRIMARY KEY,
        contract_id bigint NOT NULL,
        effective_from date NOT NULL,
        effective_to date NULL,
        base_salary decimal(19,4) NOT NULL,
        pay_basis varchar(10) NOT NULL CONSTRAINT DF_salary_terms_basis DEFAULT ('THANG'),
        document_reference nvarchar(500) NULL,
        CONSTRAINT FK_salary_terms_contract FOREIGN KEY (contract_id) REFERENCES dbo.contracts(contract_id),
        CONSTRAINT CK_salary_terms_dates CHECK (effective_to IS NULL OR effective_to > effective_from),
        CONSTRAINT CK_salary_terms_amount CHECK (base_salary >= 0),
        CONSTRAINT CK_salary_terms_basis CHECK (pay_basis IN ('THANG','NGAY','GIO'))
    );
    CREATE UNIQUE INDEX UX_salary_terms_open ON dbo.contract_salary_terms(contract_id) WHERE effective_to IS NULL;
    CREATE INDEX IX_salary_terms_dates ON dbo.contract_salary_terms(contract_id,effective_from,effective_to);

    -- 2. Lịch độc lập với công; FK ghép ngăn gắn công vào lịch người khác.
    CREATE TABLE dbo.work_schedules (
        schedule_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_work_schedules PRIMARY KEY,
        employee_id bigint NOT NULL,
        shift_id bigint NULL,
        work_date date NOT NULL,
        planned_start datetime2(3) NOT NULL,
        planned_end datetime2(3) NOT NULL,
        planned_break_minutes int NOT NULL CONSTRAINT DF_schedules_break DEFAULT (0),
        note nvarchar(1000) NULL,
        CONSTRAINT UQ_schedules_owner UNIQUE (schedule_id,employee_id),
        CONSTRAINT FK_schedules_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees(employee_id),
        CONSTRAINT FK_schedules_shift FOREIGN KEY (shift_id) REFERENCES dbo.work_shifts(shift_id),
        CONSTRAINT CK_schedules_time CHECK (planned_end > planned_start AND work_date = CONVERT(date,planned_start)
            AND planned_break_minutes >= 0 AND planned_break_minutes <= DATEDIFF_BIG(MINUTE,planned_start,planned_end))
    );
    CREATE INDEX IX_schedules_employee_time ON dbo.work_schedules(employee_id,planned_start,planned_end);
    CREATE TABLE dbo.attendance_records (
        attendance_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_attendance_records PRIMARY KEY,
        employee_id bigint NOT NULL,
        schedule_id bigint NULL,
        work_date date NOT NULL,
        actual_start datetime2(3) NULL,
        actual_end datetime2(3) NULL,
        actual_break_minutes int NOT NULL CONSTRAINT DF_attendance_break DEFAULT (0),
        attendance_result varchar(20) NOT NULL,
        verification_status varchar(20) NOT NULL CONSTRAINT DF_attendance_verification DEFAULT ('CHUA_XAC_NHAN'),
        confirmed_by bigint NULL,
        confirmed_at datetime2(3) NULL,
        correction_reason nvarchar(1000) NULL,
        CONSTRAINT FK_attendance_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees(employee_id),
        CONSTRAINT FK_attendance_schedule_owner FOREIGN KEY (schedule_id,employee_id) REFERENCES dbo.work_schedules(schedule_id,employee_id),
        CONSTRAINT FK_attendance_confirmer FOREIGN KEY (confirmed_by) REFERENCES dbo.accounts(account_id),
        CONSTRAINT CK_attendance_result CHECK (attendance_result IN ('DI_LAM','VANG')),
        CONSTRAINT CK_attendance_time CHECK (actual_break_minutes >= 0
            AND (actual_end IS NULL OR (actual_start IS NOT NULL AND actual_end > actual_start
                AND actual_break_minutes <= DATEDIFF_BIG(MINUTE,actual_start,actual_end)))
            AND ((attendance_result = 'VANG' AND actual_start IS NULL AND actual_end IS NULL AND actual_break_minutes = 0)
                OR (attendance_result = 'DI_LAM' AND actual_start IS NOT NULL))),
        CONSTRAINT CK_attendance_verification CHECK (
            (verification_status = 'CHUA_XAC_NHAN' AND confirmed_by IS NULL AND confirmed_at IS NULL)
            OR (verification_status = 'DA_XAC_NHAN' AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL
                AND (attendance_result = 'VANG' OR actual_end IS NOT NULL)))
    );
    CREATE UNIQUE INDEX UX_attendance_schedule ON dbo.attendance_records(schedule_id) WHERE schedule_id IS NOT NULL;
    CREATE INDEX IX_attendance_employee_date ON dbo.attendance_records(employee_id,work_date);
    CREATE TABLE dbo.employee_requests (
        request_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_employee_requests PRIMARY KEY,
        employee_id bigint NOT NULL,
        request_code varchar(50) NOT NULL CONSTRAINT UQ_employee_requests_code UNIQUE,
        request_type varchar(20) NOT NULL,
        reason nvarchar(1000) NOT NULL,
        status varchar(20) NOT NULL CONSTRAINT DF_employee_requests_status DEFAULT ('CHO_DUYET'),
        submitted_at datetime2(3) NOT NULL CONSTRAINT DF_employee_requests_submitted DEFAULT (SYSUTCDATETIME()),
        processed_by bigint NULL,
        processed_at datetime2(3) NULL,
        rejection_reason nvarchar(1000) NULL,
        CONSTRAINT FK_requests_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees(employee_id),
        CONSTRAINT FK_requests_processor FOREIGN KEY (processed_by) REFERENCES dbo.accounts(account_id),
        CONSTRAINT CK_requests_type CHECK (request_type IN ('NGHI_PHEP','NGHI_VIEC')),
        CONSTRAINT CK_requests_reason CHECK (LEN(LTRIM(RTRIM(reason))) > 0),
        CONSTRAINT CK_requests_processing CHECK (
            (status = 'CHO_DUYET' AND processed_by IS NULL AND processed_at IS NULL AND rejection_reason IS NULL)
            OR (status = 'DA_DUYET' AND processed_by IS NOT NULL AND processed_at IS NOT NULL AND rejection_reason IS NULL)
            OR (status = 'TU_CHOI' AND processed_by IS NOT NULL AND processed_at IS NOT NULL
                AND rejection_reason IS NOT NULL AND LEN(LTRIM(RTRIM(rejection_reason))) > 0)),
        CONSTRAINT CK_requests_processed_time CHECK (processed_at IS NULL OR processed_at >= submitted_at)
    );
    CREATE INDEX IX_requests_employee_date ON dbo.employee_requests(employee_id,submitted_at);
    CREATE INDEX IX_requests_queue ON dbo.employee_requests(status,submitted_at);
    CREATE TABLE dbo.leave_request_details (
        request_id bigint NOT NULL CONSTRAINT PK_leave_request_details PRIMARY KEY,
        leave_type_id bigint NOT NULL,
        leave_start datetime2(3) NOT NULL,
        leave_end datetime2(3) NOT NULL,
        CONSTRAINT FK_leave_details_request FOREIGN KEY (request_id) REFERENCES dbo.employee_requests(request_id),
        CONSTRAINT FK_leave_details_type FOREIGN KEY (leave_type_id) REFERENCES dbo.leave_types(leave_type_id),
        CONSTRAINT CK_leave_details_time CHECK (leave_end > leave_start)
    );
    CREATE TABLE dbo.resignation_request_details (
        request_id bigint NOT NULL CONSTRAINT PK_resignation_request_details PRIMARY KEY,
        requested_last_working_date date NOT NULL,
        approved_last_working_date date NULL,
        CONSTRAINT FK_resignation_details_request FOREIGN KEY (request_id) REFERENCES dbo.employee_requests(request_id)
    );
    CREATE TABLE dbo.leave_entitlements (
        entitlement_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_leave_entitlements PRIMARY KEY,
        employee_id bigint NOT NULL,
        leave_type_id bigint NOT NULL,
        period_start date NOT NULL,
        period_end date NOT NULL,
        entitled_hours decimal(9,4) NOT NULL CONSTRAINT DF_entitlements_hours DEFAULT (0),
        CONSTRAINT UQ_entitlements_period UNIQUE (employee_id,leave_type_id,period_start,period_end),
        CONSTRAINT FK_entitlements_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees(employee_id),
        CONSTRAINT FK_entitlements_type FOREIGN KEY (leave_type_id) REFERENCES dbo.leave_types(leave_type_id),
        CONSTRAINT CK_entitlements_dates CHECK (period_end > period_start),
        CONSTRAINT CK_entitlements_hours CHECK (entitled_hours >= 0)
    );
    CREATE TABLE dbo.overtime_requests (
        overtime_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_overtime_requests PRIMARY KEY,
        employee_id bigint NOT NULL,
        request_code varchar(50) NOT NULL CONSTRAINT UQ_overtime_requests_code UNIQUE,
        requested_start datetime2(3) NOT NULL,
        requested_end datetime2(3) NOT NULL,
        reason nvarchar(1000) NOT NULL,
        submitted_at datetime2(3) NOT NULL CONSTRAINT DF_overtime_submitted DEFAULT (SYSUTCDATETIME()),
        status varchar(20) NOT NULL CONSTRAINT DF_overtime_status DEFAULT ('CHO_DUYET'),
        processed_by bigint NULL,
        processed_at datetime2(3) NULL,
        rejection_reason nvarchar(1000) NULL,
        actual_start datetime2(3) NULL,
        actual_end datetime2(3) NULL,
        actual_break_minutes int NOT NULL CONSTRAINT DF_overtime_break DEFAULT (0),
        confirmed_by bigint NULL,
        confirmed_at datetime2(3) NULL,
        CONSTRAINT FK_overtime_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees(employee_id),
        CONSTRAINT FK_overtime_processor FOREIGN KEY (processed_by) REFERENCES dbo.accounts(account_id),
        CONSTRAINT FK_overtime_confirmer FOREIGN KEY (confirmed_by) REFERENCES dbo.accounts(account_id),
        CONSTRAINT CK_overtime_requested CHECK (requested_end > requested_start AND LEN(LTRIM(RTRIM(reason))) > 0),
        CONSTRAINT CK_overtime_processing CHECK (
            (status = 'CHO_DUYET' AND processed_by IS NULL AND processed_at IS NULL AND rejection_reason IS NULL)
            OR (status = 'DA_DUYET' AND processed_by IS NOT NULL AND processed_at IS NOT NULL AND rejection_reason IS NULL)
            OR (status = 'TU_CHOI' AND processed_by IS NOT NULL AND processed_at IS NOT NULL
                AND rejection_reason IS NOT NULL AND LEN(LTRIM(RTRIM(rejection_reason))) > 0)),
        CONSTRAINT CK_overtime_processed_time CHECK (processed_at IS NULL OR processed_at >= submitted_at),
        CONSTRAINT CK_overtime_actual CHECK (actual_break_minutes >= 0
            AND ((actual_start IS NULL AND actual_end IS NULL AND actual_break_minutes = 0)
                OR (actual_start IS NOT NULL AND actual_end IS NOT NULL AND actual_end > actual_start
                    AND actual_break_minutes <= DATEDIFF_BIG(MINUTE,actual_start,actual_end)))),
        CONSTRAINT CK_overtime_confirmation CHECK (
            (confirmed_by IS NULL AND confirmed_at IS NULL)
            OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL AND status = 'DA_DUYET'
                AND actual_start IS NOT NULL AND actual_end IS NOT NULL))
    );
    CREATE INDEX IX_overtime_employee_time ON dbo.overtime_requests(employee_id,requested_start,requested_end);
    CREATE TABLE dbo.employee_allowances (
        employee_allowance_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_employee_allowances PRIMARY KEY,
        employee_id bigint NOT NULL,
        allowance_type_id bigint NOT NULL,
        effective_from date NOT NULL,
        effective_to date NULL,
        calculation_basis varchar(20) NOT NULL CONSTRAINT DF_allowances_basis DEFAULT ('THEO_KY'),
        amount decimal(19,4) NOT NULL,
        condition_note nvarchar(1000) NULL,
        CONSTRAINT FK_allowances_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees(employee_id),
        CONSTRAINT FK_allowances_type FOREIGN KEY (allowance_type_id) REFERENCES dbo.allowance_types(allowance_type_id),
        CONSTRAINT CK_allowances_dates CHECK (effective_to IS NULL OR effective_to > effective_from),
        CONSTRAINT CK_allowances_amount CHECK (amount >= 0),
        CONSTRAINT CK_allowances_basis CHECK (calculation_basis IN ('THEO_KY','THEO_NGAY_CONG','THEO_GIO'))
    );
    CREATE UNIQUE INDEX UX_allowances_open ON dbo.employee_allowances(employee_id,allowance_type_id) WHERE effective_to IS NULL;
    CREATE INDEX IX_allowances_employee_dates ON dbo.employee_allowances(employee_id,effective_from,effective_to);
    CREATE TABLE dbo.employee_decisions (
        decision_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_employee_decisions PRIMARY KEY,
        employee_id bigint NOT NULL,
        decision_number varchar(50) NOT NULL CONSTRAINT UQ_employee_decisions_number UNIQUE,
        decision_type varchar(20) NOT NULL,
        content nvarchar(2000) NOT NULL,
        issued_date date NOT NULL,
        issued_by bigint NOT NULL,
        approved_by bigint NULL,
        approved_at datetime2(3) NULL,
        approval_status varchar(20) NOT NULL CONSTRAINT DF_decisions_status DEFAULT ('CHO_DUYET'),
        payroll_effect varchar(10) NOT NULL CONSTRAINT DF_decisions_effect DEFAULT ('NONE'),
        amount decimal(19,4) NULL,
        apply_date date NULL,
        CONSTRAINT FK_decisions_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees(employee_id),
        CONSTRAINT FK_decisions_issuer FOREIGN KEY (issued_by) REFERENCES dbo.accounts(account_id),
        CONSTRAINT FK_decisions_approver FOREIGN KEY (approved_by) REFERENCES dbo.accounts(account_id),
        CONSTRAINT CK_decisions_type CHECK (decision_type IN ('KHEN_THUONG','KY_LUAT')),
        CONSTRAINT CK_decisions_content CHECK (LEN(LTRIM(RTRIM(content))) > 0),
        CONSTRAINT CK_decisions_approval CHECK (
            (approval_status = 'CHO_DUYET' AND approved_by IS NULL AND approved_at IS NULL)
            OR (approval_status IN ('DA_DUYET','TU_CHOI') AND approved_by IS NOT NULL AND approved_at IS NOT NULL)),
        CONSTRAINT CK_decisions_payroll CHECK (
            (payroll_effect = 'NONE' AND amount IS NULL AND apply_date IS NULL)
            OR (payroll_effect IN ('ADD','DEDUCT') AND amount IS NOT NULL AND amount > 0 AND apply_date IS NOT NULL))
    );
    CREATE INDEX IX_decisions_employee_date ON dbo.employee_decisions(employee_id,apply_date);

    -- 3. Kỳ -> phiên bản bảng lương -> kết quả nhân viên -> dòng tiền/snapshot/phiếu.
    CREATE TABLE dbo.payroll_periods (
        period_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_payroll_periods PRIMARY KEY,
        period_code varchar(50) NOT NULL CONSTRAINT UQ_payroll_periods_code UNIQUE,
        period_start date NOT NULL,
        period_end date NOT NULL,
        CONSTRAINT UQ_payroll_periods_dates UNIQUE (period_start,period_end),
        CONSTRAINT CK_payroll_periods_dates CHECK (period_end > period_start)
    );
    CREATE TABLE dbo.payroll_batches (
        batch_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_payroll_batches PRIMARY KEY,
        period_id bigint NOT NULL,
        version_no int NOT NULL CONSTRAINT DF_payroll_batches_version DEFAULT (1),
        previous_batch_id bigint NULL,
        status varchar(20) NOT NULL CONSTRAINT DF_payroll_batches_status DEFAULT ('NHAP'),
        adjustment_reason nvarchar(1000) NULL,
        locked_by bigint NULL,
        locked_at datetime2(3) NULL,
        published_by bigint NULL,
        published_at datetime2(3) NULL,
        CONSTRAINT UQ_batches_version UNIQUE (period_id,version_no),
        CONSTRAINT UQ_batches_period_key UNIQUE (batch_id,period_id),
        CONSTRAINT FK_batches_period FOREIGN KEY (period_id) REFERENCES dbo.payroll_periods(period_id),
        CONSTRAINT FK_batches_previous_period FOREIGN KEY (previous_batch_id,period_id) REFERENCES dbo.payroll_batches(batch_id,period_id),
        CONSTRAINT FK_batches_locker FOREIGN KEY (locked_by) REFERENCES dbo.accounts(account_id),
        CONSTRAINT FK_batches_publisher FOREIGN KEY (published_by) REFERENCES dbo.accounts(account_id),
        CONSTRAINT CK_batches_version CHECK ((version_no = 1 AND previous_batch_id IS NULL AND adjustment_reason IS NULL)
            OR (version_no > 1 AND previous_batch_id IS NOT NULL AND adjustment_reason IS NOT NULL
                AND LEN(LTRIM(RTRIM(adjustment_reason))) > 0 AND previous_batch_id <> batch_id)),
        CONSTRAINT CK_batches_lock CHECK ((status = 'NHAP' AND locked_by IS NULL AND locked_at IS NULL)
            OR (status = 'DA_CHOT' AND locked_by IS NOT NULL AND locked_at IS NOT NULL)),
        CONSTRAINT CK_batches_publication CHECK ((published_by IS NULL AND published_at IS NULL)
            OR (published_by IS NOT NULL AND published_at IS NOT NULL AND status = 'DA_CHOT' AND published_at >= locked_at))
    );
    CREATE UNIQUE INDEX UX_batches_one_draft ON dbo.payroll_batches(period_id) WHERE status = 'NHAP';
    CREATE TABLE dbo.payroll_details (
        payroll_detail_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_payroll_details PRIMARY KEY,
        batch_id bigint NOT NULL,
        employee_id bigint NOT NULL,
        CONSTRAINT UQ_payroll_details_employee UNIQUE (batch_id,employee_id),
        CONSTRAINT FK_payroll_details_batch FOREIGN KEY (batch_id) REFERENCES dbo.payroll_batches(batch_id),
        CONSTRAINT FK_payroll_details_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees(employee_id)
    );
    CREATE INDEX IX_payroll_details_employee ON dbo.payroll_details(employee_id,batch_id);
    CREATE TABLE dbo.payroll_lines (
        payroll_line_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_payroll_lines PRIMARY KEY,
        payroll_detail_id bigint NOT NULL,
        component_id bigint NOT NULL,
        line_no int NOT NULL,
        component_code_snapshot varchar(50) NOT NULL,
        component_name_snapshot nvarchar(200) NOT NULL,
        direction_snapshot varchar(10) NOT NULL,
        quantity decimal(9,4) NULL,
        unit_rate decimal(19,4) NULL,
        multiplier decimal(9,4) NULL,
        amount decimal(19,4) NOT NULL,
        explanation nvarchar(2000) NULL,
        CONSTRAINT UQ_payroll_lines_number UNIQUE (payroll_detail_id,line_no),
        CONSTRAINT FK_payroll_lines_detail FOREIGN KEY (payroll_detail_id) REFERENCES dbo.payroll_details(payroll_detail_id),
        CONSTRAINT FK_payroll_lines_component FOREIGN KEY (component_id) REFERENCES dbo.salary_components(component_id),
        CONSTRAINT CK_payroll_lines_values CHECK (line_no > 0 AND amount >= 0
            AND (quantity IS NULL OR quantity >= 0) AND (unit_rate IS NULL OR unit_rate >= 0)
            AND (multiplier IS NULL OR multiplier > 0)),
        CONSTRAINT CK_payroll_lines_direction CHECK (direction_snapshot IN ('ADD','DEDUCT'))
    );
    CREATE TABLE dbo.payroll_snapshots (
        payroll_detail_id bigint NOT NULL CONSTRAINT PK_payroll_snapshots PRIMARY KEY,
        schema_version int NOT NULL CONSTRAINT DF_payroll_snapshots_schema DEFAULT (1),
        policy_version varchar(100) NOT NULL,
        input_snapshot_json nvarchar(max) NOT NULL,
        captured_at datetime2(3) NOT NULL CONSTRAINT DF_payroll_snapshots_captured DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_payroll_snapshots_detail FOREIGN KEY (payroll_detail_id) REFERENCES dbo.payroll_details(payroll_detail_id),
        CONSTRAINT CK_payroll_snapshots_schema CHECK (schema_version > 0),
        CONSTRAINT CK_payroll_snapshots_json CHECK (ISJSON(input_snapshot_json) = 1)
    );
    CREATE TABLE dbo.payslips (
        payslip_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_payslips PRIMARY KEY,
        payroll_detail_id bigint NOT NULL CONSTRAINT UQ_payslips_detail UNIQUE,
        payslip_number varchar(50) NOT NULL CONSTRAINT UQ_payslips_number UNIQUE,
        issued_at datetime2(3) NOT NULL CONSTRAINT DF_payslips_issued DEFAULT (SYSUTCDATETIME()),
        file_reference nvarchar(1000) NULL,
        CONSTRAINT FK_payslips_detail FOREIGN KEY (payroll_detail_id) REFERENCES dbo.payroll_details(payroll_detail_id)
    );
    CREATE TABLE dbo.audit_logs (
        audit_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_audit_logs PRIMARY KEY,
        actor_account_id bigint NULL,
        occurred_at datetime2(3) NOT NULL CONSTRAINT DF_audit_logs_occurred DEFAULT (SYSUTCDATETIME()),
        action varchar(100) NOT NULL,
        target_table varchar(128) NOT NULL,
        target_record_key nvarchar(100) NOT NULL,
        reason nvarchar(1000) NULL,
        before_data_json nvarchar(max) NULL,
        after_data_json nvarchar(max) NULL,
        correlation_id uniqueidentifier NULL,
        CONSTRAINT FK_audit_logs_actor FOREIGN KEY (actor_account_id) REFERENCES dbo.accounts(account_id),
        CONSTRAINT CK_audit_logs_before CHECK (before_data_json IS NULL OR ISJSON(before_data_json) = 1),
        CONSTRAINT CK_audit_logs_after CHECK (after_data_json IS NULL OR ISJSON(after_data_json) = 1)
    );
    CREATE INDEX IX_audit_target_time ON dbo.audit_logs(target_table,target_record_key,occurred_at);
    CREATE INDEX IX_audit_actor_time ON dbo.audit_logs(actor_account_id,occurred_at);

    -- Audit columns được thêm sau accounts để tránh FK vòng lúc CREATE TABLE.
    -- updated_at/updated_by phải được cập nhật bởi đường ghi nghiệp vụ tương lai.
    DECLARE @table sysname, @sql nvarchar(max);
    DECLARE audit_columns CURSOR LOCAL FAST_FORWARD FOR
        SELECT name FROM sys.tables WHERE schema_id = SCHEMA_ID('dbo') AND name <> 'audit_logs';
    OPEN audit_columns;
    FETCH NEXT FROM audit_columns INTO @table;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @sql = N'ALTER TABLE dbo.' + QUOTENAME(@table) + N' ADD
            created_at datetime2(3) NOT NULL CONSTRAINT ' + QUOTENAME('DF_' + @table + '_created') + N' DEFAULT (SYSUTCDATETIME()),
            updated_at datetime2(3) NULL, created_by bigint NULL, updated_by bigint NULL, row_version rowversion;
            ALTER TABLE dbo.' + QUOTENAME(@table) + N' ADD
            CONSTRAINT ' + QUOTENAME('FK_' + @table + '_creator') + N' FOREIGN KEY(created_by) REFERENCES dbo.accounts(account_id),
            CONSTRAINT ' + QUOTENAME('FK_' + @table + '_updater') + N' FOREIGN KEY(updated_by) REFERENCES dbo.accounts(account_id);';
        EXEC sys.sp_executesql @sql;
        FETCH NEXT FROM audit_columns INTO @table;
    END;
    CLOSE audit_columns;
    DEALLOCATE audit_columns;

    -- 4. Quyền DB và trigger chống hard-delete, kể cả bản chưa có FK tham chiếu.
    CREATE ROLE hrm_app_role AUTHORIZATION dbo;
    GRANT SELECT, INSERT, UPDATE ON SCHEMA::dbo TO hrm_app_role;
    DENY DELETE ON SCHEMA::dbo TO hrm_app_role;
    DENY UPDATE ON OBJECT::dbo.audit_logs TO hrm_app_role;
    -- Không tạo USER/login. Phase kết nối sau sẽ map tài khoản ứng dụng vào role này.
    -- Không dùng db_owner/sysadmin hoặc cấp quyền DISABLE TRIGGER cho tài khoản đó.
    DECLARE no_delete CURSOR LOCAL FAST_FORWARD FOR
        SELECT name FROM sys.tables WHERE schema_id = SCHEMA_ID('dbo');
    OPEN no_delete;
    FETCH NEXT FROM no_delete INTO @table;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @sql = N'CREATE TRIGGER dbo.' + QUOTENAME('tr_' + @table + '_no_delete')
            + N' ON dbo.' + QUOTENAME(@table) + N' INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N''Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.'', 1; END;';
        EXEC sys.sp_executesql @sql;
        FETCH NEXT FROM no_delete INTO @table;
    END;
    CLOSE no_delete;
    DEALLOCATE no_delete;
    EXEC(N'CREATE TRIGGER dbo.tr_audit_logs_append_only ON dbo.audit_logs AFTER UPDATE AS
        BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM inserted)
            THROW 51002, N''Nhật ký chỉ được thêm mới.'', 1; END;');

    -- Subtype đúng loại; header và chi tiết phải tạo trong cùng giao dịch.
    -- SQL Server không có deferred FK/constraint: tính đủ đúng một subtype khi commit
    -- vẫn phải kiểm tra qua service/procedure tương lai; trigger kiểm tra khi duyệt.
    EXEC(N'CREATE TRIGGER dbo.tr_leave_details_type ON dbo.leave_request_details AFTER INSERT, UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.employee_requests r ON r.request_id=i.request_id
            WHERE r.request_type <> ''NGHI_PHEP'')
            THROW 51003, N''Chi tiết phép sai loại đơn.'', 1; END;');
    EXEC(N'CREATE TRIGGER dbo.tr_resignation_details_type ON dbo.resignation_request_details AFTER INSERT, UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.employee_requests r ON r.request_id=i.request_id
            WHERE r.request_type <> ''NGHI_VIEC'')
            THROW 51004, N''Chi tiết nghỉ việc sai loại đơn.'', 1; END;');
    EXEC(N'CREATE TRIGGER dbo.tr_requests_processing ON dbo.employee_requests AFTER INSERT, UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.request_id=i.request_id
            WHERE d.status <> ''CHO_DUYET'' OR i.request_type <> d.request_type OR i.employee_id <> d.employee_id)
            THROW 51005, N''Không đổi chủ/loại đơn hoặc sửa đơn đã xử lý.'', 1;
        IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.accounts a ON a.account_id=i.processed_by
            WHERE a.employee_id=i.employee_id)
            THROW 51006, N''Không tự duyệt đơn.'', 1;
        IF EXISTS(SELECT 1 FROM inserted i WHERE i.status <> ''CHO_DUYET'' AND
            ((i.request_type=''NGHI_PHEP'' AND NOT EXISTS(SELECT 1 FROM dbo.leave_request_details x WHERE x.request_id=i.request_id))
            OR (i.request_type=''NGHI_VIEC'' AND NOT EXISTS(SELECT 1 FROM dbo.resignation_request_details x
                WHERE x.request_id=i.request_id AND (i.status<>''DA_DUYET'' OR x.approved_last_working_date IS NOT NULL)))))
            THROW 51007, N''Đơn xử lý thiếu chi tiết hoặc ngày nghỉ được chấp thuận.'', 1;
        END;');
    -- Không sửa chi tiết đơn đã duyệt/từ chối.
    DECLARE request_children CURSOR LOCAL FAST_FORWARD FOR
        SELECT name FROM sys.tables WHERE name IN ('leave_request_details','resignation_request_details');
    OPEN request_children;
    FETCH NEXT FROM request_children INTO @table;
    WHILE @@FETCH_STATUS=0
    BEGIN
        SET @sql=N'CREATE TRIGGER dbo.'+QUOTENAME('tr_'+@table+'_processed')+N' ON dbo.'+QUOTENAME(@table)+N' AFTER INSERT, UPDATE AS
            BEGIN SET NOCOUNT ON;
            IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.employee_requests r ON r.request_id=i.request_id WHERE r.status<>''CHO_DUYET'')
                THROW 51008, N''Không sửa chi tiết đơn đã xử lý.'', 1; END;';
        EXEC sys.sp_executesql @sql;
        FETCH NEXT FROM request_children INTO @table;
    END;
    CLOSE request_children; DEALLOCATE request_children;
    EXEC(N'CREATE TRIGGER dbo.tr_overtime_no_self_approval ON dbo.overtime_requests AFTER INSERT, UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.accounts a ON a.account_id=i.processed_by WHERE a.employee_id=i.employee_id)
            THROW 51009, N''Không tự duyệt tăng ca.'', 1;
        IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.overtime_id=i.overtime_id
            WHERE i.employee_id<>d.employee_id OR (d.status<>''CHO_DUYET'' AND
            (i.status<>d.status OR i.requested_start<>d.requested_start OR i.requested_end<>d.requested_end
            OR i.reason<>d.reason OR i.processed_by<>d.processed_by OR i.processed_at<>d.processed_at
            OR ISNULL(i.rejection_reason,N'''')<>ISNULL(d.rejection_reason,N''''))))
            THROW 51010, N''Không đổi chủ hoặc kết quả duyệt tăng ca đã xử lý.'', 1;
        END;');

    -- Hợp đồng/điều kiện cũ không ghi đè; đóng hiệu lực được phép, lý do lưu audit.
    EXEC(N'CREATE TRIGGER dbo.tr_contracts_keep_history ON dbo.contracts AFTER UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.contract_id=i.contract_id
            WHERE i.employee_id<>d.employee_id OR i.contract_type_id<>d.contract_type_id
            OR i.contract_number<>d.contract_number OR i.signed_date<>d.signed_date OR i.start_date<>d.start_date
            OR ISNULL(i.end_date,''99991231'')<>ISNULL(d.end_date,''99991231''))
            THROW 51011, N''Giữ nội dung hợp đồng đã ký; tạo hợp đồng/phụ lục mới.'', 1; END;');
    EXEC(N'CREATE TRIGGER dbo.tr_salary_terms_keep_history ON dbo.contract_salary_terms AFTER UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.salary_term_id=i.salary_term_id
            WHERE i.contract_id<>d.contract_id OR i.effective_from<>d.effective_from OR i.base_salary<>d.base_salary
            OR i.pay_basis<>d.pay_basis OR ISNULL(i.document_reference,N'''')<>ISNULL(d.document_reference,N'''')
            OR (d.effective_to IS NOT NULL AND ISNULL(i.effective_to,''99991231'')<>d.effective_to))
            THROW 51012, N''Không ghi đè điều kiện lương cũ; tạo điều kiện mới.'', 1; END;');

    -- Chặn sửa các bảng con nếu batch cũ HOẶC mới đã chốt (ngăn chuyển dòng).
    EXEC(N'CREATE TRIGGER dbo.tr_payroll_details_locked ON dbo.payroll_details AFTER INSERT, UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM (SELECT batch_id FROM inserted UNION SELECT batch_id FROM deleted) x
            JOIN dbo.payroll_batches b WITH (UPDLOCK,HOLDLOCK) ON b.batch_id=x.batch_id WHERE b.status=''DA_CHOT'')
            THROW 51013, N''Không sửa kết quả nhân viên của bảng lương đã chốt.'', 1; END;');
    DECLARE locked_children CURSOR LOCAL FAST_FORWARD FOR
        SELECT name FROM sys.tables WHERE name IN ('payroll_lines','payroll_snapshots');
    OPEN locked_children; FETCH NEXT FROM locked_children INTO @table;
    WHILE @@FETCH_STATUS=0
    BEGIN
        SET @sql=N'CREATE TRIGGER dbo.'+QUOTENAME('tr_'+@table+'_locked')+N' ON dbo.'+QUOTENAME(@table)+N' AFTER INSERT, UPDATE AS
            BEGIN SET NOCOUNT ON;
            IF EXISTS(SELECT 1 FROM (SELECT payroll_detail_id FROM inserted UNION SELECT payroll_detail_id FROM deleted) x
                JOIN dbo.payroll_details d ON d.payroll_detail_id=x.payroll_detail_id
                JOIN dbo.payroll_batches b WITH (UPDLOCK,HOLDLOCK) ON b.batch_id=d.batch_id WHERE b.status=''DA_CHOT'')
                THROW 51014, N''Không sửa dòng tiền/căn cứ của bảng lương đã chốt.'', 1; END;';
        EXEC sys.sp_executesql @sql;
        FETCH NEXT FROM locked_children INTO @table;
    END;
    CLOSE locked_children; DEALLOCATE locked_children;
    EXEC(N'CREATE TRIGGER dbo.tr_batches_lock_validation ON dbo.payroll_batches AFTER INSERT, UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.batch_id=i.batch_id
            WHERE d.batch_id IS NULL AND i.status<>''NHAP'')
            THROW 51015, N''Tạo bảng lương nháp trước khi chốt.'', 1;
        -- Bản chốt chỉ được công bố một lần; không đổi tiền, kỳ, version, căn cứ.
        IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.batch_id=i.batch_id WHERE d.status=''DA_CHOT''
            AND (i.status<>d.status OR i.period_id<>d.period_id OR i.version_no<>d.version_no
            OR ISNULL(i.previous_batch_id,0)<>ISNULL(d.previous_batch_id,0)
            OR ISNULL(i.adjustment_reason,N'''')<>ISNULL(d.adjustment_reason,N'''')
            OR i.locked_by<>d.locked_by OR i.locked_at<>d.locked_at
            OR d.published_at IS NOT NULL))
            THROW 51016, N''Bảng lương đã chốt bất biến; tạo phiên bản điều chỉnh.'', 1;
        IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.payroll_batches p ON p.batch_id=i.previous_batch_id
            WHERE p.status<>''DA_CHOT'' OR p.version_no>=i.version_no)
            THROW 51017, N''Phiên bản trước phải đã chốt và có số phiên bản nhỏ hơn.'', 1;
        IF EXISTS(SELECT 1 FROM inserted i WHERE i.status=''DA_CHOT'' AND
            (NOT EXISTS(SELECT 1 FROM dbo.payroll_details d WHERE d.batch_id=i.batch_id)
            OR EXISTS(SELECT 1 FROM dbo.payroll_details d WHERE d.batch_id=i.batch_id AND
                (NOT EXISTS(SELECT 1 FROM dbo.payroll_lines l WHERE l.payroll_detail_id=d.payroll_detail_id)
                OR NOT EXISTS(SELECT 1 FROM dbo.payroll_snapshots s WHERE s.payroll_detail_id=d.payroll_detail_id)))))
            THROW 51018, N''Không chốt bảng lương rỗng hoặc thiếu dòng tiền/snapshot.'', 1;
        END;');
    EXEC(N'CREATE TRIGGER dbo.tr_payslips_locked ON dbo.payslips AFTER INSERT, UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.payroll_details d ON d.payroll_detail_id=i.payroll_detail_id
            JOIN dbo.payroll_batches b WITH (UPDLOCK,HOLDLOCK) ON b.batch_id=d.batch_id WHERE b.status<>''DA_CHOT'')
            THROW 51019, N''Chỉ phát hành phiếu từ bảng lương đã chốt.'', 1;
        IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.payslip_id=i.payslip_id
            WHERE i.payroll_detail_id<>d.payroll_detail_id OR i.payslip_number<>d.payslip_number OR i.issued_at<>d.issued_at)
            THROW 51020, N''Không đổi kết quả gắn với phiếu lương đã phát hành.'', 1; END;');
    EXEC(N'CREATE TRIGGER dbo.tr_payroll_periods_locked ON dbo.payroll_periods AFTER UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.period_id=i.period_id
            JOIN dbo.payroll_batches b WITH (UPDLOCK,HOLDLOCK) ON b.period_id=i.period_id
            WHERE b.status=''DA_CHOT'' AND (i.period_start<>d.period_start OR i.period_end<>d.period_end OR i.period_code<>d.period_code))
            THROW 51021, N''Không đổi kỳ đã có bảng lương chốt.'', 1; END;');

    -- 5. Seed tối thiểu. Tài khoản mẫu KHÓA, dùng bcrypt hash thử, không có mật khẩu thuần.
    INSERT dbo.roles(role_code,role_name) VALUES ('ADMIN',N'Quản trị'),('HR',N'Nhân sự'),('EMPLOYEE',N'Nhân viên');
    INSERT dbo.permissions(permission_code,permission_name) VALUES
        ('ACCOUNT_MANAGE',N'Quản lý tài khoản'),('EMPLOYEE_MANAGE',N'Quản lý nhân viên'),
        ('SELF_READ',N'Xem dữ liệu cá nhân'),('REQUEST_APPROVE',N'Duyệt đơn'),
        ('PAYROLL_MANAGE',N'Quản lý bảng lương'),('PAYSLIP_SELF_READ',N'Xem phiếu lương cá nhân');
    INSERT dbo.role_permissions(role_id,permission_id)
        SELECT r.role_id,p.permission_id FROM dbo.roles r CROSS JOIN dbo.permissions p
        WHERE r.role_code='ADMIN'
            OR (r.role_code='HR' AND p.permission_code<>'ACCOUNT_MANAGE')
            OR (r.role_code='EMPLOYEE' AND p.permission_code IN ('SELF_READ','PAYSLIP_SELF_READ'));
    -- Quyền mẫu chỉ phục vụ kiểm thử DB; scope sở hữu và ma trận đầy đủ sẽ ở phase bảo mật.
    INSERT dbo.departments(department_code,department_name) VALUES ('HCNS',N'Hành chính nhân sự'),('DT',N'Đào tạo');
    INSERT dbo.positions(position_code,position_name) VALUES ('QT',N'Quản trị viên'),('CVNS',N'Chuyên viên nhân sự'),('GV',N'Giáo viên');
    INSERT dbo.qualification_levels(level_code,level_name) VALUES ('DH',N'Đại học');
    INSERT dbo.contract_types(type_code,type_name) VALUES ('XAC_DINH_THOI_HAN',N'Hợp đồng xác định thời hạn');
    INSERT dbo.work_shifts(shift_code,shift_name,start_time,end_time,break_minutes)
        VALUES ('NGAY',N'Ca ngày','08:00','17:00',60);
    INSERT dbo.leave_types(type_code,type_name,is_paid) VALUES ('PHEP_NAM',N'Phép năm',1),('KHONG_LUONG',N'Nghỉ không lương',0);
    INSERT dbo.allowance_types(type_code,type_name) VALUES ('AN_TRUA',N'Ăn trưa');
    INSERT dbo.salary_components(component_code,component_name,direction)
        VALUES ('LUONG_CONG',N'Lương theo công','ADD'),('TANG_CA',N'Tiền tăng ca','ADD'),
        ('PHU_CAP',N'Phụ cấp','ADD'),('THUONG',N'Thưởng','ADD'),('KHAU_TRU',N'Khấu trừ','DEDUCT');
    INSERT dbo.employees(employee_code,full_name,hire_date,employment_status,termination_date) VALUES
        ('EMP033',N'Quản trị mẫu','20260101','DANG_LAM',NULL),
        ('EMP034',N'Nhân sự mẫu','20260101','DANG_LAM',NULL),
        ('EMP030',N'Nhân viên A','20250101','DANG_LAM',NULL),
        ('EMP031',N'Nhân viên B','20260101','DANG_LAM',NULL),
        ('EMP032',N'Nhân viên đã nghỉ','20250101','DA_NGHI','20260831');
    DECLARE @bcrypt varchar(255)='$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';
    INSERT dbo.accounts(employee_id,role_id,username,password_hash,is_active)
        SELECT e.employee_id,r.role_id,
            CASE e.employee_code WHEN 'EMP033' THEN N'admin.sample' WHEN 'EMP034' THEN N'hr.sample'
                WHEN 'EMP030' THEN N'employee.a' WHEN 'EMP031' THEN N'employee.b' ELSE N'employee.old' END,@bcrypt,0
        FROM dbo.employees e JOIN dbo.roles r ON r.role_code=
            CASE e.employee_code WHEN 'EMP033' THEN 'ADMIN' WHEN 'EMP034' THEN 'HR' ELSE 'EMPLOYEE' END;
    DECLARE @admin bigint=(SELECT account_id FROM dbo.accounts WHERE username=N'admin.sample'),
        @hr bigint=(SELECT account_id FROM dbo.accounts WHERE username=N'hr.sample'),
        @a bigint=(SELECT employee_id FROM dbo.employees WHERE employee_code='EMP030'),
        @b bigint=(SELECT employee_id FROM dbo.employees WHERE employee_code='EMP031'),
        @old bigint=(SELECT employee_id FROM dbo.employees WHERE employee_code='EMP032'),
        @dept bigint=(SELECT department_id FROM dbo.departments WHERE department_code='DT'),
        @pos bigint=(SELECT position_id FROM dbo.positions WHERE position_code='GV');
    INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from,effective_to)
        VALUES (@a,@dept,@pos,'20260101',NULL),(@b,@dept,@pos,'20260101',NULL),(@old,@dept,@pos,'20250101','20260901');
    INSERT dbo.employee_qualifications(employee_id,qualification_level_id,major,institution,graduation_year,is_primary)
        SELECT @a,qualification_level_id,N'Sư phạm',N'Cơ sở đào tạo mẫu',2020,1 FROM dbo.qualification_levels WHERE level_code='DH';
    DECLARE @ctype bigint=(SELECT contract_type_id FROM dbo.contract_types WHERE type_code='XAC_DINH_THOI_HAN');
    INSERT dbo.contracts(employee_id,contract_type_id,contract_number,signed_date,start_date,end_date,status)
        VALUES (@a,@ctype,'HD-A-OLD','20250101','20250101','20251231','DA_KET_THUC'),
            (@a,@ctype,'HD-A-2026','20251220','20260101','20261231','DA_KY'),
            (@old,@ctype,'HD-CU','20250101','20250101','20260831','DA_KET_THUC');
    INSERT dbo.contract_salary_terms(contract_id,effective_from,effective_to,base_salary)
        SELECT contract_id,start_date,DATEADD(DAY,1,end_date),10000000.0000 FROM dbo.contracts;
    DECLARE @shift bigint=(SELECT shift_id FROM dbo.work_shifts WHERE shift_code='NGAY');
    INSERT dbo.work_schedules(employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes)
        VALUES (@a,@shift,'20260901','2026-09-01T08:00:00','2026-09-01T17:00:00',60),
            (@b,@shift,'20260901','2026-09-01T08:00:00','2026-09-01T17:00:00',60);
    INSERT dbo.attendance_records(employee_id,schedule_id,work_date,actual_start,actual_end,actual_break_minutes,
        attendance_result,verification_status,confirmed_by,confirmed_at)
        SELECT @a,schedule_id,work_date,planned_start,planned_end,60,'DI_LAM','DA_XAC_NHAN',@hr,'2026-09-02T01:00:00'
        FROM dbo.work_schedules WHERE employee_id=@a;
    -- B có lịch nhưng chưa có công: minh họa BR-04.
    DECLARE @leave_type bigint=(SELECT leave_type_id FROM dbo.leave_types WHERE type_code='PHEP_NAM');
    INSERT dbo.leave_entitlements(employee_id,leave_type_id,period_start,period_end,entitled_hours)
        VALUES (@a,@leave_type,'20260101','20270101',96.0000);
    INSERT dbo.employee_requests(employee_id,request_code,request_type,reason,submitted_at)
        VALUES (@a,'DON-A-LEAVE','NGHI_PHEP',N'Đơn phép mẫu','2026-09-01T01:00:00'),
            (@b,'DON-B-RESIGN','NGHI_VIEC',N'Đơn nghỉ việc mẫu','2026-09-01T01:00:00');
    INSERT dbo.leave_request_details(request_id,leave_type_id,leave_start,leave_end)
        SELECT request_id,@leave_type,'2026-09-03T08:00:00','2026-09-03T17:00:00' FROM dbo.employee_requests WHERE request_code='DON-A-LEAVE';
    INSERT dbo.resignation_request_details(request_id,requested_last_working_date)
        SELECT request_id,'20261130' FROM dbo.employee_requests WHERE request_code='DON-B-RESIGN';
    UPDATE dbo.employee_requests SET status='DA_DUYET',processed_by=@hr,processed_at='2026-09-02T01:00:00'
        WHERE request_code='DON-A-LEAVE';
    INSERT dbo.overtime_requests(employee_id,request_code,requested_start,requested_end,reason,submitted_at)
        VALUES (@a,'OT-A-APPROVED','2026-09-01T18:00:00','2026-09-01T20:00:00',N'Tăng ca mẫu','2026-08-31T01:00:00'),
            (@a,'OT-A-PENDING','2026-09-02T18:00:00','2026-09-02T20:00:00',N'Chờ duyệt','2026-08-31T01:00:00'),
            (@b,'OT-B-REJECTED','2026-09-01T18:00:00','2026-09-01T20:00:00',N'Từ chối mẫu','2026-08-31T01:00:00');
    UPDATE dbo.overtime_requests SET status='DA_DUYET',processed_by=@hr,processed_at='2026-09-01T01:00:00',
        actual_start=requested_start,actual_end=requested_end,confirmed_by=@hr,confirmed_at='2026-09-02T01:00:00'
        WHERE request_code='OT-A-APPROVED';
    UPDATE dbo.overtime_requests SET status='TU_CHOI',processed_by=@hr,processed_at='2026-09-01T01:00:00',
        rejection_reason=N'Không có nhu cầu tăng ca' WHERE request_code='OT-B-REJECTED';
    INSERT dbo.employee_allowances(employee_id,allowance_type_id,effective_from,amount)
        SELECT @a,allowance_type_id,'20260101',500000.0000 FROM dbo.allowance_types WHERE type_code='AN_TRUA';
    INSERT dbo.employee_decisions(employee_id,decision_number,decision_type,content,issued_date,issued_by,
        approval_status,approved_by,approved_at,payroll_effect,amount,apply_date)
        VALUES (@a,'QD-THUONG-A','KHEN_THUONG',N'Khoản thưởng mẫu','20260901',@admin,'DA_DUYET',@hr,'2026-09-02T01:00:00','ADD',200000.0000,'20260901'),
            (@b,'QD-KL-B','KY_LUAT',N'Nhắc nhở, không khấu trừ','20260901',@admin,'DA_DUYET',@hr,'2026-09-02T01:00:00','NONE',NULL,NULL);
    INSERT dbo.payroll_periods(period_code,period_start,period_end) VALUES ('2026-09','20260901','20261001');
    DECLARE @period bigint=CONVERT(bigint,SCOPE_IDENTITY());
    INSERT dbo.payroll_batches(period_id) VALUES (@period);
    DECLARE @batch bigint=CONVERT(bigint,SCOPE_IDENTITY());
    INSERT dbo.payroll_details(batch_id,employee_id) VALUES (@batch,@a),(@batch,@b);
    INSERT dbo.payroll_lines(payroll_detail_id,component_id,line_no,component_code_snapshot,component_name_snapshot,direction_snapshot,amount,explanation)
        SELECT d.payroll_detail_id,c.component_id,1,c.component_code,c.component_name,c.direction,100000.0000,
            N'Số giả định kiểm thử quan hệ; chưa tính lương chính thức'
        FROM dbo.payroll_details d CROSS JOIN dbo.salary_components c WHERE c.component_code='LUONG_CONG';
    INSERT dbo.payroll_snapshots(payroll_detail_id,policy_version,input_snapshot_json)
        SELECT payroll_detail_id,'DEMO_ONLY',N'{"demo":true,"notice":"Draft fixture only; not a complete payroll input"}' FROM dbo.payroll_details;
    -- Không phát hành phiếu, không chốt snapshot mẫu thiếu căn cứ nghiệp vụ.
    INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,after_data_json)
        VALUES (NULL,'BOOTSTRAP','database','HRMCenter',N'Khởi tạo schema và dữ liệu giả để kiểm thử',N'{"seed":true}');

    COMMIT TRANSACTION;
    PRINT N'Khởi tạo HRMCenter thành công. Tài khoản mẫu bị khóa; bảng lương mẫu là NHAP.';
    SELECT COUNT(*) AS table_count FROM sys.tables WHERE is_ms_shipped=0;
    SELECT employee_code,full_name,employment_status FROM dbo.employees;
    SELECT request_code,status FROM dbo.overtime_requests;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

/* Kiểm tra thực tế trên database tạm riêng (2026-10-02):
   - CREATE đủ 33 bảng, seed thành công; không lỗi thứ tự FK.
   - UNIQUE mã nhân viên, CHECK trạng thái và FK ghép công/lịch chặn dữ liệu sai.
   - Chặn hard-delete, ghi đè điều kiện lương cũ, tự duyệt và phiếu cho bản nháp.
   - Sau chốt, chặn sửa dòng tiền/snapshot/header/kỳ; phiếu cho bản chốt được tạo.
   Đây không phải kiểm thử công thức lương hoặc các quy tắc giao dịch chưa triển khai.

   Giới hạn có chủ đích:
   - Không hard-delete qua DML; sysadmin vẫn có thể vô hiệu trigger/TRUNCATE/DROP.
   - NO ACTION là mặc định của mọi FK; không ON DELETE CASCADE.
   - Role DB dùng chung không phân biệt EMPLOYEE A/B: scope phải ở backend sau này.
   - Kiểm tra quyền duyệt, đầy đủ subtype khi commit, overlap nhiều dòng, thời điểm
     nghỉ việc, hạn mức phép, công/tăng ca trùng và schema snapshot phải được triển
     khai qua giao dịch/service/procedure ở phase tương ứng, không chỉ CHECK.
   - Chốt lương trigger bảo vệ trạng thái/căn cứ tối thiểu, không xác minh công thức
     hoặc độ đầy đủ nội dung JSON. Không chốt DEMO_ONLY trong dữ liệu thật.
   - Nếu sửa công/hợp đồng/duyệt/chốt, đường ghi phải tạo audit cùng giao dịch.
   - Mọi connection ghi cần các SET options như đầu script để dùng filtered index.
   - Không UPDATE payroll snapshot/dòng tiền đã chốt; điều chỉnh tạo version mới.
*/
USE [hrm_db];
GO

SET XACT_ABORT ON;
GO

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.hr_department_scopes', N'U') IS NOT NULL
    BEGIN;
        THROW 51001,
            'hr_department_scopes da ton tai. Dung de kiem tra.',
            1;
    END;

    CREATE TABLE dbo.hr_department_scopes (
        scope_id BIGINT IDENTITY(1,1) NOT NULL,
        hr_account_id BIGINT NOT NULL,
        department_id BIGINT NOT NULL,
        effective_from DATETIME2(3) NOT NULL,
        effective_to DATETIME2(3) NULL,
        revoked_at DATETIME2(3) NULL,
        revoked_by BIGINT NULL,
        created_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_hr_scopes_created_at
            DEFAULT (SYSUTCDATETIME()),
        created_by BIGINT NOT NULL,
        updated_at DATETIME2(3) NULL,
        updated_by BIGINT NULL,

        -- Đã sửa khoảng cách ở PRIMARY KEY
        CONSTRAINT PK_hr_department_scopes PRIMARY KEY (scope_id),
        CONSTRAINT FK_hr_scopes_hr_account
            FOREIGN KEY (hr_account_id)
            REFERENCES dbo.accounts(account_id),
        CONSTRAINT FK_hr_scopes_department
            FOREIGN KEY (department_id)
            REFERENCES dbo.departments(department_id),
        CONSTRAINT FK_hr_scopes_created_by
            FOREIGN KEY (created_by)
            REFERENCES dbo.accounts(account_id),
        CONSTRAINT FK_hr_scopes_updated_by
            FOREIGN KEY (updated_by)
            REFERENCES dbo.accounts(account_id),
        CONSTRAINT FK_hr_scopes_revoked_by
            FOREIGN KEY (revoked_by)
            REFERENCES dbo.accounts(account_id),
        CONSTRAINT CK_hr_scopes_effective_dates
            CHECK (effective_to IS NULL OR effective_to > effective_from),
        CONSTRAINT CK_hr_scopes_revocation
            CHECK ((revoked_at IS NULL AND revoked_by IS NULL)
                OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL)),
        CONSTRAINT CK_hr_scopes_update_metadata
            CHECK ((updated_at IS NULL AND updated_by IS NULL)
                OR (updated_at IS NOT NULL AND updated_by IS NOT NULL))
    );

    CREATE UNIQUE INDEX UX_hr_scopes_open
    ON dbo.hr_department_scopes (hr_account_id, department_id)
    WHERE effective_to IS NULL AND revoked_at IS NULL;

    CREATE INDEX IX_hr_scopes_account_dates
    ON dbo.hr_department_scopes (hr_account_id, effective_from, effective_to)
    INCLUDE (department_id, revoked_at);

    CREATE INDEX IX_hr_scopes_pair_dates
    ON dbo.hr_department_scopes (hr_account_id, department_id, effective_from, effective_to)
    INCLUDE (revoked_at);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO