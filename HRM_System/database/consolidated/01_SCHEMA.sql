:ON ERROR EXIT
:setvar DatabaseName "hrm_center_full"
USE master;
GO
IF DB_ID(N'$(DatabaseName)') IS NOT NULL THROW 51000,N'Database already exists. Choose a NEW name. Nothing overwritten.',1;
CREATE DATABASE [$(DatabaseName)];
GO
USE [$(DatabaseName)];
GO
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON; SET NUMERIC_ROUNDABORT OFF; SET XACT_ABORT ON;
GO
-- accounts: Viet
CREATE TABLE [dbo].[accounts](
	[account_id] [bigint] IDENTITY(1,1) NOT NULL,
	[employee_id] [bigint] NOT NULL,
	[role_id] [bigint] NOT NULL,
	[username] [nvarchar](100) NOT NULL,
	[password_hash] [varchar](255) NOT NULL,
	[is_active] [bit] NOT NULL,
	[token_version] [int] NOT NULL,
	[last_login_at] [datetime2](3) NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_accounts] PRIMARY KEY CLUSTERED 
(
	[account_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- allowance_types: Duong
CREATE TABLE [dbo].[allowance_types](
	[allowance_type_id] [bigint] IDENTITY(1,1) NOT NULL,
	[type_code] [varchar](50) NOT NULL,
	[type_name] [nvarchar](200) NOT NULL,
	[description] [nvarchar](1000) NULL,
	[is_active] [bit] NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
	[is_taxable] [bit] NOT NULL,
	[counts_for_social_insurance] [bit] NOT NULL,
 CONSTRAINT [PK_allowance_types] PRIMARY KEY CLUSTERED 
(
	[allowance_type_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- attendance_records: Nhi
CREATE TABLE [dbo].[attendance_records](
	[attendance_id] [bigint] IDENTITY(1,1) NOT NULL,
	[employee_id] [bigint] NOT NULL,
	[schedule_id] [bigint] NULL,
	[work_date] [date] NOT NULL,
	[actual_start] [datetime2](3) NULL,
	[actual_end] [datetime2](3) NULL,
	[actual_break_minutes] [int] NOT NULL,
	[attendance_result] [varchar](20) NOT NULL,
	[verification_status] [varchar](20) NOT NULL,
	[confirmed_by] [bigint] NULL,
	[confirmed_at] [datetime2](3) NULL,
	[correction_reason] [nvarchar](1000) NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
	[invalidated_at] [datetime2](3) NULL,
	[invalidated_by] [bigint] NULL,
	[invalidation_reason] [nvarchar](1000) NULL,
	[check_in_reviewed_by] [bigint] NULL,
	[check_in_reviewed_at] [datetime2](3) NULL,
	[check_in_reviewed_value] [datetime2](7) NULL,
	[check_out_reviewed_by] [bigint] NULL,
	[check_out_reviewed_at] [datetime2](3) NULL,
	[check_out_reviewed_value] [datetime2](7) NULL,
 CONSTRAINT [PK_attendance_records] PRIMARY KEY CLUSTERED 
(
	[attendance_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- audit_logs: XuanAnh
CREATE TABLE [dbo].[audit_logs](
	[audit_id] [bigint] IDENTITY(1,1) NOT NULL,
	[actor_account_id] [bigint] NULL,
	[occurred_at] [datetime2](3) NOT NULL,
	[action] [varchar](100) NOT NULL,
	[target_table] [varchar](128) NOT NULL,
	[target_record_key] [nvarchar](100) NOT NULL,
	[reason] [nvarchar](1000) NULL,
	[before_data_json] [nvarchar](max) NULL,
	[after_data_json] [nvarchar](max) NULL,
	[correlation_id] [uniqueidentifier] NULL,
 CONSTRAINT [PK_audit_logs] PRIMARY KEY CLUSTERED 
(
	[audit_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]

GO
-- contract_salary_terms: Duong
CREATE TABLE [dbo].[contract_salary_terms](
	[salary_term_id] [bigint] IDENTITY(1,1) NOT NULL,
	[contract_id] [bigint] NOT NULL,
	[effective_from] [date] NOT NULL,
	[effective_to] [date] NULL,
	[base_salary] [decimal](19, 4) NOT NULL,
	[pay_basis] [varchar](10) NOT NULL,
	[document_reference] [nvarchar](500) NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
	[official_reference_salary] [decimal](19, 4) NULL,
	[official_reference_pay_basis] [varchar](10) NULL,
 CONSTRAINT [PK_contract_salary_terms] PRIMARY KEY CLUSTERED 
(
	[salary_term_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- contract_types: Duong
CREATE TABLE [dbo].[contract_types](
	[contract_type_id] [bigint] IDENTITY(1,1) NOT NULL,
	[type_code] [varchar](50) NOT NULL,
	[type_name] [nvarchar](200) NOT NULL,
	[is_active] [bit] NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
	[term_kind] [varchar](20) NOT NULL,
	[max_months] [int] NULL,
 CONSTRAINT [PK_contract_types] PRIMARY KEY CLUSTERED 
(
	[contract_type_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- contracts: Duong
CREATE TABLE [dbo].[contracts](
	[contract_id] [bigint] IDENTITY(1,1) NOT NULL,
	[employee_id] [bigint] NOT NULL,
	[contract_type_id] [bigint] NOT NULL,
	[contract_number] [varchar](50) NOT NULL,
	[signed_date] [date] NOT NULL,
	[start_date] [date] NOT NULL,
	[end_date] [date] NULL,
	[status] [varchar](20) NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
	[probation_group] [varchar](30) NULL,
	[termination_reason_type] [varchar](30) NULL,
 CONSTRAINT [PK_contracts] PRIMARY KEY CLUSTERED 
(
	[contract_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- departments: XuanAnh
CREATE TABLE [dbo].[departments](
	[department_id] [bigint] IDENTITY(1,1) NOT NULL,
	[department_code] [varchar](50) NOT NULL,
	[department_name] [nvarchar](200) NOT NULL,
	[description] [nvarchar](1000) NULL,
	[is_active] [bit] NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_departments] PRIMARY KEY CLUSTERED 
(
	[department_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- employee_allowances: Duong
CREATE TABLE [dbo].[employee_allowances](
	[employee_allowance_id] [bigint] IDENTITY(1,1) NOT NULL,
	[employee_id] [bigint] NOT NULL,
	[allowance_type_id] [bigint] NOT NULL,
	[effective_from] [date] NOT NULL,
	[effective_to] [date] NULL,
	[calculation_basis] [varchar](20) NOT NULL,
	[amount] [decimal](19, 4) NOT NULL,
	[condition_note] [nvarchar](1000) NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
	[amount_type] [varchar](24) NOT NULL,
 CONSTRAINT [PK_employee_allowances] PRIMARY KEY CLUSTERED 
(
	[employee_allowance_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- employee_assignments: XuanAnh
CREATE TABLE [dbo].[employee_assignments](
	[assignment_id] [bigint] IDENTITY(1,1) NOT NULL,
	[employee_id] [bigint] NOT NULL,
	[department_id] [bigint] NOT NULL,
	[position_id] [bigint] NOT NULL,
	[effective_from] [date] NOT NULL,
	[effective_to] [date] NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_employee_assignments] PRIMARY KEY CLUSTERED 
(
	[assignment_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- employee_decisions: Duong
CREATE TABLE [dbo].[employee_decisions](
	[decision_id] [bigint] IDENTITY(1,1) NOT NULL,
	[employee_id] [bigint] NOT NULL,
	[decision_number] [varchar](50) NOT NULL,
	[decision_type] [varchar](20) NOT NULL,
	[content] [nvarchar](2000) NOT NULL,
	[issued_date] [date] NOT NULL,
	[issued_by] [bigint] NOT NULL,
	[approved_by] [bigint] NULL,
	[approved_at] [datetime2](3) NULL,
	[approval_status] [varchar](20) NOT NULL,
	[payroll_effect] [varchar](10) NOT NULL,
	[amount] [decimal](19, 4) NULL,
	[apply_date] [date] NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
	[discipline_form] [varchar](30) NULL,
	[violation_date] [date] NULL,
	[salary_increase_delay_months] [int] NULL,
 CONSTRAINT [PK_employee_decisions] PRIMARY KEY CLUSTERED 
(
	[decision_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- employee_qualifications: XuanAnh
CREATE TABLE [dbo].[employee_qualifications](
	[employee_qualification_id] [bigint] IDENTITY(1,1) NOT NULL,
	[employee_id] [bigint] NOT NULL,
	[qualification_level_id] [bigint] NOT NULL,
	[major] [nvarchar](200) NULL,
	[institution] [nvarchar](300) NULL,
	[graduation_year] [int] NULL,
	[certificate_number] [varchar](100) NULL,
	[is_primary] [bit] NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
	[is_deleted] [bit] NOT NULL,
	[deleted_at] [datetime2](3) NULL,
	[deleted_by] [bigint] NULL,
	[issued_date] [date] NULL,
	[expiry_date] [date] NULL,
 CONSTRAINT [PK_employee_qualifications] PRIMARY KEY CLUSTERED 
(
	[employee_qualification_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- employee_request_withdrawals: XuanAnh
CREATE TABLE [dbo].[employee_request_withdrawals](
	[request_id] [bigint] NOT NULL,
	[withdrawn_by] [bigint] NOT NULL,
	[withdrawn_at] [datetime2](3) NOT NULL,
	[reason] [nvarchar](1000) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[request_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- employee_requests: XuanAnh
CREATE TABLE [dbo].[employee_requests](
	[request_id] [bigint] IDENTITY(1,1) NOT NULL,
	[employee_id] [bigint] NOT NULL,
	[request_code] [varchar](50) NOT NULL,
	[request_type] [varchar](20) NOT NULL,
	[reason] [nvarchar](1000) NOT NULL,
	[status] [varchar](20) NOT NULL,
	[submitted_at] [datetime2](3) NOT NULL,
	[processed_by] [bigint] NULL,
	[processed_at] [datetime2](3) NULL,
	[rejection_reason] [nvarchar](1000) NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_employee_requests] PRIMARY KEY CLUSTERED 
(
	[request_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- employees: XuanAnh
CREATE TABLE [dbo].[employees](
	[employee_id] [bigint] IDENTITY(1,1) NOT NULL,
	[employee_code] [varchar](50) NOT NULL,
	[full_name] [nvarchar](200) NOT NULL,
	[birth_date] [date] NULL,
	[email] [nvarchar](254) NULL,
	[phone] [varchar](30) NULL,
	[address] [nvarchar](500) NULL,
	[identity_number] [varchar](50) NULL,
	[hire_date] [date] NOT NULL,
	[employment_status] [varchar](20) NOT NULL,
	[termination_date] [date] NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
	[gender] [varchar](20) NULL,
 CONSTRAINT [PK_employees] PRIMARY KEY CLUSTERED 
(
	[employee_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- g2_leave_allocations: XuanAnh
CREATE TABLE [dbo].[g2_leave_allocations](
	[allocation_id] [bigint] IDENTITY(1,1) NOT NULL,
	[request_id] [bigint] NOT NULL,
	[schedule_id] [bigint] NOT NULL,
	[entitlement_id] [bigint] NULL,
	[charged_hours] [decimal](9, 4) NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[allocation_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- g2_leave_policies: XuanAnh
CREATE TABLE [dbo].[g2_leave_policies](
	[leave_type_id] [bigint] NOT NULL,
	[consumes_entitlement] [bit] NOT NULL,
	[updated_at] [datetime2](3) NOT NULL,
	[updated_by] [bigint] NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[leave_type_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- g2_resignation_executions: XuanAnh
CREATE TABLE [dbo].[g2_resignation_executions](
	[request_id] [bigint] NOT NULL,
	[executed_at] [datetime2](3) NOT NULL,
	[last_working_date] [date] NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[request_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- hr_department_scopes: XuanAnh
CREATE TABLE [dbo].[hr_department_scopes](
	[scope_id] [bigint] IDENTITY(1,1) NOT NULL,
	[hr_account_id] [bigint] NOT NULL,
	[department_id] [bigint] NOT NULL,
	[effective_from] [datetime2](3) NOT NULL,
	[effective_to] [datetime2](3) NULL,
	[revoked_at] [datetime2](3) NULL,
	[revoked_by] [bigint] NULL,
	[created_at] [datetime2](3) NOT NULL,
	[created_by] [bigint] NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[updated_by] [bigint] NULL,
 CONSTRAINT [PK_hr_department_scopes] PRIMARY KEY CLUSTERED 
(
	[scope_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- leave_entitlements: XuanAnh
CREATE TABLE [dbo].[leave_entitlements](
	[entitlement_id] [bigint] IDENTITY(1,1) NOT NULL,
	[employee_id] [bigint] NOT NULL,
	[leave_type_id] [bigint] NOT NULL,
	[period_start] [date] NOT NULL,
	[period_end] [date] NOT NULL,
	[entitled_hours] [decimal](9, 4) NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_leave_entitlements] PRIMARY KEY CLUSTERED 
(
	[entitlement_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- leave_request_details: XuanAnh
CREATE TABLE [dbo].[leave_request_details](
	[request_id] [bigint] NOT NULL,
	[leave_type_id] [bigint] NOT NULL,
	[leave_start] [datetime2](3) NOT NULL,
	[leave_end] [datetime2](3) NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_leave_request_details] PRIMARY KEY CLUSTERED 
(
	[request_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- leave_types: XuanAnh
CREATE TABLE [dbo].[leave_types](
	[leave_type_id] [bigint] IDENTITY(1,1) NOT NULL,
	[type_code] [varchar](50) NOT NULL,
	[type_name] [nvarchar](200) NOT NULL,
	[is_paid] [bit] NOT NULL,
	[is_active] [bit] NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_leave_types] PRIMARY KEY CLUSTERED 
(
	[leave_type_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- overtime_breaks: Nhi
CREATE TABLE [dbo].[overtime_breaks](
	[break_id] [bigint] IDENTITY(1,1) NOT NULL,
	[overtime_id] [bigint] NOT NULL,
	[break_start] [datetime2](3) NOT NULL,
	[break_end] [datetime2](3) NOT NULL,
	[is_active] [bit] NOT NULL,
	[created_by] [bigint] NULL,
	[created_at] [datetime2](3) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[break_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- overtime_requests: Nhi
CREATE TABLE [dbo].[overtime_requests](
	[overtime_id] [bigint] IDENTITY(1,1) NOT NULL,
	[employee_id] [bigint] NOT NULL,
	[request_code] [varchar](50) NOT NULL,
	[requested_start] [datetime2](3) NOT NULL,
	[requested_end] [datetime2](3) NOT NULL,
	[reason] [nvarchar](1000) NOT NULL,
	[submitted_at] [datetime2](3) NOT NULL,
	[status] [varchar](20) NOT NULL,
	[processed_by] [bigint] NULL,
	[processed_at] [datetime2](3) NULL,
	[rejection_reason] [nvarchar](1000) NULL,
	[actual_start] [datetime2](3) NULL,
	[actual_end] [datetime2](3) NULL,
	[actual_break_minutes] [int] NOT NULL,
	[confirmed_by] [bigint] NULL,
	[confirmed_at] [datetime2](3) NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
	[work_content] [nvarchar](2000) NULL,
	[requested_break_status] [varchar](24) NOT NULL,
 CONSTRAINT [PK_overtime_requests] PRIMARY KEY CLUSTERED 
(
	[overtime_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- password_recovery: Viet
CREATE TABLE [dbo].[password_recovery](
	[account_id] [bigint] NOT NULL,
	[code_hash] [varchar](64) NOT NULL,
	[expires_at] [datetime2](6) NOT NULL,
	[sent_at] [datetime2](6) NOT NULL,
	[attempts] [int] NOT NULL,
	[token_version] [int] NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[account_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- payroll_batches: Phuong
CREATE TABLE [dbo].[payroll_batches](
	[batch_id] [bigint] IDENTITY(1,1) NOT NULL,
	[period_id] [bigint] NOT NULL,
	[version_no] [int] NOT NULL,
	[previous_batch_id] [bigint] NULL,
	[status] [varchar](20) NOT NULL,
	[adjustment_reason] [nvarchar](1000) NULL,
	[locked_by] [bigint] NULL,
	[locked_at] [datetime2](3) NULL,
	[published_by] [bigint] NULL,
	[published_at] [datetime2](3) NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_payroll_batches] PRIMARY KEY CLUSTERED 
(
	[batch_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- payroll_details: Phuong
CREATE TABLE [dbo].[payroll_details](
	[payroll_detail_id] [bigint] IDENTITY(1,1) NOT NULL,
	[batch_id] [bigint] NOT NULL,
	[employee_id] [bigint] NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_payroll_details] PRIMARY KEY CLUSTERED 
(
	[payroll_detail_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- payroll_lines: Phuong
CREATE TABLE [dbo].[payroll_lines](
	[payroll_line_id] [bigint] IDENTITY(1,1) NOT NULL,
	[payroll_detail_id] [bigint] NOT NULL,
	[component_id] [bigint] NOT NULL,
	[line_no] [int] NOT NULL,
	[component_code_snapshot] [varchar](50) NOT NULL,
	[component_name_snapshot] [nvarchar](200) NOT NULL,
	[direction_snapshot] [varchar](10) NOT NULL,
	[quantity] [decimal](9, 4) NULL,
	[unit_rate] [decimal](19, 4) NULL,
	[multiplier] [decimal](9, 4) NULL,
	[amount] [decimal](19, 4) NOT NULL,
	[explanation] [nvarchar](2000) NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_payroll_lines] PRIMARY KEY CLUSTERED 
(
	[payroll_line_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- payroll_periods: Phuong
CREATE TABLE [dbo].[payroll_periods](
	[period_id] [bigint] IDENTITY(1,1) NOT NULL,
	[period_code] [varchar](50) NOT NULL,
	[period_start] [date] NOT NULL,
	[period_end] [date] NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_payroll_periods] PRIMARY KEY CLUSTERED 
(
	[period_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- payroll_snapshots: Phuong
CREATE TABLE [dbo].[payroll_snapshots](
	[payroll_detail_id] [bigint] NOT NULL,
	[schema_version] [int] NOT NULL,
	[policy_version] [varchar](100) NOT NULL,
	[input_snapshot_json] [nvarchar](max) NOT NULL,
	[captured_at] [datetime2](3) NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_payroll_snapshots] PRIMARY KEY CLUSTERED 
(
	[payroll_detail_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]

GO
-- payslips: Phuong
CREATE TABLE [dbo].[payslips](
	[payslip_id] [bigint] IDENTITY(1,1) NOT NULL,
	[payroll_detail_id] [bigint] NOT NULL,
	[payslip_number] [varchar](50) NOT NULL,
	[issued_at] [datetime2](3) NOT NULL,
	[file_reference] [nvarchar](1000) NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_payslips] PRIMARY KEY CLUSTERED 
(
	[payslip_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- permissions: Viet
CREATE TABLE [dbo].[permissions](
	[permission_id] [bigint] IDENTITY(1,1) NOT NULL,
	[permission_code] [varchar](100) NOT NULL,
	[permission_name] [nvarchar](200) NOT NULL,
	[description] [nvarchar](1000) NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_permissions] PRIMARY KEY CLUSTERED 
(
	[permission_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- personnel_leave_accrual_policies: XuanAnh
CREATE TABLE [dbo].[personnel_leave_accrual_policies](
	[leave_type_id] [bigint] NOT NULL,
	[annual_days] [int] NOT NULL,
	[hours_per_day] [decimal](6, 4) NOT NULL,
	[seniority_extra] [bit] NOT NULL,
	[updated_by] [bigint] NOT NULL,
	[updated_at] [datetime2](7) NOT NULL,
	[department_id] [bigint] NULL,
PRIMARY KEY CLUSTERED 
(
	[leave_type_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- personnel_leave_accrual_runs: XuanAnh
CREATE TABLE [dbo].[personnel_leave_accrual_runs](
	[entitlement_id] [bigint] NOT NULL,
	[employee_id] [bigint] NOT NULL,
	[leave_type_id] [bigint] NOT NULL,
	[period_start] [date] NOT NULL,
	[period_end] [date] NOT NULL,
	[granted_hours] [decimal](12, 4) NOT NULL,
	[policy_snapshot] [nvarchar](1000) NOT NULL,
	[created_by] [bigint] NOT NULL,
	[created_at] [datetime2](7) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[entitlement_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- personnel_leave_evidence: XuanAnh
CREATE TABLE [dbo].[personnel_leave_evidence](
	[request_id] [bigint] NOT NULL,
	[evidence_reference] [nvarchar](500) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[request_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- personnel_leave_rules: XuanAnh
CREATE TABLE [dbo].[personnel_leave_rules](
	[leave_type_id] [bigint] NOT NULL,
	[retroactive_days] [int] NOT NULL,
	[minimum_notice_hours] [int] NOT NULL,
	[maximum_hours] [decimal](9, 4) NULL,
	[updated_by] [bigint] NOT NULL,
	[updated_at] [datetime2](7) NOT NULL,
	[minimum_service_months] [int] NOT NULL,
	[evidence_required] [bit] NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[leave_type_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- personnel_request_approval_stages: XuanAnh
CREATE TABLE [dbo].[personnel_request_approval_stages](
	[request_id] [bigint] NOT NULL,
	[department_id] [bigint] NULL,
	[stage_status] [varchar](16) NOT NULL,
	[decided_by] [bigint] NULL,
	[decided_at] [datetime2](7) NULL,
	[note] [nvarchar](1000) NULL,
	[created_at] [datetime2](7) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[request_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- personnel_termination_decisions: XuanAnh
CREATE TABLE [dbo].[personnel_termination_decisions](
	[request_id] [bigint] NOT NULL,
	[decision_type] [varchar](40) NOT NULL,
	[decision_number] [nvarchar](100) NOT NULL,
	[created_by] [bigint] NOT NULL,
	[created_at] [datetime2](7) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[request_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- positions: XuanAnh
CREATE TABLE [dbo].[positions](
	[position_id] [bigint] IDENTITY(1,1) NOT NULL,
	[position_code] [varchar](50) NOT NULL,
	[position_name] [nvarchar](200) NOT NULL,
	[description] [nvarchar](1000) NULL,
	[is_active] [bit] NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_positions] PRIMARY KEY CLUSTERED 
(
	[position_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- qualification_levels: XuanAnh
CREATE TABLE [dbo].[qualification_levels](
	[qualification_level_id] [bigint] IDENTITY(1,1) NOT NULL,
	[level_code] [varchar](50) NOT NULL,
	[level_name] [nvarchar](200) NOT NULL,
	[description] [nvarchar](1000) NULL,
	[is_active] [bit] NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
	[rank_order] [int] NULL,
 CONSTRAINT [PK_qualification_levels] PRIMARY KEY CLUSTERED 
(
	[qualification_level_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- resignation_request_details: XuanAnh
CREATE TABLE [dbo].[resignation_request_details](
	[request_id] [bigint] NOT NULL,
	[requested_last_working_date] [date] NOT NULL,
	[approved_last_working_date] [date] NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_resignation_request_details] PRIMARY KEY CLUSTERED 
(
	[request_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- role_permissions: Viet
CREATE TABLE [dbo].[role_permissions](
	[role_id] [bigint] NOT NULL,
	[permission_id] [bigint] NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_role_permissions] PRIMARY KEY CLUSTERED 
(
	[role_id] ASC,
	[permission_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- roles: Viet
CREATE TABLE [dbo].[roles](
	[role_id] [bigint] IDENTITY(1,1) NOT NULL,
	[role_code] [varchar](50) NOT NULL,
	[role_name] [nvarchar](100) NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_roles] PRIMARY KEY CLUSTERED 
(
	[role_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- salary_components: Duong
CREATE TABLE [dbo].[salary_components](
	[component_id] [bigint] IDENTITY(1,1) NOT NULL,
	[component_code] [varchar](50) NOT NULL,
	[component_name] [nvarchar](200) NOT NULL,
	[direction] [varchar](10) NOT NULL,
	[is_active] [bit] NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_salary_components] PRIMARY KEY CLUSTERED 
(
	[component_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- work_schedule_breaks: Nhi
CREATE TABLE [dbo].[work_schedule_breaks](
	[break_id] [bigint] IDENTITY(1,1) NOT NULL,
	[schedule_id] [bigint] NOT NULL,
	[break_start] [datetime2](3) NOT NULL,
	[break_end] [datetime2](3) NOT NULL,
	[superseded_at] [datetime2](3) NULL,
PRIMARY KEY CLUSTERED 
(
	[break_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- work_schedules: Nhi
CREATE TABLE [dbo].[work_schedules](
	[schedule_id] [bigint] IDENTITY(1,1) NOT NULL,
	[employee_id] [bigint] NOT NULL,
	[shift_id] [bigint] NULL,
	[work_date] [date] NOT NULL,
	[planned_start] [datetime2](3) NOT NULL,
	[planned_end] [datetime2](3) NOT NULL,
	[planned_break_minutes] [int] NOT NULL,
	[note] [nvarchar](1000) NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
	[break_detail_status] [varchar](24) NOT NULL,
	[cancelled_at] [datetime2](3) NULL,
	[cancelled_by] [bigint] NULL,
	[cancellation_reason] [nvarchar](1000) NULL,
 CONSTRAINT [PK_work_schedules] PRIMARY KEY CLUSTERED 
(
	[schedule_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- work_shift_breaks: Nhi
CREATE TABLE [dbo].[work_shift_breaks](
	[break_id] [bigint] IDENTITY(1,1) NOT NULL,
	[shift_id] [bigint] NOT NULL,
	[break_start_offset_minutes] [int] NOT NULL,
	[break_end_offset_minutes] [int] NOT NULL,
	[superseded_at] [datetime2](7) NULL,
 CONSTRAINT [PK_work_shift_breaks] PRIMARY KEY CLUSTERED 
(
	[break_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- work_shifts: Nhi
CREATE TABLE [dbo].[work_shifts](
	[shift_id] [bigint] IDENTITY(1,1) NOT NULL,
	[shift_code] [varchar](50) NOT NULL,
	[shift_name] [nvarchar](200) NOT NULL,
	[start_time] [time](0) NOT NULL,
	[end_time] [time](0) NOT NULL,
	[end_day_offset] [int] NOT NULL,
	[break_minutes] [int] NOT NULL,
	[is_active] [bit] NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[updated_at] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[updated_by] [bigint] NULL,
	[row_version] [timestamp] NOT NULL,
 CONSTRAINT [PK_work_shifts] PRIMARY KEY CLUSTERED 
(
	[shift_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- attendance_portal_requests: Nhi
CREATE TABLE [dbo].[attendance_portal_requests](
	[request_id] [bigint] IDENTITY(1,1) NOT NULL,
	[attendance_id] [bigint] NULL,
	[request_code] [varchar](40) NOT NULL,
	[request_type] [varchar](24) NOT NULL,
	[status] [varchar](20) NOT NULL,
	[proposed_start] [datetime2](3) NULL,
	[proposed_end] [datetime2](3) NULL,
	[proposed_break_minutes] [int] NOT NULL,
	[proposed_result] [varchar](12) NOT NULL,
	[original_version] [binary](8) NULL,
	[original_start] [datetime2](3) NULL,
	[original_end] [datetime2](3) NULL,
	[original_break_minutes] [int] NOT NULL,
	[original_result] [varchar](12) NOT NULL,
	[original_verification] [varchar](20) NOT NULL,
	[original_confirmed_by] [bigint] NULL,
	[reason] [nvarchar](1000) NOT NULL,
	[submitted_by] [bigint] NOT NULL,
	[submitted_at] [datetime2](3) NOT NULL,
	[submission_deadline] [datetime2](3) NOT NULL,
	[received_by] [bigint] NULL,
	[received_at] [datetime2](3) NULL,
	[processed_by] [bigint] NULL,
	[processed_at] [datetime2](3) NULL,
	[decision_reason] [nvarchar](1000) NULL,
	[accepted_end] [datetime2](3) NULL,
	[processing_deadline] [datetime2](3) NULL,
	[schedule_id] [bigint] NOT NULL,
	[accepted_start] [datetime2](3) NULL,
	[adjustment_reason] [nvarchar](1000) NULL,
PRIMARY KEY CLUSTERED 
(
	[request_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- ot_supplement_breaks: Nhi
CREATE TABLE [dbo].[ot_supplement_breaks](
	[break_id] [bigint] IDENTITY(1,1) NOT NULL,
	[request_id] [bigint] NOT NULL,
	[break_start] [datetime2](3) NOT NULL,
	[break_end] [datetime2](3) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[break_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- ot_supplement_requests: Nhi
CREATE TABLE [dbo].[ot_supplement_requests](
	[request_id] [bigint] IDENTITY(1,1) NOT NULL,
	[overtime_id] [bigint] NOT NULL,
	[employee_id] [bigint] NOT NULL,
	[parent_version] [binary](8) NOT NULL,
	[proposed_start] [datetime2](3) NOT NULL,
	[proposed_end] [datetime2](3) NOT NULL,
	[reason] [nvarchar](1000) NOT NULL,
	[status] [varchar](24) NOT NULL,
	[created_by] [bigint] NOT NULL,
	[submitted_at] [datetime2](3) NOT NULL,
	[processed_by] [bigint] NULL,
	[processed_at] [datetime2](3) NULL,
	[decision_reason] [nvarchar](1000) NULL,
PRIMARY KEY CLUSTERED 
(
	[request_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- overtime_overflow_intervals: Nhi
CREATE TABLE [dbo].[overtime_overflow_intervals](
	[interval_id] [bigint] IDENTITY(1,1) NOT NULL,
	[request_id] [bigint] NOT NULL,
	[work_date] [date] NOT NULL,
	[requested_start] [datetime2](3) NOT NULL,
	[requested_end] [datetime2](3) NOT NULL,
	[approved_start] [datetime2](3) NULL,
	[approved_end] [datetime2](3) NULL,
	[created_by] [bigint] NULL,
	[created_at] [datetime2](3) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[interval_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- overtime_request_details: Nhi
CREATE TABLE [dbo].[overtime_request_details](
	[request_id] [bigint] NOT NULL,
	[overtime_id] [bigint] NOT NULL,
	[created_by] [bigint] NULL,
	[created_at] [datetime2](3) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[request_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- overtime_requested_breaks: Nhi
CREATE TABLE [dbo].[overtime_requested_breaks](
	[break_id] [bigint] IDENTITY(1,1) NOT NULL,
	[overtime_id] [bigint] NOT NULL,
	[break_start] [datetime2](3) NOT NULL,
	[break_end] [datetime2](3) NOT NULL,
	[is_active] [bit] NOT NULL,
	[created_by] [bigint] NULL,
	[created_at] [datetime2](3) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[break_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- schedule_change_request_breaks: Nhi
CREATE TABLE [dbo].[schedule_change_request_breaks](
	[break_id] [bigint] IDENTITY(1,1) NOT NULL,
	[request_id] [bigint] NOT NULL,
	[break_start] [datetime2](3) NOT NULL,
	[break_end] [datetime2](3) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[break_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- schedule_change_request_details: Nhi
CREATE TABLE [dbo].[schedule_change_request_details](
	[request_id] [bigint] NOT NULL,
	[schedule_id] [bigint] NOT NULL,
	[original_start] [datetime2](3) NOT NULL,
	[original_end] [datetime2](3) NOT NULL,
	[proposed_shift_id] [bigint] NULL,
	[proposed_start] [datetime2](3) NOT NULL,
	[proposed_end] [datetime2](3) NOT NULL,
	[original_row_version] [binary](8) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[request_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
-- attendance_actual_breaks: Nhi
CREATE TABLE [dbo].[attendance_actual_breaks](
	[break_id] [bigint] IDENTITY(1,1) NOT NULL,
	[attendance_id] [bigint] NOT NULL,
	[break_start] [datetime2](7) NOT NULL,
	[break_end] [datetime2](7) NOT NULL,
	[source_start] [datetime2](7) NOT NULL,
	[source_end] [datetime2](7) NOT NULL,
	[source_break_minutes] [int] NOT NULL,
	[created_by] [bigint] NOT NULL,
	[created_at] [datetime2](3) NOT NULL,
	[superseded_at] [datetime2](3) NULL,
PRIMARY KEY CLUSTERED 
(
	[break_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]

GO
ALTER TABLE [dbo].[departments] ADD  CONSTRAINT [UQ_departments_code] UNIQUE NONCLUSTERED 
(
	[department_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[employee_requests] ADD  CONSTRAINT [UQ_employee_requests_code] UNIQUE NONCLUSTERED 
(
	[request_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[employees] ADD  CONSTRAINT [UQ_employees_code] UNIQUE NONCLUSTERED 
(
	[employee_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[g2_leave_allocations] ADD  CONSTRAINT [UQ_g2_leave_schedule] UNIQUE NONCLUSTERED 
(
	[request_id] ASC,
	[schedule_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[leave_entitlements] ADD  CONSTRAINT [UQ_entitlements_period] UNIQUE NONCLUSTERED 
(
	[employee_id] ASC,
	[leave_type_id] ASC,
	[period_start] ASC,
	[period_end] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[leave_types] ADD  CONSTRAINT [UQ_leave_types_code] UNIQUE NONCLUSTERED 
(
	[type_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[personnel_leave_accrual_runs] ADD UNIQUE NONCLUSTERED 
(
	[employee_id] ASC,
	[leave_type_id] ASC,
	[period_start] ASC,
	[period_end] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[positions] ADD  CONSTRAINT [UQ_positions_code] UNIQUE NONCLUSTERED 
(
	[position_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[qualification_levels] ADD  CONSTRAINT [UQ_qualification_levels_code] UNIQUE NONCLUSTERED 
(
	[level_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[audit_logs] ADD  CONSTRAINT [DF_audit_logs_occurred]  DEFAULT (sysutcdatetime()) FOR [occurred_at]
GO
ALTER TABLE [dbo].[departments] ADD  CONSTRAINT [DF_departments_active]  DEFAULT ((1)) FOR [is_active]
GO
ALTER TABLE [dbo].[departments] ADD  CONSTRAINT [DF_departments_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[employee_assignments] ADD  CONSTRAINT [DF_employee_assignments_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[employee_qualifications] ADD  CONSTRAINT [DF_employee_qualifications_primary]  DEFAULT ((0)) FOR [is_primary]
GO
ALTER TABLE [dbo].[employee_qualifications] ADD  CONSTRAINT [DF_employee_qualifications_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[employee_qualifications] ADD  CONSTRAINT [DF_g2_qualification_deleted]  DEFAULT ((0)) FOR [is_deleted]
GO
ALTER TABLE [dbo].[employee_request_withdrawals] ADD  DEFAULT (sysutcdatetime()) FOR [withdrawn_at]
GO
ALTER TABLE [dbo].[employee_requests] ADD  CONSTRAINT [DF_employee_requests_status]  DEFAULT ('CHO_DUYET') FOR [status]
GO
ALTER TABLE [dbo].[employee_requests] ADD  CONSTRAINT [DF_employee_requests_submitted]  DEFAULT (sysutcdatetime()) FOR [submitted_at]
GO
ALTER TABLE [dbo].[employee_requests] ADD  CONSTRAINT [DF_employee_requests_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[employees] ADD  CONSTRAINT [DF_employees_status]  DEFAULT ('DANG_LAM') FOR [employment_status]
GO
ALTER TABLE [dbo].[employees] ADD  CONSTRAINT [DF_employees_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[g2_leave_allocations] ADD  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[g2_leave_policies] ADD  DEFAULT (sysutcdatetime()) FOR [updated_at]
GO
ALTER TABLE [dbo].[g2_resignation_executions] ADD  DEFAULT (sysutcdatetime()) FOR [executed_at]
GO
ALTER TABLE [dbo].[hr_department_scopes] ADD  CONSTRAINT [DF_hr_scopes_created_at]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[leave_entitlements] ADD  CONSTRAINT [DF_entitlements_hours]  DEFAULT ((0)) FOR [entitled_hours]
GO
ALTER TABLE [dbo].[leave_entitlements] ADD  CONSTRAINT [DF_leave_entitlements_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[leave_request_details] ADD  CONSTRAINT [DF_leave_request_details_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[leave_types] ADD  CONSTRAINT [DF_leave_types_paid]  DEFAULT ((0)) FOR [is_paid]
GO
ALTER TABLE [dbo].[leave_types] ADD  CONSTRAINT [DF_leave_types_active]  DEFAULT ((1)) FOR [is_active]
GO
ALTER TABLE [dbo].[leave_types] ADD  CONSTRAINT [DF_leave_types_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[personnel_leave_accrual_policies] ADD  DEFAULT ((0)) FOR [seniority_extra]
GO
ALTER TABLE [dbo].[personnel_leave_accrual_policies] ADD  DEFAULT (sysutcdatetime()) FOR [updated_at]
GO
ALTER TABLE [dbo].[personnel_leave_accrual_runs] ADD  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[personnel_leave_rules] ADD  DEFAULT ((0)) FOR [retroactive_days]
GO
ALTER TABLE [dbo].[personnel_leave_rules] ADD  DEFAULT ((0)) FOR [minimum_notice_hours]
GO
ALTER TABLE [dbo].[personnel_leave_rules] ADD  DEFAULT (sysutcdatetime()) FOR [updated_at]
GO
ALTER TABLE [dbo].[personnel_leave_rules] ADD  DEFAULT ((0)) FOR [minimum_service_months]
GO
ALTER TABLE [dbo].[personnel_leave_rules] ADD  DEFAULT ((0)) FOR [evidence_required]
GO
ALTER TABLE [dbo].[personnel_request_approval_stages] ADD  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[personnel_termination_decisions] ADD  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[positions] ADD  CONSTRAINT [DF_positions_active]  DEFAULT ((1)) FOR [is_active]
GO
ALTER TABLE [dbo].[positions] ADD  CONSTRAINT [DF_positions_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[qualification_levels] ADD  CONSTRAINT [DF_qualification_levels_active]  DEFAULT ((1)) FOR [is_active]
GO
ALTER TABLE [dbo].[qualification_levels] ADD  CONSTRAINT [DF_qualification_levels_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[resignation_request_details] ADD  CONSTRAINT [DF_resignation_request_details_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[audit_logs]  WITH CHECK ADD  CONSTRAINT [FK_audit_logs_actor] FOREIGN KEY([actor_account_id])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[audit_logs] CHECK CONSTRAINT [FK_audit_logs_actor]
GO
ALTER TABLE [dbo].[departments]  WITH CHECK ADD  CONSTRAINT [FK_departments_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[departments] CHECK CONSTRAINT [FK_departments_creator]
GO
ALTER TABLE [dbo].[departments]  WITH CHECK ADD  CONSTRAINT [FK_departments_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[departments] CHECK CONSTRAINT [FK_departments_updater]
GO
ALTER TABLE [dbo].[employee_assignments]  WITH CHECK ADD  CONSTRAINT [FK_assignments_department] FOREIGN KEY([department_id])
REFERENCES [dbo].[departments] ([department_id])
GO
ALTER TABLE [dbo].[employee_assignments] CHECK CONSTRAINT [FK_assignments_department]
GO
ALTER TABLE [dbo].[employee_assignments]  WITH CHECK ADD  CONSTRAINT [FK_assignments_employee] FOREIGN KEY([employee_id])
REFERENCES [dbo].[employees] ([employee_id])
GO
ALTER TABLE [dbo].[employee_assignments] CHECK CONSTRAINT [FK_assignments_employee]
GO
ALTER TABLE [dbo].[employee_assignments]  WITH CHECK ADD  CONSTRAINT [FK_assignments_position] FOREIGN KEY([position_id])
REFERENCES [dbo].[positions] ([position_id])
GO
ALTER TABLE [dbo].[employee_assignments] CHECK CONSTRAINT [FK_assignments_position]
GO
ALTER TABLE [dbo].[employee_assignments]  WITH CHECK ADD  CONSTRAINT [FK_employee_assignments_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_assignments] CHECK CONSTRAINT [FK_employee_assignments_creator]
GO
ALTER TABLE [dbo].[employee_assignments]  WITH CHECK ADD  CONSTRAINT [FK_employee_assignments_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_assignments] CHECK CONSTRAINT [FK_employee_assignments_updater]
GO
ALTER TABLE [dbo].[employee_qualifications]  WITH CHECK ADD FOREIGN KEY([deleted_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_qualifications]  WITH CHECK ADD  CONSTRAINT [FK_employee_qualifications_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_qualifications] CHECK CONSTRAINT [FK_employee_qualifications_creator]
GO
ALTER TABLE [dbo].[employee_qualifications]  WITH CHECK ADD  CONSTRAINT [FK_employee_qualifications_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_qualifications] CHECK CONSTRAINT [FK_employee_qualifications_updater]
GO
ALTER TABLE [dbo].[employee_qualifications]  WITH CHECK ADD  CONSTRAINT [FK_qualifications_employee] FOREIGN KEY([employee_id])
REFERENCES [dbo].[employees] ([employee_id])
GO
ALTER TABLE [dbo].[employee_qualifications] CHECK CONSTRAINT [FK_qualifications_employee]
GO
ALTER TABLE [dbo].[employee_qualifications]  WITH CHECK ADD  CONSTRAINT [FK_qualifications_level] FOREIGN KEY([qualification_level_id])
REFERENCES [dbo].[qualification_levels] ([qualification_level_id])
GO
ALTER TABLE [dbo].[employee_qualifications] CHECK CONSTRAINT [FK_qualifications_level]
GO
ALTER TABLE [dbo].[employee_request_withdrawals]  WITH CHECK ADD FOREIGN KEY([request_id])
REFERENCES [dbo].[employee_requests] ([request_id])
GO
ALTER TABLE [dbo].[employee_request_withdrawals]  WITH CHECK ADD FOREIGN KEY([withdrawn_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_requests]  WITH CHECK ADD  CONSTRAINT [FK_employee_requests_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_requests] CHECK CONSTRAINT [FK_employee_requests_creator]
GO
ALTER TABLE [dbo].[employee_requests]  WITH CHECK ADD  CONSTRAINT [FK_employee_requests_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_requests] CHECK CONSTRAINT [FK_employee_requests_updater]
GO
ALTER TABLE [dbo].[employee_requests]  WITH CHECK ADD  CONSTRAINT [FK_requests_employee] FOREIGN KEY([employee_id])
REFERENCES [dbo].[employees] ([employee_id])
GO
ALTER TABLE [dbo].[employee_requests] CHECK CONSTRAINT [FK_requests_employee]
GO
ALTER TABLE [dbo].[employee_requests]  WITH CHECK ADD  CONSTRAINT [FK_requests_processor] FOREIGN KEY([processed_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_requests] CHECK CONSTRAINT [FK_requests_processor]
GO
ALTER TABLE [dbo].[employees]  WITH CHECK ADD  CONSTRAINT [FK_employees_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employees] CHECK CONSTRAINT [FK_employees_creator]
GO
ALTER TABLE [dbo].[employees]  WITH CHECK ADD  CONSTRAINT [FK_employees_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employees] CHECK CONSTRAINT [FK_employees_updater]
GO
ALTER TABLE [dbo].[g2_leave_allocations]  WITH CHECK ADD FOREIGN KEY([entitlement_id])
REFERENCES [dbo].[leave_entitlements] ([entitlement_id])
GO
ALTER TABLE [dbo].[g2_leave_allocations]  WITH CHECK ADD FOREIGN KEY([request_id])
REFERENCES [dbo].[employee_requests] ([request_id])
GO
ALTER TABLE [dbo].[g2_leave_allocations]  WITH CHECK ADD FOREIGN KEY([schedule_id])
REFERENCES [dbo].[work_schedules] ([schedule_id])
GO
ALTER TABLE [dbo].[g2_leave_policies]  WITH CHECK ADD FOREIGN KEY([leave_type_id])
REFERENCES [dbo].[leave_types] ([leave_type_id])
GO
ALTER TABLE [dbo].[g2_leave_policies]  WITH CHECK ADD FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[g2_resignation_executions]  WITH CHECK ADD FOREIGN KEY([request_id])
REFERENCES [dbo].[employee_requests] ([request_id])
GO
ALTER TABLE [dbo].[hr_department_scopes]  WITH CHECK ADD  CONSTRAINT [FK_hr_scopes_created_by] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[hr_department_scopes] CHECK CONSTRAINT [FK_hr_scopes_created_by]
GO
ALTER TABLE [dbo].[hr_department_scopes]  WITH CHECK ADD  CONSTRAINT [FK_hr_scopes_department] FOREIGN KEY([department_id])
REFERENCES [dbo].[departments] ([department_id])
GO
ALTER TABLE [dbo].[hr_department_scopes] CHECK CONSTRAINT [FK_hr_scopes_department]
GO
ALTER TABLE [dbo].[hr_department_scopes]  WITH CHECK ADD  CONSTRAINT [FK_hr_scopes_hr_account] FOREIGN KEY([hr_account_id])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[hr_department_scopes] CHECK CONSTRAINT [FK_hr_scopes_hr_account]
GO
ALTER TABLE [dbo].[hr_department_scopes]  WITH CHECK ADD  CONSTRAINT [FK_hr_scopes_revoked_by] FOREIGN KEY([revoked_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[hr_department_scopes] CHECK CONSTRAINT [FK_hr_scopes_revoked_by]
GO
ALTER TABLE [dbo].[hr_department_scopes]  WITH CHECK ADD  CONSTRAINT [FK_hr_scopes_updated_by] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[hr_department_scopes] CHECK CONSTRAINT [FK_hr_scopes_updated_by]
GO
ALTER TABLE [dbo].[leave_entitlements]  WITH CHECK ADD  CONSTRAINT [FK_entitlements_employee] FOREIGN KEY([employee_id])
REFERENCES [dbo].[employees] ([employee_id])
GO
ALTER TABLE [dbo].[leave_entitlements] CHECK CONSTRAINT [FK_entitlements_employee]
GO
ALTER TABLE [dbo].[leave_entitlements]  WITH CHECK ADD  CONSTRAINT [FK_entitlements_type] FOREIGN KEY([leave_type_id])
REFERENCES [dbo].[leave_types] ([leave_type_id])
GO
ALTER TABLE [dbo].[leave_entitlements] CHECK CONSTRAINT [FK_entitlements_type]
GO
ALTER TABLE [dbo].[leave_entitlements]  WITH CHECK ADD  CONSTRAINT [FK_leave_entitlements_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[leave_entitlements] CHECK CONSTRAINT [FK_leave_entitlements_creator]
GO
ALTER TABLE [dbo].[leave_entitlements]  WITH CHECK ADD  CONSTRAINT [FK_leave_entitlements_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[leave_entitlements] CHECK CONSTRAINT [FK_leave_entitlements_updater]
GO
ALTER TABLE [dbo].[leave_request_details]  WITH CHECK ADD  CONSTRAINT [FK_leave_details_request] FOREIGN KEY([request_id])
REFERENCES [dbo].[employee_requests] ([request_id])
GO
ALTER TABLE [dbo].[leave_request_details] CHECK CONSTRAINT [FK_leave_details_request]
GO
ALTER TABLE [dbo].[leave_request_details]  WITH CHECK ADD  CONSTRAINT [FK_leave_details_type] FOREIGN KEY([leave_type_id])
REFERENCES [dbo].[leave_types] ([leave_type_id])
GO
ALTER TABLE [dbo].[leave_request_details] CHECK CONSTRAINT [FK_leave_details_type]
GO
ALTER TABLE [dbo].[leave_request_details]  WITH CHECK ADD  CONSTRAINT [FK_leave_request_details_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[leave_request_details] CHECK CONSTRAINT [FK_leave_request_details_creator]
GO
ALTER TABLE [dbo].[leave_request_details]  WITH CHECK ADD  CONSTRAINT [FK_leave_request_details_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[leave_request_details] CHECK CONSTRAINT [FK_leave_request_details_updater]
GO
ALTER TABLE [dbo].[leave_types]  WITH CHECK ADD  CONSTRAINT [FK_leave_types_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[leave_types] CHECK CONSTRAINT [FK_leave_types_creator]
GO
ALTER TABLE [dbo].[leave_types]  WITH CHECK ADD  CONSTRAINT [FK_leave_types_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[leave_types] CHECK CONSTRAINT [FK_leave_types_updater]
GO
ALTER TABLE [dbo].[personnel_leave_accrual_policies]  WITH CHECK ADD FOREIGN KEY([department_id])
REFERENCES [dbo].[departments] ([department_id])
GO
ALTER TABLE [dbo].[personnel_leave_accrual_policies]  WITH CHECK ADD FOREIGN KEY([leave_type_id])
REFERENCES [dbo].[leave_types] ([leave_type_id])
GO
ALTER TABLE [dbo].[personnel_leave_accrual_policies]  WITH CHECK ADD FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[personnel_leave_accrual_runs]  WITH CHECK ADD FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[personnel_leave_accrual_runs]  WITH CHECK ADD FOREIGN KEY([employee_id])
REFERENCES [dbo].[employees] ([employee_id])
GO
ALTER TABLE [dbo].[personnel_leave_accrual_runs]  WITH CHECK ADD FOREIGN KEY([entitlement_id])
REFERENCES [dbo].[leave_entitlements] ([entitlement_id])
GO
ALTER TABLE [dbo].[personnel_leave_accrual_runs]  WITH CHECK ADD FOREIGN KEY([leave_type_id])
REFERENCES [dbo].[leave_types] ([leave_type_id])
GO
ALTER TABLE [dbo].[personnel_leave_evidence]  WITH CHECK ADD FOREIGN KEY([request_id])
REFERENCES [dbo].[employee_requests] ([request_id])
GO
ALTER TABLE [dbo].[personnel_leave_rules]  WITH CHECK ADD FOREIGN KEY([leave_type_id])
REFERENCES [dbo].[leave_types] ([leave_type_id])
GO
ALTER TABLE [dbo].[personnel_leave_rules]  WITH CHECK ADD FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[personnel_request_approval_stages]  WITH CHECK ADD FOREIGN KEY([decided_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[personnel_request_approval_stages]  WITH CHECK ADD FOREIGN KEY([department_id])
REFERENCES [dbo].[departments] ([department_id])
GO
ALTER TABLE [dbo].[personnel_request_approval_stages]  WITH CHECK ADD FOREIGN KEY([request_id])
REFERENCES [dbo].[employee_requests] ([request_id])
GO
ALTER TABLE [dbo].[personnel_termination_decisions]  WITH CHECK ADD FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[personnel_termination_decisions]  WITH CHECK ADD FOREIGN KEY([request_id])
REFERENCES [dbo].[employee_requests] ([request_id])
GO
ALTER TABLE [dbo].[positions]  WITH CHECK ADD  CONSTRAINT [FK_positions_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[positions] CHECK CONSTRAINT [FK_positions_creator]
GO
ALTER TABLE [dbo].[positions]  WITH CHECK ADD  CONSTRAINT [FK_positions_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[positions] CHECK CONSTRAINT [FK_positions_updater]
GO
ALTER TABLE [dbo].[qualification_levels]  WITH CHECK ADD  CONSTRAINT [FK_qualification_levels_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[qualification_levels] CHECK CONSTRAINT [FK_qualification_levels_creator]
GO
ALTER TABLE [dbo].[qualification_levels]  WITH CHECK ADD  CONSTRAINT [FK_qualification_levels_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[qualification_levels] CHECK CONSTRAINT [FK_qualification_levels_updater]
GO
ALTER TABLE [dbo].[resignation_request_details]  WITH CHECK ADD  CONSTRAINT [FK_resignation_details_request] FOREIGN KEY([request_id])
REFERENCES [dbo].[employee_requests] ([request_id])
GO
ALTER TABLE [dbo].[resignation_request_details] CHECK CONSTRAINT [FK_resignation_details_request]
GO
ALTER TABLE [dbo].[resignation_request_details]  WITH CHECK ADD  CONSTRAINT [FK_resignation_request_details_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[resignation_request_details] CHECK CONSTRAINT [FK_resignation_request_details_creator]
GO
ALTER TABLE [dbo].[resignation_request_details]  WITH CHECK ADD  CONSTRAINT [FK_resignation_request_details_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[resignation_request_details] CHECK CONSTRAINT [FK_resignation_request_details_updater]
GO
ALTER TABLE [dbo].[audit_logs]  WITH CHECK ADD  CONSTRAINT [CK_audit_logs_after] CHECK  (([after_data_json] IS NULL OR isjson([after_data_json])=(1)))
GO
ALTER TABLE [dbo].[audit_logs] CHECK CONSTRAINT [CK_audit_logs_after]
GO
ALTER TABLE [dbo].[audit_logs]  WITH CHECK ADD  CONSTRAINT [CK_audit_logs_before] CHECK  (([before_data_json] IS NULL OR isjson([before_data_json])=(1)))
GO
ALTER TABLE [dbo].[audit_logs] CHECK CONSTRAINT [CK_audit_logs_before]
GO
ALTER TABLE [dbo].[employee_assignments]  WITH CHECK ADD  CONSTRAINT [CK_assignments_dates] CHECK  (([effective_to] IS NULL OR [effective_to]>[effective_from]))
GO
ALTER TABLE [dbo].[employee_assignments] CHECK CONSTRAINT [CK_assignments_dates]
GO
ALTER TABLE [dbo].[employee_qualifications]  WITH CHECK ADD  CONSTRAINT [CK_qualification_validity] CHECK  (([expiry_date] IS NULL OR [issued_date] IS NOT NULL AND [expiry_date]>=[issued_date]))
GO
ALTER TABLE [dbo].[employee_qualifications] CHECK CONSTRAINT [CK_qualification_validity]
GO
ALTER TABLE [dbo].[employee_qualifications]  WITH CHECK ADD  CONSTRAINT [CK_qualifications_year] CHECK  (([graduation_year] IS NULL OR [graduation_year]>=(1900) AND [graduation_year]<=(9999)))
GO
ALTER TABLE [dbo].[employee_qualifications] CHECK CONSTRAINT [CK_qualifications_year]
GO
ALTER TABLE [dbo].[employee_request_withdrawals]  WITH CHECK ADD CHECK  ((len(ltrim(rtrim([reason])))>(0)))
GO
ALTER TABLE [dbo].[employee_requests]  WITH CHECK ADD  CONSTRAINT [CK_requests_processed_time] CHECK  (([processed_at] IS NULL OR [processed_at]>=[submitted_at]))
GO
ALTER TABLE [dbo].[employee_requests] CHECK CONSTRAINT [CK_requests_processed_time]
GO
ALTER TABLE [dbo].[employee_requests]  WITH CHECK ADD  CONSTRAINT [CK_requests_processing] CHECK  (([status]='CHO_DUYET' AND [processed_by] IS NULL AND [processed_at] IS NULL AND [rejection_reason] IS NULL OR [status]='DA_DUYET' AND [processed_by] IS NOT NULL AND [processed_at] IS NOT NULL AND [rejection_reason] IS NULL OR [status]='TU_CHOI' AND [processed_by] IS NOT NULL AND [processed_at] IS NOT NULL AND [rejection_reason] IS NOT NULL AND len(ltrim(rtrim([rejection_reason])))>(0)))
GO
ALTER TABLE [dbo].[employee_requests] CHECK CONSTRAINT [CK_requests_processing]
GO
ALTER TABLE [dbo].[employee_requests]  WITH CHECK ADD  CONSTRAINT [CK_requests_reason] CHECK  ((len(ltrim(rtrim([reason])))>(0)))
GO
ALTER TABLE [dbo].[employee_requests] CHECK CONSTRAINT [CK_requests_reason]
GO
ALTER TABLE [dbo].[employee_requests]  WITH CHECK ADD  CONSTRAINT [CK_requests_type] CHECK  (([request_type]='NGHI_VIEC' OR [request_type]='NGHI_PHEP'))
GO
ALTER TABLE [dbo].[employee_requests] CHECK CONSTRAINT [CK_requests_type]
GO
ALTER TABLE [dbo].[employees]  WITH CHECK ADD  CONSTRAINT [CK_employee_optional_gender] CHECK  (([gender] IS NULL OR ([gender]='UNDISCLOSED' OR [gender]='OTHER' OR [gender]='FEMALE' OR [gender]='MALE')))
GO
ALTER TABLE [dbo].[employees] CHECK CONSTRAINT [CK_employee_optional_gender]
GO
ALTER TABLE [dbo].[employees]  WITH CHECK ADD  CONSTRAINT [CK_employees_dates] CHECK  (([birth_date] IS NULL OR [birth_date]<[hire_date]))
GO
ALTER TABLE [dbo].[employees] CHECK CONSTRAINT [CK_employees_dates]
GO
ALTER TABLE [dbo].[employees]  WITH CHECK ADD  CONSTRAINT [CK_employees_name] CHECK  ((len(ltrim(rtrim([full_name])))>(0)))
GO
ALTER TABLE [dbo].[employees] CHECK CONSTRAINT [CK_employees_name]
GO
ALTER TABLE [dbo].[employees]  WITH CHECK ADD  CONSTRAINT [CK_employees_status] CHECK  (([employment_status]='DANG_LAM' AND [termination_date] IS NULL OR [employment_status]='DA_NGHI' AND [termination_date] IS NOT NULL AND [termination_date]>=[hire_date]))
GO
ALTER TABLE [dbo].[employees] CHECK CONSTRAINT [CK_employees_status]
GO
ALTER TABLE [dbo].[g2_leave_allocations]  WITH CHECK ADD CHECK  (([charged_hours]>(0)))
GO
ALTER TABLE [dbo].[hr_department_scopes]  WITH CHECK ADD  CONSTRAINT [CK_hr_scopes_effective_dates] CHECK  (([effective_to] IS NULL OR [effective_to]>[effective_from]))
GO
ALTER TABLE [dbo].[hr_department_scopes] CHECK CONSTRAINT [CK_hr_scopes_effective_dates]
GO
ALTER TABLE [dbo].[hr_department_scopes]  WITH CHECK ADD  CONSTRAINT [CK_hr_scopes_revocation] CHECK  (([revoked_at] IS NULL AND [revoked_by] IS NULL OR [revoked_at] IS NOT NULL AND [revoked_by] IS NOT NULL))
GO
ALTER TABLE [dbo].[hr_department_scopes] CHECK CONSTRAINT [CK_hr_scopes_revocation]
GO
ALTER TABLE [dbo].[hr_department_scopes]  WITH CHECK ADD  CONSTRAINT [CK_hr_scopes_update_metadata] CHECK  (([updated_at] IS NULL AND [updated_by] IS NULL OR [updated_at] IS NOT NULL AND [updated_by] IS NOT NULL))
GO
ALTER TABLE [dbo].[hr_department_scopes] CHECK CONSTRAINT [CK_hr_scopes_update_metadata]
GO
ALTER TABLE [dbo].[leave_entitlements]  WITH CHECK ADD  CONSTRAINT [CK_entitlements_dates] CHECK  (([period_end]>[period_start]))
GO
ALTER TABLE [dbo].[leave_entitlements] CHECK CONSTRAINT [CK_entitlements_dates]
GO
ALTER TABLE [dbo].[leave_entitlements]  WITH CHECK ADD  CONSTRAINT [CK_entitlements_hours] CHECK  (([entitled_hours]>=(0)))
GO
ALTER TABLE [dbo].[leave_entitlements] CHECK CONSTRAINT [CK_entitlements_hours]
GO
ALTER TABLE [dbo].[leave_request_details]  WITH CHECK ADD  CONSTRAINT [CK_leave_details_time] CHECK  (([leave_end]>[leave_start]))
GO
ALTER TABLE [dbo].[leave_request_details] CHECK CONSTRAINT [CK_leave_details_time]
GO
ALTER TABLE [dbo].[personnel_leave_accrual_policies]  WITH CHECK ADD CHECK  (([annual_days]>=(1) AND [annual_days]<=(366)))
GO
ALTER TABLE [dbo].[personnel_leave_accrual_policies]  WITH CHECK ADD CHECK  (([hours_per_day]>(0) AND [hours_per_day]<=(24)))
GO
ALTER TABLE [dbo].[personnel_leave_rules]  WITH CHECK ADD CHECK  (([maximum_hours]>(0)))
GO
ALTER TABLE [dbo].[personnel_leave_rules]  WITH CHECK ADD CHECK  (([minimum_notice_hours]>=(0)))
GO
ALTER TABLE [dbo].[personnel_leave_rules]  WITH CHECK ADD CHECK  (([minimum_service_months]>=(0) AND [minimum_service_months]<=(1200)))
GO
ALTER TABLE [dbo].[personnel_leave_rules]  WITH CHECK ADD CHECK  (([retroactive_days]>=(0) AND [retroactive_days]<=(7)))
GO
ALTER TABLE [dbo].[personnel_request_approval_stages]  WITH CHECK ADD CHECK  (([stage_status]='SKIPPED' OR [stage_status]='REJECTED' OR [stage_status]='APPROVED' OR [stage_status]='PENDING'))
GO
ALTER TABLE [dbo].[personnel_termination_decisions]  WITH CHECK ADD CHECK  (([decision_type]='OTHER' OR [decision_type]='EMPLOYER_DECISION' OR [decision_type]='EMPLOYEE_RESIGNATION' OR [decision_type]='CONTRACT_EXPIRY' OR [decision_type]='MUTUAL_AGREEMENT'))
GO
ALTER TABLE [dbo].[qualification_levels]  WITH CHECK ADD  CONSTRAINT [CK_qualification_rank] CHECK  (([rank_order] IS NULL OR [rank_order]>=(0) AND [rank_order]<=(1000)))
GO
ALTER TABLE [dbo].[qualification_levels] CHECK CONSTRAINT [CK_qualification_rank]
GO
ALTER TABLE [dbo].[accounts] ADD  CONSTRAINT [UQ_accounts_employee] UNIQUE NONCLUSTERED 
(
	[employee_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[accounts] ADD  CONSTRAINT [UQ_accounts_username] UNIQUE NONCLUSTERED 
(
	[username] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[permissions] ADD  CONSTRAINT [UQ_permissions_code] UNIQUE NONCLUSTERED 
(
	[permission_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[roles] ADD  CONSTRAINT [UQ_roles_code] UNIQUE NONCLUSTERED 
(
	[role_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[accounts] ADD  CONSTRAINT [DF_accounts_active]  DEFAULT ((1)) FOR [is_active]
GO
ALTER TABLE [dbo].[accounts] ADD  CONSTRAINT [DF_accounts_token_version]  DEFAULT ((0)) FOR [token_version]
GO
ALTER TABLE [dbo].[accounts] ADD  CONSTRAINT [DF_accounts_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[password_recovery] ADD  DEFAULT ((0)) FOR [attempts]
GO
ALTER TABLE [dbo].[permissions] ADD  CONSTRAINT [DF_permissions_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[role_permissions] ADD  CONSTRAINT [DF_role_permissions_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[roles] ADD  CONSTRAINT [DF_roles_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[accounts]  WITH CHECK ADD  CONSTRAINT [FK_accounts_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[accounts] CHECK CONSTRAINT [FK_accounts_creator]
GO
ALTER TABLE [dbo].[accounts]  WITH CHECK ADD  CONSTRAINT [FK_accounts_employee] FOREIGN KEY([employee_id])
REFERENCES [dbo].[employees] ([employee_id])
GO
ALTER TABLE [dbo].[accounts] CHECK CONSTRAINT [FK_accounts_employee]
GO
ALTER TABLE [dbo].[accounts]  WITH CHECK ADD  CONSTRAINT [FK_accounts_role] FOREIGN KEY([role_id])
REFERENCES [dbo].[roles] ([role_id])
GO
ALTER TABLE [dbo].[accounts] CHECK CONSTRAINT [FK_accounts_role]
GO
ALTER TABLE [dbo].[accounts]  WITH CHECK ADD  CONSTRAINT [FK_accounts_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[accounts] CHECK CONSTRAINT [FK_accounts_updater]
GO
ALTER TABLE [dbo].[password_recovery]  WITH CHECK ADD FOREIGN KEY([account_id])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[permissions]  WITH CHECK ADD  CONSTRAINT [FK_permissions_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[permissions] CHECK CONSTRAINT [FK_permissions_creator]
GO
ALTER TABLE [dbo].[permissions]  WITH CHECK ADD  CONSTRAINT [FK_permissions_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[permissions] CHECK CONSTRAINT [FK_permissions_updater]
GO
ALTER TABLE [dbo].[role_permissions]  WITH CHECK ADD  CONSTRAINT [FK_role_permissions_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[role_permissions] CHECK CONSTRAINT [FK_role_permissions_creator]
GO
ALTER TABLE [dbo].[role_permissions]  WITH CHECK ADD  CONSTRAINT [FK_role_permissions_permission] FOREIGN KEY([permission_id])
REFERENCES [dbo].[permissions] ([permission_id])
GO
ALTER TABLE [dbo].[role_permissions] CHECK CONSTRAINT [FK_role_permissions_permission]
GO
ALTER TABLE [dbo].[role_permissions]  WITH CHECK ADD  CONSTRAINT [FK_role_permissions_role] FOREIGN KEY([role_id])
REFERENCES [dbo].[roles] ([role_id])
GO
ALTER TABLE [dbo].[role_permissions] CHECK CONSTRAINT [FK_role_permissions_role]
GO
ALTER TABLE [dbo].[role_permissions]  WITH CHECK ADD  CONSTRAINT [FK_role_permissions_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[role_permissions] CHECK CONSTRAINT [FK_role_permissions_updater]
GO
ALTER TABLE [dbo].[roles]  WITH CHECK ADD  CONSTRAINT [FK_roles_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[roles] CHECK CONSTRAINT [FK_roles_creator]
GO
ALTER TABLE [dbo].[roles]  WITH CHECK ADD  CONSTRAINT [FK_roles_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[roles] CHECK CONSTRAINT [FK_roles_updater]
GO
ALTER TABLE [dbo].[accounts]  WITH CHECK ADD  CONSTRAINT [CK_accounts_hash] CHECK  ((len([password_hash])>=(60)))
GO
ALTER TABLE [dbo].[accounts] CHECK CONSTRAINT [CK_accounts_hash]
GO
ALTER TABLE [dbo].[accounts]  WITH CHECK ADD  CONSTRAINT [CK_accounts_token_version] CHECK  (([token_version]>=(0)))
GO
ALTER TABLE [dbo].[accounts] CHECK CONSTRAINT [CK_accounts_token_version]
GO
ALTER TABLE [dbo].[accounts]  WITH CHECK ADD  CONSTRAINT [CK_accounts_username] CHECK  ((len(ltrim(rtrim([username])))>(0)))
GO
ALTER TABLE [dbo].[accounts] CHECK CONSTRAINT [CK_accounts_username]
GO
ALTER TABLE [dbo].[roles]  WITH CHECK ADD  CONSTRAINT [CK_roles_code_format] CHECK  ((len([role_code])>=(1) AND len([role_code])<=(50) AND NOT ([role_code]) collate Latin1_General_100_BIN2 like '%[^A-Z0-9_]%' AND (left([role_code],(1))) collate Latin1_General_100_BIN2 like '[A-Z]'))
GO
ALTER TABLE [dbo].[roles] CHECK CONSTRAINT [CK_roles_code_format]
GO
ALTER TABLE [dbo].[allowance_types] ADD  CONSTRAINT [UQ_allowance_types_code] UNIQUE NONCLUSTERED 
(
	[type_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[contract_types] ADD  CONSTRAINT [UQ_contract_types_code] UNIQUE NONCLUSTERED 
(
	[type_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[contracts] ADD  CONSTRAINT [UQ_contracts_number] UNIQUE NONCLUSTERED 
(
	[contract_number] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[employee_decisions] ADD  CONSTRAINT [UQ_employee_decisions_number] UNIQUE NONCLUSTERED 
(
	[decision_number] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[salary_components] ADD  CONSTRAINT [UQ_salary_components_code] UNIQUE NONCLUSTERED 
(
	[component_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[allowance_types] ADD  CONSTRAINT [DF_allowance_types_active]  DEFAULT ((1)) FOR [is_active]
GO
ALTER TABLE [dbo].[allowance_types] ADD  CONSTRAINT [DF_allowance_types_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[allowance_types] ADD  CONSTRAINT [DF_allowance_types_is_taxable_014]  DEFAULT ((1)) FOR [is_taxable]
GO
ALTER TABLE [dbo].[allowance_types] ADD  CONSTRAINT [DF_allowance_types_counts_for_social_insurance_014]  DEFAULT ((0)) FOR [counts_for_social_insurance]
GO
ALTER TABLE [dbo].[contract_salary_terms] ADD  CONSTRAINT [DF_salary_terms_basis]  DEFAULT ('THANG') FOR [pay_basis]
GO
ALTER TABLE [dbo].[contract_salary_terms] ADD  CONSTRAINT [DF_contract_salary_terms_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[contract_types] ADD  CONSTRAINT [DF_contract_types_active]  DEFAULT ((1)) FOR [is_active]
GO
ALTER TABLE [dbo].[contract_types] ADD  CONSTRAINT [DF_contract_types_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[contracts] ADD  CONSTRAINT [DF_contracts_status]  DEFAULT ('DA_KY') FOR [status]
GO
ALTER TABLE [dbo].[contracts] ADD  CONSTRAINT [DF_contracts_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[employee_allowances] ADD  CONSTRAINT [DF_allowances_basis]  DEFAULT ('THEO_KY') FOR [calculation_basis]
GO
ALTER TABLE [dbo].[employee_allowances] ADD  CONSTRAINT [DF_employee_allowances_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[employee_allowances] ADD  CONSTRAINT [DF_employee_allowances_amount_type_015]  DEFAULT ('FIXED') FOR [amount_type]
GO
ALTER TABLE [dbo].[employee_decisions] ADD  CONSTRAINT [DF_decisions_status]  DEFAULT ('CHO_DUYET') FOR [approval_status]
GO
ALTER TABLE [dbo].[employee_decisions] ADD  CONSTRAINT [DF_decisions_effect]  DEFAULT ('NONE') FOR [payroll_effect]
GO
ALTER TABLE [dbo].[employee_decisions] ADD  CONSTRAINT [DF_employee_decisions_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[salary_components] ADD  CONSTRAINT [DF_salary_components_active]  DEFAULT ((1)) FOR [is_active]
GO
ALTER TABLE [dbo].[salary_components] ADD  CONSTRAINT [DF_salary_components_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[allowance_types]  WITH CHECK ADD  CONSTRAINT [FK_allowance_types_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[allowance_types] CHECK CONSTRAINT [FK_allowance_types_creator]
GO
ALTER TABLE [dbo].[allowance_types]  WITH CHECK ADD  CONSTRAINT [FK_allowance_types_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[allowance_types] CHECK CONSTRAINT [FK_allowance_types_updater]
GO
ALTER TABLE [dbo].[contract_salary_terms]  WITH CHECK ADD  CONSTRAINT [FK_contract_salary_terms_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[contract_salary_terms] CHECK CONSTRAINT [FK_contract_salary_terms_creator]
GO
ALTER TABLE [dbo].[contract_salary_terms]  WITH CHECK ADD  CONSTRAINT [FK_contract_salary_terms_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[contract_salary_terms] CHECK CONSTRAINT [FK_contract_salary_terms_updater]
GO
ALTER TABLE [dbo].[contract_salary_terms]  WITH CHECK ADD  CONSTRAINT [FK_salary_terms_contract] FOREIGN KEY([contract_id])
REFERENCES [dbo].[contracts] ([contract_id])
GO
ALTER TABLE [dbo].[contract_salary_terms] CHECK CONSTRAINT [FK_salary_terms_contract]
GO
ALTER TABLE [dbo].[contract_types]  WITH CHECK ADD  CONSTRAINT [FK_contract_types_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[contract_types] CHECK CONSTRAINT [FK_contract_types_creator]
GO
ALTER TABLE [dbo].[contract_types]  WITH CHECK ADD  CONSTRAINT [FK_contract_types_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[contract_types] CHECK CONSTRAINT [FK_contract_types_updater]
GO
ALTER TABLE [dbo].[contracts]  WITH CHECK ADD  CONSTRAINT [FK_contracts_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[contracts] CHECK CONSTRAINT [FK_contracts_creator]
GO
ALTER TABLE [dbo].[contracts]  WITH CHECK ADD  CONSTRAINT [FK_contracts_employee] FOREIGN KEY([employee_id])
REFERENCES [dbo].[employees] ([employee_id])
GO
ALTER TABLE [dbo].[contracts] CHECK CONSTRAINT [FK_contracts_employee]
GO
ALTER TABLE [dbo].[contracts]  WITH CHECK ADD  CONSTRAINT [FK_contracts_type] FOREIGN KEY([contract_type_id])
REFERENCES [dbo].[contract_types] ([contract_type_id])
GO
ALTER TABLE [dbo].[contracts] CHECK CONSTRAINT [FK_contracts_type]
GO
ALTER TABLE [dbo].[contracts]  WITH CHECK ADD  CONSTRAINT [FK_contracts_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[contracts] CHECK CONSTRAINT [FK_contracts_updater]
GO
ALTER TABLE [dbo].[employee_allowances]  WITH CHECK ADD  CONSTRAINT [FK_allowances_employee] FOREIGN KEY([employee_id])
REFERENCES [dbo].[employees] ([employee_id])
GO
ALTER TABLE [dbo].[employee_allowances] CHECK CONSTRAINT [FK_allowances_employee]
GO
ALTER TABLE [dbo].[employee_allowances]  WITH CHECK ADD  CONSTRAINT [FK_allowances_type] FOREIGN KEY([allowance_type_id])
REFERENCES [dbo].[allowance_types] ([allowance_type_id])
GO
ALTER TABLE [dbo].[employee_allowances] CHECK CONSTRAINT [FK_allowances_type]
GO
ALTER TABLE [dbo].[employee_allowances]  WITH CHECK ADD  CONSTRAINT [FK_employee_allowances_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_allowances] CHECK CONSTRAINT [FK_employee_allowances_creator]
GO
ALTER TABLE [dbo].[employee_allowances]  WITH CHECK ADD  CONSTRAINT [FK_employee_allowances_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_allowances] CHECK CONSTRAINT [FK_employee_allowances_updater]
GO
ALTER TABLE [dbo].[employee_decisions]  WITH CHECK ADD  CONSTRAINT [FK_decisions_approver] FOREIGN KEY([approved_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_decisions] CHECK CONSTRAINT [FK_decisions_approver]
GO
ALTER TABLE [dbo].[employee_decisions]  WITH CHECK ADD  CONSTRAINT [FK_decisions_employee] FOREIGN KEY([employee_id])
REFERENCES [dbo].[employees] ([employee_id])
GO
ALTER TABLE [dbo].[employee_decisions] CHECK CONSTRAINT [FK_decisions_employee]
GO
ALTER TABLE [dbo].[employee_decisions]  WITH CHECK ADD  CONSTRAINT [FK_decisions_issuer] FOREIGN KEY([issued_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_decisions] CHECK CONSTRAINT [FK_decisions_issuer]
GO
ALTER TABLE [dbo].[employee_decisions]  WITH CHECK ADD  CONSTRAINT [FK_employee_decisions_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_decisions] CHECK CONSTRAINT [FK_employee_decisions_creator]
GO
ALTER TABLE [dbo].[employee_decisions]  WITH CHECK ADD  CONSTRAINT [FK_employee_decisions_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[employee_decisions] CHECK CONSTRAINT [FK_employee_decisions_updater]
GO
ALTER TABLE [dbo].[salary_components]  WITH CHECK ADD  CONSTRAINT [FK_salary_components_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[salary_components] CHECK CONSTRAINT [FK_salary_components_creator]
GO
ALTER TABLE [dbo].[salary_components]  WITH CHECK ADD  CONSTRAINT [FK_salary_components_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[salary_components] CHECK CONSTRAINT [FK_salary_components_updater]
GO
ALTER TABLE [dbo].[contract_salary_terms]  WITH CHECK ADD  CONSTRAINT [CK_salary_reference_012] CHECK  (([official_reference_salary] IS NULL AND [official_reference_pay_basis] IS NULL OR [official_reference_salary] IS NOT NULL AND [official_reference_salary]>(0) AND [official_reference_pay_basis] IS NOT NULL AND ([official_reference_pay_basis]='GIO' OR [official_reference_pay_basis]='NGAY' OR [official_reference_pay_basis]='THANG')))
GO
ALTER TABLE [dbo].[contract_salary_terms] CHECK CONSTRAINT [CK_salary_reference_012]
GO
ALTER TABLE [dbo].[contract_salary_terms]  WITH CHECK ADD  CONSTRAINT [CK_salary_terms_amount] CHECK  (([base_salary]>=(0)))
GO
ALTER TABLE [dbo].[contract_salary_terms] CHECK CONSTRAINT [CK_salary_terms_amount]
GO
ALTER TABLE [dbo].[contract_salary_terms]  WITH CHECK ADD  CONSTRAINT [CK_salary_terms_basis] CHECK  (([pay_basis]='GIO' OR [pay_basis]='NGAY' OR [pay_basis]='THANG'))
GO
ALTER TABLE [dbo].[contract_salary_terms] CHECK CONSTRAINT [CK_salary_terms_basis]
GO
ALTER TABLE [dbo].[contract_salary_terms]  WITH CHECK ADD  CONSTRAINT [CK_salary_terms_dates] CHECK  (([effective_to] IS NULL OR [effective_to]>[effective_from]))
GO
ALTER TABLE [dbo].[contract_salary_terms] CHECK CONSTRAINT [CK_salary_terms_dates]
GO
ALTER TABLE [dbo].[contract_types]  WITH CHECK ADD  CONSTRAINT [CK_contract_types_term_012] CHECK  (([term_kind]='XAC_DINH' AND [max_months] IS NOT NULL AND ([max_months]>=(1) AND [max_months]<=(36)) OR ([term_kind]='CTV_CAN_RA_SOAT' OR [term_kind]='THU_VIEC' OR [term_kind]='KHONG_XAC_DINH') AND [max_months] IS NULL))
GO
ALTER TABLE [dbo].[contract_types] CHECK CONSTRAINT [CK_contract_types_term_012]
GO
ALTER TABLE [dbo].[contracts]  WITH CHECK ADD  CONSTRAINT [CK_contracts_dates] CHECK  (([end_date] IS NULL OR [end_date]>=[start_date]))
GO
ALTER TABLE [dbo].[contracts] CHECK CONSTRAINT [CK_contracts_dates]
GO
ALTER TABLE [dbo].[contracts]  WITH CHECK ADD  CONSTRAINT [CK_contracts_probation_012] CHECK  (([probation_group] IS NULL OR ([probation_group]='CONG_VIEC_KHAC' OR [probation_group]='TRUNG_CAP_NGHIEP_VU' OR [probation_group]='CAO_DANG_TRO_LEN' OR [probation_group]='QUAN_LY_DN')))
GO
ALTER TABLE [dbo].[contracts] CHECK CONSTRAINT [CK_contracts_probation_012]
GO
ALTER TABLE [dbo].[contracts]  WITH CHECK ADD  CONSTRAINT [CK_contracts_status] CHECK  (([status]='DA_KET_THUC' OR [status]='DA_KY'))
GO
ALTER TABLE [dbo].[contracts] CHECK CONSTRAINT [CK_contracts_status]
GO
ALTER TABLE [dbo].[contracts]  WITH CHECK ADD  CONSTRAINT [CK_contracts_termination_reason_013] CHECK  (([termination_reason_type] IS NULL OR ([termination_reason_type]='SA_THAI' OR [termination_reason_type]='NSDLD_DON_PHUONG' OR [termination_reason_type]='NLD_DON_PHUONG' OR [termination_reason_type]='THOA_THUAN' OR [termination_reason_type]='HET_HAN')))
GO
ALTER TABLE [dbo].[contracts] CHECK CONSTRAINT [CK_contracts_termination_reason_013]
GO
ALTER TABLE [dbo].[employee_allowances]  WITH CHECK ADD  CONSTRAINT [CK_allowances_amount] CHECK  (([amount]>=(0)))
GO
ALTER TABLE [dbo].[employee_allowances] CHECK CONSTRAINT [CK_allowances_amount]
GO
ALTER TABLE [dbo].[employee_allowances]  WITH CHECK ADD  CONSTRAINT [CK_allowances_basis] CHECK  (([calculation_basis]='THEO_GIO' OR [calculation_basis]='THEO_NGAY_CONG' OR [calculation_basis]='THEO_KY'))
GO
ALTER TABLE [dbo].[employee_allowances] CHECK CONSTRAINT [CK_allowances_basis]
GO
ALTER TABLE [dbo].[employee_allowances]  WITH CHECK ADD  CONSTRAINT [CK_allowances_dates] CHECK  (([effective_to] IS NULL OR [effective_to]>[effective_from]))
GO
ALTER TABLE [dbo].[employee_allowances] CHECK CONSTRAINT [CK_allowances_dates]
GO
ALTER TABLE [dbo].[employee_allowances]  WITH CHECK ADD  CONSTRAINT [CK_employee_allowances_amount_type_015] CHECK  (([amount_type]='FIXED' OR [amount_type]='PERCENT_BASE_SALARY' AND ([amount]>=(0) AND [amount]<=(100))))
GO
ALTER TABLE [dbo].[employee_allowances] CHECK CONSTRAINT [CK_employee_allowances_amount_type_015]
GO
ALTER TABLE [dbo].[employee_decisions]  WITH CHECK ADD  CONSTRAINT [CK_decisions_approval] CHECK  (([approval_status]='CHO_DUYET' AND [approved_by] IS NULL AND [approved_at] IS NULL OR ([approval_status]='TU_CHOI' OR [approval_status]='DA_DUYET') AND [approved_by] IS NOT NULL AND [approved_at] IS NOT NULL))
GO
ALTER TABLE [dbo].[employee_decisions] CHECK CONSTRAINT [CK_decisions_approval]
GO
ALTER TABLE [dbo].[employee_decisions]  WITH CHECK ADD  CONSTRAINT [CK_decisions_business_011] CHECK  (([decision_type]='KY_LUAT' AND [payroll_effect]='NONE' AND [amount] IS NULL AND [apply_date] IS NULL AND [discipline_form] IS NOT NULL AND ([discipline_form]='SA_THAI' OR [discipline_form]='CACH_CHUC' OR [discipline_form]='KEO_DAI_NANG_LUONG' OR [discipline_form]='KHIEN_TRACH') AND [violation_date] IS NOT NULL AND ([discipline_form]='KEO_DAI_NANG_LUONG' AND [salary_increase_delay_months] IS NOT NULL AND ([salary_increase_delay_months]>=(1) AND [salary_increase_delay_months]<=(6)) OR [discipline_form]<>'KEO_DAI_NANG_LUONG' AND [salary_increase_delay_months] IS NULL) OR [decision_type]='KHEN_THUONG' AND [discipline_form] IS NULL AND [violation_date] IS NULL AND [salary_increase_delay_months] IS NULL AND ([payroll_effect]='NONE' AND [amount] IS NULL AND [apply_date] IS NULL OR [payroll_effect]='ADD' AND [amount] IS NOT NULL AND [amount]>(0) AND [apply_date] IS NOT NULL) OR [decision_type]='BOI_THUONG' AND [discipline_form] IS NULL AND [violation_date] IS NULL AND [salary_increase_delay_months] IS NULL AND [payroll_effect]='DEDUCT' AND [amount] IS NOT NULL AND [amount]>(0) AND [apply_date] IS NOT NULL))
GO
ALTER TABLE [dbo].[employee_decisions] CHECK CONSTRAINT [CK_decisions_business_011]
GO
ALTER TABLE [dbo].[employee_decisions]  WITH CHECK ADD  CONSTRAINT [CK_decisions_content] CHECK  ((len(ltrim(rtrim([content])))>(0)))
GO
ALTER TABLE [dbo].[employee_decisions] CHECK CONSTRAINT [CK_decisions_content]
GO
ALTER TABLE [dbo].[employee_decisions]  WITH CHECK ADD  CONSTRAINT [CK_decisions_payroll] CHECK  (([payroll_effect]='NONE' AND [amount] IS NULL AND [apply_date] IS NULL OR ([payroll_effect]='DEDUCT' OR [payroll_effect]='ADD') AND [amount] IS NOT NULL AND [amount]>(0) AND [apply_date] IS NOT NULL))
GO
ALTER TABLE [dbo].[employee_decisions] CHECK CONSTRAINT [CK_decisions_payroll]
GO
ALTER TABLE [dbo].[employee_decisions]  WITH CHECK ADD  CONSTRAINT [CK_decisions_type] CHECK  (([decision_type]='BOI_THUONG' OR [decision_type]='KY_LUAT' OR [decision_type]='KHEN_THUONG'))
GO
ALTER TABLE [dbo].[employee_decisions] CHECK CONSTRAINT [CK_decisions_type]
GO
ALTER TABLE [dbo].[salary_components]  WITH CHECK ADD  CONSTRAINT [CK_salary_components_direction] CHECK  (([direction]='DEDUCT' OR [direction]='ADD'))
GO
ALTER TABLE [dbo].[salary_components] CHECK CONSTRAINT [CK_salary_components_direction]
GO
CREATE NONCLUSTERED INDEX [IX_attendance_actual_breaks_current] ON [dbo].[attendance_actual_breaks]
(
	[attendance_id] ASC,
	[superseded_at] ASC,
	[break_start] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[attendance_portal_requests] ADD UNIQUE NONCLUSTERED 
(
	[request_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_attendance_portal_requests_attendance] ON [dbo].[attendance_portal_requests]
(
	[attendance_id] ASC,
	[status] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE UNIQUE NONCLUSTERED INDEX [UX_attendance_portal_requests_open] ON [dbo].[attendance_portal_requests]
(
	[schedule_id] ASC
)
WHERE ([status] IN ('CHO_XU_LY', 'DANG_XU_LY'))
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_attendance_employee_date] ON [dbo].[attendance_records]
(
	[employee_id] ASC,
	[work_date] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE UNIQUE NONCLUSTERED INDEX [UX_attendance_schedule] ON [dbo].[attendance_records]
(
	[schedule_id] ASC
)
WHERE ([schedule_id] IS NOT NULL)
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE UNIQUE NONCLUSTERED INDEX [UX_ot_supplement_open] ON [dbo].[ot_supplement_requests]
(
	[overtime_id] ASC
)
WHERE ([status]='CHO_XU_LY')
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[overtime_requests] ADD  CONSTRAINT [UQ_overtime_requests_code] UNIQUE NONCLUSTERED 
(
	[request_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_overtime_employee_time] ON [dbo].[overtime_requests]
(
	[employee_id] ASC,
	[requested_start] ASC,
	[requested_end] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_schedule_change_schedule] ON [dbo].[schedule_change_request_details]
(
	[schedule_id] ASC,
	[request_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_schedule_break_current] ON [dbo].[work_schedule_breaks]
(
	[schedule_id] ASC,
	[superseded_at] ASC,
	[break_start] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[work_schedules] ADD  CONSTRAINT [UQ_schedules_owner] UNIQUE NONCLUSTERED 
(
	[schedule_id] ASC,
	[employee_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_schedules_employee_time] ON [dbo].[work_schedules]
(
	[employee_id] ASC,
	[planned_start] ASC,
	[planned_end] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_work_shift_breaks_current] ON [dbo].[work_shift_breaks]
(
	[shift_id] ASC,
	[superseded_at] ASC,
	[break_start_offset_minutes] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[work_shifts] ADD  CONSTRAINT [UQ_work_shifts_code] UNIQUE NONCLUSTERED 
(
	[shift_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[attendance_actual_breaks] ADD  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[attendance_portal_requests] ADD  DEFAULT ('CHO_XU_LY') FOR [status]
GO
ALTER TABLE [dbo].[attendance_portal_requests] ADD  DEFAULT (sysutcdatetime()) FOR [submitted_at]
GO
ALTER TABLE [dbo].[attendance_records] ADD  CONSTRAINT [DF_attendance_break]  DEFAULT ((0)) FOR [actual_break_minutes]
GO
ALTER TABLE [dbo].[attendance_records] ADD  CONSTRAINT [DF_attendance_verification]  DEFAULT ('CHUA_XAC_NHAN') FOR [verification_status]
GO
ALTER TABLE [dbo].[attendance_records] ADD  CONSTRAINT [DF_attendance_records_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[ot_supplement_requests] ADD  DEFAULT ('CHO_XU_LY') FOR [status]
GO
ALTER TABLE [dbo].[ot_supplement_requests] ADD  DEFAULT (sysutcdatetime()) FOR [submitted_at]
GO
ALTER TABLE [dbo].[overtime_breaks] ADD  DEFAULT ((1)) FOR [is_active]
GO
ALTER TABLE [dbo].[overtime_breaks] ADD  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[overtime_overflow_intervals] ADD  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[overtime_request_details] ADD  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[overtime_requested_breaks] ADD  DEFAULT ((1)) FOR [is_active]
GO
ALTER TABLE [dbo].[overtime_requested_breaks] ADD  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[overtime_requests] ADD  CONSTRAINT [DF_overtime_submitted]  DEFAULT (sysutcdatetime()) FOR [submitted_at]
GO
ALTER TABLE [dbo].[overtime_requests] ADD  CONSTRAINT [DF_overtime_status]  DEFAULT ('CHO_DUYET') FOR [status]
GO
ALTER TABLE [dbo].[overtime_requests] ADD  CONSTRAINT [DF_overtime_break]  DEFAULT ((0)) FOR [actual_break_minutes]
GO
ALTER TABLE [dbo].[overtime_requests] ADD  CONSTRAINT [DF_overtime_requests_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[overtime_requests] ADD  CONSTRAINT [DF_ot_requested_break_status]  DEFAULT ('LEGACY_UNVERIFIED') FOR [requested_break_status]
GO
ALTER TABLE [dbo].[work_schedules] ADD  CONSTRAINT [DF_schedules_break]  DEFAULT ((0)) FOR [planned_break_minutes]
GO
ALTER TABLE [dbo].[work_schedules] ADD  CONSTRAINT [DF_work_schedules_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[work_schedules] ADD  CONSTRAINT [DF_schedule_break_detail]  DEFAULT ('LEGACY_UNVERIFIED') FOR [break_detail_status]
GO
ALTER TABLE [dbo].[work_shifts] ADD  CONSTRAINT [DF_work_shifts_offset]  DEFAULT ((0)) FOR [end_day_offset]
GO
ALTER TABLE [dbo].[work_shifts] ADD  CONSTRAINT [DF_work_shifts_break]  DEFAULT ((0)) FOR [break_minutes]
GO
ALTER TABLE [dbo].[work_shifts] ADD  CONSTRAINT [DF_work_shifts_active]  DEFAULT ((1)) FOR [is_active]
GO
ALTER TABLE [dbo].[work_shifts] ADD  CONSTRAINT [DF_work_shifts_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[attendance_actual_breaks]  WITH CHECK ADD FOREIGN KEY([attendance_id])
REFERENCES [dbo].[attendance_records] ([attendance_id])
GO
ALTER TABLE [dbo].[attendance_actual_breaks]  WITH CHECK ADD FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[attendance_portal_requests]  WITH CHECK ADD FOREIGN KEY([attendance_id])
REFERENCES [dbo].[attendance_records] ([attendance_id])
GO
ALTER TABLE [dbo].[attendance_portal_requests]  WITH CHECK ADD FOREIGN KEY([processed_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[attendance_portal_requests]  WITH CHECK ADD FOREIGN KEY([received_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[attendance_portal_requests]  WITH CHECK ADD FOREIGN KEY([submitted_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[attendance_portal_requests]  WITH CHECK ADD  CONSTRAINT [FK_attendance_request_schedule] FOREIGN KEY([schedule_id])
REFERENCES [dbo].[work_schedules] ([schedule_id])
GO
ALTER TABLE [dbo].[attendance_portal_requests] CHECK CONSTRAINT [FK_attendance_request_schedule]
GO
ALTER TABLE [dbo].[attendance_records]  WITH CHECK ADD FOREIGN KEY([check_in_reviewed_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[attendance_records]  WITH CHECK ADD FOREIGN KEY([check_out_reviewed_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[attendance_records]  WITH CHECK ADD FOREIGN KEY([invalidated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[attendance_records]  WITH CHECK ADD  CONSTRAINT [FK_attendance_confirmer] FOREIGN KEY([confirmed_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[attendance_records] CHECK CONSTRAINT [FK_attendance_confirmer]
GO
ALTER TABLE [dbo].[attendance_records]  WITH CHECK ADD  CONSTRAINT [FK_attendance_employee] FOREIGN KEY([employee_id])
REFERENCES [dbo].[employees] ([employee_id])
GO
ALTER TABLE [dbo].[attendance_records] CHECK CONSTRAINT [FK_attendance_employee]
GO
ALTER TABLE [dbo].[attendance_records]  WITH CHECK ADD  CONSTRAINT [FK_attendance_records_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[attendance_records] CHECK CONSTRAINT [FK_attendance_records_creator]
GO
ALTER TABLE [dbo].[attendance_records]  WITH CHECK ADD  CONSTRAINT [FK_attendance_records_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[attendance_records] CHECK CONSTRAINT [FK_attendance_records_updater]
GO
ALTER TABLE [dbo].[attendance_records]  WITH CHECK ADD  CONSTRAINT [FK_attendance_schedule_owner] FOREIGN KEY([schedule_id], [employee_id])
REFERENCES [dbo].[work_schedules] ([schedule_id], [employee_id])
GO
ALTER TABLE [dbo].[attendance_records] CHECK CONSTRAINT [FK_attendance_schedule_owner]
GO
ALTER TABLE [dbo].[ot_supplement_breaks]  WITH CHECK ADD FOREIGN KEY([request_id])
REFERENCES [dbo].[ot_supplement_requests] ([request_id])
GO
ALTER TABLE [dbo].[ot_supplement_requests]  WITH CHECK ADD FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[ot_supplement_requests]  WITH CHECK ADD FOREIGN KEY([employee_id])
REFERENCES [dbo].[employees] ([employee_id])
GO
ALTER TABLE [dbo].[ot_supplement_requests]  WITH CHECK ADD FOREIGN KEY([overtime_id])
REFERENCES [dbo].[overtime_requests] ([overtime_id])
GO
ALTER TABLE [dbo].[ot_supplement_requests]  WITH CHECK ADD FOREIGN KEY([processed_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[overtime_breaks]  WITH CHECK ADD FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[overtime_breaks]  WITH CHECK ADD FOREIGN KEY([overtime_id])
REFERENCES [dbo].[overtime_requests] ([overtime_id])
GO
ALTER TABLE [dbo].[overtime_overflow_intervals]  WITH CHECK ADD FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[overtime_overflow_intervals]  WITH CHECK ADD FOREIGN KEY([request_id])
REFERENCES [dbo].[overtime_request_details] ([request_id])
GO
ALTER TABLE [dbo].[overtime_request_details]  WITH CHECK ADD FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[overtime_request_details]  WITH CHECK ADD FOREIGN KEY([overtime_id])
REFERENCES [dbo].[overtime_requests] ([overtime_id])
GO
ALTER TABLE [dbo].[overtime_request_details]  WITH CHECK ADD FOREIGN KEY([request_id])
REFERENCES [dbo].[employee_requests] ([request_id])
GO
ALTER TABLE [dbo].[overtime_requested_breaks]  WITH CHECK ADD FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[overtime_requested_breaks]  WITH CHECK ADD FOREIGN KEY([overtime_id])
REFERENCES [dbo].[overtime_requests] ([overtime_id])
GO
ALTER TABLE [dbo].[overtime_requests]  WITH CHECK ADD  CONSTRAINT [FK_overtime_confirmer] FOREIGN KEY([confirmed_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[overtime_requests] CHECK CONSTRAINT [FK_overtime_confirmer]
GO
ALTER TABLE [dbo].[overtime_requests]  WITH CHECK ADD  CONSTRAINT [FK_overtime_employee] FOREIGN KEY([employee_id])
REFERENCES [dbo].[employees] ([employee_id])
GO
ALTER TABLE [dbo].[overtime_requests] CHECK CONSTRAINT [FK_overtime_employee]
GO
ALTER TABLE [dbo].[overtime_requests]  WITH CHECK ADD  CONSTRAINT [FK_overtime_processor] FOREIGN KEY([processed_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[overtime_requests] CHECK CONSTRAINT [FK_overtime_processor]
GO
ALTER TABLE [dbo].[overtime_requests]  WITH CHECK ADD  CONSTRAINT [FK_overtime_requests_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[overtime_requests] CHECK CONSTRAINT [FK_overtime_requests_creator]
GO
ALTER TABLE [dbo].[overtime_requests]  WITH CHECK ADD  CONSTRAINT [FK_overtime_requests_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[overtime_requests] CHECK CONSTRAINT [FK_overtime_requests_updater]
GO
ALTER TABLE [dbo].[schedule_change_request_breaks]  WITH CHECK ADD FOREIGN KEY([request_id])
REFERENCES [dbo].[schedule_change_request_details] ([request_id])
GO
ALTER TABLE [dbo].[schedule_change_request_details]  WITH CHECK ADD FOREIGN KEY([proposed_shift_id])
REFERENCES [dbo].[work_shifts] ([shift_id])
GO
ALTER TABLE [dbo].[schedule_change_request_details]  WITH CHECK ADD FOREIGN KEY([request_id])
REFERENCES [dbo].[employee_requests] ([request_id])
GO
ALTER TABLE [dbo].[schedule_change_request_details]  WITH CHECK ADD FOREIGN KEY([schedule_id])
REFERENCES [dbo].[work_schedules] ([schedule_id])
GO
ALTER TABLE [dbo].[work_schedule_breaks]  WITH CHECK ADD FOREIGN KEY([schedule_id])
REFERENCES [dbo].[work_schedules] ([schedule_id])
GO
ALTER TABLE [dbo].[work_schedules]  WITH CHECK ADD FOREIGN KEY([cancelled_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[work_schedules]  WITH CHECK ADD  CONSTRAINT [FK_schedules_employee] FOREIGN KEY([employee_id])
REFERENCES [dbo].[employees] ([employee_id])
GO
ALTER TABLE [dbo].[work_schedules] CHECK CONSTRAINT [FK_schedules_employee]
GO
ALTER TABLE [dbo].[work_schedules]  WITH CHECK ADD  CONSTRAINT [FK_schedules_shift] FOREIGN KEY([shift_id])
REFERENCES [dbo].[work_shifts] ([shift_id])
GO
ALTER TABLE [dbo].[work_schedules] CHECK CONSTRAINT [FK_schedules_shift]
GO
ALTER TABLE [dbo].[work_schedules]  WITH CHECK ADD  CONSTRAINT [FK_work_schedules_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[work_schedules] CHECK CONSTRAINT [FK_work_schedules_creator]
GO
ALTER TABLE [dbo].[work_schedules]  WITH CHECK ADD  CONSTRAINT [FK_work_schedules_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[work_schedules] CHECK CONSTRAINT [FK_work_schedules_updater]
GO
ALTER TABLE [dbo].[work_shift_breaks]  WITH CHECK ADD  CONSTRAINT [FK_work_shift_breaks_shift] FOREIGN KEY([shift_id])
REFERENCES [dbo].[work_shifts] ([shift_id])
GO
ALTER TABLE [dbo].[work_shift_breaks] CHECK CONSTRAINT [FK_work_shift_breaks_shift]
GO
ALTER TABLE [dbo].[work_shifts]  WITH CHECK ADD  CONSTRAINT [FK_work_shifts_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[work_shifts] CHECK CONSTRAINT [FK_work_shifts_creator]
GO
ALTER TABLE [dbo].[work_shifts]  WITH CHECK ADD  CONSTRAINT [FK_work_shifts_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[work_shifts] CHECK CONSTRAINT [FK_work_shifts_updater]
GO
ALTER TABLE [dbo].[attendance_actual_breaks]  WITH CHECK ADD CHECK  (([source_break_minutes]>=(0)))
GO
ALTER TABLE [dbo].[attendance_actual_breaks]  WITH CHECK ADD CHECK  (([break_end]>[break_start] AND [source_end]>[source_start] AND [break_start]>=[source_start] AND [break_end]<=[source_end]))
GO
ALTER TABLE [dbo].[attendance_portal_requests]  WITH CHECK ADD CHECK  (([proposed_break_minutes]>=(0)))
GO
ALTER TABLE [dbo].[attendance_portal_requests]  WITH CHECK ADD CHECK  (([proposed_result]='VANG' OR [proposed_result]='DI_LAM'))
GO
ALTER TABLE [dbo].[attendance_portal_requests]  WITH CHECK ADD CHECK  (([status]='TU_CHOI' OR [status]='DA_CHAP_NHAN' OR [status]='DANG_XU_LY' OR [status]='CHO_XU_LY'))
GO
ALTER TABLE [dbo].[attendance_portal_requests]  WITH CHECK ADD CHECK  (([status]='CHO_XU_LY' AND [received_by] IS NULL AND [processed_by] IS NULL OR [status]='DANG_XU_LY' AND [received_by] IS NOT NULL AND [processed_by] IS NULL OR ([status]='TU_CHOI' OR [status]='DA_CHAP_NHAN') AND [received_by] IS NOT NULL AND [processed_by] IS NOT NULL))
GO
ALTER TABLE [dbo].[attendance_portal_requests]  WITH CHECK ADD CHECK  (([status]<>'TU_CHOI' OR len(ltrim(rtrim([decision_reason])))>(0)))
GO
ALTER TABLE [dbo].[attendance_portal_requests]  WITH CHECK ADD  CONSTRAINT [CK_attendance_request_original] CHECK  (([request_type]='MISSING_ATTENDANCE' OR [attendance_id] IS NOT NULL AND [original_version] IS NOT NULL))
GO
ALTER TABLE [dbo].[attendance_portal_requests] CHECK CONSTRAINT [CK_attendance_request_original]
GO
ALTER TABLE [dbo].[attendance_portal_requests]  WITH CHECK ADD  CONSTRAINT [CK_attendance_request_type] CHECK  (([request_type]='UNCONFIRMED_CORRECTION' OR [request_type]='MISSING_ATTENDANCE' OR [request_type]='CONFIRMED_CORRECTION' OR [request_type]='MISSING_CHECKOUT'))
GO
ALTER TABLE [dbo].[attendance_portal_requests] CHECK CONSTRAINT [CK_attendance_request_type]
GO
ALTER TABLE [dbo].[attendance_records]  WITH CHECK ADD  CONSTRAINT [CK_attendance_result] CHECK  (([attendance_result]='VANG' OR [attendance_result]='DI_LAM'))
GO
ALTER TABLE [dbo].[attendance_records] CHECK CONSTRAINT [CK_attendance_result]
GO
ALTER TABLE [dbo].[attendance_records]  WITH CHECK ADD  CONSTRAINT [CK_attendance_time] CHECK  (([actual_break_minutes]>=(0) AND ([actual_end] IS NULL OR [actual_start] IS NOT NULL AND [actual_end]>[actual_start] AND [actual_break_minutes]<=datediff_big(minute,[actual_start],[actual_end])) AND ([attendance_result]='VANG' AND [actual_start] IS NULL AND [actual_end] IS NULL AND [actual_break_minutes]=(0) OR [attendance_result]='DI_LAM' AND [actual_start] IS NOT NULL)))
GO
ALTER TABLE [dbo].[attendance_records] CHECK CONSTRAINT [CK_attendance_time]
GO
ALTER TABLE [dbo].[attendance_records]  WITH CHECK ADD  CONSTRAINT [CK_attendance_verification] CHECK  (([verification_status]='CHUA_XAC_NHAN' AND [confirmed_by] IS NULL AND [confirmed_at] IS NULL OR [verification_status]='DA_XAC_NHAN' AND [confirmed_by] IS NOT NULL AND [confirmed_at] IS NOT NULL AND ([attendance_result]='VANG' OR [actual_end] IS NOT NULL)))
GO
ALTER TABLE [dbo].[attendance_records] CHECK CONSTRAINT [CK_attendance_verification]
GO
ALTER TABLE [dbo].[ot_supplement_breaks]  WITH CHECK ADD CHECK  (([break_end]>[break_start]))
GO
ALTER TABLE [dbo].[ot_supplement_requests]  WITH CHECK ADD  CONSTRAINT [CK_ot_supplement_decision] CHECK  (([status]='CHO_XU_LY' AND [processed_by] IS NULL AND [processed_at] IS NULL OR [status]<>'CHO_XU_LY' AND [processed_by] IS NOT NULL AND [processed_at] IS NOT NULL AND [processed_by]<>[created_by]))
GO
ALTER TABLE [dbo].[ot_supplement_requests] CHECK CONSTRAINT [CK_ot_supplement_decision]
GO
ALTER TABLE [dbo].[ot_supplement_requests]  WITH CHECK ADD  CONSTRAINT [CK_ot_supplement_range] CHECK  (([proposed_end]>[proposed_start]))
GO
ALTER TABLE [dbo].[ot_supplement_requests] CHECK CONSTRAINT [CK_ot_supplement_range]
GO
ALTER TABLE [dbo].[ot_supplement_requests]  WITH CHECK ADD  CONSTRAINT [CK_ot_supplement_status] CHECK  (([status]='TU_CHOI' OR [status]='DA_CHAP_NHAN' OR [status]='CHO_XU_LY'))
GO
ALTER TABLE [dbo].[ot_supplement_requests] CHECK CONSTRAINT [CK_ot_supplement_status]
GO
ALTER TABLE [dbo].[overtime_breaks]  WITH CHECK ADD CHECK  (([break_end]>[break_start]))
GO
ALTER TABLE [dbo].[overtime_overflow_intervals]  WITH CHECK ADD CHECK  (([requested_end]>[requested_start]))
GO
ALTER TABLE [dbo].[overtime_overflow_intervals]  WITH CHECK ADD CHECK  (([approved_start] IS NULL AND [approved_end] IS NULL OR [approved_start]>=[requested_start] AND [approved_end]<=[requested_end] AND [approved_end]>[approved_start]))
GO
ALTER TABLE [dbo].[overtime_requested_breaks]  WITH CHECK ADD CHECK  (([break_end]>[break_start]))
GO
ALTER TABLE [dbo].[overtime_requests]  WITH CHECK ADD  CONSTRAINT [CK_overtime_actual] CHECK  (([actual_break_minutes]>=(0) AND ([actual_start] IS NULL AND [actual_end] IS NULL AND [actual_break_minutes]=(0) OR [actual_start] IS NOT NULL AND [actual_end] IS NOT NULL AND [actual_end]>[actual_start] AND [actual_break_minutes]<=datediff_big(minute,[actual_start],[actual_end])) OR [status]='DA_DUYET' AND [actual_start] IS NOT NULL AND [actual_end] IS NULL AND [actual_break_minutes]=(0) AND [confirmed_by] IS NULL AND [confirmed_at] IS NULL))
GO
ALTER TABLE [dbo].[overtime_requests] CHECK CONSTRAINT [CK_overtime_actual]
GO
ALTER TABLE [dbo].[overtime_requests]  WITH CHECK ADD  CONSTRAINT [CK_overtime_confirmation] CHECK  (([confirmed_by] IS NULL AND [confirmed_at] IS NULL OR [confirmed_by] IS NOT NULL AND [confirmed_at] IS NOT NULL AND [status]='DA_DUYET' AND [actual_start] IS NOT NULL AND [actual_end] IS NOT NULL))
GO
ALTER TABLE [dbo].[overtime_requests] CHECK CONSTRAINT [CK_overtime_confirmation]
GO
ALTER TABLE [dbo].[overtime_requests]  WITH CHECK ADD  CONSTRAINT [CK_overtime_processed_time] CHECK  (([processed_at] IS NULL OR [processed_at]>=[submitted_at]))
GO
ALTER TABLE [dbo].[overtime_requests] CHECK CONSTRAINT [CK_overtime_processed_time]
GO
ALTER TABLE [dbo].[overtime_requests]  WITH CHECK ADD  CONSTRAINT [CK_overtime_processing] CHECK  (([status]='CHO_DUYET' AND [processed_by] IS NULL AND [processed_at] IS NULL AND [rejection_reason] IS NULL OR [status]='DA_DUYET' AND [processed_by] IS NOT NULL AND [processed_at] IS NOT NULL AND [rejection_reason] IS NULL OR [status]='TU_CHOI' AND [processed_by] IS NOT NULL AND [processed_at] IS NOT NULL AND [rejection_reason] IS NOT NULL AND len(ltrim(rtrim([rejection_reason])))>(0) OR [status]='DA_HUY' AND [confirmed_by] IS NULL AND [confirmed_at] IS NULL AND [actual_start] IS NULL AND [actual_end] IS NULL))
GO
ALTER TABLE [dbo].[overtime_requests] CHECK CONSTRAINT [CK_overtime_processing]
GO
ALTER TABLE [dbo].[overtime_requests]  WITH CHECK ADD  CONSTRAINT [CK_overtime_requested] CHECK  (([requested_end]>[requested_start] AND len(ltrim(rtrim([reason])))>(0)))
GO
ALTER TABLE [dbo].[overtime_requests] CHECK CONSTRAINT [CK_overtime_requested]
GO
ALTER TABLE [dbo].[schedule_change_request_breaks]  WITH CHECK ADD  CONSTRAINT [CK_schedule_change_break] CHECK  (([break_end]>[break_start]))
GO
ALTER TABLE [dbo].[schedule_change_request_breaks] CHECK CONSTRAINT [CK_schedule_change_break]
GO
ALTER TABLE [dbo].[schedule_change_request_details]  WITH CHECK ADD  CONSTRAINT [CK_schedule_change_time] CHECK  (([original_end]>[original_start] AND [proposed_end]>[proposed_start]))
GO
ALTER TABLE [dbo].[schedule_change_request_details] CHECK CONSTRAINT [CK_schedule_change_time]
GO
ALTER TABLE [dbo].[work_schedule_breaks]  WITH CHECK ADD  CONSTRAINT [CK_schedule_break_window] CHECK  (([break_end]>[break_start]))
GO
ALTER TABLE [dbo].[work_schedule_breaks] CHECK CONSTRAINT [CK_schedule_break_window]
GO
ALTER TABLE [dbo].[work_schedules]  WITH CHECK ADD  CONSTRAINT [CK_schedules_time] CHECK  (([planned_end]>[planned_start] AND [work_date]=CONVERT([date],[planned_start]) AND [planned_break_minutes]>=(0) AND [planned_break_minutes]<=datediff_big(minute,[planned_start],[planned_end])))
GO
ALTER TABLE [dbo].[work_schedules] CHECK CONSTRAINT [CK_schedules_time]
GO
ALTER TABLE [dbo].[work_shift_breaks]  WITH CHECK ADD  CONSTRAINT [CK_work_shift_breaks_offsets] CHECK  (([break_start_offset_minutes]>=(0) AND [break_end_offset_minutes]>[break_start_offset_minutes] AND [break_end_offset_minutes]<=(1440)))
GO
ALTER TABLE [dbo].[work_shift_breaks] CHECK CONSTRAINT [CK_work_shift_breaks_offsets]
GO
ALTER TABLE [dbo].[work_shifts]  WITH CHECK ADD  CONSTRAINT [CK_work_shifts_time] CHECK  ((([end_day_offset]=(1) OR [end_day_offset]=(0)) AND [break_minutes]>=(0) AND (datediff(minute,[start_time],[end_time])+(1440)*[end_day_offset])>(0) AND [break_minutes]<=(datediff(minute,[start_time],[end_time])+(1440)*[end_day_offset])))
GO
ALTER TABLE [dbo].[work_shifts] CHECK CONSTRAINT [CK_work_shifts_time]
GO
ALTER TABLE [dbo].[payroll_batches] ADD  CONSTRAINT [UQ_batches_period_key] UNIQUE NONCLUSTERED 
(
	[batch_id] ASC,
	[period_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[payroll_batches] ADD  CONSTRAINT [UQ_batches_version] UNIQUE NONCLUSTERED 
(
	[period_id] ASC,
	[version_no] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE UNIQUE NONCLUSTERED INDEX [UX_batches_one_draft] ON [dbo].[payroll_batches]
(
	[period_id] ASC
)
WHERE ([status]='NHAP')
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[payroll_details] ADD  CONSTRAINT [UQ_payroll_details_employee] UNIQUE NONCLUSTERED 
(
	[batch_id] ASC,
	[employee_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
CREATE NONCLUSTERED INDEX [IX_payroll_details_employee] ON [dbo].[payroll_details]
(
	[employee_id] ASC,
	[batch_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[payroll_lines] ADD  CONSTRAINT [UQ_payroll_lines_number] UNIQUE NONCLUSTERED 
(
	[payroll_detail_id] ASC,
	[line_no] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[payroll_periods] ADD  CONSTRAINT [UQ_payroll_periods_code] UNIQUE NONCLUSTERED 
(
	[period_code] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[payroll_periods] ADD  CONSTRAINT [UQ_payroll_periods_dates] UNIQUE NONCLUSTERED 
(
	[period_start] ASC,
	[period_end] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[payslips] ADD  CONSTRAINT [UQ_payslips_detail] UNIQUE NONCLUSTERED 
(
	[payroll_detail_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[payslips] ADD  CONSTRAINT [UQ_payslips_number] UNIQUE NONCLUSTERED 
(
	[payslip_number] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[payroll_batches] ADD  CONSTRAINT [DF_payroll_batches_version]  DEFAULT ((1)) FOR [version_no]
GO
ALTER TABLE [dbo].[payroll_batches] ADD  CONSTRAINT [DF_payroll_batches_status]  DEFAULT ('NHAP') FOR [status]
GO
ALTER TABLE [dbo].[payroll_batches] ADD  CONSTRAINT [DF_payroll_batches_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[payroll_details] ADD  CONSTRAINT [DF_payroll_details_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[payroll_lines] ADD  CONSTRAINT [DF_payroll_lines_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[payroll_periods] ADD  CONSTRAINT [DF_payroll_periods_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[payroll_snapshots] ADD  CONSTRAINT [DF_payroll_snapshots_schema]  DEFAULT ((1)) FOR [schema_version]
GO
ALTER TABLE [dbo].[payroll_snapshots] ADD  CONSTRAINT [DF_payroll_snapshots_captured]  DEFAULT (sysutcdatetime()) FOR [captured_at]
GO
ALTER TABLE [dbo].[payroll_snapshots] ADD  CONSTRAINT [DF_payroll_snapshots_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[payslips] ADD  CONSTRAINT [DF_payslips_issued]  DEFAULT (sysutcdatetime()) FOR [issued_at]
GO
ALTER TABLE [dbo].[payslips] ADD  CONSTRAINT [DF_payslips_created]  DEFAULT (sysutcdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[payroll_batches]  WITH CHECK ADD  CONSTRAINT [FK_batches_locker] FOREIGN KEY([locked_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[payroll_batches] CHECK CONSTRAINT [FK_batches_locker]
GO
ALTER TABLE [dbo].[payroll_batches]  WITH CHECK ADD  CONSTRAINT [FK_batches_period] FOREIGN KEY([period_id])
REFERENCES [dbo].[payroll_periods] ([period_id])
GO
ALTER TABLE [dbo].[payroll_batches] CHECK CONSTRAINT [FK_batches_period]
GO
ALTER TABLE [dbo].[payroll_batches]  WITH CHECK ADD  CONSTRAINT [FK_batches_previous_period] FOREIGN KEY([previous_batch_id], [period_id])
REFERENCES [dbo].[payroll_batches] ([batch_id], [period_id])
GO
ALTER TABLE [dbo].[payroll_batches] CHECK CONSTRAINT [FK_batches_previous_period]
GO
ALTER TABLE [dbo].[payroll_batches]  WITH CHECK ADD  CONSTRAINT [FK_batches_publisher] FOREIGN KEY([published_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[payroll_batches] CHECK CONSTRAINT [FK_batches_publisher]
GO
ALTER TABLE [dbo].[payroll_batches]  WITH CHECK ADD  CONSTRAINT [FK_payroll_batches_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[payroll_batches] CHECK CONSTRAINT [FK_payroll_batches_creator]
GO
ALTER TABLE [dbo].[payroll_batches]  WITH CHECK ADD  CONSTRAINT [FK_payroll_batches_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[payroll_batches] CHECK CONSTRAINT [FK_payroll_batches_updater]
GO
ALTER TABLE [dbo].[payroll_details]  WITH CHECK ADD  CONSTRAINT [FK_payroll_details_batch] FOREIGN KEY([batch_id])
REFERENCES [dbo].[payroll_batches] ([batch_id])
GO
ALTER TABLE [dbo].[payroll_details] CHECK CONSTRAINT [FK_payroll_details_batch]
GO
ALTER TABLE [dbo].[payroll_details]  WITH CHECK ADD  CONSTRAINT [FK_payroll_details_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[payroll_details] CHECK CONSTRAINT [FK_payroll_details_creator]
GO
ALTER TABLE [dbo].[payroll_details]  WITH CHECK ADD  CONSTRAINT [FK_payroll_details_employee] FOREIGN KEY([employee_id])
REFERENCES [dbo].[employees] ([employee_id])
GO
ALTER TABLE [dbo].[payroll_details] CHECK CONSTRAINT [FK_payroll_details_employee]
GO
ALTER TABLE [dbo].[payroll_details]  WITH CHECK ADD  CONSTRAINT [FK_payroll_details_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[payroll_details] CHECK CONSTRAINT [FK_payroll_details_updater]
GO
ALTER TABLE [dbo].[payroll_lines]  WITH CHECK ADD  CONSTRAINT [FK_payroll_lines_component] FOREIGN KEY([component_id])
REFERENCES [dbo].[salary_components] ([component_id])
GO
ALTER TABLE [dbo].[payroll_lines] CHECK CONSTRAINT [FK_payroll_lines_component]
GO
ALTER TABLE [dbo].[payroll_lines]  WITH CHECK ADD  CONSTRAINT [FK_payroll_lines_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[payroll_lines] CHECK CONSTRAINT [FK_payroll_lines_creator]
GO
ALTER TABLE [dbo].[payroll_lines]  WITH CHECK ADD  CONSTRAINT [FK_payroll_lines_detail] FOREIGN KEY([payroll_detail_id])
REFERENCES [dbo].[payroll_details] ([payroll_detail_id])
GO
ALTER TABLE [dbo].[payroll_lines] CHECK CONSTRAINT [FK_payroll_lines_detail]
GO
ALTER TABLE [dbo].[payroll_lines]  WITH CHECK ADD  CONSTRAINT [FK_payroll_lines_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[payroll_lines] CHECK CONSTRAINT [FK_payroll_lines_updater]
GO
ALTER TABLE [dbo].[payroll_periods]  WITH CHECK ADD  CONSTRAINT [FK_payroll_periods_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[payroll_periods] CHECK CONSTRAINT [FK_payroll_periods_creator]
GO
ALTER TABLE [dbo].[payroll_periods]  WITH CHECK ADD  CONSTRAINT [FK_payroll_periods_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[payroll_periods] CHECK CONSTRAINT [FK_payroll_periods_updater]
GO
ALTER TABLE [dbo].[payroll_snapshots]  WITH CHECK ADD  CONSTRAINT [FK_payroll_snapshots_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[payroll_snapshots] CHECK CONSTRAINT [FK_payroll_snapshots_creator]
GO
ALTER TABLE [dbo].[payroll_snapshots]  WITH CHECK ADD  CONSTRAINT [FK_payroll_snapshots_detail] FOREIGN KEY([payroll_detail_id])
REFERENCES [dbo].[payroll_details] ([payroll_detail_id])
GO
ALTER TABLE [dbo].[payroll_snapshots] CHECK CONSTRAINT [FK_payroll_snapshots_detail]
GO
ALTER TABLE [dbo].[payroll_snapshots]  WITH CHECK ADD  CONSTRAINT [FK_payroll_snapshots_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[payroll_snapshots] CHECK CONSTRAINT [FK_payroll_snapshots_updater]
GO
ALTER TABLE [dbo].[payslips]  WITH CHECK ADD  CONSTRAINT [FK_payslips_creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[payslips] CHECK CONSTRAINT [FK_payslips_creator]
GO
ALTER TABLE [dbo].[payslips]  WITH CHECK ADD  CONSTRAINT [FK_payslips_detail] FOREIGN KEY([payroll_detail_id])
REFERENCES [dbo].[payroll_details] ([payroll_detail_id])
GO
ALTER TABLE [dbo].[payslips] CHECK CONSTRAINT [FK_payslips_detail]
GO
ALTER TABLE [dbo].[payslips]  WITH CHECK ADD  CONSTRAINT [FK_payslips_updater] FOREIGN KEY([updated_by])
REFERENCES [dbo].[accounts] ([account_id])
GO
ALTER TABLE [dbo].[payslips] CHECK CONSTRAINT [FK_payslips_updater]
GO
ALTER TABLE [dbo].[payroll_batches]  WITH CHECK ADD  CONSTRAINT [CK_batches_lock] CHECK  (([status]='NHAP' AND [locked_by] IS NULL AND [locked_at] IS NULL OR [status]='DA_CHOT' AND [locked_by] IS NOT NULL AND [locked_at] IS NOT NULL))
GO
ALTER TABLE [dbo].[payroll_batches] CHECK CONSTRAINT [CK_batches_lock]
GO
ALTER TABLE [dbo].[payroll_batches]  WITH CHECK ADD  CONSTRAINT [CK_batches_publication] CHECK  (([published_by] IS NULL AND [published_at] IS NULL OR [published_by] IS NOT NULL AND [published_at] IS NOT NULL AND [status]='DA_CHOT' AND [published_at]>=[locked_at]))
GO
ALTER TABLE [dbo].[payroll_batches] CHECK CONSTRAINT [CK_batches_publication]
GO
ALTER TABLE [dbo].[payroll_batches]  WITH CHECK ADD  CONSTRAINT [CK_batches_version] CHECK  (([version_no]=(1) AND [previous_batch_id] IS NULL AND [adjustment_reason] IS NULL OR [version_no]>(1) AND [previous_batch_id] IS NOT NULL AND [adjustment_reason] IS NOT NULL AND len(ltrim(rtrim([adjustment_reason])))>(0) AND [previous_batch_id]<>[batch_id]))
GO
ALTER TABLE [dbo].[payroll_batches] CHECK CONSTRAINT [CK_batches_version]
GO
ALTER TABLE [dbo].[payroll_lines]  WITH CHECK ADD  CONSTRAINT [CK_payroll_lines_direction] CHECK  (([direction_snapshot]='DEDUCT' OR [direction_snapshot]='ADD'))
GO
ALTER TABLE [dbo].[payroll_lines] CHECK CONSTRAINT [CK_payroll_lines_direction]
GO
ALTER TABLE [dbo].[payroll_lines]  WITH CHECK ADD  CONSTRAINT [CK_payroll_lines_values] CHECK  (([line_no]>(0) AND [amount]>=(0) AND ([quantity] IS NULL OR [quantity]>=(0)) AND ([unit_rate] IS NULL OR [unit_rate]>=(0)) AND ([multiplier] IS NULL OR [multiplier]>(0))))
GO
ALTER TABLE [dbo].[payroll_lines] CHECK CONSTRAINT [CK_payroll_lines_values]
GO
ALTER TABLE [dbo].[payroll_periods]  WITH CHECK ADD  CONSTRAINT [CK_payroll_periods_dates] CHECK  (([period_end]>[period_start]))
GO
ALTER TABLE [dbo].[payroll_periods] CHECK CONSTRAINT [CK_payroll_periods_dates]
GO
ALTER TABLE [dbo].[payroll_snapshots]  WITH CHECK ADD  CONSTRAINT [CK_payroll_snapshots_json] CHECK  ((isjson([input_snapshot_json])=(1)))
GO
ALTER TABLE [dbo].[payroll_snapshots] CHECK CONSTRAINT [CK_payroll_snapshots_json]
GO
ALTER TABLE [dbo].[payroll_snapshots]  WITH CHECK ADD  CONSTRAINT [CK_payroll_snapshots_schema] CHECK  (([schema_version]>(0)))
GO
ALTER TABLE [dbo].[payroll_snapshots] CHECK CONSTRAINT [CK_payroll_snapshots_schema]
GO
