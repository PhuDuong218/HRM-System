-- SQL Server regression check. All contract updates are rolled back.
SET NOCOUNT ON;
DECLARE @id bigint = (SELECT TOP (1) contract_id FROM dbo.contracts
    WHERE status = 'DA_KY' AND termination_reason_type IS NULL
      AND end_date > DATEADD(day, 3, start_date)
    ORDER BY contract_id);
IF @id IS NULL THROW 51980, 'A signed fixed-term contract is required for rollback tests.', 1;
SELECT * INTO #contract_before FROM dbo.contracts WHERE contract_id = @id;
DECLARE @cases TABLE (n int, label varchar(100), statement nvarchar(max), rejected bit);
INSERT INTO @cases VALUES
(1, 'shorten on first termination', N'UPDATE dbo.contracts SET end_date=DATEADD(day,-1,end_date), termination_reason_type=''THOA_THUAN'' WHERE contract_id=@id', 0),
(2, 'retroactive termination closes status', N'UPDATE dbo.contracts SET end_date=start_date, status=''DA_KET_THUC'', termination_reason_type=''NLD_DON_PHUONG'' WHERE contract_id=@id', 0),
(3, 'no reason cannot rewrite dates', N'UPDATE dbo.contracts SET end_date=DATEADD(day,-1,end_date) WHERE contract_id=@id', 1),
(4, 'termination cannot extend dates', N'UPDATE dbo.contracts SET end_date=DATEADD(day,1,end_date), termination_reason_type=''THOA_THUAN'' WHERE contract_id=@id', 1),
(5, 'cannot change signed number', N'UPDATE dbo.contracts SET contract_number=contract_number+''X'' WHERE contract_id=@id', 1),
(6, 'cannot change signed date with termination', N'UPDATE dbo.contracts SET signed_date=DATEADD(day,-1,signed_date), end_date=DATEADD(day,-1,end_date), termination_reason_type=''THOA_THUAN'' WHERE contract_id=@id', 1),
(7, 'cannot terminate twice', N'UPDATE dbo.contracts SET end_date=DATEADD(day,-1,end_date), termination_reason_type=''THOA_THUAN'' WHERE contract_id=@id; UPDATE dbo.contracts SET end_date=DATEADD(day,-1,end_date) WHERE contract_id=@id', 1),
(8, 'normal expiry keeps original date', N'UPDATE dbo.contracts SET status=''DA_KET_THUC'', termination_reason_type=''HET_HAN'' WHERE contract_id=@id', 0),
(9, 'same-date termination is allowed', N'UPDATE dbo.contracts SET termination_reason_type=''THOA_THUAN'' WHERE contract_id=@id', 0),
(10, 'cannot remove a fixed end date', N'UPDATE dbo.contracts SET end_date=NULL, termination_reason_type=''THOA_THUAN'' WHERE contract_id=@id', 1);
DECLARE @n int=1, @label varchar(100), @statement nvarchar(max), @rejected bit;
WHILE @n <= 10
BEGIN
    SELECT @label=label, @statement=statement, @rejected=rejected FROM @cases WHERE n=@n;
    BEGIN TRY
        BEGIN TRANSACTION;
        EXEC sp_executesql @statement, N'@id bigint', @id;
        ROLLBACK;
        IF @rejected=1 THROW 51981, 'Expected history protection did not reject the update.', 1;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK;
        IF @rejected<>1 OR ERROR_NUMBER()<>51011 THROW;
    END CATCH;
    IF EXISTS (SELECT * FROM dbo.contracts WHERE contract_id=@id EXCEPT SELECT * FROM #contract_before)
        THROW 51982, 'Rollback failed to restore the contract.', 1;
    PRINT CONCAT('PASS ', @n, ': ', @label);
    SET @n+=1;
END;
DROP TABLE #contract_before;
PRINT 'All 10 cases passed; contract rows unchanged.';
