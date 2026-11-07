. (Join-Path $PSScriptRoot 'db-common.ps1')
try {
 $readme=Get-Content (Join-Path $root 'HRM_System/database/README_TAI_KHOAN.md') -Raw
 $hash=[regex]::Match($readme,"password_hash = '([^']+)'").Groups[1].Value
 if(-not $hash.StartsWith('$2')){throw 'README hash missing'}
 $cmd=$connection.CreateCommand();$cmd.CommandText="UPDATE a SET is_active=1,password_hash=@hash,token_version=token_version+1,updated_at=SYSUTCDATETIME() FROM dbo.accounts a JOIN dbo.employees e ON e.employee_id=a.employee_id JOIN dbo.roles r ON r.role_id=a.role_id WHERE a.username='admin.sample' AND r.role_code='ADMIN' AND e.employment_status='DANG_LAM'";$null=$cmd.Parameters.AddWithValue('@hash',$hash);$n=$cmd.ExecuteNonQuery()
 @{database='hrm_test';exception='Account preparation per README_TAI_KHOAN.md; restricted to admin.sample only';rows=$n;fields=@('is_active','password_hash (value hidden)','token_version','updated_at')}|ConvertTo-Json |Set-Content (Join-Path $PSScriptRoot 'evidence/account-preparation.json')
 Write-Output ('Prepared sample test administrator: '+$n+' row; no password exported.')
}finally{$connection.Dispose()}
