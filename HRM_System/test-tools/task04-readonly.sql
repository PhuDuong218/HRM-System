-- Read-only SQL Server smoke check, generated from ContractWarningRepository SQL blocks.
-- No schema/data writes. @today should match the backend business date when comparing counts.
SET NOCOUNT ON;
DECLARE @today date=CONVERT(date,SYSDATETIME());
DECLARE @until date=DATEADD(day,30,@today),@offset bigint=0,@size int=10;
SELECT @today AS report_date,@until AS expiring_through;
SELECT name FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.contracts') AND name='IX_contracts_employee_dates';
select count(*) AS expiring_total from dbo.contracts c
join dbo.employees e on e.employee_id=c.employee_id
where e.employment_status='DANG_LAM' and c.status='DA_KY'
and c.end_date>=@today and c.end_date<=@until;
select c.contract_id,e.employee_id,e.employee_code,e.full_name,c.end_date
from dbo.contracts c
join dbo.employees e on e.employee_id=c.employee_id
where e.employment_status='DANG_LAM' and c.status='DA_KY'
and c.end_date>=@today and c.end_date<=@until
order by c.end_date asc,c.contract_id asc
offset @offset rows fetch next @size rows only;
select count(*) AS without_active_total from dbo.employees e
where e.employment_status='DANG_LAM'
and not exists (
select 1 from dbo.contracts active_contract
where active_contract.employee_id=e.employee_id
and active_contract.start_date<=@today
and (active_contract.end_date is null or active_contract.end_date>=@today)
);
select e.employee_id,e.employee_code,e.full_name,
(select max(past.end_date) from dbo.contracts past
where past.employee_id=e.employee_id and past.end_date<@today) as last_contract_end_date,
(select min(future.start_date) from dbo.contracts future
where future.employee_id=e.employee_id and future.start_date>@today) as next_contract_start
from dbo.employees e
where e.employment_status='DANG_LAM'
and not exists (
select 1 from dbo.contracts active_contract
where active_contract.employee_id=e.employee_id
and active_contract.start_date<=@today
and (active_contract.end_date is null or active_contract.end_date>=@today)
)
order by e.employee_id asc
offset @offset rows fetch next @size rows only;
